#!/usr/bin/env bash
# DDL 自增主键检查脚本
# 用法: bash tools/checks/check-no-autoincrement.sh
# 检查 database/migrations 下所有 SQL 文件是否存在 bigserial/serial 自增主键
# 设计文档要求: 禁止使用数据库自增序列作为主键
# 输入: database/migrations/*.sql
# 输出: 发现违规则输出违规行并返回 1, 否则返回 0
# 失败规则: 任何 SQL 文件包含 serial/bigserial 关键字(注释除外)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MIGRATIONS_DIR="$PROJECT_ROOT/database/migrations"

if [ ! -d "$MIGRATIONS_DIR" ]; then
  echo "ERROR: migrations directory not found: $MIGRATIONS_DIR"
  exit 1
fi

VIOLATIONS=0

for sql_file in "$MIGRATIONS_DIR"/*.sql; do
  [ -f "$sql_file" ] || continue
  # 检查 serial/bigserial (排除注释行)
  matches=$(grep -inE '^\s*[^-].*\b(serial|bigserial)\b' "$sql_file" 2>/dev/null || true)
  if [ -n "$matches" ]; then
    echo "VIOLATION: Auto-increment primary key found in $(basename "$sql_file"):"
    echo "$matches"
    VIOLATIONS=$((VIOLATIONS + 1))
  fi
done

if [ "$VIOLATIONS" -gt 0 ]; then
  echo ""
  echo "FAILED: Found $VIOLATIONS file(s) with auto-increment primary keys."
  echo "Design doc requires ULID string primary keys (varchar(32))."
  exit 1
else
  echo "PASSED: No auto-increment primary keys found."
  exit 0
fi
