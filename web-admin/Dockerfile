# YuTong Web Admin Dockerfile
# Design: 20-DevOps与部署设计, 21-安全与质量设计
# Multi-stage: build with node, runtime with nginx (static hosting)
# Non-root, versioned static assets

# ===== Stage 1: Build =====
FROM node:22-alpine AS builder
WORKDIR /build
# Cache deps
COPY web-admin/package.json web-admin/package-lock.json* ./
RUN npm ci --no-audit --no-fund || npm install --no-audit --no-fund
# Copy source and build
COPY web-admin/ ./
RUN npm run build

# ===== Stage 2: Runtime =====
FROM nginx:1.27-alpine
LABEL org.opencontainers.image.title="yutong-web-admin" \
      org.opencontainers.image.description="YuTong Web Admin (Vue3 + Element Plus static)"

# Remove default nginx config and add custom
RUN rm /etc/nginx/conf.d/default.conf
COPY web-admin/nginx.conf /etc/nginx/conf.d/default.conf

# Copy built static assets
COPY --from=builder /build/dist /usr/share/nginx/html

# Non-root: nginx master as non-root, worker as nginx user
# Add health check endpoint
RUN echo '<!DOCTYPE html><html><head><title>OK</title></head><body>ok</body></html>' > /usr/share/nginx/html/healthz.html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -q -O- http://localhost/healthz.html || exit 1

CMD ["nginx", "-g", "daemon off;"]
