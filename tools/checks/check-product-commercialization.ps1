<#
.SYNOPSIS
  GA2-L166 产品化与商业版本设计 检查脚本
.DESCRIPTION
  Design source: 31-产品化与商业版本设计.md (DOC-PRD-005)
  YAML skeleton: YuTong-Java-Docs/contracts/governance/product-commercialization.yaml
  Code alignment: backend/yutong-* 10 modules + web-admin + mobile-uniapp + database/migrations + deploy/docker-compose.run.yml
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
# Phase 1 (PC-001 ~ PC-020): 31 号文档存在性与关键内容
# ============================================================

Write-Host '=== 阶段 1: 31 号文档存在性与关键内容 ===' -ForegroundColor Cyan

$doc31 = "$root\YuTong-Java-Docs\31-产品化与商业版本设计\31-产品化与商业版本设计.md"
$yaml = "$root\YuTong-Java-Docs\contracts\governance\product-commercialization.yaml"

Test-FileExists $doc31 'PC-001' '31 号文档存在'
Test-ContentContains $doc31 '文档目标' 'PC-002' '31 号文档包含 文档目标'
Test-ContentContains $doc31 '产品定位' 'PC-003' '31 号文档包含 产品定位'
Test-ContentContains $doc31 '商业版本分层' 'PC-004' '31 号文档包含 商业版本分层'
Test-ContentContains $doc31 '交付形态' 'PC-005' '31 号文档包含 交付形态'
Test-ContentContains $doc31 '版本号策略' 'PC-006' '31 号文档包含 版本号策略'
Test-ContentContains $doc31 '升级兼容策略' 'PC-007' '31 号文档包含 升级兼容策略'
Test-ContentContains $doc31 '授权与许可预留' 'PC-008' '31 号文档包含 授权与许可预留'
Test-ContentContains $doc31 '产品官网与文档站' 'PC-009' '31 号文档包含 产品官网与文档站'
Test-ContentContains $doc31 '演示环境设计' 'PC-010' '31 号文档包含 演示环境设计'
Test-ContentContains $doc31 '客户支持与问题闭环' 'PC-011' '31 号文档包含 客户支持与问题闭环'
Test-ContentContains $doc31 '商用验收档位' 'PC-012' '31 号文档包含 商用验收档位'
Test-ContentContains $doc31 '商业版本能力矩阵' 'PC-013' '31 号文档包含 商业版本能力矩阵'
Test-ContentContains $doc31 '商业 SKU' 'PC-014' '31 号文档包含 商业 SKU 与交付边界'
Test-ContentContains $doc31 '客户验收口径' 'PC-015' '31 号文档包含 客户验收口径'
Test-ContentContains $doc31 '版本生命周期与支持策略' 'PC-016' '31 号文档包含 版本生命周期与支持策略'
Test-ContentContains $doc31 '商业交付清单' 'PC-017' '31 号文档包含 商业交付清单'
Test-ContentContains $doc31 '商业证据门槛补充' 'PC-018' '31 号文档包含 商业证据门槛补充'
Test-ContentContains $doc31 '商业运营与支持边界' 'PC-019' '31 号文档包含 商业运营与支持边界'
Test-ContentContains $doc31 '验收标准' 'PC-020' '31 号文档包含 验收标准'

# ============================================================
# Phase 2 (PC-021 ~ PC-040): YAML 骨架章节存在性
# ============================================================

Write-Host '=== 阶段 2: YAML 骨架章节存在性 ===' -ForegroundColor Cyan

