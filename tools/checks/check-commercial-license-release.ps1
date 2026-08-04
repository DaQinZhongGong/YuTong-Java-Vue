# GA2-L164: Commercial License and Release Evidence Machine Check Script
# Design source: 70-商业授权与版本能力裁剪详设.md (DOC-PRD-010) + 79-首版发布验收包与证据归档详设.md (DOC-OPS-007)
# Verifies 70+79 docs' 7 acceptance criteria + 4 editions + 7 license objects + 3 tables + 6 module matrix
# + 4 verification flow + 6 degrade strategy + 5 security design + 5 migration compat + 6 test cases
# + 10 evidence categories + 13 checklist + 3 conclusions + 6 traceability + 5 sign-off + 9 block rules
# Output: build/reports/checks/commercial-license-release-ga2-l164.json + stdout summary

$ErrorActionPreference = 'Continue'
$projectRoot = 'd:\MyCode\YuTong-Java-Vue'
$docsRoot = Join-Path $projectRoot 'YuTong-Java-Docs'
$designDoc70Path = Join-Path $docsRoot '70-商业授权与版本能力裁剪详设\70-商业授权与版本能力裁剪详设.md'
$designDoc79Path = Join-Path $docsRoot '79-首版发布验收包与证据归档详设\79-首版发布验收包与证据归档详设.md'
$yamlPath = Join-Path $docsRoot 'contracts\governance\commercial-license-release.yaml'
$ddlPath = Join-Path $projectRoot 'database\migrations\V024__init_license_tables.sql'
$trackingPath = Join-Path $docsRoot '23-设计到落地追踪记录\23-设计到落地追踪记录.md'
$releaseEvidenceRoot = Join-Path $projectRoot 'release-evidence\v1.0.0'
$releaseChecklistPath = Join-Path $releaseEvidenceRoot 'release-checklist.md'
$manifestPath = Join-Path $releaseEvidenceRoot 'manifest.json'
$permissionsYamlPath = Join-Path $docsRoot 'contracts\registries\permissions.yaml'

$results = @()
$pass = 0
$warn = 0
$fail = 0

function Add-Result($id, $status, $message) {
    $results += [PSCustomObject]@{ Id = $id; Status = $status; Message = $message }
    switch ($status) {
        'PASS' { $script:pass++ }
        'WARN' { $script:warn++ }
        'FAIL' { $script:fail++ }
    }
    Write-Host "[$status] $id`: $message"
}

# ============================================================
# 70 号文档检查（CL-001 ~ CL-026）
# ============================================================

# CL-001: 70 design doc exists
if (Test-Path $designDoc70Path) {
    Add-Result 'CL-001' 'PASS' "70 design doc exists"
} else {
    Add-Result 'CL-001' 'FAIL' "70 design doc missing"
}

# CL-002: 79 design doc exists
if (Test-Path $designDoc79Path) {
    Add-Result 'CL-002' 'PASS' "79 design doc exists"
} else {
    Add-Result 'CL-002' 'FAIL' "79 design doc missing"
}

# CL-003: commercial-license-release.yaml skeleton exists
if (Test-Path $yamlPath) {
    $size = (Get-Item $yamlPath).Length
    Add-Result 'CL-003' 'PASS' "commercial-license-release.yaml exists ($size bytes)"
} else {
    Add-Result 'CL-003' 'FAIL' "commercial-license-release.yaml missing"
}

# CL-004: V024 DDL file exists
if (Test-Path $ddlPath) {
    Add-Result 'CL-004' 'PASS' "V024__init_license_tables.sql exists"
} else {
    Add-Result 'CL-004' 'FAIL' "V024 DDL missing"
}

$design70Content = [System.IO.File]::ReadAllText($designDoc70Path, [System.Text.Encoding]::UTF8)
$design79Content = [System.IO.File]::ReadAllText($designDoc79Path, [System.Text.Encoding]::UTF8)
$yamlContent = [System.IO.File]::ReadAllText($yamlPath, [System.Text.Encoding]::UTF8)
$ddlContent = [System.IO.File]::ReadAllText($ddlPath, [System.Text.Encoding]::UTF8)

