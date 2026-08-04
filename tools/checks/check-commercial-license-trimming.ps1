<#
.SYNOPSIS
  GA2-L170 商业授权与版本能力裁剪详设 检查脚本
.DESCRIPTION
  Design source: 70-商业授权与版本能力裁剪详设.md (DOC-PRD-010)
  YAML skeleton: YuTong-Java-Docs/contracts/governance/commercial-license-trimming.yaml
  Code alignment: backend/yutong-* 10 模块 + yutong-boot application.yml + database/migrations + deploy/docker-compose.run.yml + IdGenerator + Result
  yutong prefix rule: yutong-* docker images + yutong_default network + yutong.modules config prefix + yutong: redis prefix + yutong- minio prefix
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
    $sectionIndent = 0
    $count = 0
    foreach ($line in $lines) {
        if (-not $inSection) {
            if ($line -match "^(\s*)$([regex]::Escape($sectionName))`:\s*(#.*)?$") {
                $inSection = $true
                $sectionIndent = $matches[1].Length
                continue
            }
        } else {
            if ($line.Trim() -eq '') { continue }
            if ($line -match '^(\s*)(\S)') {
                $currentIndent = $matches[1].Length
                if ($currentIndent -le $sectionIndent) { break }
            }
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
# 阶段 1: 70 号文档存在性与关键内容
# ============================================================

Write-Host '=== 阶段 1: 70 号文档存在性与关键内容 ===' -ForegroundColor Cyan

$doc70 = "$root\YuTong-Java-Docs\70-商业授权与版本能力裁剪详设\70-商业授权与版本能力裁剪详设.md"
$yaml = "$root\YuTong-Java-Docs\contracts\governance\commercial-license-trimming.yaml"

Test-FileExists $doc70 'CL-001' '70 号文档存在'
Test-ContentContains $doc70 '文档目标' 'CL-002' '70 号文档包含文档目标'
Test-ContentContains $doc70 '版本能力矩阵' 'CL-003' '70 号文档包含版本能力矩阵'
Test-ContentContains $doc70 '授权对象' 'CL-004' '70 号文档包含授权对象'
Test-ContentContains $doc70 'License 文件结构' 'CL-005' '70 号文档包含 License 文件结构'
Test-ContentContains $doc70 '模块开关' 'CL-006' '70 号文档包含模块开关'
Test-ContentContains $doc70 '授权校验接口' 'CL-007' '70 号文档包含授权校验接口'
Test-ContentContains $doc70 '授权运行数据模型' 'CL-008' '70 号文档包含授权运行数据模型'
Test-ContentContains $doc70 '裁剪策略' 'CL-009' '70 号文档包含裁剪策略'
Test-ContentContains $doc70 '模块授权行为矩阵' 'CL-010' '70 号文档包含模块授权行为矩阵'
Test-ContentContains $doc70 '授权校验流程' 'CL-011' '70 号文档包含授权校验流程'
Test-ContentContains $doc70 '失败与降级策略' 'CL-012' '70 号文档包含失败与降级策略'
Test-ContentContains $doc70 '授权安全设计' 'CL-013' '70 号文档包含授权安全设计'
Test-ContentContains $doc70 '授权迁移与兼容' 'CL-014' '70 号文档包含授权迁移与兼容'
Test-ContentContains $doc70 '验收用例' 'CL-015' '70 号文档包含验收用例'
Test-ContentContains $doc70 '验收标准' 'CL-016' '70 号文档包含验收标准'
Test-ContentContains $doc70 'Community' 'CL-017' '70 号文档包含 Community 版本'
Test-ContentContains $doc70 'Enterprise' 'CL-018' '70 号文档包含 Enterprise 版本'
Test-ContentContains $doc70 'LicenseService' 'CL-019' '70 号文档包含 LicenseService 接口'
Test-ContentContains $doc70 'deploymentId' 'CL-020' '70 号文档包含 deploymentId'

# ============================================================
# 阶段 2: YAML 骨架章节存在性
# ============================================================

Write-Host '=== 阶段 2: YAML 骨架章节存在性 ===' -ForegroundColor Cyan

Test-FileExists $yaml 'CL-021' 'YAML 骨架文件存在'
Test-ContentContains $yaml 'schemaVersion:' 'CL-022' 'YAML 包含 schemaVersion'
Test-ContentContains $yaml 'taskId: GA2-L170' 'CL-023' 'YAML 包含 taskId GA2-L170'
Test-ContentContains $yaml 'designDocId: DOC-PRD-010' 'CL-024' 'YAML 包含 designDocId DOC-PRD-010'
Test-ContentContains $yaml 'documentPurpose:' 'CL-025' 'YAML 包含 documentPurpose'
Test-ContentContains $yaml 'versionCapabilityMatrix:' 'CL-026' 'YAML 包含 versionCapabilityMatrix'
Test-ContentContains $yaml 'licenseObjects:' 'CL-027' 'YAML 包含 licenseObjects'
Test-ContentContains $yaml 'licenseFileStructure:' 'CL-028' 'YAML 包含 licenseFileStructure'
Test-ContentContains $yaml 'moduleSwitch:' 'CL-029' 'YAML 包含 moduleSwitch'
Test-ContentContains $yaml 'licenseCheckInterface:' 'CL-030' 'YAML 包含 licenseCheckInterface'
Test-ContentContains $yaml 'licenseRuntimeDataModel:' 'CL-031' 'YAML 包含 licenseRuntimeDataModel'
Test-ContentContains $yaml 'trimmingStrategy:' 'CL-032' 'YAML 包含 trimmingStrategy'
Test-ContentContains $yaml 'moduleAuthBehaviorMatrix:' 'CL-033' 'YAML 包含 moduleAuthBehaviorMatrix'
Test-ContentContains $yaml 'licenseCheckFlow:' 'CL-034' 'YAML 包含 licenseCheckFlow'
Test-ContentContains $yaml 'failureDegradeStrategy:' 'CL-035' 'YAML 包含 failureDegradeStrategy'
Test-ContentContains $yaml 'licenseSecurityDesign:' 'CL-036' 'YAML 包含 licenseSecurityDesign'
Test-ContentContains $yaml 'licenseMigrationCompat:' 'CL-037' 'YAML 包含 licenseMigrationCompat'
Test-ContentContains $yaml 'acceptanceUseCases:' 'CL-038' 'YAML 包含 acceptanceUseCases'
Test-ContentContains $yaml 'acceptanceCriteria:' 'CL-039' 'YAML 包含 acceptanceCriteria'
Test-ContentContains $yaml 'yutongPrefixRule:' 'CL-040' 'YAML 包含 yutongPrefixRule'

# ============================================================
# 阶段 3: YAML 章节精确计数
# ============================================================

Write-Host '=== 阶段 3: YAML 章节精确计数 ===' -ForegroundColor Cyan

Test-YamlSectionCount $yaml 'versionCapabilityMatrix' '^\s+- capability: ' 11 'CL-041' 'YAML versionCapabilityMatrix 11 能力'
Test-YamlSectionCount $yaml 'licenseObjects' '^\s+- object: ' 7 'CL-042' 'YAML licenseObjects 7 项'
Test-YamlSectionCount $yaml 'licenseFileStructure' '^\s+- field: ' 8 'CL-043' 'YAML licenseFileStructure 8 字段'
Test-YamlSectionCount $yaml 'moduleSwitch' '^\s+- code: ' 4 'CL-044' 'YAML moduleSwitch 4 模块'
Test-YamlSectionCount $yaml 'licenseCheckInterface' '^\s+- method: ' 3 'CL-045' 'YAML licenseCheckInterface 3 方法'
Test-YamlSectionCount $yaml 'sys_license' '^\s+- field: ' 11 'CL-046' 'YAML sys_license 11 字段'
Test-YamlSectionCount $yaml 'sys_license_usage' '^\s+- field: ' 6 'CL-047' 'YAML sys_license_usage 6 字段'
Test-YamlSectionCount $yaml 'sys_license_audit_log' '^\s+- field: ' 7 'CL-048' 'YAML sys_license_audit_log 7 字段'
Test-YamlSectionCount $yaml 'quotaDeductionRules' '^\s+- rule: ' 4 'CL-049' 'YAML quotaDeductionRules 4 项'
Test-YamlSectionCount $yaml 'trimmingStrategy' '^\s+- layer: ' 4 'CL-050' 'YAML trimmingStrategy 4 层级'
Test-YamlSectionCount $yaml 'moduleAuthBehaviorMatrix' '^\s+- module: ' 6 'CL-051' 'YAML moduleAuthBehaviorMatrix 6 模块'
Test-YamlSectionCount $yaml 'licenseCheckFlow' '^\s+- timing: ' 4 'CL-052' 'YAML licenseCheckFlow 4 时机'
Test-YamlSectionCount $yaml 'failureDegradeStrategy' '^\s+- scenario: ' 6 'CL-053' 'YAML failureDegradeStrategy 6 场景'
Test-YamlSectionCount $yaml 'licenseSecurityDesign' '^\s+- item: ' 5 'CL-054' 'YAML licenseSecurityDesign 5 项'
Test-YamlSectionCount $yaml 'licenseMigrationCompat' '^\s+- scenario: ' 5 'CL-055' 'YAML licenseMigrationCompat 5 场景'
Test-YamlSectionCount $yaml 'acceptanceUseCases' '^\s+- scenario: ' 6 'CL-056' 'YAML acceptanceUseCases 6 场景'
Test-YamlSectionCount $yaml 'acceptanceCriteria' '^\s+- criterion: ' 3 'CL-057' 'YAML acceptanceCriteria 3 项'
Test-YamlSectionCount $yaml 'existingEvidence' '^\s+- evidence: ' 13 'CL-058' 'YAML existingEvidence 13 项 (GA2-L170 源代码落地追加 3 项)'
Test-YamlSectionCount $yaml 'knownDeviations' '^\s+- id: ' 4 'CL-059' 'YAML knownDeviations 4 项'
Test-YamlSectionCount $yaml 'codeAlignment' '^\s+- item: ' 12 'CL-060' 'YAML codeAlignment 12 项'

# ============================================================
# 阶段 4: yutong 前缀规则验证
# ============================================================

Write-Host '=== 阶段 4: yutong 前缀规则验证 ===' -ForegroundColor Cyan

Test-ContentContains $yaml 'yutong-backend-run' 'CL-061' 'YAML 包含 yutong-backend-run 镜像'
Test-ContentContains $yaml 'yutong-redis' 'CL-062' 'YAML 包含 yutong-redis 镜像'
Test-ContentContains $yaml 'yutong-postgres' 'CL-063' 'YAML 包含 yutong-postgres 镜像'
Test-ContentContains $yaml 'yutong-minio' 'CL-064' 'YAML 包含 yutong-minio 镜像'
Test-ContentContains $yaml 'yutong_default' 'CL-065' 'YAML 包含 yutong_default 网络'
Test-ContentContains $yaml "yutong:" 'CL-066' 'YAML 包含 yutong: Redis Key 前缀'
Test-ContentContains $yaml "minioBucketPrefix: 'yutong-'" 'CL-067' 'YAML 包含 yutong- MinIO Bucket 前缀'
Test-ContentContains $yaml 'configPrefix: yutong.modules' 'CL-068' 'YAML 包含 yutong.modules 配置前缀'
$composeRun = "$root\deploy\docker-compose.run.yml"
Test-FileExists $composeRun 'CL-069' 'docker-compose.run.yml 存在（deploy/）'
Test-ContentContains $composeRun 'yutong-backend-run' 'CL-070' 'docker-compose.run.yml 包含 yutong-backend-run'
Test-ContentContains $composeRun 'yutong_default' 'CL-071' 'docker-compose.run.yml 包含 yutong_default 网络'
Test-YamlSectionCount $yaml 'mavenModules' '^\s+- yutong-' 10 'CL-072' 'YAML mavenModules 10 模块'
Test-ContentContains $yaml 'yutong-common' 'CL-073' 'YAML 包含 yutong-common 模块'
Test-ContentContains $yaml 'yutong-boot' 'CL-074' 'YAML 包含 yutong-boot 模块'
Test-ContentContains $yaml 'yutong-auth-adapter' 'CL-075' 'YAML 包含 yutong-auth-adapter 模块'

# ============================================================
# 阶段 5: 代码对齐验证
# ============================================================

Write-Host '=== 阶段 5: 代码对齐验证 ===' -ForegroundColor Cyan

Test-FileExists "$root\backend\yutong-common" 'CL-076' 'yutong-common 模块存在'
Test-FileExists "$root\backend\yutong-infra" 'CL-077' 'yutong-infra 模块存在'
Test-FileExists "$root\backend\yutong-api" 'CL-078' 'yutong-api 模块存在'
Test-FileExists "$root\backend\yutong-auth-adapter" 'CL-079' 'yutong-auth-adapter 模块存在'
Test-FileExists "$root\backend\yutong-system-service" 'CL-080' 'yutong-system-service 模块存在'
Test-FileExists "$root\backend\yutong-sample-service" 'CL-081' 'yutong-sample-service 模块存在'
Test-FileExists "$root\backend\yutong-lowcode-service" 'CL-082' 'yutong-lowcode-service 模块存在'
Test-FileExists "$root\backend\yutong-ai-service" 'CL-083' 'yutong-ai-service 模块存在'
Test-FileExists "$root\backend\yutong-workflow-service" 'CL-084' 'yutong-workflow-service 模块存在'
Test-FileExists "$root\backend\yutong-boot" 'CL-085' 'yutong-boot 模块存在'
Test-FileExists "$root\database\migrations" 'CL-086' 'database/migrations 目录存在'
Test-ContentContains "$root\backend\yutong-common\src\main\java\com\yutong\common\id\IdGenerator.java" 'ULID' 'CL-087' 'yutong-common IdGenerator 包含 ULID'
Test-ContentContains "$root\backend\yutong-common\src\main\java\com\yutong\common\response\Result.java" 'traceId' 'CL-088' 'yutong-common Result 包含 traceId'
Test-FileExists "$root\backend\yutong-boot\src\main\resources\application.yml" 'CL-089' 'yutong-boot application.yml 存在'
Test-FileExists "$root\YuTong-Java-Docs\31-产品化与商业版本设计" 'CL-090' '31-产品化与商业版本设计 上游文档存在'

# ============================================================
# 阶段 6: 23 追踪记录与证据文件 + 运行时验证 + 已知偏差 + 交付物
# ============================================================

Write-Host '=== 阶段 6: 23 追踪记录与证据文件 + 运行时验证 + 已知偏差 + 交付物 ===' -ForegroundColor Cyan

$tracker = "$root\YuTong-Java-Docs\23-设计到落地追踪记录\23-设计到落地追踪记录.md"
Test-FileExists $tracker 'CL-091' '23 追踪记录文件存在'

# 23 追踪记录 L170 条目由主代理统一回写，未回写时记为 WARN（不阻塞）
if (Test-Path $tracker) {
    $trackerContent = Get-Content $tracker -Raw -Encoding UTF8
    if ($trackerContent -match 'L170' -or $trackerContent -match '商业授权与版本能力裁剪') {
        Add-Result 'CL-092' '23 追踪记录包含 L170 条目' 'PASS' '23 追踪记录已登记 L170'
    } else {
        Add-Result 'CL-092' '23 追踪记录包含 L170 条目' 'WARN' '23 追踪记录 L170 条目由主代理统一回写'
    }
} else {
    Add-Result 'CL-092' '23 追踪记录包含 L170 条目' 'WARN' '23 追踪记录文件不存在，由主代理统一回写'
}

# 运行时验证
$containers = docker ps --format '{{.Names}}' 2>$null
if ($containers -match 'yutong-backend-run') {
    Add-Result 'CL-093' 'yutong-backend-run 容器运行' 'PASS' 'yutong-backend-run 运行中'
} else {
    Add-Result 'CL-093' 'yutong-backend-run 容器运行' 'WARN' 'yutong-backend-run 未运行'
}

if ($containers -match 'yutong-redis') {
    Add-Result 'CL-094' 'yutong-redis 容器运行' 'PASS' 'yutong-redis 运行中'
} else {
    Add-Result 'CL-094' 'yutong-redis 容器运行' 'WARN' 'yutong-redis 未运行'
}

if ($containers -match 'yutong-postgres') {
    Add-Result 'CL-095' 'yutong-postgres 容器运行' 'PASS' 'yutong-postgres 运行中'
} else {
    Add-Result 'CL-095' 'yutong-postgres 容器运行' 'WARN' 'yutong-postgres 未运行'
}

if ($containers -match 'yutong-minio') {
    Add-Result 'CL-096' 'yutong-minio 容器运行' 'PASS' 'yutong-minio 运行中'
} else {
    Add-Result 'CL-096' 'yutong-minio 容器运行' 'WARN' 'yutong-minio 未运行'
}

# 已知偏差验证
Test-ContentContains $yaml 'DEV-L170-001' 'CL-097' 'YAML 包含 DEV-L170-001 偏差（License Server 第一版不实现）'
Test-ContentContains $yaml 'DEV-L170-004' 'CL-098' 'YAML 包含 DEV-L170-004 偏差（yutong.modules 配置未落地）'
Test-ContentContains $yaml 'closeTime' 'CL-099' 'YAML 偏差含 closeTime 关闭路径'

# 交付物验证
Test-ContentContains $yaml 'deliverables:' 'CL-100' 'YAML 包含 deliverables 交付物章节'

# ============================================================
# 汇总与输出
# ============================================================

Write-Host ''
Write-Host '================================================================' -ForegroundColor Yellow
Write-Host '  GA2-L170 商业授权与版本能力裁剪详设 检查结果汇总' -ForegroundColor Yellow
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
$csvPath = "$reportDir\ga2-l170-check-results.csv"
$script:results | Export-Csv -Path $csvPath -NoTypeInformation -Encoding UTF8
Write-Host "CSV 报告: $csvPath" -ForegroundColor Cyan

# JSON 摘要
$jsonPath = "$reportDir\ga2-l170-check-summary.json"
$summary = @{
    taskId = 'GA2-L170'
    taskName = '商业授权与版本能力裁剪详设'
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