Test-FileExists $yaml 'PC-021' 'YAML 骨架文件存在'
Test-ContentContains $yaml 'schemaVersion:' 'PC-022' 'YAML 包含 schemaVersion'
Test-ContentContains $yaml 'taskId: GA2-L166' 'PC-023' 'YAML 包含 taskId GA2-L166'
Test-ContentContains $yaml 'designDocId: DOC-PRD-005' 'PC-024' 'YAML 包含 designDocId DOC-PRD-005'
Test-ContentContains $yaml 'productPositioning:' 'PC-025' 'YAML 包含 productPositioning 产品定位'
Test-ContentContains $yaml 'commercialEditions:' 'PC-026' 'YAML 包含 commercialEditions 商业版本分层'
Test-ContentContains $yaml 'deliveryForms:' 'PC-027' 'YAML 包含 deliveryForms 交付形态'
Test-ContentContains $yaml 'versionNumberPolicy:' 'PC-028' 'YAML 包含 versionNumberPolicy 版本号策略'
Test-ContentContains $yaml 'upgradeCompatStrategy:' 'PC-029' 'YAML 包含 upgradeCompatStrategy 升级兼容策略'
Test-ContentContains $yaml 'licenseReservation:' 'PC-030' 'YAML 包含 licenseReservation 授权预留'
Test-ContentContains $yaml 'docSiteContent:' 'PC-031' 'YAML 包含 docSiteContent 文档站内容'
Test-ContentContains $yaml 'demoEnvironment:' 'PC-032' 'YAML 包含 demoEnvironment 演示环境'
Test-ContentContains $yaml 'customerSupport:' 'PC-033' 'YAML 包含 customerSupport 客户支持'
Test-ContentContains $yaml 'commercialAcceptanceLevels:' 'PC-034' 'YAML 包含 commercialAcceptanceLevels 验收档位'
Test-ContentContains $yaml 'versionCapabilityMatrix:' 'PC-035' 'YAML 包含 versionCapabilityMatrix 能力矩阵'
Test-ContentContains $yaml 'commercialSkuBoundary:' 'PC-036' 'YAML 包含 commercialSkuBoundary SKU 边界'
Test-ContentContains $yaml 'customerAcceptance:' 'PC-037' 'YAML 包含 customerAcceptance 客户验收'
Test-ContentContains $yaml 'versionLifecycleSupport:' 'PC-038' 'YAML 包含 versionLifecycleSupport 生命周期'
Test-ContentContains $yaml 'customerSupportLevels:' 'PC-039' 'YAML 包含 customerSupportLevels 支持分级'
Test-ContentContains $yaml 'commercialDeliveryChecklist:' 'PC-040' 'YAML 包含 commercialDeliveryChecklist 交付清单'

# ============================================================
# Phase 3 (PC-041 ~ PC-055): YAML 章节精确计数
# ============================================================

Write-Host '=== 阶段 3: YAML 章节精确计数 ===' -ForegroundColor Cyan

Test-YamlSectionCount $yaml 'productPositioning' '^\s+- positioning: ' 5 'PC-041' 'YAML productPositioning 5 项'
Test-YamlSectionCount $yaml 'commercialEditions' '^\s+- edition: ' 4 'PC-042' 'YAML commercialEditions 4 项'
Test-YamlSectionCount $yaml 'deliveryForms' '^\s+- form: ' 5 'PC-043' 'YAML deliveryForms 5 项'
Test-YamlSectionCount $yaml 'upgradeCompatStrategy' '^\s+- target: ' 6 'PC-044' 'YAML upgradeCompatStrategy 6 项'
Test-YamlSectionCount $yaml 'docSiteContent' '^\s+- content: ' 10 'PC-045' 'YAML docSiteContent 10 项'
Test-YamlSectionCount $yaml 'demoEnvironment' '^\s+- demo: ' 6 'PC-046' 'YAML demoEnvironment.demos 6 项'
Test-YamlSectionCount $yaml 'demoEnvironment' '^\s+- requirement: ' 5 'PC-047' 'YAML demoEnvironment.requirements 5 项'
Test-YamlSectionCount $yaml 'customerSupport' '^\s+- type: ' 4 'PC-048' 'YAML customerSupport 4 项'
Test-YamlSectionCount $yaml 'commercialAcceptanceLevels' '^\s+- level: ' 4 'PC-049' 'YAML commercialAcceptanceLevels 4 项'
Test-YamlSectionCount $yaml 'versionCapabilityMatrix' '^\s+- capability: ' 8 'PC-050' 'YAML versionCapabilityMatrix 8 项'
Test-YamlSectionCount $yaml 'commercialSkuBoundary' '^\s+- sku: ' 4 'PC-051' 'YAML commercialSkuBoundary 4 项'
Test-YamlSectionCount $yaml 'customerAcceptance' '^\s+- version: ' 3 'PC-052' 'YAML customerAcceptance 3 项'
Test-YamlSectionCount $yaml 'versionLifecycleSupport' '^\s+- target: ' 6 'PC-053' 'YAML versionLifecycleSupport 6 项'
Test-YamlSectionCount $yaml 'customerSupportLevels' '^\s+- level: ' 4 'PC-054' 'YAML customerSupportLevels 4 项'
Test-YamlSectionCount $yaml 'commercialDeliveryChecklist' '^\s+- deliverable: ' 8 'PC-055' 'YAML commercialDeliveryChecklist 8 项'

