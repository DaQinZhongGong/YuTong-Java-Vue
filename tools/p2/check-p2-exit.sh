#!/usr/bin/env bash
# P2 阶段退出条件检查脚本
# Design: 85-P2每日推进节奏与会议机制 (阶段退出条件)
# Source: tools/p2/p2-exit-checklist.yaml
# Usage: bash tools/p2/check-p2-exit.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# PROJECT_ROOT = SCRIPT_DIR/../..（tools/p2 -> tools -> 根）
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." 2>/dev/null && pwd)"
if [ -z "$PROJECT_ROOT" ] || [ "$PROJECT_ROOT" = "/" ]; then
  # 兜底：从 PWD 推断（容器场景 -w /workspace）
  PROJECT_ROOT="$PWD"
fi
DOCS_DIR="$PROJECT_ROOT/YuTong-Java-Docs"
EVIDENCE_DIR="$PROJECT_ROOT/release-evidence/v0.2.0"
CHECKS_DIR="$PROJECT_ROOT/tools/checks"

errors=0
warnings=0

pass() {
  echo "[PASS] $1"
}

fail() {
  echo "[FAIL] $1"
  errors=$((errors + 1))
}

warn() {
  echo "[WARN] $1"
  warnings=$((warnings + 1))
}

echo "=== P2 Exit Checklist Verification ==="
echo "Based on: 85-P2每日推进节奏与会议机制"
echo ""

# EXIT-01: P0 问题清零
echo "--- EXIT-01: P0 problems count = 0 ---"
PROBLEM_LIST="$DOCS_DIR/contracts/governance/problem-remediation-list.yaml"
if [ ! -f "$PROBLEM_LIST" ]; then
  warn "problem-remediation-list.yaml not found (G0 not signed yet): $PROBLEM_LIST"
  warn "Treating as 0 P0 problems (skeleton state)."
  p0_count=0
else
  # 简易扫描：统计 problems 数组下 severity: P0 的条目（仅骨架校验，正式场景由 Ruby 脚本解析 YAML）
  # 注意 grep -c 在无匹配时 exit code=1 但 stdout 仍输出 0，不能用 || echo 0 否则输出 "0\n0"
  p0_count=$(grep -c 'severity: P0' "$PROBLEM_LIST" 2>/dev/null || true)
  p0_count=${p0_count:-0}
fi
if [ "$p0_count" -eq 0 ]; then
  pass "No P0 problems blocking P2 exit."
else
  fail "Found $p0_count P0 problem(s) in problem-remediation-list.yaml"
fi
echo ""

# EXIT-02: P1 问题有责任人和截止日期
echo "--- EXIT-02: P1 problems have owner and dueDate ---"
if [ ! -f "$PROBLEM_LIST" ]; then
  warn "problem-remediation-list.yaml not found; skipping P1 check (skeleton state)."
else
  p1_count=$(grep -c 'severity: P1' "$PROBLEM_LIST" 2>/dev/null || true)
  p1_count=${p1_count:-0}
  if [ "$p1_count" -eq 0 ]; then
    pass "No P1 problems to inspect."
  else
    warn "Found $p1_count P1 problem(s); manual review required for owner/dueDate fields."
  fi
fi
echo ""

# EXIT-03: D1~D8 关键证据齐全
echo "--- EXIT-03: v0.2.0 evidence artifacts present ---"
required_artifacts=("smoke-test.txt" "quality-baseline.txt" "acceptance-summary.md")
if [ ! -d "$EVIDENCE_DIR" ]; then
  warn "release-evidence/v0.2.0 not found; P2 has not produced evidence yet."
else
  for artifact in "${required_artifacts[@]}"; do
    target="$EVIDENCE_DIR/$artifact"
    if [ -f "$target" ]; then
      pass "Evidence present: $artifact"
    else
      fail "Missing evidence: $artifact (expected at $target)"
    fi
  done
fi
echo ""

# EXIT-04: 文档状态同步（23/39/75/80 存在）
echo "--- EXIT-04: Required design docs exist ---"
required_docs=(
  "23-设计到落地追踪记录"
  "39-实现基线冻结与开工准入检查"
  "75-v0.2工程初始化任务书"
  "80-v0.2工程初始化执行清单"
)
for doc in "${required_docs[@]}"; do
  found=$(find "$DOCS_DIR" -maxdepth 1 -type d -name "${doc}" 2>/dev/null | head -1)
  if [ -n "$found" ]; then
    pass "Doc present: $doc"
  else
    fail "Missing doc: $doc"
  fi
done
echo ""

# EXIT-05: 6 项 P2 检查脚本可运行
echo "--- EXIT-05: P2 gate scripts present ---"
required_scripts=(
  "check-document-catalog"
  "check-dir-layout"
  "check-id-policy"
  "check-env-vars"
  "check-secrets.sh"
  "check-openapi-diff.sh"
)
for script in "${required_scripts[@]}"; do
  target="$CHECKS_DIR/$script"
  if [ -f "$target" ]; then
    pass "Script present: $script"
  else
    fail "Missing script: $script"
  fi
done
echo ""

# 汇总
echo "=== Summary ==="
echo "Errors: $errors"
echo "Warnings: $warnings"
echo ""

if [ "$errors" -gt 0 ]; then
  echo "RESULT: FAIL - P2 cannot exit to v0.3 until $errors blocking issue(s) resolved."
  exit 1
fi

if [ "$warnings" -gt 0 ]; then
  echo "RESULT: CONDITIONAL_PASS - No blockers, but $warnings warning(s) require manual review."
  exit 0
fi

echo "RESULT: PASS - All 5 exit conditions satisfied. P2 may exit to v0.3."
exit 0
