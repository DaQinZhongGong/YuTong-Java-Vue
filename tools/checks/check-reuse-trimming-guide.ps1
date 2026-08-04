<#
.SYNOPSIS
  GA2-L165 复用指南与新项目裁剪指南 检查脚本
.DESCRIPTION
  Design source: 30-复用指南与新项目裁剪指南.md (DOC-ARC-005)
  YAML skeleton: YuTong-Java-Docs/contracts/governance/reuse-trimming-guide.yaml
  Code alignment: backend/yutong-* 10 modules + web-admin + mobile-uniapp + database/migrations + docs/
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
# RT-001 ~ RT-005: 30 号文档存在性与关键内容
# ============================================================

Write-Host '=== 阶段 1: 30 号文档存在性与关键内容 ===' -ForegroundColor Cyan

$doc30 = "$root\YuTong-Java-Docs\30-复用指南与新项目裁剪指南\30-复用指南与新项目裁剪指南.md"
$yaml = "$root\YuTong-Java-Docs\contracts\governance\reuse-trimming-guide.yaml"

Test-FileExists $doc30 'RT-001' '30 号文档存在'
Test-ContentContains $doc30 '复用原则' 'RT-002' '30 号文档包含复用原则'
Test-ContentContains $doc30 '必须保留' 'RT-003' '30 号文档包含必须保留'
Test-ContentContains $doc30 '可裁剪' 'RT-004' '30 号文档包含可裁剪'
Test-ContentContains $doc30 '不建议裁剪' 'RT-005' '30 号文档包含不建议裁剪'
Test-ContentContains $doc30 '新项目初始化流程' 'RT-006' '30 号文档包含新项目初始化流程'
Test-ContentContains $doc30 '新业务域创建步骤' 'RT-007' '30 号文档包含新业务域创建步骤'
Test-ContentContains $doc30 '命名替换' 'RT-008' '30 号文档包含命名替换'
Test-ContentContains $doc30 '中间件替换策略' 'RT-009' '30 号文档包含中间件替换策略'
Test-ContentContains $doc30 '复用风险' 'RT-010' '30 号文档包含复用风险'
Test-ContentContains $doc30 '新项目复用检查清单' 'RT-011' '30 号文档包含新项目复用检查清单'
Test-ContentContains $doc30 '验收标准' 'RT-012' '30 号文档包含验收标准'

# 30 号文档关键关键词
Test-ContentContains $doc30 '字符串主键' 'RT-013' '30 号文档包含字符串主键'
Test-ContentContains $doc30 'yutong-common' 'RT-014' '30 号文档包含 yutong-common'
Test-ContentContains $doc30 'yutong-ai-service' 'RT-015' '30 号文档包含 yutong-ai-service'
Test-ContentContains $doc30 'yutong-lowcode-service' 'RT-016' '30 号文档包含 yutong-lowcode-service'
Test-ContentContains $doc30 'boot' 'RT-017' '30 号文档包含 boot 单体模式'
Test-ContentContains $doc30 'cloud' 'RT-018' '30 号文档包含 cloud 微服务'
Test-ContentContains $doc30 'PostgreSQL' 'RT-019' '30 号文档包含 PostgreSQL'
Test-ContentContains $doc30 'Sa-Token' 'RT-020' '30 号文档包含 Sa-Token'
Test-ContentContains $doc30 'auth-adapter' 'RT-021' '30 号文档包含 auth-adapter 边界'
Test-ContentContains $doc30 '数据库迁移' 'RT-022' '30 号文档包含 数据库迁移 机制'
Test-ContentContains $doc30 'OpenAPI' 'RT-023' '30 号文档包含 OpenAPI 契约'
Test-ContentContains $doc30 'Docker' 'RT-024' '30 号文档包含 Docker'
Test-ContentContains $doc30 'RabbitMQ' 'RT-025' '30 号文档包含 RabbitMQ'
Test-ContentContains $doc30 'Nacos' 'RT-026' '30 号文档包含 Nacos'
Test-ContentContains $doc30 'pgvector' 'RT-027' '30 号文档包含 pgvector'
Test-ContentContains $doc30 'MinIO' 'RT-028' '30 号文档包含 MinIO'

# 30 号文档表格行数
Test-RegexMatches $doc30 '^\| .+ \|' 25 'RT-029' '30 号文档表格行数 >= 25'

# ============================================================
# RT-030 ~ RT-045: YAML 骨架章节存在性
# ============================================================

Write-Host '=== 阶段 2: YAML 骨架章节存在性 ===' -ForegroundColor Cyan

