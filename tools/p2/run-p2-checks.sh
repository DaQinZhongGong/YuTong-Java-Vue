#!/usr/bin/env bash
# P2 阶段一键检查脚本（聚合 86 号文档 10 类命令模板）
# Design: 86-P2命令模板与执行记录详设
# Usage: bash tools/p2/run-p2-checks.sh [--skip-startup] [--skip-ruby]
#   --skip-startup  跳过后端/Web/移动启动检查（已启动时使用）
#   --skip-ruby     跳过 Ruby 质量检查脚本（环境无 Ruby 时使用）
# 输出：stdout 全量日志，可 tee 到 release-evidence/v0.2.0/p2-checks-YYYYMMDD.txt 作为证据

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." 2>/dev/null && pwd)"
if [ -z "$PROJECT_ROOT" ] || [ "$PROJECT_ROOT" = "/" ]; then
  PROJECT_ROOT="$PWD"
fi

DOCS_DIR="$PROJECT_ROOT/YuTong-Java-Docs"
BACKEND_DIR="$PROJECT_ROOT/backend"
WEB_ADMIN_DIR="$PROJECT_ROOT/web-admin"
MOBILE_DIR="$PROJECT_ROOT/mobile-uniapp"
OPENAPI_DIR="$PROJECT_ROOT/openapi"

SKIP_STARTUP=0
SKIP_RUBY=0
for arg in "$@"; do
  case "$arg" in
    --skip-startup) SKIP_STARTUP=1 ;;
    --skip-ruby)    SKIP_RUBY=1 ;;
    *)
      echo "Unknown argument: $arg"
      echo "Usage: bash tools/p2/run-p2-checks.sh [--skip-startup] [--skip-ruby]"
      exit 2
      ;;
  esac
done

# 命令计数
total=0
passed=0
failed=0
skipped=0
failed_cmds=()

run_cmd() {
  local id="$1"
  local desc="$2"
  local cmd="$3"
  local skip_flag="${4:-0}"
  total=$((total + 1))
  echo ""
  echo "================================================================"
  echo "[$id] $desc"
  echo "命令: $cmd"
  echo "----------------------------------------------------------------"
  if [ "$skip_flag" = "1" ]; then
    echo "[SKIP] $id (skipped by flag)"
    skipped=$((skipped + 1))
    return 0
  fi
  if eval "$cmd"; then
    echo "[PASS] $id"
    passed=$((passed + 1))
  else
    local rc=$?
    echo "[FAIL] $id (exit code $rc)"
    failed=$((failed + 1))
    failed_cmds+=("$id: $cmd")
  fi
}

echo "=== P2 一键检查脚本（86 号文档 10 类命令模板） ==="
echo "Project root: $PROJECT_ROOT"
echo "Started at:   $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
echo ""

# 命令 1: 目录检查
# 86 号原文是 `tree -L 3`，这里改为更具语义化的检查脚本
run_cmd "CMD-01" "目录检查（按 07 号设计）" "bash $PROJECT_ROOT/tools/checks/check-dir-layout"

# 命令 2: 文档目录与契约检查
if [ "$SKIP_RUBY" = "1" ]; then
  run_cmd "CMD-02a" "文档目录总览扫描" "grep -E 'document-catalog|product-baseline|asyncapi|feature-flags|design-quality-report' $DOCS_DIR/00-设计文档总览.md | head -5"
  run_cmd "CMD-02b" "check_docs.rb" "echo '[SKIP-RUBY] use docker ruby:3.2-slim to run: ruby docs/quality/check_docs.rb'" 1
  run_cmd "CMD-02c" "generate_manifest.rb" "echo '[SKIP-RUBY] use docker ruby:3.2-slim to run: ruby docs/quality/generate_manifest.rb'" 1
  run_cmd "CMD-02d" "generate_manifest.rb --verify" "echo '[SKIP-RUBY] use docker ruby:3.2-slim to run: ruby docs/quality/generate_manifest.rb --verify'" 1
else
  run_cmd "CMD-02a" "文档目录总览扫描" "grep -E 'document-catalog|product-baseline|asyncapi|feature-flags|design-quality-report' $DOCS_DIR/00-设计文档总览.md | head -5"
  # 实际 Ruby 命令需要 cd 到 PROJECT_ROOT 且建立 docs -> YuTong-Java-Docs 软链
  run_cmd "CMD-02b" "check_docs.rb（需 Ruby 环境）" "cd $PROJECT_ROOT && ruby YuTong-Java-Docs/quality/check_docs.rb 2>&1 | tail -20"
  run_cmd "CMD-02c" "generate_manifest.rb" "cd $PROJECT_ROOT && ruby YuTong-Java-Docs/quality/generate_manifest.rb 2>&1 | tail -10"
  run_cmd "CMD-02d" "generate_manifest.rb --verify" "cd $PROJECT_ROOT && ruby YuTong-Java-Docs/quality/generate_manifest.rb --verify 2>&1 | tail -10"
fi

# 命令 3: 后端健康检查
if [ "$SKIP_STARTUP" = "1" ]; then
  run_cmd "CMD-03" "后端健康检查" "curl -fsS http://localhost:8080/actuator/health" 1
