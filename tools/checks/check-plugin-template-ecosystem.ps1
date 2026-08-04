<#
.SYNOPSIS
  GA2-L169 插件与模板生态设计 检查脚本
.DESCRIPTION
  Design source: 45-插件与模板生态设计.md (DOC-EXT-004)
  YAML skeleton: YuTong-Java-Docs/contracts/governance/plugin-template-ecosystem.yaml
  Code alignment: backend/yutong-lowcode-service + backend/yutong-ai-service + backend/yutong-infra + backend/yutong-auth-adapter + backend/yutong-workflow-service + database/migrations + contracts/openapi
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
# 阶段 1: 45 号文档存在性与关键内容
# ============================================================

Write-Host '=== 阶段 1: 45 号文档存在性与关键内容 ===' -ForegroundColor Cyan

$doc45 = "$root\YuTong-Java-Docs\45-插件与模板生态设计\45-插件与模板生态设计.md"
$yaml = "$root\YuTong-Java-Docs\contracts\governance\plugin-template-ecosystem.yaml"

Test-FileExists $doc45 'PE-001' '45 号文档存在'
Test-ContentContains $doc45 '文档目标' 'PE-002' '45 号文档包含文档目标'
Test-ContentContains $doc45 '生态分层' 'PE-003' '45 号文档包含生态分层'
Test-ContentContains $doc45 '可扩展点' 'PE-004' '45 号文档包含可扩展点'
Test-ContentContains $doc45 '插件包规范' 'PE-005' '45 号文档包含插件包规范'
Test-ContentContains $doc45 '插件安全模型' 'PE-006' '45 号文档包含插件安全模型'
Test-ContentContains $doc45 '低代码组件插件协议' 'PE-007' '45 号文档包含低代码组件插件协议'
Test-ContentContains $doc45 '代码生成模板' 'PE-008' '45 号文档包含代码生成模板'
Test-ContentContains $doc45 '模板市场' 'PE-009' '45 号文档包含模板市场'
Test-ContentContains $doc45 '插件运行数据模型' 'PE-010' '45 号文档包含插件运行数据模型'
Test-ContentContains $doc45 '插件安装执行流程' 'PE-011' '45 号文档包含插件安装执行流程'
Test-ContentContains $doc45 'API' 'PE-012' '45 号文档包含 API'
Test-ContentContains $doc45 '安全' 'PE-013' '45 号文档包含安全'
Test-ContentContains $doc45 '商业版本' 'PE-014' '45 号文档包含商业版本'
Test-ContentContains $doc45 '插件 API 兼容等级' 'PE-015' '45 号文档包含插件 API 兼容等级'
Test-ContentContains $doc45 '插件生命周期' 'PE-016' '45 号文档包含插件生命周期'
Test-ContentContains $doc45 '验收标准' 'PE-017' '45 号文档包含验收标准'
Test-ContentContains $doc45 'E0' 'PE-018' '45 号文档包含 E0 生态分层'
Test-ContentContains $doc45 'TemplatePlugin' 'PE-019' '45 号文档包含 TemplatePlugin 风险分级'
Test-ContentContains $doc45 'LowcodeComponentRegistry' 'PE-020' '45 号文档包含 LowcodeComponentRegistry 注册表'

# ============================================================
# 阶段 2: YAML 骨架章节存在性
# ============================================================

Write-Host '=== 阶段 2: YAML 骨架章节存在性 ===' -ForegroundColor Cyan

