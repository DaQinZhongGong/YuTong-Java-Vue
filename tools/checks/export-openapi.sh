#!/usr/bin/env bash
# Export OpenAPI from running Spring Boot application
# Design: 27-OpenAPI与前端类型生成规范, 99-CI流水线与发布证据自动化详设 (openapi-export)
# Starts the app, fetches /v3/api-docs, saves to openapi/openapi.json + openapi.yaml
# Usage: bash tools/checks/export-openapi.sh [jar-path] [port]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OPENAPI_DIR="$PROJECT_ROOT/openapi"
PORT="${2:-8099}"
JAR_PATH="${1:-$PROJECT_ROOT/backend/yutong-boot/target/yutong-boot-*.jar}"

mkdir -p "$OPENAPI_DIR"

# Find jar
JAR=$(ls $JAR_PATH 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
  echo "ERROR: yutong-boot jar not found. Run 'mvn package' first."
  exit 1
fi

echo "Starting app on port $PORT to export OpenAPI..."
# Start app with random port, no DB connection needed for OpenAPI export
# Use test profile with H2 or skip DB-dependent init
java -jar "$JAR" \
  --spring.profiles.active=local \
  --server.port="$PORT" \
  --spring.datasource.url=jdbc:postgresql://localhost:5434/yutong \
  --spring.datasource.username=yutong \
  --spring.datasource.password=yutong-dev-password \
  --spring.flyway.enabled=false \
  --spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration &
APP_PID=$!

# Wait for app to be ready
echo "Waiting for app to start..."
ready=0
for i in $(seq 1 60); do
  if curl -sf "http://localhost:$PORT/actuator/health" > /dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 2
done

if [ "$ready" -ne 1 ]; then
  echo "ERROR: App failed to start within 120s"
  kill $APP_PID 2>/dev/null || true
  exit 1
fi

echo "App ready, exporting OpenAPI..."
# Export JSON
curl -sf "http://localhost:$PORT/v3/api-docs" -o "$OPENAPI_DIR/openapi.json"
if [ ! -s "$OPENAPI_DIR/openapi.json" ]; then
  echo "ERROR: OpenAPI export empty"
  kill $APP_PID 2>/dev/null || true
  exit 1
fi

# Convert to YAML if python3 + pyyaml available
if command -v python3 > /dev/null 2>&1; then
  python3 -c "
import json, sys
try:
    import yaml
    with open('$OPENAPI_DIR/openapi.json') as f:
        data = json.load(f)
    with open('$OPENAPI_DIR/openapi.yaml', 'w') as f:
        yaml.dump(data, f, allow_unicode=True, sort_keys=False)
    print('YAML export done')
except ImportError:
    print('PyYAML not available, skipping YAML export')
" || echo "YAML conversion skipped"
fi

# Compute hash for release manifest
cd "$OPENAPI_DIR"
OPENAPI_HASH=$(sha256sum openapi.json | awk '{print $1}')
echo "OpenAPI exported: openapi.json ($(wc -c < openapi.json) bytes)"
echo "OpenAPI hash (sha256): $OPENAPI_HASH"

# Stop app
kill $APP_PID 2>/dev/null || true
wait $APP_PID 2>/dev/null || true

echo "PASSED: OpenAPI export complete."
exit 0
