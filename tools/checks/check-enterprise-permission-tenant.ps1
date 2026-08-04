<#
.SYNOPSIS
  GA2-L167 企业级权限与租户接入方案 检查脚本
.DESCRIPTION
  Design source: 32-企业级权限与租户接入方案.md (DOC-SEC-003)
  YAML skeleton: YuTong-Java-Docs/contracts/governance/enterprise-permission-tenant.yaml
  Code alignment: backend/yutong-auth-adapter + backend/yutong-common + contracts/registries + deploy/docker-compose.run.yml
  yutong prefix rule: yutong-* docker images + yutong_default network + yutong: redis prefix + yutong- minio prefix
.NOTES
  Encoding: UTF-8 BOM (PowerShell 5.1 Chinese compatibility)
#>

$ErrorActionPreference = 'Stop'
$root = 'd:\MyCode\YuTong-Java-Vue'

$script:results = New-Object System.Collections.ArrayList
$script:passCount = 0
$script:warnCount = 0
$script:failCount = 0

function Add-Result($id, $name, $status, $detail) {
    [void]$script:results.Add([PSCustomObject]@{ ID = $id; Name = $name; Status = $status; Detail = $detail })
    if ($status -eq 'PASS') { $script:passCount++ }
    elseif ($status -eq 'WARN') { $script:warnCount++ }
    elseif ($status -eq 'FAIL') { $script:failCount++ }
}

function Test-FileExists($path, $id, $name) {
    if (Test-Path $path) {
        Add-Result $id $name 'PASS' "存在: $path"
    } else {
        Add-Result $id $name 'FAIL' "缺失: $path"
    }
}

function Test-ContentContains($path, $pattern, $id, $name) {
    if (-not (Test-Path $path)) {
        Add-Result $id $name 'FAIL' "文件不存在: $path"
        return
    }
    $content = Get-Content $path -Raw -Encoding UTF8
    if ($content -match [regex]::Escape($pattern)) {
        Add-Result $id $name 'PASS' "命中: $pattern"
    } else {
        Add-Result $id $name 'FAIL' "未命中: $pattern"
    }
}

function Test-RegexMatches($path, $pattern, $expectedMin, $id, $name) {
    if (-not (Test-Path $path)) {
        Add-Result $id $name 'FAIL' "文件不存在: $path"
        return
    }
    $content = Get-Content $path -Raw -Encoding UTF8
    if (-not $pattern.StartsWith('(?m)')) { $pattern = '(?m)' + $pattern }
    $matches = [regex]::Matches($content, $pattern)
    $count = $matches.Count
    if ($count -ge $expectedMin) {
        Add-Result $id $name 'PASS' "匹配 $count (>= $expectedMin)"
    } else {
        Add-Result $id $name 'FAIL' "匹配 $count (< $expectedMin)"
    }
}

function Test-RegexExact($path, $pattern, $expected, $id, $name) {
    if (-not (Test-Path $path)) {
        Add-Result $id $name 'FAIL' "文件不存在: $path"
        return
    }
    $content = Get-Content $path -Raw -Encoding UTF8
    if (-not $pattern.StartsWith('(?m)')) { $pattern = '(?m)' + $pattern }
    $matches = [regex]::Matches($content, $pattern)
    $count = $matches.Count
    if ($count -eq $expected) {
        Add-Result $id $name 'PASS' "匹配 $count (== $expected)"
    } else {
        Add-Result $id $name 'FAIL' "匹配 $count (!= $expected)"
    }
}

function Test-YamlSectionCount($path, $sectionName, $fieldPattern, $expected, $id, $name) {
    if (-not (Test-Path $path)) {
        Add-Result $id $name 'FAIL' "文件不存在: $path"
        return
    }
    $lines = Get-Content $path -Encoding UTF8
    $inSection = $false
    $count = 0
    foreach ($line in $lines) {
        if ($line -match "^$([regex]::Escape($sectionName))`:") {
            $inSection = $true
            continue
        }
        if ($inSection) {
            if ($line -match '^[a-zA-Z]') { break }
            if ($line -match $fieldPattern) { $count++ }
        }
    }
    if ($count -eq $expected) {
        Add-Result $id $name 'PASS' "匹配 $count (== $expected)"
    } else {
        Add-Result $id $name 'FAIL' "匹配 $count (!= $expected)"
    }
}

# ============================================================
# 阶段 1: 32 号文档存在性与关键内容
# ============================================================

Write-Host '=== 阶段 1: 32 号文档存在性与关键内容 ===' -ForegroundColor Cyan

