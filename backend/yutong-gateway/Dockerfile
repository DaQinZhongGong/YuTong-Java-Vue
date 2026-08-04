# YuTong Gateway Dockerfile
# Multi-stage build: build with maven, runtime with minimal JRE
# Non-root user, no package manager cache, fixed base image

# ===== Stage 1: Build =====
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /build
# Copy only pom files first to cache dependencies
COPY backend/pom.xml ./pom.xml
COPY backend/yutong-common/pom.xml ./yutong-common/pom.xml
COPY backend/yutong-infra/pom.xml ./yutong-infra/pom.xml
COPY backend/yutong-api/pom.xml ./yutong-api/pom.xml
COPY backend/yutong-auth-adapter/pom.xml ./yutong-auth-adapter/pom.xml
COPY backend/yutong-sample-service/pom.xml ./yutong-sample-service/pom.xml
COPY backend/yutong-lowcode-service/pom.xml ./yutong-lowcode-service/pom.xml
COPY backend/yutong-ai-service/pom.xml ./yutong-ai-service/pom.xml
COPY backend/yutong-system-service/pom.xml ./yutong-system-service/pom.xml
COPY backend/yutong-workflow-service/pom.xml ./yutong-workflow-service/pom.xml
COPY backend/yutong-boot/pom.xml ./yutong-boot/pom.xml
COPY backend/yutong-gateway/pom.xml ./yutong-gateway/pom.xml
RUN mvn -B -q dependency:go-offline -DskipTests || true
# Copy source and build
COPY backend/ ./
RUN mvn -B -q -pl yutong-gateway -am package -Dmaven.test.skip=true

# ===== Stage 2: Runtime =====
FROM eclipse-temurin:25-jre-jammy
LABEL org.opencontainers.image.title="yutong-gateway" \
      org.opencontainers.image.description="YuTong platform API gateway" \
      org.opencontainers.image.source="https://github.com/yutong/yutong-java-vue"

# Create non-root user (uid 1000) + install wget for health check
RUN groupadd -r yutong && useradd -r -g yutong -u 1000 -d /app -s /sbin/nologin yutong \
    && apt-get update && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
# Copy built jar
COPY --from=builder /build/yutong-gateway/target/yutong-gateway-*.jar /app/yutong-gateway.jar

# Expose port
EXPOSE 8080

# Switch to non-root user
USER yutong

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1

# JVM args: container-aware, graceful shutdown
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom -Dserver.shutdown.grace-period=30s"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/yutong-gateway.jar"]
