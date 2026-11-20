# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the application ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependencies first, so editing source code does not re-download them.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Tests are not run here; the CI pipeline runs them before an image is built.
COPY src/ src/
RUN ./mvnw -B -q -DskipTests package && cp target/fintrack-backend-*.jar app.jar

# ---- Runtime stage: a small JRE image running as a non-root user ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S fintrack && adduser -S fintrack -G fintrack
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER fintrack

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# Configuration (database URL, credentials, JWT secret) comes from environment variables.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