# ============================================================
# Phase 4 (PC-056 ~ PC-065): yutong 前缀规则验证
# ============================================================

Write-Host '=== 阶段 4: yutong 前缀规则验证 ===' -ForegroundColor Cyan

Test-ContentContains $yaml 'yutong-backend-run' 'PC-056' 'YAML 包含 yutong-backend-run 镜像'
Test-ContentContains $yaml 'yutong-redis' 'PC-057' 'YAML 包含 yutong-redis 镜像'
Test-ContentContains $yaml 'yutong-postgres' 'PC-058' 'YAML 包含 yutong-postgres 镜像'
Test-ContentContains $yaml 'yutong-minio' 'PC-059' 'YAML 包含 yutong-minio 镜像'
Test-ContentContains $yaml 'yutong_default' 'PC-060' 'YAML 包含 yutong_default 网络'

# Docker compose 文件验证 (deploy\docker-compose.run.yml)
$composeRun = "$root\deploy\docker-compose.run.yml"
Test-FileExists $composeRun 'PC-061' 'docker-compose.run.yml 存在 (deploy/)'
Test-ContentContains $composeRun 'yutong-backend-run' 'PC-062' 'docker-compose.run.yml 包含 yutong-backend-run'
Test-ContentContains $composeRun 'yutong-redis' 'PC-063' 'docker-compose.run.yml 包含 yutong-redis'
Test-ContentContains $composeRun 'yutong-postgres' 'PC-064' 'docker-compose.run.yml 包含 yutong-postgres'
Test-ContentContains $composeRun 'yutong_default' 'PC-065' 'docker-compose.run.yml 包含 yutong_default 网络'

# ============================================================
# Phase 5 (PC-066 ~ PC-075): 代码对齐验证
# ============================================================

Write-Host '=== 阶段 5: 代码对齐验证 ===' -ForegroundColor Cyan

Test-FileExists "$root\backend\yutong-common" 'PC-066' 'yutong-common 模块存在'
Test-FileExists "$root\backend\yutong-infra" 'PC-067' 'yutong-infra 模块存在'
Test-FileExists "$root\backend\yutong-auth-adapter" 'PC-068' 'yutong-auth-adapter 模块存在'
Test-FileExists "$root\backend\yutong-system-service" 'PC-069' 'yutong-system-service 模块存在'
Test-FileExists "$root\backend\yutong-sample-service" 'PC-070' 'yutong-sample-service 模块存在'
Test-FileExists "$root\backend\yutong-lowcode-service" 'PC-071' 'yutong-lowcode-service 模块存在'
Test-FileExists "$root\backend\yutong-ai-service" 'PC-072' 'yutong-ai-service 模块存在'
Test-FileExists "$root\backend\yutong-boot" 'PC-073' 'yutong-boot 模块存在'
Test-ContentContains "$root\backend\yutong-common\src\main\java\com\yutong\common\id\IdGenerator.java" 'ULID' 'PC-074' 'yutong-common IdGenerator 包含 ULID'
Test-ContentContains "$root\backend\yutong-common\src\main\java\com\yutong\common\response\Result.java" 'traceId' 'PC-075' 'yutong-common Result 包含 traceId'