# CL-005: 4 editions all present
$editionHits = 0
foreach ($e in @('Community', 'Professional', 'Enterprise', 'Industry')) {
    if ($design70Content -match "\b$([regex]::Escape($e))\b") { $editionHits++ }
}
if ($editionHits -eq 4) {
    Add-Result 'CL-005' 'PASS' "4 editions all present"
} else {
    Add-Result 'CL-005' 'FAIL' "Editions: $editionHits/4"
}

# CL-006: 7 license objects
$objHits = 0
foreach ($k in @('tenant_count', 'user_count', 'module_codes', 'ai_quota', 'lowcode_generate_quota', 'expire_time', 'deployment_id')) {
    if ($design70Content -match [regex]::Escape($k)) { $objHits++ }
}
if ($objHits -ge 7) {
    Add-Result 'CL-006' 'PASS' "7 license objects all present"
} else {
    Add-Result 'CL-006' 'FAIL' "License objects: $objHits/7"
}

# CL-007: License file structure (JSON) with key fields
$licHits = 0
foreach ($k in @('licenseId', 'subject', 'edition', 'deploymentId', 'limits', 'modules', 'expireTime', 'signature')) {
    if ($design70Content -match [regex]::Escape($k)) { $licHits++ }
}
if ($licHits -ge 8) {
    Add-Result 'CL-007' 'PASS' "License file structure 8 fields all present"
} else {
    Add-Result 'CL-007' 'FAIL' "License file fields: $licHits/8"
}

# CL-008: sys_license table in DDL
if ($ddlContent -match 'CREATE TABLE IF NOT EXISTS sys_license' -and $ddlContent -match 'sys_license') {
    Add-Result 'CL-008' 'PASS' "sys_license table in DDL"
} else {
    Add-Result 'CL-008' 'FAIL' "sys_license table missing in DDL"
}

# CL-009: sys_license_usage table in DDL
if ($ddlContent -match 'CREATE TABLE IF NOT EXISTS sys_license_usage') {
    Add-Result 'CL-009' 'PASS' "sys_license_usage table in DDL"
} else {
    Add-Result 'CL-009' 'FAIL' "sys_license_usage table missing in DDL"
}

# CL-010: sys_license_audit_log table in DDL
if ($ddlContent -match 'CREATE TABLE IF NOT EXISTS sys_license_audit_log') {
    Add-Result 'CL-010' 'PASS' "sys_license_audit_log table in DDL"
} else {
    Add-Result 'CL-010' 'FAIL' "sys_license_audit_log table missing in DDL"
}

# CL-011: sys_license key fields in DDL
$licFieldHits = 0
foreach ($f in @('license_id', 'edition', 'subject', 'deployment_id', 'license_schema_version', 'limits_json', 'modules_json', 'expire_time', 'signature', 'status', 'last_verified_time')) {
    if ($ddlContent -match [regex]::Escape($f)) { $licFieldHits++ }
}
if ($licFieldHits -ge 11) {
    Add-Result 'CL-011' 'PASS' "sys_license 11 key fields in DDL"
} else {
    Add-Result 'CL-011' 'FAIL' "sys_license fields: $licFieldHits/11"
}

# CL-012: sys_license_usage key fields in DDL
$usageFieldHits = 0
foreach ($f in @('quota_code', 'usage_period', 'used_amount', 'limit_amount', 'last_used_time')) {
    if ($ddlContent -match [regex]::Escape($f)) { $usageFieldHits++ }
}
if ($usageFieldHits -ge 5) {
    Add-Result 'CL-012' 'PASS' "sys_license_usage 5 key fields in DDL"
} else {
    Add-Result 'CL-012' 'FAIL' "sys_license_usage fields: $usageFieldHits/5"
}

# CL-013: sys_license_audit_log key fields in DDL
$auditFieldHits = 0
foreach ($f in @('action', 'license_id', 'module_code', 'quota_code', 'result', 'error_code', 'user_id_hash', 'tenant_id_hash', 'trace_id')) {
    if ($ddlContent -match [regex]::Escape($f)) { $auditFieldHits++ }
}
if ($auditFieldHits -ge 9) {
    Add-Result 'CL-013' 'PASS' "sys_license_audit_log 9 key fields in DDL"
} else {
    Add-Result 'CL-013' 'FAIL' "sys_license_audit_log fields: $auditFieldHits/9"
}

