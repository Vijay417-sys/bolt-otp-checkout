# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Stage 1 - build the Spring Boot jar with Maven
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

# Dependencies are resolved first so the layer is cached across code changes.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------------------------------------------------------------------------
# Stage 2 - minimal Java 21 runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as a non-root user.
RUN addgroup -S spring && adduser -S spring -G spring \
    && chown -R spring:spring /app
USER spring:spring

COPY --from=build --chown=spring:spring /workspace/target/*.jar app.jar

EXPOSE 8080

# The database schema is NOT created by the container: database/schema.sql must
# be applied to MySQL first, and Hibernate only validates it.
#
# Render assigns a dynamic PORT; Spring Boot reads it through ${PORT}.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
