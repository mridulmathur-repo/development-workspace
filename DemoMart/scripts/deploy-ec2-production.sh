#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    echo 'Run this deployment script with sudo.' >&2
    exit 1
fi

if [[ "$#" -ne 2 ]]; then
    echo 'Usage: deploy-ec2-production.sh <staging-directory> <release-id>' >&2
    exit 1
fi

STAGING_ROOT="$(realpath "$1")"
RELEASE_ID="$2"
APP_ROOT='/opt/demomart'
DATA_ROOT='/var/lib/demomart'
CONFIG_ROOT='/etc/demomart'
CURRENT_LINK="${APP_ROOT}/current"
RELEASE_ROOT="${APP_ROOT}/releases/${RELEASE_ID}"

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

for command_name in java curl nginx systemctl; do
    command -v "${command_name}" >/dev/null || {
        echo "Required command is missing: ${command_name}" >&2
        exit 1
    }
done

required_files=(
    artifacts/service-registry.jar
    artifacts/identity-service.jar
    artifacts/catalog-service.jar
    artifacts/order-service.jar
    artifacts/api-gateway.jar
    frontend-root/demomart/index.html
    nginx/demomart.conf
    systemd/demomart-registry.service
    systemd/demomart-identity.service
    systemd/demomart-catalog.service
    systemd/demomart-order.service
    systemd/demomart-gateway.service
    demomart.env
)
for required_file in "${required_files[@]}"; do
    if [[ ! -f "${STAGING_ROOT}/${required_file}" ]]; then
        echo "Deployment bundle is missing ${required_file}" >&2
        exit 1
    fi
done

if ! grep -Eq '^JWT_SECRET=.{32,}$' "${STAGING_ROOT}/demomart.env"; then
    echo 'JWT_SECRET must be a single-line value of at least 32 characters.' >&2
    exit 1
fi
if ! grep -Eq '^INTERNAL_SERVICE_KEY=.{32,}$' "${STAGING_ROOT}/demomart.env"; then
    echo 'INTERNAL_SERVICE_KEY must be a single-line value of at least 32 characters.' >&2
    exit 1
fi

if ! id -u demomart >/dev/null 2>&1; then
    useradd --system --home-dir "${DATA_ROOT}" --shell /sbin/nologin demomart
fi

install -d -o root -g root -m 755 "${APP_ROOT}" "${APP_ROOT}/releases" "${CONFIG_ROOT}"
for data_directory in registry identity catalog orders gateway; do
    install -d -o demomart -g demomart -m 750 "${DATA_ROOT}/${data_directory}"
done

if [[ -e "${RELEASE_ROOT}" ]]; then
    echo "Release already exists and will not be overwritten: ${RELEASE_ROOT}" >&2
    exit 1
fi

install -d -o root -g root -m 755 "${RELEASE_ROOT}"
install -m 640 -o root -g demomart "${STAGING_ROOT}/artifacts/"*.jar "${RELEASE_ROOT}/"
cp -a "${STAGING_ROOT}/frontend-root" "${RELEASE_ROOT}/frontend-root"
chown -R root:root "${RELEASE_ROOT}/frontend-root"
find "${RELEASE_ROOT}/frontend-root" -type d -exec chmod 755 {} +
find "${RELEASE_ROOT}/frontend-root" -type f -exec chmod 644 {} +

install -m 600 -o root -g root "${STAGING_ROOT}/demomart.env" "${CONFIG_ROOT}/demomart.env"
for unit_file in "${STAGING_ROOT}/systemd/"*.service; do
    install -m 644 -o root -g root "${unit_file}" "/etc/systemd/system/$(basename "${unit_file}")"
done
install -d -o root -g root -m 755 /etc/nginx/default.d
install -m 644 -o root -g root "${STAGING_ROOT}/nginx/demomart.conf" /etc/nginx/default.d/demomart.conf

previous_release=''
if [[ -L "${CURRENT_LINK}" ]]; then
    previous_release="$(readlink -f "${CURRENT_LINK}")"
fi

deployment_switched=false
rollback_on_error() {
    local exit_code=$?
    trap - EXIT
    if [[ "${exit_code}" -ne 0 && "${deployment_switched}" == true && -n "${previous_release}" ]]; then
        echo "Deployment failed. Restoring ${previous_release}." >&2
        ln -s "${previous_release}" "${CURRENT_LINK}.rollback"
        mv -Tf "${CURRENT_LINK}.rollback" "${CURRENT_LINK}"
        systemctl restart demomart-registry demomart-identity demomart-catalog demomart-order demomart-gateway || true
        nginx -t && systemctl reload nginx || true
    fi
    exit "${exit_code}"
}
trap rollback_on_error EXIT

rm -f "${CURRENT_LINK}.new"
ln -s "${RELEASE_ROOT}" "${CURRENT_LINK}.new"
mv -Tf "${CURRENT_LINK}.new" "${CURRENT_LINK}"
deployment_switched=true

services=(
    demomart-registry
    demomart-identity
    demomart-catalog
    demomart-order
    demomart-gateway
)

systemctl daemon-reload
systemctl enable "${services[@]}"

wait_for_health() {
    local service_name="$1"
    local health_url="$2"
    local attempts=36
    for ((attempt = 1; attempt <= attempts; attempt++)); do
        if curl --fail --silent --show-error --max-time 5 "${health_url}" | grep -q '"status":"UP"'; then
            echo "${service_name} is UP."
            return 0
        fi
        sleep 5
    done
    echo "${service_name} did not become healthy: ${health_url}" >&2
    systemctl status "${service_name}" --no-pager || true
    journalctl -u "${service_name}" -n 80 --no-pager || true
    return 1
}

systemctl restart demomart-registry
wait_for_health demomart-registry http://127.0.0.1:8762/actuator/health

for service_name in demomart-identity demomart-catalog demomart-order demomart-gateway; do
    systemctl restart "${service_name}"
done

wait_for_health demomart-identity http://127.0.0.1:8091/actuator/health
wait_for_health demomart-catalog http://127.0.0.1:8092/actuator/health
wait_for_health demomart-order http://127.0.0.1:8093/actuator/health
wait_for_health demomart-gateway http://127.0.0.1:8090/actuator/health

nginx -t
systemctl enable --now nginx
systemctl reload nginx

if ! curl --fail --silent --show-error --max-time 10 http://127.0.0.1/demomart/ | grep -q 'DemoMart'; then
    echo 'Nginx did not serve the DemoMart page at /demomart/.' >&2
    exit 1
fi

deployment_switched=false
trap - EXIT
echo "DemoMart release ${RELEASE_ID} deployed successfully."
echo 'Open http://<EC2-PUBLIC-IP>/demomart/'