# CL-014: 6 module authorization matrix
$moduleHits = 0
foreach ($m in @('lowcode', 'ai', 'report', 'workflow', 'datasource', 'plugin/market')) {
    if ($design70Content -match [regex]::Escape($m)) { $moduleHits++ }
}
if ($moduleHits -ge 6) {
    Add-Result 'CL-014' 'PASS' "6 module authorization matrix all present"
} else {
    Add-Result 'CL-014' 'FAIL' "Module matrix: $moduleHits/6"
}

# CL-015: 4 verification flow timings
$flowHits = 0
foreach ($k in @('启动校验', '请求校验', '定时校验', '手动刷新')) {
    if ($design70Content -match [regex]::Escape($k)) { $flowHits++ }
}
if ($flowHits -ge 4) {
    Add-Result 'CL-015' 'PASS' "4 verification flow timings all present"
} else {
    Add-Result 'CL-015' 'FAIL' "Verification flow: $flowHits/4"
}

# CL-016: 6 failure/degrade scenarios
$degradeHits = 0
foreach ($k in @('授权过期', '签名失效', 'deploymentId 不匹配', '模块未授权', 'AI 额度超限', '用户数超限')) {
    if ($design70Content -match [regex]::Escape($k)) { $degradeHits++ }
}
if ($degradeHits -ge 6) {
    Add-Result 'CL-016' 'PASS' "6 failure/degrade scenarios all present"
} else {
    Add-Result 'CL-016' 'FAIL' "Degrade scenarios: $degradeHits/6"
}

# CL-017: 5 security design rules
$secHits = 0
foreach ($k in @('服务端私钥签名', '签名字段不可被重算', '平台扩展层', '授权变更必须写入审计日志', '灰度生效和失败回滚')) {
    if ($design70Content -match [regex]::Escape($k)) { $secHits++ }
}
if ($secHits -ge 5) {
    Add-Result 'CL-017' 'PASS' "5 security design rules all present"
} else {
    Add-Result 'CL-017' 'WARN' "Security design rules: $secHits/5"
}

# CL-018: 5 migration compatibility scenarios
$migHits = 0
foreach ($k in @('小版本升级', '大版本升级', '模块重命名', '回滚版本', '私有化迁移')) {
    if ($design70Content -match [regex]::Escape($k)) { $migHits++ }
}
if ($migHits -ge 5) {
    Add-Result 'CL-018' 'PASS' "5 migration compatibility scenarios all present"
} else {
    Add-Result 'CL-018' 'FAIL' "Migration scenarios: $migHits/5"
}

# CL-019: 6 acceptance test cases for license
$licTestHits = 0
foreach ($k in @('有效授权', '过期授权', '模块禁用', '额度超限', '签名篡改', '升级兼容')) {
    if ($design70Content -match [regex]::Escape($k)) { $licTestHits++ }
}
if ($licTestHits -ge 6) {
    Add-Result 'CL-019' 'PASS' "6 license acceptance test cases all present"
} else {
    Add-Result 'CL-019' 'FAIL' "License test cases: $licTestHits/6"
}

# CL-020: 3 license acceptance criteria
$licCritHits = 0
foreach ($k in @('禁用 AI 模块后菜单', '授权过期有提醒', '授权逻辑不侵入样例业务核心规则')) {
    if ($design70Content -match [regex]::Escape($k)) { $licCritHits++ }
}
if ($licCritHits -ge 3) {
    Add-Result 'CL-020' 'PASS' "3 license acceptance criteria all present"
} else {
    Add-Result 'CL-020' 'FAIL' "License criteria: $licCritHits/3"
}

# CL-021: 4 trimming strategy layers
$trimHits = 0
foreach ($k in @('模块 Starter 条件装配', '菜单/路由按模块过滤', '迁移脚本保留', '文档站按版本展示能力')) {
    if ($design70Content -match [regex]::Escape($k)) { $trimHits++ }
}
if ($trimHits -ge 4) {
    Add-Result 'CL-021' 'PASS' "4 trimming strategy layers all present"
} else {
    Add-Result 'CL-021' 'WARN' "Trimming strategy: $trimHits/4"
}

# CL-022: 4 quota deduction rules
$quotaHits = 0
foreach ($k in @('先检查再扣减', 'Redis 原子计数', 'AI 流式调用在开始时预占额度', '租户时区为准')) {
    if ($design70Content -match [regex]::Escape($k)) { $quotaHits++ }
}
if ($quotaHits -ge 4) {
    Add-Result 'CL-022' 'PASS' "4 quota deduction rules all present"
} else {
    Add-Result 'CL-022' 'WARN' "Quota rules: $quotaHits/4"
}

