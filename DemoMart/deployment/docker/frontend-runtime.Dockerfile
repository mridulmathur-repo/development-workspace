# syntax=docker/dockerfile:1

FROM nginx:stable-alpine
COPY docker/frontend-nginx.conf /etc/nginx/conf.d/default.conf
COPY frontend-root/demomart /usr/share/nginx/html

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=12 \
    CMD wget -q -O - http://127.0.0.1:8080/health | grep -q 'UP'

