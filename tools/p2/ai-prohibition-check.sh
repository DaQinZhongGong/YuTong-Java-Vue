#!/usr/bin/env bash
# AI 实现禁止项检查脚本（90 号文档机器化）
# Design: 90-AI实现总控与上下文编排规范 (AI 禁止临时发挥清单 + 非 Demo 实现要求)
# Source: tools/p2/ai-implementation-phases.yaml + tools/p2/ai-prohibition-rules.yaml
# Usage: bash tools/p2/ai-prohibition-check.sh
#   或 Docker: docker run --rm -v "$PWD:/workspace" -w /workspace ubuntu:22.04 bash tools/p2/ai-prohibition-check.sh
#
# 检查 90 号文档"AI 禁止临时发挥清单"9 条：
#   1. 临时改技术栈（无法静态扫描，跳过）
#   2. 局部使用 Long id / id: number / 自增主键
#   3. 页面直接约定后端字段（无法静态扫描，跳过）
#   4. Controller 直接访问 Mapper
#   5. 低代码直接执行生产 DDL（无法静态扫描，跳过）
#   6. AI 自动执行 SQL 或审批（无法静态扫描，跳过）
#   7. 为了跑通而关闭安全/审计
#   8. UI 脱离 Token 自定义样式
#   9. 不写测试或证据（无法静态扫描，跳过）
# 实际静态扫描 4 项：#2、#4、#7、#8

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." 2>/dev/null && pwd)"
if [ -z "$PROJECT_ROOT" ] || [ "$PROJECT_ROOT" = "/" ]; then
  PROJECT_ROOT="$PWD"
fi

errors=0
warnings=0
violations=()

pass() {
  echo "[PASS] $1"
}

fail() {
  echo "[FAIL] $1"
  errors=$((errors + 1))
  violations+=("$1")
}

warn() {
  echo "[WARN] $1"
  warnings=$((warnings + 1))
}

# 扫描根目录下的核心源码目录
SCAN_DIRS=()
for d in backend database openapi web-admin/src mobile-uniapp/src; do
  if [ -d "$PROJECT_ROOT/$d" ]; then
    SCAN_DIRS+=("$PROJECT_ROOT/$d")
  fi
done

echo "=== AI 禁止项检查脚本（90 号文档机器化） ==="
echo "Project root: $PROJECT_ROOT"
echo "Scan dirs:    ${SCAN_DIRS[*]}"
echo ""

# ========== 禁止项 #2: Long id / id: number / 自增主键 ==========
echo "--- 禁止项 #2: 字符串主键策略违反 ---"
# 81 号文档"禁止模式"清单：
# - bigserial
# - serial primary key
# - Long id
# - number 类型 id 出现在 OpenAPI Schema
# - 前端 id: number
# - created_at/updated_at/create_time/update_time 作为正式字段名（允许 created_time/updated_time）

violation_count=0
for dir in "${SCAN_DIRS[@]}"; do
  # 排除设计文档中的"禁止示例"（doc 目录不扫描，已在 SCAN_DIRS 中限定源码目录）
  # 排除 .md/.mdx 中的"禁止示例"（只扫源码 .java/.ts/.vue/.sql/.yaml/.yml/.json）
  matches=$(grep -rnE '(bigserial|serial primary key|AUTO_INCREMENT|Long id|id:\s*number)' \
    --include='*.java' --include='*.ts' --include='*.vue' --include='*.sql' \
    --include='*.yaml' --include='*.yml' --include='*.json' \
    "$dir" 2>/dev/null | grep -vE '(//|\*|#)\s*(禁止|forbidden|example|示例)' || true)
  if [ -n "$matches" ]; then
    while IFS= read -r line; do
      fail "#2 主键违规: $line"
      violation_count=$((violation_count + 1))
    done <<< "$matches"
  fi
done
if [ "$violation_count" -eq 0 ]; then
  pass "#2 无主键策略违规"
fi
echo ""