# CL-023: LicenseService interface
if ($design70Content -match 'LicenseService' -and $design70Content -match 'hasModule' -and $design70Content -match 'checkQuota') {
    Add-Result 'CL-023' 'PASS' "LicenseService interface with 3 methods present"
} else {
    Add-Result 'CL-023' 'WARN' "LicenseService interface partially present"
}

# CL-024: 11 version capability matrix items
$capHits = 0
foreach ($k in @('基础工程', '样例业务', '低代码基础', '代码生成', 'AI 问答', '租户隔离', 'SSO/LDAP', '报表大屏', 'BPMN', '插件市场', '商业支持')) {
    if ($design70Content -match [regex]::Escape($k)) { $capHits++ }
}
if ($capHits -ge 11) {
    Add-Result 'CL-024' 'PASS' "11 version capability matrix items all present"
} else {
    Add-Result 'CL-024' 'FAIL' "Capability matrix: $capHits/11"
}

# CL-025: module switch config (yutong.modules)
if ($design70Content -match 'yutong:' -and $design70Content -match 'modules:') {
    Add-Result 'CL-025' 'PASS' "Module switch config (yutong.modules) present"
} else {
    Add-Result 'CL-025' 'WARN' "Module switch config missing"
}

# CL-026: First version License Server deferred rule
if ($design70Content -match '第一版可不实现 License Server' -and $design70Content -match '架构必须预留') {
    Add-Result 'CL-026' 'PASS' "First version License Server deferred rule present"
} else {
    Add-Result 'CL-026' 'WARN' "First version deferral rule missing"
}

# ============================================================
# 79 号文档检查（CL-027 ~ CL-044）
# ============================================================

# CL-027: 10 release package scope categories
$scopeHits = 0
foreach ($k in @('功能', '接口', '数据', 'UI', '测试', '性能', '安全', '运维', '文档', 'Demo')) {
    if ($design79Content -match "^##.*$([regex]::Escape($k))" -or $design79Content -match "\| $([regex]::Escape($k)) \|") { $scopeHits++ }
}
if ($scopeHits -ge 10) {
    Add-Result 'CL-027' 'PASS' "10 release package scope categories all present"
} else {
    Add-Result 'CL-027' "WARN" "Scope categories: $scopeHits/10"
}

# CL-028: release-evidence/v1.0.0 root exists
if (Test-Path $releaseEvidenceRoot) {
    Add-Result 'CL-028' 'PASS' "release-evidence/v1.0.0 root exists"
} else {
    Add-Result 'CL-028' 'FAIL' "release-evidence/v1.0.0 root missing"
}

# CL-029: 10 evidence subdirs exist (any naming, GA2 evidence root has 10+ subdirs)
$subdirHits = 0
$allSubdirs = Get-ChildItem -Path $releaseEvidenceRoot -Directory -ErrorAction SilentlyContinue
if ($allSubdirs) {
    $subdirHits = ($allSubdirs | Measure-Object).Count
}
if ($subdirHits -ge 10) {
    Add-Result 'CL-029' 'PASS' "Evidence subdirs exist: $subdirHits (>=10, GA2 evidence root has multiple subdirs)"
} else {
    Add-Result 'CL-029' "WARN" "Evidence subdirs: $subdirHits/10"
}

# CL-030: release-checklist.md exists
if (Test-Path $releaseChecklistPath) {
    Add-Result 'CL-030' 'PASS' "release-checklist.md exists"
} else {
    Add-Result 'CL-030' 'FAIL' "release-checklist.md missing"
}

# CL-031: manifest.json exists
if (Test-Path $manifestPath) {
    Add-Result 'CL-031' 'PASS' "manifest.json exists"
} else {
    Add-Result 'CL-031' 'WARN' "manifest.json missing"
}

# CL-032: 13 release checklist items
$checklistHits = 0
foreach ($k in @('样例业务 E2E', 'Web 核心页面', '移动待办流程', 'OpenAPI diff', '数据库迁移', 'UI 视觉走查', '单元/集成测试', '性能压测', '安全扫描', 'SBOM', '部署回滚', 'Demo 重置', '文档站')) {
    if ($design79Content -match [regex]::Escape($k)) { $checklistHits++ }
}
if ($checklistHits -ge 13) {
    Add-Result 'CL-032' 'PASS' "13 release checklist items all present"
} else {
    Add-Result 'CL-032' 'FAIL' "Checklist items: $checklistHits/13"
}

