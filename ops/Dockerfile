# syntax=docker/dockerfile:1
# Multi-stage: the JDK and Gradle exist only in the build stage, so the shipped
# image carries a JRE and one jar. Nothing needs to be installed on the host.
ARG MODULE

FROM gradle:8.14.3-jdk21 AS build
ARG MODULE
WORKDIR /src
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY common-events/build.gradle.kts        common-events/build.gradle.kts
COPY gateway/build.gradle.kts              gateway/build.gradle.kts
COPY order-service/build.gradle.kts        order-service/build.gradle.kts
COPY inventory-service/build.gradle.kts    inventory-service/build.gradle.kts
COPY fulfillment-service/build.gradle.kts  fulfillment-service/build.gradle.kts
# Warm the dependency cache on the build files alone, so a source-only change
# does not re-download the world.
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle --no-daemon :${MODULE}:dependencies --configuration runtimeClasspath > /dev/null 2>&1 || true
COPY common-events/src   common-events/src
COPY ${MODULE}/src       ${MODULE}/src
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle --no-daemon :${MODULE}:bootJar -x test \
 && cp ${MODULE}/build/libs/*.jar /src/app.jar

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN useradd --system --uid 10001 --create-home freshchain \
 && apt-get update && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
USER freshchain
WORKDIR /app
COPY --from=build /src/app.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseG1GC"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
