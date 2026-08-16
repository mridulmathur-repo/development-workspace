#!/usr/bin/env bash
set -euo pipefail

# Public web entry point for the Spring Boot application.
# Spring Boot remains private on 127.0.0.1:8082; Nginx receives public HTTP on port 80.

sudo dnf install -y nginx

sudo tee /etc/nginx/conf.d/file-upload.conf > /dev/null <<'NGINX'
server {
    listen 80;
    server_name _;

    location / {
        proxy_pass http://127.0.0.1:8082;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
NGINX

sudo nginx -t
sudo systemctl enable --now nginx

echo 'Nginx is running. Open http://<your-ec2-public-ip> in a browser.'
