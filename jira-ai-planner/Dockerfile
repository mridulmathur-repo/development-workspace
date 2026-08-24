FROM node:22-alpine AS ui-build

WORKDIR /ui
COPY ui/package.json ui/package-lock.json ./
RUN npm ci
COPY ui/ ./
RUN npm run build

FROM eclipse-temurin:17-jdk-alpine

RUN apk add --no-cache curl git openssh-client maven nodejs npm \
    && addgroup -S -g 10001 planner \
    && adduser -S -D -H -u 10001 -G planner planner \
    && mkdir -p /app/data \
    && chown -R planner:planner /app \
    && git config --system --add safe.directory /workspace

WORKDIR /app
COPY target/jira-ai-planner-*.jar app.jar
COPY --from=ui-build /ui/dist /app/ui

USER planner
EXPOSE 8100

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8100/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
