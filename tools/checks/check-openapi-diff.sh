#!/usr/bin/env bash
# OpenAPI breaking change check
# Design: 27-OpenAPI与前端类型生成规范, 99-CI流水线与发布证据自动化详设 (openapi-diff)
# Usage:
#   bash tools/checks/check-openapi-diff.sh                   # compare openapi/openapi.json vs design contract baseline
#   bash tools/checks/check-openapi-diff.sh old.json new.json # compare two files
# Breaking changes: removed paths, removed methods, removed operationId, new required fields, removed response codes, type changes
#
# GA2-L180 改造说明（对齐 99 号文档 P0 产物要求 + 84 号 ADR 流程联动 + GA2-51 CHK-06 CT 测试闭环）：
#   1. JSON 报告产物输出（99 号 line 43 P0）：oasdiff --format json + Python 兜底等价结构，
#      退出码 0 时也输出空 diff JSON 到 build/reports/openapi/diff.json
#   2. baseline 源切换：从 git HEAD:openapi/openapi.json 切换到设计契约源
#      YuTong-Java-Docs/contracts/openapi/openapi.yaml（YAML→JSON 转换），python3/yaml 缺失时回退到运行时导出
#   3. breaking change ADR 指引（99 号 line 83 + 84 号 ADR 联动）：exit 1 前输出 ADR 流程指引
#   4. Python 兜底增强：operationId 删除 + required 字段新增 + response code 删除检测
#   5. 与 GA2-51 CHK-06 CT 测试闭环：非 breaking 但有新增 operation 时输出 P1 警告

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OPENAPI_DIR="$PROJECT_ROOT/openapi"
REPORT_DIR="$PROJECT_ROOT/build/reports/openapi"
DIFF_JSON="$REPORT_DIR/diff.json"
BASELINE_JSON="$REPORT_DIR/baseline.json"
DESIGN_CONTRACT="$PROJECT_ROOT/YuTong-Java-Docs/contracts/openapi/openapi.yaml"

# 创建报告输出目录（99 号文档 P0 证据路径）
mkdir -p "$REPORT_DIR"

# 临时文件初始化 + 退出时清理
STATUS_FILE=""
DIFF_RAW=""
cleanup() {
  rm -f "$STATUS_FILE" "$DIFF_RAW" 2>/dev/null || true
}
trap cleanup EXIT

# 生成空 diff JSON（退出码 0 时的默认产物，99 号 P0 要求）
# 参数 $1: tool 名称 (oasdiff / python-fallback / python-error / error)
emit_empty_diff() {
  cat > "$DIFF_JSON" <<EOF
{
  "breaking_changes": [],
  "warnings": [],
  "summary": {
    "tool": "$1",
    "breaking_count": 0,
    "warning_count": 0,
    "status": "passed"
  }
}
EOF
}

# 输出 ADR 指引（breaking change 检测到时调用，99 号 line 83 + 84 号 ADR 联动）
emit_adr_guide() {
  echo ""
  echo "❌ Breaking change detected! Please follow ADR process (84 号文档):"
  echo "  1. 创建 ADR 记录变更原因和影响"
  echo "  2. 更新 48/55/Mock/typegen 相关产物"
  echo "  3. 同步 contracts/openapi/openapi.yaml 设计契约源"
  echo "  4. 重跑 check-openapi-diff.sh 验证"
}

# ========== baseline 源选择 ==========
# 默认模式：对比 current openapi.json vs baseline
# baseline 源切换（GA2-L180）：优先使用设计契约源 YuTong-Java-Docs/contracts/openapi/openapi.yaml
# python3 不可用或 yaml 库缺失时，回退到 git HEAD:openapi/openapi.json（保持原有兼容）
CLEANUP_GIT_BASELINE=0