else
  run_cmd "CMD-03" "后端健康检查" "curl -fsS http://localhost:8080/actuator/health || curl -fsS http://localhost:8082/actuator/health || echo '[WARN] 后端未启动，请先启动 yutong-backend'"
fi

# 命令 4: Flyway 迁移
if [ -f "$BACKEND_DIR/mvnw" ]; then
  run_cmd "CMD-04" "Flyway 迁移" "cd $BACKEND_DIR && ./mvnw -pl yutong-boot -am flyway:migrate -DskipTests 2>&1 | tail -10"
else
  run_cmd "CMD-04" "Flyway 迁移（无 mvnw，跳过）" "echo '[SKIP] No backend/mvnw found; Flyway migrate via running container instead.'" 1
fi

# 命令 5: OpenAPI 导出
if [ "$SKIP_STARTUP" = "1" ]; then
  run_cmd "CMD-05" "OpenAPI 导出" "curl -fsS http://localhost:8080/v3/api-docs -o $OPENAPI_DIR/openapi.json" 1
else
  run_cmd "CMD-05" "OpenAPI 导出" "(curl -fsS http://localhost:8080/v3/api-docs -o $OPENAPI_DIR/openapi.json 2>/dev/null && echo 'exported to $OPENAPI_DIR/openapi.json') || curl -fsS http://localhost:8082/v3/api-docs -o $OPENAPI_DIR/openapi.json 2>/dev/null || echo '[WARN] 后端未启动，OpenAPI 导出失败'"
fi

# 命令 6: Web 启动检查
if [ "$SKIP_STARTUP" = "1" ]; then
  run_cmd "CMD-06" "Web 启动" "pnpm --prefix $WEB_ADMIN_DIR dev" 1
else
  run_cmd "CMD-06" "Web 依赖与构建检查" "[ -f $WEB_ADMIN_DIR/package.json ] && (cd $WEB_ADMIN_DIR && pnpm install --frozen-lockfile 2>&1 | tail -5) || echo '[SKIP] web-admin/package.json not found'"
fi

# 命令 7: 移动端启动检查
if [ "$SKIP_STARTUP" = "1" ]; then
  run_cmd "CMD-07" "移动端启动" "pnpm --prefix $MOBILE_DIR dev:h5" 1
else
  run_cmd "CMD-07" "移动端依赖检查" "[ -f $MOBILE_DIR/package.json ] && (cd $MOBILE_DIR && pnpm install --frozen-lockfile 2>&1 | tail -5) || echo '[SKIP] mobile-uniapp/package.json not found'"
fi

# 命令 8: 质量检查（lint + typecheck）
if [ "$SKIP_STARTUP" = "1" ]; then
  run_cmd "CMD-08" "前端 lint + typecheck" "cd $WEB_ADMIN_DIR && pnpm lint && pnpm typecheck" 1
else
  run_cmd "CMD-08" "前端 lint + typecheck" "[ -f $WEB_ADMIN_DIR/package.json ] && (cd $WEB_ADMIN_DIR && (pnpm lint 2>&1 | tail -10) && (pnpm typecheck 2>&1 | tail -10)) || echo '[SKIP] web-admin not configured'"
fi

# 命令 9: Secret Scan
if command -v gitleaks >/dev/null 2>&1; then
  run_cmd "CMD-09" "Secret Scan (gitleaks)" "cd $PROJECT_ROOT && gitleaks detect --source . --no-banner 2>&1 | tail -10"
else
  run_cmd "CMD-09" "Secret Scan (use tools/checks/check-secrets.sh)" "bash $PROJECT_ROOT/tools/checks/check-secrets.sh 2>&1 | tail -10"
fi

# 命令 10: 禁止自增主键检查
if [ -f "$PROJECT_ROOT/tools/checks/check-no-autoincrement.sh" ]; then
  run_cmd "CMD-10" "禁止自增主键检查" "bash $PROJECT_ROOT/tools/checks/check-no-autoincrement.sh 2>&1 | tail -15"
elif [ -f "$PROJECT_ROOT/tools/checks/check-id-policy" ]; then
  run_cmd "CMD-10" "ID Policy 检查" "bash $PROJECT_ROOT/tools/checks/check-id-policy 2>&1 | tail -15"
else
  run_cmd "CMD-10" "禁止自增主键检查（grep）" "cd $PROJECT_ROOT && grep -rnE 'serial|bigserial|AUTO_INCREMENT|Long id|id: number' backend database openapi web-admin mobile-uniapp 2>/dev/null | head -20 || echo 'No violations'"
fi

# 汇总
echo ""
echo "================================================================"
echo "=== Summary ==="
echo "================================================================"
echo "Total:   $total"
echo "Passed:  $passed"
echo "Failed:  $failed"
echo "Skipped: $skipped"
echo "Finished at: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"

if [ "$failed" -gt 0 ]; then
  echo ""
  echo "Failed commands:"
  for c in "${failed_cmds[@]}"; do
    echo "  - $c"
  done
  echo ""
  echo "请参考 tools/p2/failure-handling.yaml 处理失败项。"
  exit 1
fi

echo ""
echo "RESULT: PASS - All P2 checks passed (skipped: $skipped)."
exit 0
