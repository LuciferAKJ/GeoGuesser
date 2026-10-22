# ============================================================
# Stage 1: Build stage with Maven and Eclipse Temurin Java 17
# ============================================================
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy Maven descriptor
COPY pom.xml .

# Pre-fetch dependencies to leverage Docker layer caching
RUN mvn -B dependency:go-offline -DskipTests || true

# Copy application source code
COPY src ./src

# Clean, execute test suite, and package executable jar
RUN mvn -B clean package

# ============================================================
# Stage 2: Minimal runtime stage with Eclipse Temurin Java 17 JRE
# ============================================================
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Create dedicated non-root application user and group for security
RUN groupadd -r appgroup && useradd -r -g appgroup -d /app -s /sbin/nologin appuser

# Copy executable jar from builder stage
COPY --from=builder /build/target/geoguesser-backend-1.0.0.jar /app/app.jar

# Ensure proper non-root ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Render injects PORT; default to 10000 if unset
ENV PORT=10000
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 10000

# Launch Spring Boot with non-blocking random seed
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
