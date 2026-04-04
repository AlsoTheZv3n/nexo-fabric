FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY fabric-engine/pom.xml fabric-engine/pom.xml
COPY fabric-events/pom.xml fabric-events/pom.xml
COPY fabric-resolution/pom.xml fabric-resolution/pom.xml
COPY fabric-query/pom.xml fabric-query/pom.xml
COPY fabric-api/pom.xml fabric-api/pom.xml

RUN apk add --no-cache maven \
    && mvn dependency:go-offline -pl fabric-api -am -B 2>/dev/null || true

COPY fabric-engine/src fabric-engine/src
COPY fabric-events/src fabric-events/src
COPY fabric-resolution/src fabric-resolution/src
COPY fabric-query/src fabric-query/src
COPY fabric-api/src fabric-api/src

RUN mvn clean package -pl fabric-api -am -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache libgcc libstdc++
WORKDIR /app
COPY --from=build /app/fabric-api/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