# ============================================================
# Phase 6 (PC-076 ~ PC-080): 关联文档验证
# ============================================================

Write-Host '=== 阶段 6: 关联文档验证 ===' -ForegroundColor Cyan

Test-FileExists "$root\YuTong-Java-Docs\70-商业授权与版本能力裁剪详设\70-商业授权与版本能力裁剪详设.md" 'PC-076' '70 号文档存在'
Test-FileExists "$root\YuTong-Java-Docs\79-首版发布验收包与证据归档详设\79-首版发布验收包与证据归档详设.md" 'PC-077' '79 号文档存在'
Test-FileExists "$root\YuTong-Java-Docs\100-商业级产品定义与权威口径基线\100-商业级产品定义与权威口径基线.md" 'PC-078' '100 号文档存在'
Test-FileExists "$root\database\migrations\V024__init_license_tables.sql" 'PC-079' 'V024 License DDL 存在'
Test-FileExists "$root\YuTong-Java-Docs\contracts\governance\commercial-license-release.yaml" 'PC-080' 'commercial-license-release.yaml 存在'

# ============================================================
# Phase 7 (PC-081 ~ PC-085): 23 追踪记录与证据文件
# ============================================================

Write-Host '=== 阶段 7: 23 追踪记录与证据文件 ===' -ForegroundColor Cyan

$tracker = "$root\YuTong-Java-Docs\23-设计到落地追踪记录\23-设计到落地追踪记录.md"
Test-FileExists $tracker 'PC-081' '23 追踪记录存在'
Test-ContentContains $tracker '31-产品化与商业版本设计' 'PC-082' '23 追踪记录包含 31-产品化与商业版本设计'
Test-ContentContains $tracker 'L166' 'PC-083' '23 追踪记录包含 L166'
Test-FileExists "$root\release-evidence\v1.0.0\ga2-l166-evidence.md" 'PC-084' 'ga2-l166-evidence.md 证据文件存在'
Test-ContentContains $tracker 'Done' 'PC-085' '23 追踪记录包含 Done 状态'

# ============================================================
# Phase 8 (PC-086 ~ PC-089): 运行时验证
# ============================================================

Write-Host '=== 阶段 8: 运行时验证 ===' -ForegroundColor Cyan

$containers = docker ps --format '{{.Names}}' 2>$null
if ($containers -match 'yutong-backend-run') {
    Add-Result 'PC-086' 'yutong-backend-run 容器运行' 'PASS' 'yutong-backend-run 运行中'
} else {
    Add-Result 'PC-086' 'yutong-backend-run 容器运行' 'WARN' 'yutong-backend-run 未运行'
}

if ($containers -match 'yutong-redis') {
    Add-Result 'PC-087' 'yutong-redis 容器运行' 'PASS' 'yutong-redis 运行中'
} else {
    Add-Result 'PC-087' 'yutong-redis 容器运行' 'WARN' 'yutong-redis 未运行'
}

if ($containers -match 'yutong-postgres') {
    Add-Result 'PC-088' 'yutong-postgres 容器运行' 'PASS' 'yutong-postgres 运行中'
} else {
    Add-Result 'PC-088' 'yutong-postgres 容器运行' 'WARN' 'yutong-postgres 未运行'
}

