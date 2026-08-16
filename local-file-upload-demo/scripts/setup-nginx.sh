#!/usr/bin/env bash
set -euo pipefail

# Public web entry point for the Spring Boot application.
# Spring Boot remains private on 127.0.0.1:8082; Nginx receives public HTTP on port 80.

sudo dnf install -y nginx

sudo rm -f /etc/nginx/conf.d/file-upload.conf
sudo mkdir -p /etc/nginx/default.d

# Amazon Linux already has the default server for port 80 in nginx.conf.
# Add the proxy location inside that server instead of defining a second server.
sudo tee /etc/nginx/default.d/file-upload.conf > /dev/null <<'NGINX'
location / {
    proxy_pass http://127.0.0.1:8082;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
NGINX

sudo nginx -t
sudo systemctl enable --now nginx

echo 'Nginx is running. Open http://<your-ec2-public-ip> in a browser.'