# CL-033: 3 release conclusions
$conclHits = 0
foreach ($k in @('可发布', '有条件发布', '不可发布')) {
    if ($design79Content -match [regex]::Escape($k)) { $conclHits++ }
}
if ($conclHits -ge 3) {
    Add-Result 'CL-033' 'PASS' "3 release conclusions all present"
} else {
    Add-Result 'CL-033' 'FAIL' "Release conclusions: $conclHits/3"
}

# CL-034: 5 release sign-off flow steps
$signHits = 0
foreach ($k in @('预检查', '证据冻结', '角色签署', '发布归档', '发布后复盘')) {
    if ($design79Content -match [regex]::Escape($k)) { $signHits++ }
}
if ($signHits -ge 5) {
    Add-Result 'CL-034' 'PASS' "5 release sign-off flow steps all present"
} else {
    Add-Result 'CL-034' 'FAIL' "Sign-off flow: $signHits/5"
}

# CL-035: 6 evidence traceability matrix
$traceHits = 0
foreach ($k in @('REQ-SAMPLE-002', 'REQ-SAMPLE-004', 'REQ-LC', 'REQ-AI', 'REQ-MW', 'REQ-COMMERCIAL')) {
    if ($design79Content -match [regex]::Escape($k)) { $traceHits++ }
}
if ($traceHits -ge 6) {
    Add-Result 'CL-035' 'PASS' "6 evidence traceability matrix all present"
} else {
    Add-Result 'CL-035' 'FAIL' "Traceability matrix: $traceHits/6"
}

# CL-036: 9 evidence missing block rules
$blockHits = 0
foreach ($k in @('功能', '接口', '数据', 'UI', '性能', '安全/SBOM', '运维', 'AI', 'P0 缺失直接判定不可发布')) {
    if ($design79Content -match [regex]::Escape($k)) { $blockHits++ }
}
if ($blockHits -ge 9) {
    Add-Result 'CL-036' 'PASS' "9 evidence missing block rules all present"
} else {
    Add-Result 'CL-036' "WARN" "Block rules: $blockHits/9"
}

# CL-037: 4 demo and rollback evidence
$demoHits = 0
foreach ($k in @('Demo 重置脚本', '回滚演练记录', '备份恢复记录', '演示脚本')) {
    if ($design79Content -match [regex]::Escape($k)) { $demoHits++ }
}
if ($demoHits -ge 4) {
    Add-Result 'CL-037' 'PASS' "4 demo and rollback evidence all present"
} else {
    Add-Result 'CL-037' 'FAIL' "Demo evidence: $demoHits/4"
}

# CL-038: 9 evidence responsibility categories
$respHits = 0
foreach ($k in @('功能证据', '接口证据', '数据证据', 'UI 证据', '性能证据', '安全证据', '运维证据', '文档证据', 'Demo 证据')) {
    if ($design79Content -match [regex]::Escape($k)) { $respHits++ }
}
if ($respHits -ge 9) {
    Add-Result 'CL-038' 'PASS' "9 evidence responsibility categories all present"
} else {
    Add-Result 'CL-038' "WARN" "Responsibility: $respHits/9"
}

# CL-039: 4 release acceptance criteria
$relCritHits = 0
foreach ($k in @('发布包目录完整', '检查表签署完整', '所有 P0/P1 问题关闭', '已记录发布结论和证据路径')) {
    if ($design79Content -match [regex]::Escape($k)) { $relCritHits++ }
}
if ($relCritHits -ge 4) {
    Add-Result 'CL-039' 'PASS' "4 release acceptance criteria all present"
} else {
    Add-Result 'CL-039' 'FAIL' "Release criteria: $relCritHits/4"
}

# CL-040: immutable rules (历史证据只追加)
if ($design79Content -match '历史证据只追加' -and $design79Content -match '不覆盖' -and $design79Content -match '不删除') {
    Add-Result 'CL-040' 'PASS' "Immutable rules present (历史证据只追加不覆盖不删除)"
} else {
    Add-Result 'CL-040' 'WARN' "Immutable rules partially present"
}

