#!/usr/bin/env bash
# Secret 扫描脚本
# 用法: bash tools/checks/check-secrets.sh
# 使用 gitleaks 扫描代码中的硬编码密钥
# 输入: 项目根目录
# 输出: 扫描报告到 stdout, 发现密钥返回 1
# 失败规则: 发现任何硬编码的密码、API Key、Token 等
# 前置: 需要安装 gitleaks (https://github.com/gitleaks/gitleaks)
#   - macOS: brew install gitleaks
#   - Windows: scoop install gitleaks
#   - Docker: docker run --rm -v $(pwd):/repo zricethezav/gitleaks:latest

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# 检查 gitleaks 是否安装
if ! command -v gitleaks &>/dev/null; then
  echo "WARNING: gitleaks not installed."
  echo "Install: https://github.com/gitleaks/gitleaks/releases"
  echo "Or use Docker: docker run --rm -v $PROJECT_ROOT:/repo zricethezav/gitleaks:latest detect --source=/repo"
  echo ""
  echo "Skipping secret scan (stub mode)."
  exit 0
fi

echo "Running gitleaks secret scan..."
cd "$PROJECT_ROOT"

if gitleaks detect --source . --config .gitleaks.toml --no-banner; then
  echo "PASSED: No secrets detected."
  exit 0
else
  echo "FAILED: Potential secrets detected. Review the report above."
  exit 1
fi
