# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn/ .mvn/

RUN sed -i 's/\r$//' mvnw

COPY src/ src/

RUN --mount=type=cache,target=/root/.m2 sh ./mvnw -B -ntp verify

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 vietfresh \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin vietfresh \
    && mkdir -p /app/logs/access /app/uploads/product \
    && chown -R 10001:10001 /app

COPY --from=build --chown=10001:10001 /workspace/target/*.jar app.jar

EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
LABEL io.vietfresh.deployment="1"
USER 10001:10001

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=4 \
    CMD curl --fail --silent http://127.0.0.1:8081/actuator/health/readiness > /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
