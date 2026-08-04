# YuTong Backend Dockerfile
# Design: 20-DevOps与部署设计, 21-安全与质量设计, 73-供应链安全与SBOM详设
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
RUN mvn -B -q dependency:go-offline -DskipTests || true
# Copy source and build
COPY backend/ ./
COPY database/ /build/database/
RUN mvn -B -q -pl yutong-boot -am package -Dmaven.test.skip=true

# ===== Stage 2: Runtime =====
# 本地测试环境使用已缓存的 maven 镜像作为运行时，避免 Docker Hub 网络不可达导致无法拉取 eclipse-temurin:25-jre-jammy
FROM maven:3.9-eclipse-temurin-25
LABEL org.opencontainers.image.title="yutong-boot" \
      org.opencontainers.image.description="YuTong platform backend (single-jar)" \
      org.opencontainers.image.source="https://github.com/yutong/yutong-java-vue"

# Create non-root user (uid 1000) + install wget for health check
RUN groupadd -r yutong && useradd -r -g yutong -u 1001 -d /app -s /sbin/nologin yutong \
    && apt-get update && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
# Copy built jar
COPY --from=builder /build/yutong-boot/target/yutong-boot-*.jar /app/yutong-boot.jar
# Copy database migrations (Flyway filesystem location)
COPY --from=builder /build/database /app/database

# Expose port
EXPOSE 8080

# Switch to non-root user
USER yutong

# Health check (GA2-30: 使用 wget 替代 curl, eclipse-temurin JRE 镜像未预装 curl)
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1

# JVM args: container-aware, graceful shutdown
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom -Dserver.shutdown.grace-period=30s"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/yutong-boot.jar"]


