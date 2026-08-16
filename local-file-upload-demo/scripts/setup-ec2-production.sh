#!/usr/bin/env bash
set -euo pipefail

APP_USER="ec2-user"
APP_GROUP="ec2-user"
APP_ROOT="/opt/file-upload"
SERVICE_NAME="file-upload"

sudo mkdir -p "${APP_ROOT}/uploads"
sudo chown -R "${APP_USER}:${APP_GROUP}" "${APP_ROOT}"

sudo tee "/etc/systemd/system/${SERVICE_NAME}.service" > /dev/null <<'UNIT'
[Unit]
Description=File Upload Spring Boot application
After=network.target

[Service]
Type=simple
User=ec2-user
Group=ec2-user
WorkingDirectory=/opt/file-upload
Environment=FILE_STORAGE_LOCATION=/opt/file-upload/uploads
Environment=SERVER_PORT=8082
ExecStart=/usr/bin/java -jar /opt/file-upload/file-upload-app.jar --server.address=127.0.0.1
SuccessExitStatus=143
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
UNIT

sudo systemctl daemon-reload
sudo systemctl enable "${SERVICE_NAME}"

echo "EC2 production service has been configured. It will start after Jenkins copies the application JAR."