# ========== 禁止项 #4: Controller 直接访问 Mapper ==========
echo "--- 禁止项 #4: Controller 直接访问 Mapper ---"
# 后端 Controller 类不应直接注入 Mapper（应通过 ApplicationService/DomainService）
violation_count=0
if [ -d "$PROJECT_ROOT/backend" ]; then
  # 找所有 *Controller.java 文件
  while IFS= read -r ctrl_file; do
    # 检查文件内是否同时出现 @Autowired/@Resource Mapper 或 private XxxMapper 字段
    if grep -qE '(@Autowired|@Resource)\s+.*Mapper|private\s+final\s+\w+Mapper|private\s+\w+Mapper\s+\w+' "$ctrl_file" 2>/dev/null; then
      match_line=$(grep -nE '(@Autowired|@Resource)\s+.*Mapper|private\s+final\s+\w+Mapper|private\s+\w+Mapper\s+\w+' "$ctrl_file" | head -1)
      fail "#4 Controller 直接访问 Mapper: $ctrl_file -> $match_line"
      violation_count=$((violation_count + 1))
    fi
  done < <(find "$PROJECT_ROOT/backend" -name '*Controller.java' -type f 2>/dev/null)
fi
if [ "$violation_count" -eq 0 ]; then
  pass "#4 无 Controller 直接访问 Mapper"
fi
echo ""

# ========== 禁止项 #7: 关闭安全/审计 ==========
echo "--- 禁止项 #7: 为跑通而关闭安全/审计 ---"
# 扫描注释或代码中疑似关闭审计/安全的临时 hack
violation_count=0
for dir in "${SCAN_DIRS[@]}"; do
  matches=$(grep -rnEi '(//\s*TODO|#audit.*disable|@Auditable.*enabled\s*=\s*false|//\s*关闭审计|//\s*临时关闭|//\s*skip audit|//\s*bypass auth)' \
    --include='*.java' --include='*.ts' --include='*.vue' --include='*.yaml' --include='*.yml' \
    "$dir" 2>/dev/null | grep -vE '(test/|Test\.java|spec\.ts|__tests__|\.test\.)' || true)
  if [ -n "$matches" ]; then
    while IFS= read -r line; do
      warn "#7 疑似关闭审计/安全: $line"
      violation_count=$((violation_count + 1))
    done <<< "$matches"
  fi
done
if [ "$violation_count" -eq 0 ]; then
  pass "#7 无关闭审计/安全迹象"
fi
echo ""

# ========== 禁止项 #8: UI 脱离 Token 自定义样式 ==========
echo "--- 禁止项 #8: UI 脱离 Token 自定义样式 ---"
# Web 管理端和移动端不应直接写硬编码颜色值（应通过 Token/CSS 变量/Element Plus 主题）
violation_count=0
for dir in "$PROJECT_ROOT/web-admin/src" "$PROJECT_ROOT/mobile-uniapp/src"; do
  if [ ! -d "$dir" ]; then
    continue
  fi
  # 扫描 .vue/.scss/.css 中的硬编码颜色值（#RRGGBB 或 rgb()），但排除 :root token 定义和注释
  matches=$(grep -rnE '#[0-9a-fA-F]{6}\b|rgb\([0-9]' \
    --include='*.vue' --include='*.scss' --include='*.css' \
    "$dir" 2>/dev/null \
    | grep -vE '(:root|//|\*|/\*|#[0-9a-fA-F]{8}\b|var\(--|theme)' || true)
  if [ -n "$matches" ]; then
    while IFS= read -r line; do
      # 限制只显示前 5 条，避免噪声
      if [ "$violation_count" -lt 5 ]; then
        warn "#8 疑似硬编码颜色: $line"
      fi
      violation_count=$((violation_count + 1))
    done <<< "$matches"
    if [ "$violation_count" -gt 5 ]; then
      warn "#8 ... 还有 $((violation_count - 5)) 处疑似硬编码颜色未显示"
    fi
  fi
done
if [ "$violation_count" -eq 0 ]; then
  pass "#8 无硬编码颜色"
else
  warn "#8 共 $violation_count 处疑似硬编码颜色（建议走 Token/CSS 变量，按 50/56/76 验收）"
fi
echo ""

# ========== 汇总 ==========
echo "=== Summary ==="
echo "Errors:   $errors"
echo "Warnings: $warnings"
echo ""

if [ "$errors" -gt 0 ]; then
  echo "禁止项违规清单（必须修复）:"
  for v in "${violations[@]}"; do
    echo "  - $v"
  done
  echo ""
  echo "RESULT: FAIL - 90 号文档禁止项存在 $errors 个 error，必须按 84 ADR 流程或修复代码。"
  exit 1
fi

if [ "$warnings" -gt 0 ]; then
  echo "RESULT: PASS (with $warnings warning(s)) - 静态禁止项无违规，但需人工复核 warning。"
  exit 0
fi

echo "RESULT: PASS - 90 号文档静态禁止项全部通过。"
exit 0
