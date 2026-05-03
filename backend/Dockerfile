# syntax=docker/dockerfile:1.6

# ---- Build stage ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    apt-get update -qq \
    && apt-get install -yqq --no-install-recommends maven \
    && mvn -B -q -DskipTests package \
    && mv target/deepaudit-backend-*.jar target/app.jar

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# C-env-003 / R7: PDFBox needs Chinese fonts to render 病案首页 PDFs in Phase 3.
# Install at the base layer so every later phase inherits font availability.
RUN apt-get update -qq \
    && DEBIAN_FRONTEND=noninteractive apt-get install -yqq --no-install-recommends \
        fonts-noto-cjk \
        fonts-noto-cjk-extra \
        ca-certificates \
        curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/target/app.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=4 \
    CMD curl -fs http://localhost:8080/api/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
