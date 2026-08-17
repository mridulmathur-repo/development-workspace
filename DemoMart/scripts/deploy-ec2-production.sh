#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    echo 'Run this deployment script with sudo.' >&2
    exit 1
fi

if [[ "$#" -ne 4 ]]; then
    echo 'Usage: deploy-ec2-production.sh <staging-directory> <release-id> <aws-region> <secret-id>' >&2
    exit 1
fi

STAGING_ROOT="$(realpath "$1")"
RELEASE_ID="$2"
AWS_REGION="$3"
SECRET_ID="$4"
APP_ROOT='/opt/demomart'
DATA_ROOT='/var/lib/demomart'
CONFIG_ROOT='/etc/demomart'
CURRENT_LINK="${APP_ROOT}/current"
RELEASE_ROOT="${APP_ROOT}/releases/${RELEASE_ID}"
SECRETS_FILE="${CONFIG_ROOT}/demomart.env"
COMPOSE_RELATIVE_PATH='docker/compose.production.yaml'

case "${STAGING_ROOT}" in
    /tmp/demomart-*) ;;
    *)
        echo "For safety, the staging directory must be below /tmp/demomart-. Received: ${STAGING_ROOT}" >&2
        exit 1
        ;;
esac

if [[ ! "${RELEASE_ID}" =~ ^[A-Za-z0-9._-]+$ ]]; then
    echo "Invalid release id: ${RELEASE_ID}" >&2
    exit 1
fi
if [[ ! "${AWS_REGION}" =~ ^[a-z]{2}(-gov)?-[a-z]+-[0-9]+$ ]]; then
    echo "Invalid AWS region: ${AWS_REGION}" >&2
    exit 1
fi
if [[ ! "${SECRET_ID}" =~ ^[A-Za-z0-9:/_+=.@-]+$ ]]; then
    echo 'The Secrets Manager secret id contains unsupported characters.' >&2
    exit 1
fi

for command_name in aws curl docker nginx python3 systemctl; do
    command -v "${command_name}" >/dev/null || {
        echo "Required command is missing: ${command_name}" >&2
        exit 1
    }
done
docker compose version >/dev/null 2>&1 || {
    echo 'The Docker Compose v2 plugin is required (docker compose).' >&2
    exit 1
}

required_files=(
    artifacts/service-registry.jar
    artifacts/identity-service.jar
    artifacts/catalog-service.jar
    artifacts/order-service.jar
    artifacts/api-gateway.jar
    frontend-root/demomart/index.html
    docker/java-runtime.Dockerfile
    docker/frontend-runtime.Dockerfile
    docker/frontend-nginx.conf
    docker/compose.production.yaml
    nginx/demomart.conf
)
for required_file in "${required_files[@]}"; do
    if [[ ! -f "${STAGING_ROOT}/${required_file}" ]]; then
        echo "Deployment bundle is missing ${required_file}" >&2
        exit 1
    fi
done

if ! id -u demomart >/dev/null 2>&1; then
    useradd --system --home-dir "${DATA_ROOT}" --shell /sbin/nologin demomart
fi
DEMOMART_UID="$(id -u demomart)"
DEMOMART_GID="$(id -g demomart)"

install -d -o root -g root -m 755 "${APP_ROOT}" "${APP_ROOT}/releases" "${CONFIG_ROOT}"
for data_directory in identity catalog orders; do
    install -d -o demomart -g demomart -m 750 "${DATA_ROOT}/${data_directory}"
done

if [[ -e "${RELEASE_ROOT}" ]]; then
    echo "Release already exists and will not be overwritten: ${RELEASE_ROOT}" >&2
    exit 1
fi

install -d -o root -g root -m 755 "${RELEASE_ROOT}"
cp -a "${STAGING_ROOT}/artifacts" "${RELEASE_ROOT}/artifacts"
cp -a "${STAGING_ROOT}/docker" "${RELEASE_ROOT}/docker"
cp -a "${STAGING_ROOT}/frontend-root" "${RELEASE_ROOT}/frontend-root"
cp -a "${STAGING_ROOT}/nginx" "${RELEASE_ROOT}/nginx"
chown -R root:root "${RELEASE_ROOT}"
find "${RELEASE_ROOT}" -type d -exec chmod 755 {} +
find "${RELEASE_ROOT}" -type f -exec chmod 644 {} +

echo "Retrieving ${SECRET_ID} from AWS Secrets Manager in ${AWS_REGION}."
secret_json="$(aws secretsmanager get-secret-value \
    --secret-id "${SECRET_ID}" \
    --region "${AWS_REGION}" \
    --query SecretString \
    --output text \
    --no-cli-pager)"

SECRET_JSON="${secret_json}" SECRETS_FILE="${SECRETS_FILE}" python3 <<'PY'
import json
import os
import re
from pathlib import Path

payload = json.loads(os.environ["SECRET_JSON"])
required = ("JWT_SECRET", "INTERNAL_SERVICE_KEY")
for key in required:
    value = payload.get(key)
    if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9_+/=-]{32,}", value):
        raise SystemExit(f"{key} must be a Base64-style string of at least 32 characters")

