#!/usr/bin/env bash
# Generate release manifest for a version
# Design: 99-CI流水线与发布证据自动化详设 (release-manifest), 87-验收证据规范
# Output: release-evidence/{version}/manifest.json + hashes.sha256
# Usage: bash tools/release/generate-manifest.sh
# Env: GIT_TAG (e.g. v0.5.0), COMMIT_SHA

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

VERSION="${GIT_TAG:-v0.5.0}"
COMMIT_SHA="${COMMIT_SHA:-$(git rev-parse HEAD 2>/dev/null || echo 'unknown')}"
BUILD_NUMBER="${BUILD_NUMBER:-0}"
BUILT_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
BUILDER="${BUILDER:-$(git config user.name 2>/dev/null || echo 'ci')}"

EVIDENCE_DIR="$PROJECT_ROOT/release-evidence/$VERSION"
mkdir -p "$EVIDENCE_DIR"

# Compute hashes for key artifacts
compute_hash() {
  local file="$1"
  if [ -f "$file" ]; then
    sha256sum "$file" | awk '{print $1}'
  else
    echo ""
  fi
}

OPENAPI_HASH=$(compute_hash "$PROJECT_ROOT/openapi/openapi.json")
MIGRATION_HASH=$(compute_hash "$PROJECT_ROOT/database/migrations")
SBOM_HASH=$(compute_hash "$PROJECT_ROOT/backend/target/bom.json")
TEST_REPORT_HASH=$(compute_hash "$PROJECT_ROOT/backend/yutong-boot/target/surefire-reports")

# Generate manifest.json
cat > "$EVIDENCE_DIR/manifest.json" <<EOF
{
  "version": "$VERSION",
  "gitTag": "$VERSION",
  "commitSha": "$COMMIT_SHA",
  "buildNumber": "$BUILD_NUMBER",
  "acceptanceProfile": "local",
  "slsaBuildLevel": "L1",
  "builtAt": "$BUILT_AT",
  "builder": "$BUILDER",
  "javaVersion": "25",
  "nodeVersion": "22",
  "imageDigests": {
    "yutong-boot": "sha256:pending",
    "yutong-web-admin": "sha256:pending"
  },
  "openapiHash": "$OPENAPI_HASH",
  "asyncapiHash": "",
  "migrationHash": "$MIGRATION_HASH",
  "sbomHash": "$SBOM_HASH",
  "provenanceHash": "",
  "signatureVerificationHash": "",
  "testReportHash": "$TEST_REPORT_HASH",
  "securityReportHash": "",
  "releaseEvidenceHash": "",
  "riskAcceptances": [],
  "signOffStatus": "PENDING"
}
EOF

# Generate hashes.sha256 (all evidence files)
cd "$EVIDENCE_DIR"
find . -type f ! -name hashes.sha256 ! -name manifest.json -exec sha256sum {} \; > hashes.sha256 2>/dev/null || true

echo "Release manifest generated: $EVIDENCE_DIR/manifest.json"
cat "$EVIDENCE_DIR/manifest.json"
echo ""
echo "PASSED: Release manifest generated."
exit 0