if [ "$#" -lt 2 ]; then
  CURRENT="$OPENAPI_DIR/openapi.json"
  BASELINE_FETCHED=0
  # 优先：从设计契约源 YAML 转换为 JSON baseline（受版本控制的设计契约源）
  if [ -f "$DESIGN_CONTRACT" ] && command -v python3 > /dev/null 2>&1; then
    if python3 -c "import yaml" 2>/dev/null; then
      if python3 -c "import yaml,json,sys; print(json.dumps(yaml.safe_load(open(sys.argv[1])), ensure_ascii=False))" "$DESIGN_CONTRACT" > "$BASELINE_JSON" 2>/dev/null; then
        BASELINE="$BASELINE_JSON"
        BASELINE_FETCHED=1
        echo "Using YuTong-Java-Docs/contracts/openapi/openapi.yaml as baseline (design contract)"
      fi
    fi
  fi
  # 回退：使用 git HEAD:openapi/openapi.json（保持原有兼容，首次运行时用 current 兜底）
  if [ "$BASELINE_FETCHED" -ne 1 ]; then
    BASELINE="$OPENAPI_DIR/.baseline.json"
    CLEANUP_GIT_BASELINE=1
    if git -C "$PROJECT_ROOT" show HEAD:openapi/openapi.json > "$BASELINE" 2>/dev/null; then
      echo "WARNING: design contract unavailable, fallback to git HEAD:openapi/openapi.json as baseline"
    else
      echo "WARNING: no baseline available, using current as baseline (first run)"
      cp "$CURRENT" "$BASELINE"
    fi
  fi
  OLD="$BASELINE"
  NEW="$CURRENT"
else
  OLD="$1"
  NEW="$2"
fi

if [ ! -f "$OLD" ]; then
  echo "ERROR: Old OpenAPI file not found: $OLD"
  emit_empty_diff "error"
  exit 1
fi
if [ ! -f "$NEW" ]; then
  echo "ERROR: New OpenAPI file not found: $NEW"
  emit_empty_diff "error"
  exit 1
fi

echo "OpenAPI Diff Check"
echo "  Baseline: $OLD ($(wc -c < "$OLD") bytes)"
echo "  Current:  $NEW ($(wc -c < "$NEW") bytes)"
echo ""