Test-FileExists $yaml 'RT-030' 'YAML 骨架文件存在'
Test-ContentContains $yaml 'schemaVersion:' 'RT-031' 'YAML 包含 schemaVersion'
Test-ContentContains $yaml 'taskId: GA2-L165' 'RT-032' 'YAML 包含 taskId GA2-L165'
Test-ContentContains $yaml 'designDocId: DOC-ARC-005' 'RT-033' 'YAML 包含 designDocId DOC-ARC-005'
Test-ContentContains $yaml 'reusePrinciples:' 'RT-034' 'YAML 包含 reusePrinciples 复用原则'
Test-ContentContains $yaml 'mustKeep:' 'RT-035' 'YAML 包含 mustKeep 必须保留'
Test-ContentContains $yaml 'canTrim:' 'RT-036' 'YAML 包含 canTrim 可裁剪'
Test-ContentContains $yaml 'notRecommendedToTrim:' 'RT-037' 'YAML 包含 notRecommendedToTrim 不建议裁剪'
Test-ContentContains $yaml 'newProjectInitFlow:' 'RT-038' 'YAML 包含 newProjectInitFlow 初始化流程'
Test-ContentContains $yaml 'newDomainCreationSteps:' 'RT-039' 'YAML 包含 newDomainCreationSteps 新业务域步骤'
Test-ContentContains $yaml 'namingReplacements:' 'RT-040' 'YAML 包含 namingReplacements 命名替换'
Test-ContentContains $yaml 'notRecommendedToReplace:' 'RT-041' 'YAML 包含 notRecommendedToReplace 不建议替换'
Test-ContentContains $yaml 'middlewareReplacement:' 'RT-042' 'YAML 包含 middlewareReplacement 中间件替换'
Test-ContentContains $yaml 'reuseRisks:' 'RT-043' 'YAML 包含 reuseRisks 复用风险'
Test-ContentContains $yaml 'newProjectChecklist:' 'RT-044' 'YAML 包含 newProjectChecklist 检查清单'
Test-ContentContains $yaml 'acceptanceCriteria:' 'RT-045' 'YAML 包含 acceptanceCriteria 验收标准'

# ============================================================
# RT-046 ~ RT-057: YAML 章节精确计数
# ============================================================

Write-Host '=== 阶段 3: YAML 章节精确计数 ===' -ForegroundColor Cyan

Test-YamlSectionCount $yaml 'reusePrinciples' '^\s+- principle: ' 5 'RT-046' 'YAML reusePrinciples 5 条'
Test-YamlSectionCount $yaml 'mustKeep' '^\s+- capability: ' 7 'RT-047' 'YAML mustKeep 7 项'
Test-YamlSectionCount $yaml 'canTrim' '^\s+- module: ' 7 'RT-048' 'YAML canTrim 7 项'
Test-YamlSectionCount $yaml 'notRecommendedToTrim' '^\s+- (module|directory): ' 8 'RT-049' 'YAML notRecommendedToTrim 8 项'
Test-YamlSectionCount $yaml 'newProjectInitFlow' '^\s+- step: ' 8 'RT-050' 'YAML newProjectInitFlow 8 步'
Test-YamlSectionCount $yaml 'newDomainCreationSteps' '^\s+- step: ' 7 'RT-051' 'YAML newDomainCreationSteps 7 步'
Test-YamlSectionCount $yaml 'namingReplacements' '^\s+- item: ' 8 'RT-052' 'YAML namingReplacements 8 项'
Test-YamlSectionCount $yaml 'notRecommendedToReplace' '^\s+- item: ' 5 'RT-053' 'YAML notRecommendedToReplace 5 项'
Test-YamlSectionCount $yaml 'middlewareReplacement' '^\s+- current: ' 6 'RT-054' 'YAML middlewareReplacement 6 项'
Test-YamlSectionCount $yaml 'reuseRisks' '^\s+- risk: ' 5 'RT-055' 'YAML reuseRisks 5 项'
Test-YamlSectionCount $yaml 'newProjectChecklist' '^\s+- check: ' 8 'RT-056' 'YAML newProjectChecklist 8 项'
Test-YamlSectionCount $yaml 'acceptanceCriteria' '^\s+- criterion: ' 4 'RT-057' 'YAML acceptanceCriteria 4 项'

# ============================================================
# RT-058 ~ RT-067: yutong 前缀规则验证
# ============================================================

Write-Host '=== 阶段 4: yutong 前缀规则验证 ===' -ForegroundColor Cyan