Test-FileExists $yaml 'PE-021' 'YAML 骨架文件存在'
Test-ContentContains $yaml 'schemaVersion:' 'PE-022' 'YAML 包含 schemaVersion'
Test-ContentContains $yaml 'taskId: GA2-L169' 'PE-023' 'YAML 包含 taskId GA2-L169'
Test-ContentContains $yaml 'designDocId: DOC-EXT-004' 'PE-024' 'YAML 包含 designDocId DOC-EXT-004'
Test-ContentContains $yaml 'designDocPath:' 'PE-025' 'YAML 包含 designDocPath'
Test-ContentContains $yaml "physicalNumber: '45'" 'PE-026' 'YAML 包含 physicalNumber 45'
Test-ContentContains $yaml 'domain: EXT' 'PE-027' 'YAML 包含 domain EXT'
Test-ContentContains $yaml 'documentPurpose:' 'PE-028' 'YAML 包含 documentPurpose'
Test-ContentContains $yaml 'ecosystemLayers:' 'PE-029' 'YAML 包含 ecosystemLayers 生态分层'
Test-ContentContains $yaml 'extensionPoints:' 'PE-030' 'YAML 包含 extensionPoints 可扩展点'
Test-ContentContains $yaml 'pluginPackageSpec:' 'PE-031' 'YAML 包含 pluginPackageSpec 插件包规范'
Test-ContentContains $yaml 'pluginSecurityModel:' 'PE-032' 'YAML 包含 pluginSecurityModel 安全模型'
Test-ContentContains $yaml 'lowcodeComponentProtocol:' 'PE-033' 'YAML 包含 lowcodeComponentProtocol 组件协议'
Test-ContentContains $yaml 'codeGenTemplates:' 'PE-034' 'YAML 包含 codeGenTemplates 代码生成模板'
Test-ContentContains $yaml 'templateMarket:' 'PE-035' 'YAML 包含 templateMarket 模板市场'
Test-ContentContains $yaml 'pluginRuntimeDataModel:' 'PE-036' 'YAML 包含 pluginRuntimeDataModel 运行数据模型'
Test-ContentContains $yaml 'pluginInstallFlow:' 'PE-037' 'YAML 包含 pluginInstallFlow 安装流程'
Test-ContentContains $yaml 'apiEndpoints:' 'PE-038' 'YAML 包含 apiEndpoints API'
Test-ContentContains $yaml 'securityRequirements:' 'PE-039' 'YAML 包含 securityRequirements 安全要求'
Test-ContentContains $yaml 'commercialEditions:' 'PE-040' 'YAML 包含 commercialEditions 商业版本'

# ============================================================
# 阶段 3: YAML 章节精确计数
# ============================================================

Write-Host '=== 阶段 3: YAML 章节精确计数 ===' -ForegroundColor Cyan

Test-YamlSectionCount $yaml 'ecosystemLayers' '^\s+- layer: E[0-3]$' 4 'PE-041' 'YAML ecosystemLayers 4 项'
Test-YamlSectionCount $yaml 'extensionPoints' '^\s+- point: ' 8 'PE-042' 'YAML extensionPoints 8 项'
Test-YamlSectionCount $yaml 'pluginPackageSpec' '^\s+- entry: ' 8 'PE-043' 'YAML pluginPackageSpec packageStructure 8 项'
Test-YamlSectionCount $yaml 'pluginPackageSpec' '^\s+- field: ' 8 'PE-044' 'YAML pluginPackageSpec pluginYamlFields 8 项'
Test-YamlSectionCount $yaml 'pluginSecurityModel' '^\s+- type: ' 6 'PE-045' 'YAML pluginSecurityModel riskLevels 6 类'
Test-YamlSectionCount $yaml 'pluginSecurityModel' '^\s+- check: ' 7 'PE-046' 'YAML pluginSecurityModel preInstallChecks 7 项'
Test-YamlSectionCount $yaml 'pluginSecurityModel' '^\s+- stage: ' 3 'PE-047' 'YAML pluginSecurityModel permissionCheckPoints 3 项'
Test-YamlSectionCount $yaml 'pluginSecurityModel' '^\s+- rule: ' 3 'PE-048' 'YAML pluginSecurityModel uninstallRules 3 项'
Test-YamlSectionCount $yaml 'lowcodeComponentProtocol' '^\s+- field: ' 6 'PE-049' 'YAML lowcodeComponentProtocol 6 字段'
Test-YamlSectionCount $yaml 'codeGenTemplates' '^\s+- type: ' 4 'PE-050' 'YAML codeGenTemplates 4 类'
Test-YamlSectionCount $yaml 'templateMarket' '^\s+- category: ' 4 'PE-051' 'YAML templateMarket templateCategories 4 类'
Test-YamlSectionCount $yaml 'templateMarket' '^\s+- field: ' 8 'PE-052' 'YAML templateMarket mktTemplate 8 字段'
Test-YamlSectionCount $yaml 'pluginRuntimeDataModel' '^\s+- field: ' 28 'PE-053' 'YAML pluginRuntimeDataModel 三表 28 字段'
Test-YamlSectionCount $yaml 'pluginInstallFlow' '^\s+- step: ' 8 'PE-054' 'YAML pluginInstallFlow 8 步'
Test-YamlSectionCount $yaml 'apiEndpoints' '^\s+- method: ' 5 'PE-055' 'YAML apiEndpoints 5 个'
Test-YamlSectionCount $yaml 'securityRequirements' '^\s+- rule: ' 6 'PE-056' 'YAML securityRequirements 6 项'
Test-YamlSectionCount $yaml 'commercialEditions' '^\s+- edition: ' 4 'PE-057' 'YAML commercialEditions 4 版本'
Test-YamlSectionCount $yaml 'pluginApiCompatLevels' '^\s+- level: ' 3 'PE-058' 'YAML pluginApiCompatLevels 3 等级'
Test-YamlSectionCount $yaml 'pluginApiCompatLevels' '^\s+- item: ' 7 'PE-059' 'YAML pluginApiCompatLevels compatibilityScan 7 项'
Test-YamlSectionCount $yaml 'pluginLifecycle' '^\s+- stage: ' 7 'PE-060' 'YAML pluginLifecycle mustActions 7 项'