# ========== 增强分析函数（docker 路径与 Python 兜底共用） ==========
# 环境变量传入：OLD_FILE, NEW_FILE, DIFF_JSON, STATUS_FILE, OASDIFF_VERDICT, DIFF_RAW_FILE
# 输出：DIFF_JSON (规范化 JSON 报告) + STATUS_FILE (BREAKING/OK) + stdout (摘要 + 警告)
# 检测项：路径删除 + 方法删除 + operationId 删除/变更 + required 字段新增 + response code 删除
run_analysis() {
  python3 <<'PYEOF'
import json, sys, os

HTTP_METHODS = {'get', 'put', 'post', 'delete', 'options', 'head', 'patch', 'trace'}

with open(os.environ['OLD_FILE'], encoding='utf-8-sig') as f:
    old = json.load(f)
with open(os.environ['NEW_FILE'], encoding='utf-8-sig') as f:
    new = json.load(f)

breaking_changes = []
warnings = []

old_paths = set(old.get("paths", {}).keys())
new_paths = set(new.get("paths", {}).keys())

# oasdiff 为权威判定：若 oasdiff 判定 pass，跳过 Python breaking 检测
# （避免设计契约 server URL 前缀差异等导致的路径误报，oasdiff 会正确处理 base path 归一化）
oasdiff_verdict = os.environ.get('OASDIFF_VERDICT', 'none')
skip_breaking = (oasdiff_verdict == 'pass')

if not skip_breaking:
    # 1. 路径删除检测（breaking）
    for p in sorted(old_paths - new_paths):
        breaking_changes.append({"type": "path_removed", "path": p, "detail": "Path removed: %s" % p})

    # 2. 方法删除 + operationId 删除/变更 + response code 删除 + requestBody required 字段新增
    for path in sorted(old_paths & new_paths):
        old_item = old["paths"][path]
        new_item = new["paths"][path]
        if not isinstance(old_item, dict) or not isinstance(new_item, dict):
            continue
        old_methods = set(k for k in old_item.keys() if k in HTTP_METHODS)
        new_methods = set(k for k in new_item.keys() if k in HTTP_METHODS)
        # 方法删除（breaking）
        for m in sorted(old_methods - new_methods):
            op_val = old_item.get(m, {})
            op_id = op_val.get('operationId', '') if isinstance(op_val, dict) else ''
            breaking_changes.append({"type": "method_removed", "path": path, "method": m, "operationId": op_id, "detail": "Method %s removed from %s" % (m.upper(), path)})
        # 公共方法详细检查
        for m in sorted(old_methods & new_methods):
            old_op = old_item.get(m, {})
            new_op = new_item.get(m, {})
            if not isinstance(old_op, dict) or not isinstance(new_op, dict):
                continue
            old_oid = old_op.get('operationId')
            new_oid = new_op.get('operationId')
            # operationId 删除（breaking）
            if old_oid and not new_oid:
                breaking_changes.append({"type": "operationId_removed", "path": path, "method": m, "operationId": old_oid, "detail": "operationId removed from %s %s" % (m.upper(), path)})
            elif old_oid and new_oid and old_oid != new_oid:
                breaking_changes.append({"type": "operationId_changed", "path": path, "method": m, "operationId": old_oid, "detail": "operationId changed from %s to %s on %s %s" % (old_oid, new_oid, m.upper(), path)})
            # response code 删除（breaking）
            old_responses = set(old_op.get('responses', {}).keys()) if isinstance(old_op.get('responses'), dict) else set()
            new_responses = set(new_op.get('responses', {}).keys()) if isinstance(new_op.get('responses'), dict) else set()
            for code in sorted(old_responses - new_responses):
                breaking_changes.append({"type": "response_code_removed", "path": path, "method": m, "operationId": old_oid or '', "response_code": code, "detail": "Response %s removed from %s %s" % (code, m.upper(), path)})
            # requestBody required 字段新增（breaking：客户端必须提供新字段）
            old_rb = old_op.get('requestBody', {}) or {}
            new_rb = new_op.get('requestBody', {}) or {}
            if isinstance(old_rb, dict) and isinstance(new_rb, dict):
                old_req = set()
                for ct in (old_rb.get('content', {}) or {}).values():
                    schema = (ct or {}).get('schema', {}) or {}
                    if isinstance(schema, dict):
                        old_req.update(schema.get('required', []) or [])
                new_req = set()
                for ct in (new_rb.get('content', {}) or {}).values():
                    schema = (ct or {}).get('schema', {}) or {}
                    if isinstance(schema, dict):
                        new_req.update(schema.get('required', []) or [])
                for field in sorted(new_req - old_req):
                    breaking_changes.append({"type": "required_field_added", "path": path, "method": m, "operationId": old_oid or '', "field": field, "detail": "New required field '%s' in requestBody of %s %s" % (field, m.upper(), path)})

# 3. 新增 operationId 检测（P1 警告，GA2-51 CHK-06 CT 测试闭环）
old_ops = set()
for p, methods in old.get("paths", {}).items():
    if not isinstance(methods, dict):
        continue
    for m, op in methods.items():
        if isinstance(op, dict) and 'operationId' in op:
            old_ops.add(op['operationId'])
new_ops = set()
for p, methods in new.get("paths", {}).items():
    if not isinstance(methods, dict):
        continue
    for m, op in methods.items():
        if isinstance(op, dict) and 'operationId' in op:
            new_ops.add(op['operationId'])
added_ops = new_ops - old_ops
for oid in sorted(added_ops):
    warnings.append({"type": "new_operationId", "operationId": oid, "detail": "New operationId detected: %s, please add CT-%s contract test (GA2-51 CHK-06)" % (oid, oid)})

# 4. oasdiff 融合：oasdiff 报告 breaking 但 Python 未检测到结构化变更时，追加兜底条目
if oasdiff_verdict == 'breaking' and not breaking_changes:
    raw_detail = ''
    raw_file = os.environ.get('DIFF_RAW_FILE', '')
    if raw_file and os.path.exists(raw_file):
        try:
            with open(raw_file, encoding='utf-8') as f:
                raw_detail = f.read().strip()[:2000]
        except Exception:
            pass
    breaking_changes.append({"type": "oasdiff_detected", "detail": "oasdiff reported breaking changes (not captured by basic Python analysis)", "raw": raw_detail})

# 5. 写入规范化 JSON 报告（99 号 P0 证据产物）
tool_name = "oasdiff+python" if oasdiff_verdict != 'none' else "python-fallback"
report = {
    "breaking_changes": breaking_changes,
    "warnings": warnings,
    "summary": {
        "tool": tool_name,
        "breaking_count": len(breaking_changes),
        "warning_count": len(warnings),
        "old_paths": len(old_paths),
        "new_paths": len(new_paths),
        "status": "failed" if breaking_changes else "passed"
    }
}
with open(os.environ['DIFF_JSON'], 'w', encoding='utf-8') as f:
    json.dump(report, f, ensure_ascii=False, indent=2)

# 6. 写入状态文件供 bash 判断退出码
with open(os.environ['STATUS_FILE'], 'w') as f:
    f.write("BREAKING\n" if breaking_changes else "OK\n")

# 7. 输出人类可读摘要
if breaking_changes:
    print("FAILED: %d breaking change(s) detected:" % len(breaking_changes))
    for c in breaking_changes:
        loc = ""
        if c.get('path'):
            loc = " %s %s" % (c.get('method', '').upper(), c['path'])
        oid_part = " [%s]" % c['operationId'] if c.get('operationId') else ''
        print("  - [%s]%s%s: %s" % (c['type'], loc, oid_part, c['detail']))
else:
    print("PASSED: No breaking changes detected. (%d baseline paths, %d current paths)" % (len(old_paths), len(new_paths)))

# 仅在无 breaking 时输出新增 operation 警告（breaking 优先修复，警告延后）
if not breaking_changes:
    for w in warnings:
        print("⚠️ New operationId detected: %s, please add CT-%s contract test (GA2-51 CHK-06)" % (w['operationId'], w['operationId']))
PYEOF
}