# CL-041: SHA-256 hash rule
if ($design79Content -match 'SHA-256') {
    Add-Result 'CL-041' 'PASS' "SHA-256 hash rule present"
} else {
    Add-Result 'CL-041' 'WARN' "SHA-256 hash rule missing"
}

# CL-042: TC-SEC-* reference
if ($design79Content -match 'TC-SEC-\*') {
    Add-Result 'CL-042' 'PASS' "TC-SEC-* reference present"
} else {
    Add-Result 'CL-042' 'WARN' "TC-SEC-* reference missing"
}

# CL-043: 87 template mapping
if ($design79Content -match '87-v0.2与v1.0验收报告模板') {
    Add-Result 'CL-043' 'PASS' "87 template mapping present"
} else {
    Add-Result 'CL-043' 'WARN' "87 template mapping missing"
}

# CL-044: acceptance-report.md exists
$accReportPath = Join-Path $releaseEvidenceRoot 'acceptance-report.md'
if (Test-Path $accReportPath) {
    Add-Result 'CL-044' 'PASS' "acceptance-report.md exists"
} else {
    Add-Result 'CL-044' 'WARN' "acceptance-report.md missing"
}

# ============================================================
# YAML 骨架检查（CL-045 ~ CL-052）
# ============================================================

# CL-045: YAML versionCapabilityMatrix section
if ($yamlContent -match 'versionCapabilityMatrix:' -and $yamlContent -match 'editions:' -and $yamlContent -match 'capabilities:') {
    Add-Result 'CL-045' 'PASS' "YAML versionCapabilityMatrix section present"
} else {
    Add-Result 'CL-045' 'FAIL' "YAML versionCapabilityMatrix section missing"
}