# ============================================================
# 阶段 4: yutong 前缀规则验证
# ============================================================

Write-Host '=== 阶段 4: yutong 前缀规则验证 ===' -ForegroundColor Cyan

Test-ContentContains $yaml 'yutong-backend-run' 'PE-061' 'YAML 包含 yutong-backend-run 镜像'
Test-ContentContains $yaml 'yutong-redis' 'PE-062' 'YAML 包含 yutong-redis 镜像'
Test-ContentContains $yaml 'yutong-postgres' 'PE-063' 'YAML 包含 yutong-postgres 镜像'
Test-ContentContains $yaml 'yutong-minio' 'PE-064' 'YAML 包含 yutong-minio 镜像'
Test-ContentContains $yaml 'yutong_default' 'PE-065' 'YAML 包含 yutong_default 网络'
Test-ContentContains $yaml "yutong:" 'PE-066' 'YAML 包含 yutong: Redis Key 前缀'
Test-ContentContains $yaml 'yutong-' 'PE-067' 'YAML 包含 yutong- MinIO Bucket 前缀'
Test-RegexExact $yaml '^\s+- yutong-(backend-run|redis|postgres|minio)$' 4 'PE-068' 'YAML yutongPrefixRule dockerImages 4 项'
Test-RegexExact $yaml '^\s+- yutong-(common|infra|api|auth-adapter|system-service|sample-service|lowcode-service|ai-service|workflow-service|boot)$' 10 'PE-069' 'YAML yutongPrefixRule mavenModules 10 项'

$composeRun = "$root\deploy\docker-compose.run.yml"
Test-FileExists $composeRun 'PE-070' 'docker-compose.run.yml 存在（deploy/）'
Test-ContentContains $composeRun 'yutong-backend-run' 'PE-071' 'docker-compose.run.yml 包含 yutong-backend-run'
Test-ContentContains $composeRun 'yutong-redis' 'PE-072' 'docker-compose.run.yml 包含 yutong-redis'
Test-ContentContains $composeRun 'yutong-postgres' 'PE-073' 'docker-compose.run.yml 包含 yutong-postgres'
Test-ContentContains $composeRun 'yutong-minio' 'PE-074' 'docker-compose.run.yml 包含 yutong-minio'
Test-ContentContains $composeRun 'yutong_default' 'PE-075' 'docker-compose.run.yml 包含 yutong_default 网络'

# ============================================================
# 阶段 5: 代码对齐验证
# ============================================================

Write-Host '=== 阶段 5: 代码对齐验证 ===' -ForegroundColor Cyan