# 创建状态临时文件
STATUS_FILE=$(mktemp 2>/dev/null || echo "/tmp/openapi_diff_status_$$")

# 设置分析函数所需的环境变量
export OLD_FILE="$OLD"
export NEW_FILE="$NEW"
export DIFF_JSON="$DIFF_JSON"
export STATUS_FILE="$STATUS_FILE"

# ========== Docker oasdiff 路径（优先） ==========
if command -v docker > /dev/null 2>&1; then
  echo "Running oasdiff via Docker..."
  OLD_DIR=$(cd "$(dirname "$OLD")" && pwd)
  NEW_DIR=$(cd "$(dirname "$NEW")" && pwd)
  OLD_BASE=$(basename "$OLD")
  NEW_BASE=$(basename "$NEW")

  # docker 挂载：处理 OLD 和 NEW 在不同目录的情况（baseline 在 build/reports/，current 在 openapi/）
  if [ "$OLD_DIR" = "$NEW_DIR" ]; then
    DOCKER_MOUNTS=(-v "$OLD_DIR:/spec")
    OLD_REF="/spec/$OLD_BASE"
    NEW_REF="/spec/$NEW_BASE"
  else
    DOCKER_MOUNTS=(-v "$OLD_DIR:/spec/old" -v "$NEW_DIR:/spec/new")
    OLD_REF="/spec/old/$OLD_BASE"
    NEW_REF="/spec/new/$NEW_BASE"
  fi

  # oasdiff 文本输出（控制台展示，保留原有行为）
  RESULT=$(docker run --rm "${DOCKER_MOUNTS[@]}" \
    tufin/oasdiff breaking "$OLD_REF" "$NEW_REF" \
    --format text 2>&1 || true)

  # oasdiff JSON 输出（99 号 P0 证据产物，落盘到临时文件供 Python 融合）
  DIFF_RAW="$REPORT_DIR/.oasdiff-raw.json"
  docker run --rm "${DOCKER_MOUNTS[@]}" \
    tufin/oasdiff breaking "$OLD_REF" "$NEW_REF" \
    --format json > "$DIFF_RAW" 2>/dev/null || true

  # 判定 breaking（保留原有 grep 逻辑）
  VERDICT="pass"
  if echo "$RESULT" | grep -qi "breaking"; then
    VERDICT="breaking"
  fi

  # 运行增强 Python 分析（生成规范化 JSON 报告 + 新增 operation 警告）
  export OASDIFF_VERDICT="$VERDICT"
  export DIFF_RAW_FILE="$DIFF_RAW"
  set +e
  run_analysis
  ANALYSIS_EXIT=$?
  set -e

  if [ $ANALYSIS_EXIT -ne 0 ]; then
    echo "WARNING: Python analysis failed, falling back to oasdiff raw output"
    if [ -s "$DIFF_RAW" ]; then
      cp "$DIFF_RAW" "$DIFF_JSON"
    else
      emit_empty_diff "oasdiff"
    fi
  fi
  unset OASDIFF_VERDICT DIFF_RAW_FILE

  # 清理 git 回退 baseline 临时文件（设计契约 baseline.json 保留为证据）
  if [ "$CLEANUP_GIT_BASELINE" -eq 1 ]; then
    rm -f "$OPENAPI_DIR/.baseline.json" 2>/dev/null || true
  fi

  # 退出码语义不变：breaking → 1，非 breaking → 0
  if [ "$VERDICT" = "breaking" ]; then
    echo ""
    echo "oasdiff raw output:"
    echo "$RESULT"
    emit_adr_guide
    exit 1
  fi

  echo "PASSED: No breaking changes detected (oasdiff)."
  exit 0
