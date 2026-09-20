# Multi-stage build: compile with Maven, then run on a lightweight JRE image.
# Render deploys Java apps via Docker (it has no native Java runtime), so this Dockerfile
# is what Render's "Docker" environment builds and runs for the back-end Web Service.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/ingredient-app.jar app.jar

# Render injects its own PORT env var at runtime; application.properties already reads
# server.port=${PORT:8080}, so no extra configuration is needed here.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
