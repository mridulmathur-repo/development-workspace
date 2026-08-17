# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jre-alpine

ARG JAR_FILE
RUN apk add --no-cache curl \
    && addgroup -S -g 10001 demomart \
    && adduser -S -D -H -u 10001 -G demomart demomart \
    && mkdir -p /app/data \
    && chown -R demomart:demomart /app

WORKDIR /app
COPY --chown=demomart:demomart ${JAR_FILE} /app/app.jar

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