fi

# ========== Python 兜底路径（无 docker 时基础保障） ==========
echo "WARNING: docker not available, using enhanced Python analysis"
if ! command -v python3 > /dev/null 2>&1; then
  echo "ERROR: python3 not available for comparison"
  emit_empty_diff "error"
  if [ "$CLEANUP_GIT_BASELINE" -eq 1 ]; then
    rm -f "$OPENAPI_DIR/.baseline.json" 2>/dev/null || true
  fi
  exit 1
fi

export OASDIFF_VERDICT="none"
export DIFF_RAW_FILE=""
set +e
run_analysis
ANALYSIS_EXIT=$?
set -e

if [ $ANALYSIS_EXIT -ne 0 ]; then
  echo "ERROR: Python analysis failed"
  emit_empty_diff "python-error"
  if [ "$CLEANUP_GIT_BASELINE" -eq 1 ]; then
    rm -f "$OPENAPI_DIR/.baseline.json" 2>/dev/null || true
  fi
  exit 1
fi

# 读取状态文件判断退出码（exit 0/1 语义不变）
if grep -q "BREAKING" "$STATUS_FILE" 2>/dev/null; then
  if [ "$CLEANUP_GIT_BASELINE" -eq 1 ]; then
    rm -f "$OPENAPI_DIR/.baseline.json" 2>/dev/null || true
  fi
  emit_adr_guide
  exit 1
fi

if [ "$CLEANUP_GIT_BASELINE" -eq 1 ]; then
  rm -f "$OPENAPI_DIR/.baseline.json" 2>/dev/null || true
fi
exit 0