destination = Path(os.environ["SECRETS_FILE"])
temporary = destination.with_suffix(".tmp")
temporary.write_text(
    f"JWT_SECRET={payload['JWT_SECRET']}\n"
    f"INTERNAL_SERVICE_KEY={payload['INTERNAL_SERVICE_KEY']}\n",
    encoding="utf-8",
)
temporary.chmod(0o600)
temporary.replace(destination)
PY
unset secret_json SECRET_JSON
chown root:root "${SECRETS_FILE}"
chmod 600 "${SECRETS_FILE}"

previous_release=''
if [[ -L "${CURRENT_LINK}" ]]; then
    previous_release="$(readlink -f "${CURRENT_LINK}")"
fi

compose() {
    local release_root="$1"
    local image_tag="$2"
    shift 2
    DEMOMART_UID="${DEMOMART_UID}" \
    DEMOMART_GID="${DEMOMART_GID}" \
    IMAGE_TAG="${image_tag}" \
    docker compose \
        --project-name demomart \
        --env-file "${SECRETS_FILE}" \
        --file "${release_root}/${COMPOSE_RELATIVE_PATH}" \
        "$@"
}

echo "Building immutable DemoMart container images tagged ${RELEASE_ID}."
compose "${RELEASE_ROOT}" "${RELEASE_ID}" build --pull

# JARs and React build output were only image-build inputs. The final runtime
# code now lives in the versioned images, so do not retain loose copies on EC2.
rm -rf "${RELEASE_ROOT}/artifacts" "${RELEASE_ROOT}/frontend-root"

legacy_services=(
    demomart-registry
    demomart-identity
    demomart-catalog
    demomart-order
    demomart-gateway
)
legacy_was_running=false
for service_name in "${legacy_services[@]}"; do
    if systemctl is-active --quiet "${service_name}.service"; then
        legacy_was_running=true
        break
    fi
done

deployment_started=false
rollback_on_error() {
    local exit_code=$?
    trap - EXIT
    if [[ "${exit_code}" -ne 0 && "${deployment_started}" == true ]]; then
        echo 'Container deployment failed. Attempting rollback.' >&2
        compose "${RELEASE_ROOT}" "${RELEASE_ID}" down --remove-orphans || true
        if [[ -n "${previous_release}" && -f "${previous_release}/${COMPOSE_RELATIVE_PATH}" ]]; then
            local previous_tag
            previous_tag="$(basename "${previous_release}")"
            compose "${previous_release}" "${previous_tag}" up -d --no-build --remove-orphans || true
            install -m 644 -o root -g root "${previous_release}/nginx/demomart.conf" /etc/nginx/default.d/demomart.conf || true
        elif [[ "${legacy_was_running}" == true ]]; then
            systemctl start "${legacy_services[@]}" || true
        fi
        nginx -t && systemctl reload nginx || true
    fi
    exit "${exit_code}"
}
trap rollback_on_error EXIT

# The old systemd services use the same H2 files. Stop them before containers
# mount those files, then disable them only after the container stack is healthy.
systemctl stop "${legacy_services[@]}" 2>/dev/null || true
deployment_started=true
compose "${RELEASE_ROOT}" "${RELEASE_ID}" up -d --remove-orphans

wait_for_frontend() {
    local attempts=48
    for ((attempt = 1; attempt <= attempts; attempt++)); do
        if curl --fail --silent --show-error --max-time 5 http://127.0.0.1:8088/health | grep -q '"status":"UP"'; then
            echo 'DemoMart container stack is healthy.'
            return 0
        fi
        sleep 5
    done
    echo 'DemoMart containers did not become healthy.' >&2
    compose "${RELEASE_ROOT}" "${RELEASE_ID}" ps >&2 || true
    compose "${RELEASE_ROOT}" "${RELEASE_ID}" logs --tail 120 >&2 || true
    return 1
}
wait_for_frontend

install -d -o root -g root -m 755 /etc/nginx/default.d
install -m 644 -o root -g root "${RELEASE_ROOT}/nginx/demomart.conf" /etc/nginx/default.d/demomart.conf
nginx -t
systemctl enable --now nginx
systemctl reload nginx

if ! curl --fail --silent --show-error --max-time 10 http://127.0.0.1/demomart/ | grep -q 'DemoMart'; then
    echo 'Nginx did not serve the DemoMart page at /demomart/.' >&2
    exit 1
fi

rm -f "${CURRENT_LINK}.new"
ln -s "${RELEASE_ROOT}" "${CURRENT_LINK}.new"
mv -Tf "${CURRENT_LINK}.new" "${CURRENT_LINK}"
systemctl disable "${legacy_services[@]}" 2>/dev/null || true

deployment_started=false
trap - EXIT
echo "DemoMart container release ${RELEASE_ID} deployed successfully."
echo 'Open http://<EC2-PUBLIC-IP>/demomart/'