$doc32 = "$root\YuTong-Java-Docs\32-企业级权限与租户接入方案\32-企业级权限与租户接入方案.md"
$yaml = "$root\YuTong-Java-Docs\contracts\governance\enterprise-permission-tenant.yaml"

Test-FileExists $doc32 'EP-001' '32 号文档存在'
Test-ContentContains $doc32 '文档目标' 'EP-002' '32 号文档包含文档目标'
Test-ContentContains $doc32 '设计范围' 'EP-003' '32 号文档包含设计范围'
Test-ContentContains $doc32 '权限能力分层' 'EP-004' '32 号文档包含权限能力分层'
Test-ContentContains $doc32 '身份认证接入' 'EP-005' '32 号文档包含身份认证接入'
Test-ContentContains $doc32 'GA 生产身份契约' 'EP-006' '32 号文档包含 GA 生产身份契约'
Test-ContentContains $doc32 'AuthAdapter 接口能力' 'EP-007' '32 号文档包含 AuthAdapter 接口能力'
Test-ContentContains $doc32 'RBAC 模型建议' 'EP-008' '32 号文档包含 RBAC 模型建议'
Test-ContentContains $doc32 '多租户模型' 'EP-009' '32 号文档包含多租户模型'
Test-ContentContains $doc32 '数据权限策略' 'EP-010' '32 号文档包含数据权限策略'
Test-ContentContains $doc32 '字段权限与脱敏' 'EP-011' '32 号文档包含字段权限与脱敏'
Test-ContentContains $doc32 '租户套餐和模块授权' 'EP-012' '32 号文档包含租户套餐和模块授权'
Test-ContentContains $doc32 '审计要求' 'EP-013' '32 号文档包含审计要求'
Test-ContentContains $doc32 '与现有文档关系' 'EP-014' '32 号文档包含与现有文档关系'
Test-ContentContains $doc32 '验收标准' 'EP-015' '32 号文档包含验收标准'
Test-ContentContains $doc32 'OIDC' 'EP-016' '32 号文档包含 OIDC'
Test-ContentContains $doc32 'RBAC' 'EP-017' '32 号文档包含 RBAC'
Test-ContentContains $doc32 'tenant_id' 'EP-018' '32 号文档包含 tenant_id'
Test-ContentContains $doc32 'AuthAdapter' 'EP-019' '32 号文档包含 AuthAdapter'
Test-ContentContains $doc32 '租户隔离' 'EP-020' '32 号文档包含租户隔离'

# ============================================================
# 阶段 2: YAML 骨架章节存在性
# ============================================================

Write-Host '=== 阶段 2: YAML 骨架章节存在性 ===' -ForegroundColor Cyan

Test-FileExists $yaml 'EP-021' 'YAML 骨架文件存在'
Test-ContentContains $yaml 'schemaVersion:' 'EP-022' 'YAML 包含 schemaVersion'
Test-ContentContains $yaml 'taskId: GA2-L167' 'EP-023' 'YAML 包含 taskId GA2-L167'
Test-ContentContains $yaml 'designDocId: DOC-SEC-003' 'EP-024' 'YAML 包含 designDocId DOC-SEC-003'
Test-ContentContains $yaml 'designScope:' 'EP-025' 'YAML 包含 designScope 设计范围'
Test-ContentContains $yaml 'permissionCapabilityLayers:' 'EP-026' 'YAML 包含 permissionCapabilityLayers 权限能力分层'
Test-ContentContains $yaml 'identityAuthMethods:' 'EP-027' 'YAML 包含 identityAuthMethods 身份认证方式'
Test-ContentContains $yaml 'gaProductionIdentityContract:' 'EP-028' 'YAML 包含 gaProductionIdentityContract 生产身份契约'
Test-ContentContains $yaml 'authAdapterCapabilities:' 'EP-029' 'YAML 包含 authAdapterCapabilities AuthAdapter 能力'
Test-ContentContains $yaml 'rbacModel:' 'EP-030' 'YAML 包含 rbacModel RBAC 模型'
Test-ContentContains $yaml 'multiTenantModel:' 'EP-031' 'YAML 包含 multiTenantModel 多租户模型'
Test-ContentContains $yaml 'dataPermissionStrategy:' 'EP-032' 'YAML 包含 dataPermissionStrategy 数据权限策略'
Test-ContentContains $yaml 'fieldPermissionMasking:' 'EP-033' 'YAML 包含 fieldPermissionMasking 字段权限'
Test-ContentContains $yaml 'tenantPackageModule:' 'EP-034' 'YAML 包含 tenantPackageModule 租户套餐'
Test-ContentContains $yaml 'auditRequirements:' 'EP-035' 'YAML 包含 auditRequirements 审计要求'

