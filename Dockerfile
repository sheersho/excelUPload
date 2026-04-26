# Multi-stage build: Java 21 Maven
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime: Java 21 minimal image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Copy built jar from builder stage
COPY --from=builder /app/target/excelUPload-1.0-SNAPSHOT.jar app.jar

# Create directories for file uploads and data
RUN mkdir -p /app/uploads /app/data

# Expose port (Render will assign this dynamically, but 8080 is default)
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/api/records?limit=1 || exit 1

# Run Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]

