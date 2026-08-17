# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-17 AS build

ARG SERVICE
WORKDIR /workspace

COPY pom.xml ./pom.xml
COPY service-registry/pom.xml ./service-registry/pom.xml
COPY api-gateway/pom.xml ./api-gateway/pom.xml
COPY identity-service/pom.xml ./identity-service/pom.xml
COPY catalog-service/pom.xml ./catalog-service/pom.xml
COPY order-service/pom.xml ./order-service/pom.xml

RUN test -n "${SERVICE}" \
    && mvn -B -ntp -pl "${SERVICE}" -am dependency:go-offline

COPY ${SERVICE}/src ./${SERVICE}/src
RUN mvn -B -ntp -pl "${SERVICE}" -am clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine

ARG SERVICE
RUN apk add --no-cache curl \
    && addgroup -S -g 10001 demomart \
    && adduser -S -D -H -u 10001 -G demomart demomart \
    && mkdir -p /app/data \
    && chown -R demomart:demomart /app

WORKDIR /app
COPY --from=build --chown=demomart:demomart /workspace/${SERVICE}/target/${SERVICE}-0.0.1-SNAPSHOT.jar /app/app.jar

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