# Backend health check
try {
    $resp = Invoke-WebRequest -Uri 'http://localhost:8082/actuator/health' -UseBasicParsing -TimeoutSec 5
    if ($resp.StatusCode -eq 200 -and $resp.Content -match 'UP') {
        Add-Result 'PC-089' '后端健康检查 http://localhost:8082/actuator/health' 'PASS' "HTTP $($resp.StatusCode) UP"
    } else {
        Add-Result 'PC-089' '后端健康检查 http://localhost:8082/actuator/health' 'WARN' "HTTP $($resp.StatusCode) 非 UP"
    }
} catch {
    Add-Result 'PC-089' '后端健康检查 http://localhost:8082/actuator/health' 'WARN' "请求失败: $($_.Exception.Message)"
}

# ============================================================
# Phase 9 (PC-090 ~ PC-094): 已知偏差验证
# ============================================================

Write-Host '=== 阶段 9: 已知偏差验证 ===' -ForegroundColor Cyan

Test-ContentContains $yaml 'DEV-L166-001' 'PC-090' 'YAML 包含 DEV-L166-001 偏差'
Test-ContentContains $yaml 'DEV-L166-002' 'PC-091' 'YAML 包含 DEV-L166-002 偏差'
Test-ContentContains $yaml 'DEV-L166-003' 'PC-092' 'YAML 包含 DEV-L166-003 偏差'
Test-ContentContains $yaml 'DEV-L166-004' 'PC-093' 'YAML 包含 DEV-L166-004 偏差'
Test-ContentContains $yaml 'DEV-L166-005' 'PC-094' 'YAML 包含 DEV-L166-005 偏差'

# ============================================================
# Phase 10 (PC-095 ~ PC-098): 交付物验证
# ============================================================

Write-Host '=== 阶段 10: 交付物验证 ===' -ForegroundColor Cyan

Test-ContentContains $yaml 'deliverables:' 'PC-095' 'YAML 包含 deliverables 交付物'
Test-ContentContains $yaml 'acceptance:' 'PC-096' 'YAML 包含 acceptance 验收'
Test-FileExists "$root\tools\checks\check-product-commercialization.ps1" 'PC-097' '检查脚本存在'
Test-ContentContains $yaml 'yutongPrefixRule:' 'PC-098' 'YAML 包含 yutongPrefixRule 前缀规则'

# ============================================================
# Phase 11 (PC-099 ~ PC-100): 商业运营边界与验收标准
# ============================================================

Write-Host '=== 阶段 11: 商业运营边界与验收标准 ===' -ForegroundColor Cyan

Test-YamlSectionCount $yaml 'commercialOpsBoundary' '^\s+- rule: ' 5 'PC-099' 'YAML commercialOpsBoundary 5 项'
Test-YamlSectionCount $yaml 'acceptanceCriteria' '^\s+- criterion: ' 5 'PC-100' 'YAML acceptanceCriteria 5 项'

# ============================================================
# 汇总与输出
# ============================================================

Write-Host ''
Write-Host '================================================================' -ForegroundColor Yellow
Write-Host '  GA2-L166 产品化与商业版本设计 检查结果汇总' -ForegroundColor Yellow
Write-Host '================================================================' -ForegroundColor Yellow
Write-Host "  PASS: $script:passCount" -ForegroundColor Green
Write-Host "  WARN: $script:warnCount" -ForegroundColor Yellow
Write-Host "  FAIL: $script:failCount" -ForegroundColor Red
Write-Host '================================================================' -ForegroundColor Yellow

# CSV 报告
$csvDir = "$root\build\reports\checks"
if (-not (Test-Path $csvDir)) { New-Item -ItemType Directory -Path $csvDir -Force | Out-Null }
$csvPath = "$csvDir\ga2-l166-check-results.csv"
$script:results | Export-Csv -Path $csvPath -NoTypeInformation -Encoding UTF8
Write-Host "CSV 报告: $csvPath" -ForegroundColor Cyan

# JSON 摘要
$jsonPath = "$csvDir\ga2-l166-check-summary.json"
$summary = @{
    taskId = 'GA2-L166'
    taskName = '产品化与商业版本设计'
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