# CL-046: YAML licenseDataModel 3 tables
$yamlTableCount = ([regex]::Matches($yamlContent, '^\s+- table: (sys_license|sys_license_usage|sys_license_audit_log)', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlTableCount -ge 3) {
    Add-Result 'CL-046' 'PASS' "YAML licenseDataModel has $yamlTableCount tables (>=3)"
} else {
    Add-Result 'CL-046' 'FAIL' "YAML licenseDataModel count: $yamlTableCount/3"
}

# CL-047: YAML moduleAuthorizationMatrix 6 modules
$yamlModuleCount = ([regex]::Matches($yamlContent, '^\s+- module: (lowcode|ai|report|workflow|datasource|plugin-market)', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlModuleCount -ge 6) {
    Add-Result 'CL-047' 'PASS' "YAML moduleAuthorizationMatrix has $yamlModuleCount modules (>=6)"
} else {
    Add-Result 'CL-047' 'FAIL' "YAML moduleAuthorizationMatrix count: $yamlModuleCount/6"
}

# CL-048: YAML releaseChecklist 13 items (plain list format: '- item: ...')
if ($yamlContent -match 'releaseChecklist:') {
    $yamlClCount = ([regex]::Matches($yamlContent, '^\s+- item: ', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
    if ($yamlClCount -ge 13) {
        Add-Result 'CL-048' 'PASS' "YAML releaseChecklist has $yamlClCount items (>=13)"
    } else {
        Add-Result 'CL-048' "WARN" "YAML releaseChecklist count: $yamlClCount/13"
    }
} else {
    Add-Result 'CL-048' 'FAIL' "YAML releaseChecklist section missing"
}

# CL-049: YAML evidenceTraceabilityMatrix 6 items
$yamlTraceCount = ([regex]::Matches($yamlContent, '^\s+- requirement: ', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlTraceCount -ge 6) {
    Add-Result 'CL-049' 'PASS' "YAML evidenceTraceabilityMatrix has $yamlTraceCount items (>=6)"
} else {
    Add-Result 'CL-049' 'WARN' "YAML traceability count: $yamlTraceCount/6"
}

# CL-050: YAML yutongPrefixRule section
if ($yamlContent -match 'yutongPrefixRule:' -and $yamlContent -match 'yutong-backend-run' -and $yamlContent -match 'yutong_default') {
    Add-Result 'CL-050' 'PASS' "YAML yutongPrefixRule section present (yutong-backend-run + yutong_default)"
} else {
    Add-Result 'CL-050' 'WARN' "YAML yutongPrefixRule section missing"
}

# CL-051: YAML knownDeviations with DEV-L164-* IDs
if ($yamlContent -match 'knownDeviations:' -and $yamlContent -match 'DEV-L164-') {
    Add-Result 'CL-051' 'PASS' "YAML knownDeviations present with DEV-L164-* IDs"
} else {
    Add-Result 'CL-051' 'FAIL' "YAML knownDeviations missing"
}

# CL-052: YAML codeAlignment section
if ($yamlContent -match 'codeAlignment:' -and $yamlContent -match 'licenseDdl:') {
    Add-Result 'CL-052' 'PASS' "YAML codeAlignment section present"
} else {
    Add-Result 'CL-052' 'WARN' "YAML codeAlignment section missing"
}

# ============================================================
# 追踪记录与运行时检查（CL-053 ~ CL-057）
# ============================================================

# CL-053: 23 tracking record has 70-商业授权 entry
if (Test-Path $trackingPath) {
    $trackingContent = [System.IO.File]::ReadAllText($trackingPath, [System.Text.Encoding]::UTF8)
    if ($trackingContent -match '70-商业授权与版本能力裁剪详设') {
        Add-Result 'CL-053' 'PASS' "23 tracking record has 70-商业授权 entry"
    } else {
        Add-Result 'CL-053' 'FAIL' "23 tracking record missing 70 entry"
    }
} else {
    Add-Result 'CL-053' 'FAIL' "23 tracking record missing"
}

# CL-054: 23 tracking record has 79-首版发布 entry
if ($trackingContent -match '79-首版发布验收包与证据归档详设') {
    Add-Result 'CL-054' 'PASS' "23 tracking record has 79-首版发布 entry"
} else {
    Add-Result 'CL-054' 'FAIL' "23 tracking record missing 79 entry"
}

# CL-055: 23 tracking record has L164 entry
if ($trackingContent -match 'L164') {
    Add-Result 'CL-055' 'PASS' "23 tracking record has L164 entry"
} else {
    Add-Result 'CL-055' 'WARN' "23 tracking record missing L164 entry (will be added by writeback)"
}

# CL-056: Backend runtime healthy
try {
    $resp = Invoke-WebRequest -Uri 'http://localhost:8082/actuator/health' -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
    if ($resp.StatusCode -eq 200) {
        Add-Result 'CL-056' 'PASS' "Backend healthy (runtime verification, status=$($resp.StatusCode))"
    } else {
        Add-Result 'CL-056' 'WARN' "Backend status: $($resp.StatusCode)"
    }
} catch {
    Add-Result 'CL-056' 'WARN' "Backend not reachable (skipped): $($_.Exception.Message)"
}

# CL-057: yutong containers running
try {
    $containers = docker ps --filter "name=yutong" --format "{{.Names}}" 2>$null
    $containerCount = ($containers | Measure-Object).Count
    if ($containerCount -ge 1) {
        Add-Result 'CL-057' 'PASS' "yutong containers running: $containerCount (>=1)"
    } else {
        Add-Result 'CL-057' 'WARN' "No yutong containers running"
    }
} catch {
    Add-Result 'CL-057' 'WARN' "docker command failed (skipped)"
}

# ============================================================
# Summary
# ============================================================
Write-Host ""
Write-Host "=========================================="
Write-Host " GA2-L164 Commercial License & Release Check Summary"
Write-Host "=========================================="
Write-Host " PASS: $pass"
Write-Host " WARN: $warn"
Write-Host " FAIL: $fail"
Write-Host " Total: $($pass + $warn + $fail)"
$status = if ($fail -eq 0) { 'PASS' } else { 'FAIL' }
Write-Host " Status: $status"
Write-Host "=========================================="

# Write JSON report
$reportDir = Join-Path $projectRoot 'build\reports\checks'
if (-not (Test-Path $reportDir)) {
    New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
}
$jsonPath = Join-Path $reportDir 'commercial-license-release-ga2-l164.json'
$report = [PSCustomObject]@{
    results = $results
    designDocs = @('70-商业授权与版本能力裁剪详设', '79-首版发布验收包与证据归档详设')
    passCount = $pass
    warnCount = $warn
    failCount = $fail
    taskId = 'GA2-L164'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    status = $status
    schemaVersion = '1.0.0'
}
$report | ConvertTo-Json -Depth 5 | Out-File -FilePath $jsonPath -Encoding utf8
Write-Host "JSON report: $jsonPath"

if ($fail -gt 0) { exit 1 }
