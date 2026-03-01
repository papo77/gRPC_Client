# Multi-stage Dockerfile for Java gRPC Client

# Build stage
FROM eclipse-temurin:21-jdk AS builder

# Set working directory
WORKDIR /app

# Copy Maven configuration
COPY pom.xml ./
COPY src ./src/

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -B

# Build the application
RUN mvn clean package -DskipTests -B

# Runtime stage  
FROM eclipse-temurin:21-jre

# Set working directory
WORKDIR /app

# Copy the built jar from build stage
COPY --from=builder /app/target/grpc-client-1.0.0.jar ./grpc-client.jar

# Copy configuration and data files
COPY --from=builder /app/src/main/resources/application.yml ./
COPY names.csv ./

# Create output directory
RUN mkdir -p /app/output

# Set JVM options for container environment
ENV JVM_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=80.0"

# Expose any management ports if needed (optional)
EXPOSE 8080

# Set the default command
ENTRYPOINT ["sh", "-c", "java ${JVM_OPTS} -jar grpc-client.jar"]

# Health check (optional)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD java ${JVM_OPTS} -cp grpc-client.jar com.example.grpcclient.GrpcClientApplication || exit 1

# Labels for metadata
LABEL maintainer="grpc-client-team"
LABEL version="1.0.0"
LABEL description="Java SpringBoot gRPC Client with bidirectional streaming support"