Test-FileExists "$root\backend\yutong-common" 'PE-076' 'yutong-common 模块存在'
Test-FileExists "$root\backend\yutong-infra" 'PE-077' 'yutong-infra 模块存在'
Test-FileExists "$root\backend\yutong-api" 'PE-078' 'yutong-api 模块存在'
Test-FileExists "$root\backend\yutong-auth-adapter" 'PE-079' 'yutong-auth-adapter 模块存在'
Test-FileExists "$root\backend\yutong-system-service" 'PE-080' 'yutong-system-service 模块存在'
Test-FileExists "$root\backend\yutong-lowcode-service" 'PE-081' 'yutong-lowcode-service 模块存在'
Test-FileExists "$root\backend\yutong-ai-service" 'PE-082' 'yutong-ai-service 模块存在'
Test-FileExists "$root\backend\yutong-workflow-service" 'PE-083' 'yutong-workflow-service 模块存在'
Test-FileExists "$root\backend\yutong-boot" 'PE-084' 'yutong-boot 模块存在'
Test-FileExists "$root\backend\yutong-sample-service" 'PE-085' 'yutong-sample-service 模块存在'
Test-FileExists "$root\database\migrations" 'PE-086' 'database/migrations 目录存在'
Test-ContentContains "$root\backend\yutong-common\src\main\java\com\yutong\common\id\IdGenerator.java" 'ULID' 'PE-087' 'yutong-common IdGenerator 包含 ULID'
Test-ContentContains "$root\backend\yutong-common\src\main\java\com\yutong\common\response\Result.java" 'traceId' 'PE-088' 'yutong-common Result 包含 traceId'
Test-FileExists "$root\YuTong-Java-Docs\contracts\openapi\openapi.yaml" 'PE-089' 'openapi.yaml OpenAPI 契约存在'
Test-FileExists "$root\YuTong-Java-Docs\contracts\registries\permissions.yaml" 'PE-090' 'permissions.yaml 权限码注册表存在'

# ============================================================
# 阶段 6: 23 追踪记录与证据文件 + 运行时验证 + 已知偏差 + 交付物
# ============================================================

Write-Host '=== 阶段 6: 23 追踪记录与证据文件 + 运行时验证 ===' -ForegroundColor Cyan

$tracker = "$root\YuTong-Java-Docs\23-设计到落地追踪记录\23-设计到落地追踪记录.md"
Test-FileExists $tracker 'PE-091' '23 追踪记录存在'
Test-ContentContains $tracker '插件与模板生态' 'PE-092' '23 追踪记录包含插件与模板生态任务名'
Test-ContentContains $tracker '45-插件与模板' 'PE-093' '23 追踪记录引用 45 号文档'

# L169 行回写检查（待主代理回写，未回写时记 WARN 不算 FAIL）
if (Test-Path $tracker) {
    $trackerContent = Get-Content $tracker -Raw -Encoding UTF8
    if ($trackerContent -match 'L169') {
        Add-Result 'PE-094' '23 追踪记录包含 L169' 'PASS' 'L169 已回写'
    } else {
        Add-Result 'PE-094' '23 追踪记录包含 L169' 'WARN' 'L169 待主代理回写'
    }
} else {
    Add-Result 'PE-094' '23 追踪记录包含 L169' 'WARN' '23 追踪记录文件不存在'
}

# 证据文件检查（本任务创建，未创建时记 WARN 不算 FAIL）
$evidenceFile = "$root\release-evidence\v1.0.0\ga2-l169-evidence.md"
if (Test-Path $evidenceFile) {
    Add-Result 'PE-095' 'ga2-l169-evidence.md 证据文件存在' 'PASS' "存在: $evidenceFile"
} else {
    Add-Result 'PE-095' 'ga2-l169-evidence.md 证据文件存在' 'WARN' '证据文件待本任务创建'
}

# 容器运行时验证（未运行时记 WARN 不算 FAIL，与既有脚本一致）
$containers = docker ps --format '{{.Names}}' 2>$null
if ($containers -match 'yutong-backend-run') {
    Add-Result 'PE-096' 'yutong-backend-run 容器运行' 'PASS' 'yutong-backend-run 运行中'
} else {
    Add-Result 'PE-096' 'yutong-backend-run 容器运行' 'WARN' 'yutong-backend-run 未运行'
}

if ($containers -match 'yutong-redis') {
    Add-Result 'PE-097' 'yutong-redis 容器运行' 'PASS' 'yutong-redis 运行中'
} else {
    Add-Result 'PE-097' 'yutong-redis 容器运行' 'WARN' 'yutong-redis 未运行'
}

