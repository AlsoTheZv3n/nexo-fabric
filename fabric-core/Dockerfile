# ─── Build Stage ─────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml .
COPY fabric-engine/pom.xml fabric-engine/pom.xml
COPY fabric-events/pom.xml fabric-events/pom.xml
COPY fabric-resolution/pom.xml fabric-resolution/pom.xml
COPY fabric-query/pom.xml fabric-query/pom.xml
COPY fabric-api/pom.xml fabric-api/pom.xml

RUN mvn dependency:go-offline -pl fabric-api -am -B 2>/dev/null || true

COPY fabric-engine/src fabric-engine/src
COPY fabric-events/src fabric-events/src
COPY fabric-resolution/src fabric-resolution/src
COPY fabric-query/src fabric-query/src
COPY fabric-api/src fabric-api/src

RUN mvn clean package -pl fabric-api -am -DskipTests -B

# ─── Runtime Stage ───────────────────────────────────────────────
# Ubuntu Jammy (glibc) instead of Alpine (musl) — required for DJL native libs
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# DJL model cache
VOLUME /root/.djl.ai

COPY --from=build /app/fabric-api/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-Xmx1g", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