# ============================================================
# 阶段 3: YAML 章节精确计数
# ============================================================

Write-Host '=== 阶段 3: YAML 章节精确计数 ===' -ForegroundColor Cyan

Test-YamlSectionCount $yaml 'designScope' '^\s+- scope: ' 6 'EP-036' 'YAML designScope 6 项'
Test-YamlSectionCount $yaml 'identityAuthMethods' '^\s+- method: ' 5 'EP-037' 'YAML identityAuthMethods 5 项'
Test-YamlSectionCount $yaml 'rbacModel' '^\s+- object: ' 10 'EP-038' 'YAML rbacModel 10 对象'
Test-YamlSectionCount $yaml 'fieldPermissionMasking' '^\s+- type: ' 4 'EP-039' 'YAML fieldPermissionMasking 4 类型'
Test-YamlSectionCount $yaml 'tenantPackageModule' '^\s+- module: ' 6 'EP-040' 'YAML tenantPackageModule 6 模块'
Test-YamlSectionCount $yaml 'relatedDocs' '^\s+- doc: ' 5 'EP-041' 'YAML relatedDocs 5 项'
Test-YamlSectionCount $yaml 'acceptanceCriteria' '^\s+- criterion: ' 7 'EP-042' 'YAML acceptanceCriteria 7 项'
Test-YamlSectionCount $yaml 'existingEvidence' '^\s+- evidence: ' 9 'EP-043' 'YAML existingEvidence 9 项'
Test-YamlSectionCount $yaml 'knownDeviations' '^\s+- id: ' 5 'EP-044' 'YAML knownDeviations 5 项'
Test-YamlSectionCount $yaml 'codeAlignment' '^\s+- item: ' 15 'EP-045' 'YAML codeAlignment 15 项'
Test-RegexExact $yaml '^\s+- level: T[0-3]$' 4 'EP-046' 'YAML multiTenantModel isolationLevels 4 等级'
Test-RegexExact $yaml '^\s+- strategy: (ALL|SELF|DEPT|DEPT_AND_CHILD|CUSTOM|TENANT|NONE)$' 7 'EP-047' 'YAML dataPermissionStrategy 7 策略'
Test-YamlSectionCount $yaml 'deliverables' '^\s+- item: ' 4 'EP-048' 'YAML deliverables 4 项'
Test-YamlSectionCount $yaml 'acceptance' '^\s+- criterion: ' 4 'EP-049' 'YAML acceptance 4 项'

# ============================================================
# 阶段 4: yutong 前缀规则验证
# ============================================================

Write-Host '=== 阶段 4: yutong 前缀规则验证 ===' -ForegroundColor Cyan

Test-ContentContains $yaml 'yutong-backend-run' 'EP-050' 'YAML 包含 yutong-backend-run 镜像'
Test-ContentContains $yaml 'yutong-redis' 'EP-051' 'YAML 包含 yutong-redis 镜像'
Test-ContentContains $yaml 'yutong-postgres' 'EP-052' 'YAML 包含 yutong-postgres 镜像'
Test-ContentContains $yaml 'yutong-minio' 'EP-053' 'YAML 包含 yutong-minio 镜像'
Test-ContentContains $yaml 'yutong_default' 'EP-054' 'YAML 包含 yutong_default 网络'
Test-ContentContains $yaml "yutong:" 'EP-055' 'YAML 包含 yutong: Redis Key 前缀'
Test-ContentContains $yaml 'yutong-auth-adapter' 'EP-056' 'YAML 包含 yutong-auth-adapter 模块'
$composeRun = "$root\deploy\docker-compose.run.yml"
Test-FileExists $composeRun 'EP-057' 'docker-compose.run.yml 存在（deploy/）'
Test-ContentContains $composeRun 'yutong-backend-run' 'EP-058' 'docker-compose.run.yml 包含 yutong-backend-run'
Test-ContentContains $composeRun 'yutong_default' 'EP-059' 'docker-compose.run.yml 包含 yutong_default 网络'

# ============================================================
# 阶段 5: 代码对齐验证
# ============================================================

Write-Host '=== 阶段 5: 代码对齐验证 ===' -ForegroundColor Cyan