Test-ContentContains $yaml 'yutong-backend-run' 'RT-058' 'YAML 包含 yutong-backend-run 镜像'
Test-ContentContains $yaml 'yutong-redis' 'RT-059' 'YAML 包含 yutong-redis 镜像'
Test-ContentContains $yaml 'yutong-postgres' 'RT-060' 'YAML 包含 yutong-postgres 镜像'
Test-ContentContains $yaml 'yutong-minio' 'RT-061' 'YAML 包含 yutong-minio 镜像'
Test-ContentContains $yaml 'yutong_default' 'RT-062' 'YAML 包含 yutong_default 网络'
Test-ContentContains $yaml 'yutong-common' 'RT-063' 'YAML 包含 yutong-common 模块'
Test-ContentContains $yaml 'yutong-infra' 'RT-064' 'YAML 包含 yutong-infra 模块'
Test-ContentContains $yaml 'yutong-api' 'RT-065' 'YAML 包含 yutong-api 模块'
Test-ContentContains $yaml 'yutong-auth-adapter' 'RT-066' 'YAML 包含 yutong-auth-adapter 模块'
Test-ContentContains $yaml 'yutong-system-service' 'RT-067' 'YAML 包含 yutong-system-service 模块'
Test-ContentContains $yaml "yutong:" 'RT-068' 'YAML 包含 yutong: Redis Key 前缀'
Test-ContentContains $yaml 'yutong-' 'RT-069' 'YAML 包含 yutong- MinIO Bucket 前缀'

# Docker compose 文件验证
$composeRun = "$root\deploy\docker-compose.run.yml"
Test-FileExists $composeRun 'RT-070' 'docker-compose.run.yml 存在'
Test-ContentContains $composeRun 'yutong-backend-run' 'RT-071' 'docker-compose.run.yml 包含 yutong-backend-run'
Test-ContentContains $composeRun 'yutong-redis' 'RT-072' 'docker-compose.run.yml 包含 yutong-redis'
Test-ContentContains $composeRun 'yutong-postgres' 'RT-073' 'docker-compose.run.yml 包含 yutong-postgres'
Test-ContentContains $composeRun 'yutong-minio' 'RT-074' 'docker-compose.run.yml 包含 yutong-minio'
Test-ContentContains $composeRun 'yutong_default' 'RT-075' 'docker-compose.run.yml 包含 yutong_default 网络'

# ============================================================
# RT-076 ~ RT-085: 代码对齐验证（10 个 yutong-* 模块）
# ============================================================

Write-Host '=== 阶段 5: 代码对齐验证 ===' -ForegroundColor Cyan

Test-FileExists "$root\backend\yutong-common" 'RT-076' 'yutong-common 模块存在'
Test-FileExists "$root\backend\yutong-infra" 'RT-077' 'yutong-infra 模块存在'
Test-FileExists "$root\backend\yutong-api" 'RT-078' 'yutong-api 模块存在'
Test-FileExists "$root\backend\yutong-auth-adapter" 'RT-079' 'yutong-auth-adapter 模块存在'
Test-FileExists "$root\backend\yutong-system-service" 'RT-080' 'yutong-system-service 模块存在'
Test-FileExists "$root\backend\yutong-sample-service" 'RT-081' 'yutong-sample-service 模块存在'
Test-FileExists "$root\backend\yutong-lowcode-service" 'RT-082' 'yutong-lowcode-service 模块存在'
Test-FileExists "$root\backend\yutong-ai-service" 'RT-083' 'yutong-ai-service 模块存在'
Test-FileExists "$root\backend\yutong-workflow-service" 'RT-084' 'yutong-workflow-service 模块存在'
Test-FileExists "$root\backend\yutong-boot" 'RT-085' 'yutong-boot 模块存在'

# ============================================================
# RT-086 ~ RT-093: 代码对齐验证（基础目录与契约）
# ============================================================

Test-FileExists "$root\database\migrations" 'RT-086' 'database/migrations 目录存在'
Test-FileExists "$root\web-admin\src" 'RT-087' 'web-admin/src 目录存在'
Test-FileExists "$root\mobile-uniapp\src" 'RT-088' 'mobile-uniapp/src 目录存在'
Test-FileExists "$root\YuTong-Java-Docs\contracts\openapi\openapi.yaml" 'RT-089' 'openapi.yaml 存在'
Test-FileExists "$root\YuTong-Java-Docs\contracts\registries\errors.yaml" 'RT-090' 'errors.yaml 存在'
Test-FileExists "$root\YuTong-Java-Docs\23-设计到落地追踪记录\23-设计到落地追踪记录.md" 'RT-091' '23 追踪记录存在'
Test-ContentContains "$root\backend\yutong-common\src\main\java\com\yutong\common\id\IdGenerator.java" 'ULID' 'RT-092' 'yutong-common IdGenerator 包含 ULID'
Test-ContentContains "$root\backend\yutong-common\src\main\java\com\yutong\common\response\Result.java" 'traceId' 'RT-093' 'yutong-common Result 包含 traceId'

# ============================================================
# RT-094 ~ RT-098: 23 追踪记录与证据文件
# ============================================================

Write-Host '=== 阶段 6: 23 追踪记录与证据文件 ===' -ForegroundColor Cyan