if ($containers -match 'yutong-postgres') {
    Add-Result 'PE-098' 'yutong-postgres 容器运行' 'PASS' 'yutong-postgres 运行中'
} else {
    Add-Result 'PE-098' 'yutong-postgres 容器运行' 'WARN' 'yutong-postgres 未运行'
}

if ($containers -match 'yutong-minio') {
    Add-Result 'PE-099' 'yutong-minio 容器运行' 'PASS' 'yutong-minio 运行中'
} else {
    Add-Result 'PE-099' 'yutong-minio 容器运行' 'WARN' 'yutong-minio 未运行'
}

# 后端健康检查
$backendPort = if ($env:BACKEND_PORT) { $env:BACKEND_PORT } else { '8092' }
$healthUrl = "http://localhost:${backendPort}/actuator/health"
try {
    $resp = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 5
    if ($resp.StatusCode -eq 200) {
        Add-Result 'PE-100' "后端健康检查 $healthUrl" 'PASS' "HTTP $($resp.StatusCode)"
    } else {
        Add-Result 'PE-100' "后端健康检查 $healthUrl" 'WARN' "HTTP $($resp.StatusCode)"
    }
} catch {
    Add-Result 'PE-100' "后端健康检查 $healthUrl" 'WARN' '健康端点不可达'
}

# ============================================================
# 阶段 7: 已知偏差与交付物验证
# ============================================================

Write-Host '=== 阶段 7: 已知偏差与交付物验证 ===' -ForegroundColor Cyan

Test-ContentContains $yaml 'DEV-L169-001' 'PE-101' 'YAML 包含 DEV-L169-001 偏差'
Test-ContentContains $yaml 'DEV-L169-002' 'PE-102' 'YAML 包含 DEV-L169-002 偏差'
Test-ContentContains $yaml 'DEV-L169-003' 'PE-103' 'YAML 包含 DEV-L169-003 偏差'
Test-ContentContains $yaml 'DEV-L169-004' 'PE-104' 'YAML 包含 DEV-L169-004 偏差'
Test-ContentContains $yaml 'closeTime' 'PE-105' 'YAML 偏差含 closeTime 关闭路径'
Test-YamlSectionCount $yaml 'knownDeviations' '^\s+- id: ' 4 'PE-106' 'YAML knownDeviations 4 项'
Test-YamlSectionCount $yaml 'existingEvidence' '^\s+- evidence: ' 9 'PE-107' 'YAML existingEvidence 9 项'
Test-YamlSectionCount $yaml 'codeAlignment' '^\s+- item: ' 12 'PE-108' 'YAML codeAlignment 12 项'
Test-YamlSectionCount $yaml 'deliverables' '^\s+- item: ' 4 'PE-109' 'YAML deliverables 4 项'
Test-YamlSectionCount $yaml 'acceptance' '^\s+- criterion: ' 4 'PE-110' 'YAML acceptance 4 项'
Test-ContentContains $yaml 'yutongPrefixRule:' 'PE-111' 'YAML 包含 yutongPrefixRule 前缀规则'
Test-ContentContains $yaml 'pluginApiCompatLevels:' 'PE-112' 'YAML 包含 pluginApiCompatLevels 兼容等级'
Test-ContentContains $yaml 'pluginLifecycle:' 'PE-113' 'YAML 包含 pluginLifecycle 生命周期'
Test-FileExists "$root\tools\checks\check-plugin-template-ecosystem.ps1" 'PE-114' '检查脚本自身存在'

# ============================================================
# 汇总与输出
# ============================================================

Write-Host ''
Write-Host '================================================================' -ForegroundColor Yellow
Write-Host '  GA2-L169 插件与模板生态设计 检查结果汇总' -ForegroundColor Yellow
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
$csvPath = "$reportDir\ga2-l169-check-results.csv"
$script:results | Export-Csv -Path $csvPath -NoTypeInformation -Encoding UTF8
Write-Host "CSV 报告: $csvPath" -ForegroundColor Cyan

# JSON 摘要
$jsonPath = "$reportDir\ga2-l169-check-summary.json"
$summary = @{
    taskId = 'GA2-L169'
    taskName = '插件与模板生态设计'
    designDocId = 'DOC-EXT-004'
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