Test-FileExists "$root\backend\yutong-auth-adapter" 'EP-060' 'yutong-auth-adapter 模块存在'
Test-ContentContains "$root\backend\yutong-auth-adapter\src\main\java\com\yutong\auth\AuthAdapter.java" 'getDataScope' 'EP-061' 'AuthAdapter 包含 getDataScope 方法'
Test-ContentContains "$root\backend\yutong-auth-adapter\src\main\java\com\yutong\auth\AuthAdapter.java" 'STATIC_ALL_PERMISSION' 'EP-062' 'AuthAdapter 包含 STATIC_ALL_PERMISSION 约束'
Test-FileExists "$root\backend\yutong-auth-adapter\src\main\java\com\yutong\auth\MockAuthAdapter.java" 'EP-063' 'MockAuthAdapter 存在'
Test-FileExists "$root\backend\yutong-auth-adapter\src\main\java\com\yutong\auth\DataScopeResolver.java" 'EP-064' 'DataScopeResolver 存在'
Test-ContentContains "$root\backend\yutong-auth-adapter\src\main\java\com\yutong\auth\AuthContext.java" 'tenantId' 'EP-065' 'AuthContext 包含 tenantId'
Test-FileExists "$root\backend\yutong-common\src\main\java\com\yutong\common\auth\DataScope.java" 'EP-066' 'DataScope 数据范围模型存在'
Test-FileExists "$root\YuTong-Java-Docs\contracts\registries\permissions.yaml" 'EP-067' 'permissions.yaml 权限码注册表存在'
Test-ContentContains "$root\YuTong-Java-Docs\contracts\registries\permissions.yaml" 'productionIdentityRequirement' 'EP-068' 'permissions.yaml 包含生产身份要求'
Test-FileExists "$root\YuTong-Java-Docs\contracts\registries\routes.yaml" 'EP-069' 'routes.yaml 路由注册表存在'
Test-FileExists "$root\YuTong-Java-Docs\contracts\registries\statuses.yaml" 'EP-070' 'statuses.yaml 状态注册表存在'
Test-ContentContains "$root\backend\yutong-common\src\main\java\com\yutong\common\id\IdGenerator.java" 'ULID' 'EP-071' 'yutong-common IdGenerator 包含 ULID'

# ============================================================
# 阶段 6: 关联文档验证
# ============================================================

Write-Host '=== 阶段 6: 关联文档验证 ===' -ForegroundColor Cyan

Test-FileExists "$root\YuTong-Java-Docs\15-权限接入边界设计" 'EP-072' '15-权限接入边界设计 文档存在'
Test-FileExists "$root\YuTong-Java-Docs\67-数据权限与审计日志详设" 'EP-073' '67-数据权限与审计日志详设 文档存在'
Test-FileExists "$root\YuTong-Java-Docs\21-安全与质量设计" 'EP-074' '21-安全与质量设计 文档存在'
Test-FileExists "$root\YuTong-Java-Docs\05-数据架构设计" 'EP-075' '05-数据架构设计 文档存在'
Test-FileExists "$root\YuTong-Java-Docs\10-Vue3管理端设计" 'EP-076' '10-Vue3管理端设计 文档存在'

# ============================================================
# 阶段 7: 23 追踪记录与证据文件
# ============================================================

Write-Host '=== 阶段 7: 23 追踪记录与证据文件 ===' -ForegroundColor Cyan

$tracker = "$root\YuTong-Java-Docs\23-设计到落地追踪记录\23-设计到落地追踪记录.md"
Test-FileExists $tracker 'EP-077' '23 追踪记录存在'
Test-ContentContains $tracker '企业权限与租户' 'EP-078' '23 追踪记录包含企业权限与租户任务名'
Test-ContentContains $tracker 'L167' 'EP-079' '23 追踪记录包含 L167'
Test-ContentContains $tracker '32-企业级权限与租户接入方案' 'EP-080' '23 追踪记录引用 32 号文档'
Test-FileExists "$root\release-evidence\v1.0.0\ga2-l167-evidence.md" 'EP-081' 'ga2-l167-evidence.md 证据文件存在'

# ============================================================
# 阶段 8: 运行时验证
# ============================================================

Write-Host '=== 阶段 8: 运行时验证 ===' -ForegroundColor Cyan

$containers = docker ps --format '{{.Names}}' 2>$null
if ($containers -match 'yutong-backend-run') {
    Add-Result 'EP-082' 'yutong-backend-run 容器运行' 'PASS' 'yutong-backend-run 运行中'
} else {
    Add-Result 'EP-082' 'yutong-backend-run 容器运行' 'WARN' 'yutong-backend-run 未运行'
}

