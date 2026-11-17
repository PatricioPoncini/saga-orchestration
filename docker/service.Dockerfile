# syntax=docker/dockerfile:1.7
FROM gradle:8.12-jdk21 AS build
WORKDIR /workspace
COPY settings.gradle.kts gradle.properties ./
COPY buildSrc buildSrc
COPY messaging messaging
COPY order-service order-service
COPY payment-service payment-service
COPY inventory-service inventory-service
COPY saga-orchestrator saga-orchestrator
RUN --mount=type=cache,target=/home/gradle/.gradle/caches \
    gradle bootJar --no-daemon -q

FROM eclipse-temurin:21-jre
ARG SERVICE
WORKDIR /app
COPY --from=build /workspace/${SERVICE}/build/libs/app.jar app.jar
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