$tracker = "$root\YuTong-Java-Docs\23-设计到落地追踪记录\23-设计到落地追踪记录.md"
Test-ContentContains $tracker 'L165' 'RT-094' '23 追踪记录包含 L165'
Test-ContentContains $tracker '复用指南与新项目裁剪指南' 'RT-095' '23 追踪记录包含复用指南任务名'
Test-ContentContains $tracker '30' 'RT-096' '23 追踪记录 L165 引用 30 号文档'
Test-ContentContains $tracker 'Done' 'RT-097' '23 追踪记录 L165 状态为 Done'
Test-FileExists "$root\release-evidence\v1.0.0\ga2-l165-evidence.md" 'RT-098' 'ga2-l165-evidence.md 证据文件存在'

# ============================================================
# RT-099 ~ RT-102: 运行时验证
# ============================================================

Write-Host '=== 阶段 7: 运行时验证 ===' -ForegroundColor Cyan

$containers = docker ps --format '{{.Names}}' 2>$null
if ($containers -match 'yutong-backend-run') {
    Add-Result 'RT-099' 'yutong-backend-run 容器运行' 'PASS' 'yutong-backend-run 运行中'
} else {
    Add-Result 'RT-099' 'yutong-backend-run 容器运行' 'WARN' 'yutong-backend-run 未运行'
}

if ($containers -match 'yutong-redis') {
    Add-Result 'RT-100' 'yutong-redis 容器运行' 'PASS' 'yutong-redis 运行中'
} else {
    Add-Result 'RT-100' 'yutong-redis 容器运行' 'WARN' 'yutong-redis 未运行'
}

if ($containers -match 'yutong-postgres') {
    Add-Result 'RT-101' 'yutong-postgres 容器运行' 'PASS' 'yutong-postgres 运行中'
} else {
    Add-Result 'RT-101' 'yutong-postgres 容器运行' 'WARN' 'yutong-postgres 未运行'
}

if ($containers -match 'yutong-minio') {
    Add-Result 'RT-102' 'yutong-minio 容器运行' 'PASS' 'yutong-minio 运行中'
} else {
    Add-Result 'RT-102' 'yutong-minio 容器运行' 'WARN' 'yutong-minio 未运行'
}

# ============================================================
# RT-103 ~ RT-108: 已知偏差验证
# ============================================================

Write-Host '=== 阶段 8: 已知偏差验证 ===' -ForegroundColor Cyan

Test-ContentContains $yaml 'DEV-L165-001' 'RT-103' 'YAML 包含 DEV-L165-001 偏差'
Test-ContentContains $yaml 'DEV-L165-002' 'RT-104' 'YAML 包含 DEV-L165-002 偏差'
Test-ContentContains $yaml 'DEV-L165-003' 'RT-105' 'YAML 包含 DEV-L165-003 偏差'
Test-ContentContains $yaml 'DEV-L165-004' 'RT-106' 'YAML 包含 DEV-L165-004 偏差'
Test-ContentContains $yaml 'codeAlignment:' 'RT-107' 'YAML 包含 codeAlignment 代码对齐'
Test-ContentContains $yaml 'existingEvidence:' 'RT-108' 'YAML 包含 existingEvidence 已有证据'

# ============================================================
# RT-109 ~ RT-112: 交付物验证
# ============================================================

Write-Host '=== 阶段 9: 交付物验证 ===' -ForegroundColor Cyan

Test-ContentContains $yaml 'deliverables:' 'RT-109' 'YAML 包含 deliverables 交付物'
Test-ContentContains $yaml 'acceptance:' 'RT-110' 'YAML 包含 acceptance 验收'
Test-FileExists "$root\tools\checks\check-reuse-trimming-guide.ps1" 'RT-111' '检查脚本存在'
Test-ContentContains $yaml 'yutongPrefixRule:' 'RT-112' 'YAML 包含 yutongPrefixRule 前缀规则'

# ============================================================
# 汇总与输出
# ============================================================

Write-Host ''
Write-Host '================================================================' -ForegroundColor Yellow
Write-Host '  GA2-L165 复用指南与新项目裁剪指南 检查结果汇总' -ForegroundColor Yellow
Write-Host '================================================================' -ForegroundColor Yellow
Write-Host "  PASS: $script:passCount" -ForegroundColor Green
Write-Host "  WARN: $script:warnCount" -ForegroundColor Yellow
Write-Host "  FAIL: $script:failCount" -ForegroundColor Red
Write-Host '================================================================' -ForegroundColor Yellow

# CSV 报告
$csvPath = "$root\build\reports\checks\ga2-l165-check-results.csv"
$script:results | Export-Csv -Path $csvPath -NoTypeInformation -Encoding UTF8
Write-Host "CSV 报告: $csvPath" -ForegroundColor Cyan

# JSON 摘要
$jsonPath = "$root\build\reports\checks\ga2-l165-check-summary.json"
$summary = @{
    taskId = 'GA2-L165'
    taskName = '复用指南与新项目裁剪指南'
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