if ($containers -match 'yutong-redis') {
    Add-Result 'EP-083' 'yutong-redis 容器运行' 'PASS' 'yutong-redis 运行中'
} else {
    Add-Result 'EP-083' 'yutong-redis 容器运行' 'WARN' 'yutong-redis 未运行'
}

if ($containers -match 'yutong-postgres') {
    Add-Result 'EP-084' 'yutong-postgres 容器运行' 'PASS' 'yutong-postgres 运行中'
} else {
    Add-Result 'EP-084' 'yutong-postgres 容器运行' 'WARN' 'yutong-postgres 未运行'
}

try {
    $resp = Invoke-WebRequest -Uri 'http://localhost:8082/actuator/health' -UseBasicParsing -TimeoutSec 5
    if ($resp.StatusCode -eq 200) {
        Add-Result 'EP-085' '后端健康检查 http://localhost:8082/actuator/health' 'PASS' "HTTP $($resp.StatusCode)"
    } else {
        Add-Result 'EP-085' '后端健康检查 http://localhost:8082/actuator/health' 'WARN' "HTTP $($resp.StatusCode)"
    }
} catch {
    Add-Result 'EP-085' '后端健康检查 http://localhost:8082/actuator/health' 'WARN' '健康端点不可达'
}

# ============================================================
# 阶段 9: 已知偏差验证
# ============================================================

Write-Host '=== 阶段 9: 已知偏差验证 ===' -ForegroundColor Cyan

Test-ContentContains $yaml 'DEV-L167-001' 'EP-086' 'YAML 包含 DEV-L167-001 偏差'
Test-ContentContains $yaml 'DEV-L167-002' 'EP-087' 'YAML 包含 DEV-L167-002 偏差'
Test-ContentContains $yaml 'DEV-L167-003' 'EP-088' 'YAML 包含 DEV-L167-003 偏差'
Test-ContentContains $yaml 'DEV-L167-004' 'EP-089' 'YAML 包含 DEV-L167-004 偏差'
Test-ContentContains $yaml 'DEV-L167-005' 'EP-090' 'YAML 包含 DEV-L167-005 偏差'

# ============================================================
# 阶段 10: 交付物验证
# ============================================================

Write-Host '=== 阶段 10: 交付物验证 ===' -ForegroundColor Cyan

Test-ContentContains $yaml 'deliverables:' 'EP-091' 'YAML 包含 deliverables 交付物'
Test-ContentContains $yaml 'acceptance:' 'EP-092' 'YAML 包含 acceptance 验收'
Test-FileExists "$root\tools\checks\check-enterprise-permission-tenant.ps1" 'EP-093' '检查脚本存在'
Test-ContentContains $yaml 'yutongPrefixRule:' 'EP-094' 'YAML 包含 yutongPrefixRule 前缀规则'

# ============================================================
# 汇总与输出
# ============================================================

Write-Host ''
Write-Host '================================================================' -ForegroundColor Yellow
Write-Host '  GA2-L167 企业级权限与租户接入方案 检查结果汇总' -ForegroundColor Yellow
Write-Host '================================================================' -ForegroundColor Yellow
Write-Host "  PASS: $script:passCount" -ForegroundColor Green
Write-Host "  WARN: $script:warnCount" -ForegroundColor Yellow
Write-Host "  FAIL: $script:failCount" -ForegroundColor Red
Write-Host '================================================================' -ForegroundColor Yellow

# 确保输出目录存在
$reportDir = "$root\build\reports\checks"
if (-not (Test-Path $reportDir)) {
    New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
}

# CSV 报告
$csvPath = "$reportDir\ga2-l167-check-results.csv"
$script:results | Export-Csv -Path $csvPath -NoTypeInformation -Encoding UTF8
Write-Host "CSV 报告: $csvPath" -ForegroundColor Cyan

# JSON 摘要
$jsonPath = "$reportDir\ga2-l167-check-summary.json"
$summary = @{
    taskId = 'GA2-L167'
    taskName = '企业级权限与租户接入方案'
    totalChecks = $script:results.Count
    passCount = $script:passCount
    warnCount = $script:warnCount
    failCount = $script:failCount
    status = if ($script:failCount -eq 0) { 'PASS' } else { 'FAIL' }
} | ConvertTo-Json -Depth 3
Set-Content -Path $jsonPath -Value $summary -Encoding UTF8
Write-Host "JSON 摘要: $jsonPath" -ForegroundColor Cyan

if ($script:failCount -gt 0) {
    Write-Host "FAIL: 存在 $script:failCount 项失败" -ForegroundColor Red
    exit 1
}
