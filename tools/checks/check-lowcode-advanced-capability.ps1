<#
.SYNOPSIS
  GA2-L168 低代码高级能力设计 检查脚本
.DESCRIPTION
  Design source: 36-低代码高级能力设计.md (DOC-LC-002)
  YAML skeleton: YuTong-Java-Docs/contracts/governance/lowcode-advanced-capability.yaml
  Code alignment: backend/yutong-lowcode-service + backend/yutong-common + database/migrations + deploy/docker-compose.run.yml
  yutong prefix rule: yutong-* docker images + yutong_default network + 10 yutong-* maven modules + yutong: redis prefix + yutong- minio prefix
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
# 阶段 1: 36 号文档存在性与关键内容
# ============================================================

Write-Host '=== 阶段 1: 36 号文档存在性与关键内容 ===' -ForegroundColor Cyan

$doc36 = "$root\YuTong-Java-Docs\36-低代码高级能力设计\36-低代码高级能力设计.md"
$yaml = "$root\YuTong-Java-Docs\contracts\governance\lowcode-advanced-capability.yaml"

Test-FileExists $doc36 'LC-001' '36 号文档存在'
Test-ContentContains $doc36 '文档目标' 'LC-002' '36 号文档包含文档目标'
Test-ContentContains $doc36 '能力定位' 'LC-003' '36 号文档包含能力定位'
Test-ContentContains $doc36 '高级能力地图' 'LC-004' '36 号文档包含高级能力地图'
Test-ContentContains $doc36 '规则与表达式' 'LC-005' '36 号文档包含规则与表达式'
Test-ContentContains $doc36 '组件协议' 'LC-006' '36 号文档包含组件协议'
Test-ContentContains $doc36 '插件机制' 'LC-007' '36 号文档包含插件机制'
Test-ContentContains $doc36 '模板版本兼容' 'LC-008' '36 号文档包含模板版本兼容'
Test-ContentContains $doc36 '复杂表单能力' 'LC-009' '36 号文档包含复杂表单能力'
Test-ContentContains $doc36 '轻量流程能力' 'LC-010' '36 号文档包含轻量流程能力'
Test-ContentContains $doc36 '报表配置能力' 'LC-011' '36 号文档包含报表配置能力'
Test-ContentContains $doc36 '运行时性能治理' 'LC-012' '36 号文档包含运行时性能治理'
Test-ContentContains $doc36 '生成冲突处理' 'LC-013' '36 号文档包含生成冲突处理'
Test-ContentContains $doc36 '与 AI 协同' 'LC-014' '36 号文档包含与 AI 协同'
Test-ContentContains $doc36 '元模型与组件协议兼容细则' 'LC-015' '36 号文档包含元模型与组件协议兼容细则'
Test-ContentContains $doc36 '运行时性能治理指标' 'LC-016' '36 号文档包含运行时性能治理指标'
Test-ContentContains $doc36 '验收标准' 'LC-017' '36 号文档包含验收标准'
Test-ContentContains $doc36 'AiToolPlugin' 'LC-018' '36 号文档包含 AiToolPlugin'
Test-ContentContains $doc36 'config_hash' 'LC-019' '36 号文档包含 config_hash'
Test-ContentContains $doc36 'diff_json' 'LC-020' '36 号文档包含 diff_json'

# ============================================================
# 阶段 2: YAML 骨架章节存在性
# ============================================================

Write-Host '=== 阶段 2: YAML 骨架章节存在性 ===' -ForegroundColor Cyan

Test-FileExists $yaml 'LC-021' 'YAML 骨架文件存在'
Test-ContentContains $yaml 'schemaVersion:' 'LC-022' 'YAML 包含 schemaVersion'
Test-ContentContains $yaml 'taskId: GA2-L168' 'LC-023' 'YAML 包含 taskId GA2-L168'
Test-ContentContains $yaml 'designDocId: DOC-LC-002' 'LC-024' 'YAML 包含 designDocId DOC-LC-002'
Test-ContentContains $yaml 'documentPurpose:' 'LC-025' 'YAML 包含 documentPurpose 文档目标'
Test-ContentContains $yaml 'capabilityPositioning:' 'LC-026' 'YAML 包含 capabilityPositioning 能力定位'
Test-ContentContains $yaml 'advancedCapabilityMap:' 'LC-027' 'YAML 包含 advancedCapabilityMap 高级能力地图'
Test-ContentContains $yaml 'ruleExpression:' 'LC-028' 'YAML 包含 ruleExpression 规则与表达式'
Test-ContentContains $yaml 'componentProtocol:' 'LC-029' 'YAML 包含 componentProtocol 组件协议'
Test-ContentContains $yaml 'pluginMechanism:' 'LC-030' 'YAML 包含 pluginMechanism 插件机制'
Test-ContentContains $yaml 'templateVersionCompat:' 'LC-031' 'YAML 包含 templateVersionCompat 模板版本兼容'
Test-ContentContains $yaml 'complexFormCapability:' 'LC-032' 'YAML 包含 complexFormCapability 复杂表单能力'
Test-ContentContains $yaml 'lightweightWorkflow:' 'LC-033' 'YAML 包含 lightweightWorkflow 轻量流程能力'
Test-ContentContains $yaml 'reportConfig:' 'LC-034' 'YAML 包含 reportConfig 报表配置能力'
Test-ContentContains $yaml 'runtimePerformanceGov:' 'LC-035' 'YAML 包含 runtimePerformanceGov 运行时性能治理'
Test-ContentContains $yaml 'generationConflictHandling:' 'LC-036' 'YAML 包含 generationConflictHandling 生成冲突处理'
Test-ContentContains $yaml 'aiCollaboration:' 'LC-037' 'YAML 包含 aiCollaboration 与 AI 协同'
Test-ContentContains $yaml 'componentCompatDetail:' 'LC-038' 'YAML 包含 componentCompatDetail 兼容细则'
Test-ContentContains $yaml 'runtimePerfMetrics:' 'LC-039' 'YAML 包含 runtimePerfMetrics 性能治理指标'
Test-ContentContains $yaml 'acceptanceCriteria:' 'LC-040' 'YAML 包含 acceptanceCriteria 验收标准'

# ============================================================
# 阶段 3: YAML 章节精确计数
# ============================================================

Write-Host '=== 阶段 3: YAML 章节精确计数 ===' -ForegroundColor Cyan

Test-YamlSectionCount $yaml 'capabilityPositioning' '^\s+- ' 8 'LC-041' 'YAML capabilityPositioning coverage 8 项'
Test-YamlSectionCount $yaml 'advancedCapabilityMap' '^\s+- capability: ' 8 'LC-042' 'YAML advancedCapabilityMap 8 大高级能力'
Test-YamlSectionCount $yaml 'ruleExpression' '^\s+- type: ' 6 'LC-043' 'YAML ruleExpression ruleTypes 6 类规则'
Test-YamlSectionCount $yaml 'ruleExpression' '^\s+- principle: ' 5 'LC-044' 'YAML ruleExpression designPrinciples 5 项设计原则'
Test-YamlSectionCount $yaml 'ruleExpression' '^\s+- object: ' 6 'LC-045' 'YAML ruleExpression contextObjects 6 个上下文对象'
Test-YamlSectionCount $yaml 'ruleExpression' '^\s+- constraint: ' 7 'LC-046' 'YAML ruleExpression dslSandboxConstraints 7 项 DSL 沙箱约束'
Test-YamlSectionCount $yaml 'componentProtocol' '^\s+- field: ' 9 'LC-047' 'YAML componentProtocol metadata 9 项元数据'
Test-YamlSectionCount $yaml 'componentProtocol' '^\s+- category: ' 7 'LC-048' 'YAML componentProtocol categories 7 类组件分类'
Test-YamlSectionCount $yaml 'componentProtocol' '^\s+- ' 19 'LC-049' 'YAML componentProtocol 元数据+分类+服务三方 19 项'
Test-YamlSectionCount $yaml 'pluginMechanism' '^\s+- plugin: ' 6 'LC-050' 'YAML pluginMechanism pluginTypes 6 类插件'
Test-YamlSectionCount $yaml 'pluginMechanism' '^\s+- constraint: ' 7 'LC-051' 'YAML pluginMechanism constraints 7 项约束'
Test-YamlSectionCount $yaml 'pluginMechanism' '^\s+- point: ' 6 'LC-052' 'YAML pluginMechanism permissionEnforcePoints 6 项权限强制点'
Test-YamlSectionCount $yaml 'templateVersionCompat' '^\s+- object: ' 5 'LC-053' 'YAML templateVersionCompat requirements 5 项要求'
Test-YamlSectionCount $yaml 'complexFormCapability' '^\s+- capability: ' 10 'LC-054' 'YAML complexFormCapability capabilities 10 项能力'
Test-YamlSectionCount $yaml 'complexFormCapability' '^\s+- limitation: ' 3 'LC-055' 'YAML complexFormCapability limitations 3 项限制'
Test-YamlSectionCount $yaml 'lightweightWorkflow' '^\s+- capability: ' 7 'LC-056' 'YAML lightweightWorkflow capabilities 7 项'
Test-YamlSectionCount $yaml 'reportConfig' '^\s+- capability: ' 7 'LC-057' 'YAML reportConfig capabilities 7 项'
Test-YamlSectionCount $yaml 'runtimePerformanceGov' '^\s+- capability: ' 7 'LC-058' 'YAML runtimePerformanceGov capabilities 7 项'
Test-YamlSectionCount $yaml 'generationConflictHandling' '^\s+- type: ' 6 'LC-059' 'YAML generationConflictHandling identification 6 类识别'
Test-YamlSectionCount $yaml 'generationConflictHandling' '^\s+- strategy: ' 5 'LC-060' 'YAML generationConflictHandling strategies 5 项策略'

# ============================================================
# 阶段 4: YAML 章节精确计数（续）
# ============================================================

Write-Host '=== 阶段 4: YAML 章节精确计数（续） ===' -ForegroundColor Cyan

Test-YamlSectionCount $yaml 'aiCollaboration' '^\s+- action: ' 9 'LC-061' 'YAML aiCollaboration allowed+notAllowed 9 项'
Test-YamlSectionCount $yaml 'componentCompatDetail' '^\s+- tier: ' 3 'LC-062' 'YAML componentCompatDetail tiers 3 档管理'
Test-YamlSectionCount $yaml 'componentCompatDetail' '^\s+- change: ' 6 'LC-063' 'YAML componentCompatDetail changeStrategies 6 项变更策略'
Test-YamlSectionCount $yaml 'runtimePerfMetrics' '^\s+- metric: ' 6 'LC-064' 'YAML runtimePerfMetrics metrics 6 项指标'
Test-YamlSectionCount $yaml 'runtimePerfMetrics' '^\s+- item: ' 5 'LC-065' 'YAML runtimePerfMetrics complexityThresholds 5 项复杂度阈值'
Test-YamlSectionCount $yaml 'acceptanceCriteria' '^\s+- criterion: ' 5 'LC-066' 'YAML acceptanceCriteria 5 项验收标准'
Test-YamlSectionCount $yaml 'existingEvidence' '^\s+- evidence: ' 12 'LC-067' 'YAML existingEvidence 12 项已有证据'
Test-YamlSectionCount $yaml 'knownDeviations' '^\s+- id: ' 4 'LC-068' 'YAML knownDeviations 4 项偏差'
Test-YamlSectionCount $yaml 'codeAlignment' '^\s+- item: ' 12 'LC-069' 'YAML codeAlignment 12 项 RT-001~RT-012'
Test-YamlSectionCount $yaml 'deliverables' '^\s+- item: ' 4 'LC-070' 'YAML deliverables 4 项交付物'
Test-YamlSectionCount $yaml 'acceptance' '^\s+- criterion: ' 4 'LC-071' 'YAML acceptance 4 项验收'
Test-YamlSectionCount $yaml 'yutongPrefixRule' '^\s+- yutong-' 14 'LC-072' 'YAML yutongPrefixRule yutong- 前缀条目 14 项（4 镜像 + 10 模块）'
Test-RegexExact $yaml '^\s+- YUTONG_' 2 'LC-073' 'YAML yutongPrefixRule envVars 2 环境变量'
Test-RegexExact $yaml '^\s+- tier: (Stable|Experimental|Internal)$' 3 'LC-074' 'YAML componentCompatDetail 3 档 Stable/Experimental/Internal'
Test-RegexExact $yaml '^\s+risk: P[12]$' 5 'LC-075' 'YAML runtimePerfMetrics complexityThresholds 5 项 risk P1/P2'

# ============================================================
# 阶段 5: yutong 前缀规则验证
# ============================================================

Write-Host '=== 阶段 5: yutong 前缀规则验证 ===' -ForegroundColor Cyan

Test-ContentContains $yaml 'yutong-backend-run' 'LC-076' 'YAML 包含 yutong-backend-run 镜像'
Test-ContentContains $yaml 'yutong-redis' 'LC-077' 'YAML 包含 yutong-redis 镜像'
Test-ContentContains $yaml 'yutong-postgres' 'LC-078' 'YAML 包含 yutong-postgres 镜像'
Test-ContentContains $yaml 'yutong-minio' 'LC-079' 'YAML 包含 yutong-minio 镜像'
Test-ContentContains $yaml 'yutong_default' 'LC-080' 'YAML 包含 yutong_default 网络'
Test-ContentContains $yaml "yutong:" 'LC-081' 'YAML 包含 yutong: Redis Key 前缀'
Test-ContentContains $yaml 'yutong-lowcode-service' 'LC-082' 'YAML 包含 yutong-lowcode-service 模块'
Test-ContentContains $yaml 'yutong-common' 'LC-083' 'YAML 包含 yutong-common 模块'
Test-ContentContains $yaml 'yutong-boot' 'LC-084' 'YAML 包含 yutong-boot 模块'
$composeRun = "$root\deploy\docker-compose.run.yml"
Test-FileExists $composeRun 'LC-085' 'docker-compose.run.yml 存在（deploy/）'
Test-ContentContains $composeRun 'yutong-backend-run' 'LC-086' 'docker-compose.run.yml 包含 yutong-backend-run'
Test-ContentContains $composeRun 'yutong_default' 'LC-087' 'docker-compose.run.yml 包含 yutong_default 网络'

# ============================================================
# 阶段 6: 代码对齐验证
# ============================================================

Write-Host '=== 阶段 6: 代码对齐验证 ===' -ForegroundColor Cyan

Test-FileExists "$root\backend\yutong-lowcode-service" 'LC-088' 'yutong-lowcode-service 模块存在'
Test-FileExists "$root\backend\yutong-lowcode-service\src\main\java\com\yutong\lowcode\meta\domain\LcComponent.java" 'LC-089' 'LcComponent 组件元模型存在'
Test-FileExists "$root\backend\yutong-lowcode-service\src\main\java\com\yutong\lowcode\meta\domain\LcAction.java" 'LC-090' 'LcAction 动作元模型存在'
Test-FileExists "$root\backend\yutong-lowcode-service\src\main\java\com\yutong\lowcode\meta\domain\LcPage.java" 'LC-091' 'LcPage 页面元模型存在'
Test-FileExists "$root\backend\yutong-lowcode-service\src\main\java\com\yutong\lowcode\meta\domain\LcGeneratorTask.java" 'LC-092' 'LcGeneratorTask 生成任务元模型存在'
Test-FileExists "$root\backend\yutong-lowcode-service\src\main\java\com\yutong\lowcode\generator\service\CodeTemplateService.java" 'LC-093' 'CodeTemplateService 代码模板服务存在'
Test-FileExists "$root\backend\yutong-lowcode-service\src\main\java\com\yutong\lowcode\generator\service\GeneratorDiffService.java" 'LC-094' 'GeneratorDiffService 生成差异服务存在'
Test-FileExists "$root\backend\yutong-lowcode-service\src\main\java\com\yutong\lowcode\meta\service\ConfigHashService.java" 'LC-095' 'ConfigHashService 配置哈希服务存在'
Test-FileExists "$root\backend\yutong-common\src\main\java\com\yutong\common\id\IdGenerator.java" 'LC-096' 'IdGenerator 主键生成器存在'
Test-ContentContains "$root\backend\yutong-common\src\main\java\com\yutong\common\id\IdGenerator.java" 'ULID' 'LC-097' 'yutong-common IdGenerator 包含 ULID'
Test-FileExists "$root\backend\yutong-common\src\main\java\com\yutong\common\response\Result.java" 'LC-098' 'Result 统一响应存在'
Test-ContentContains "$root\backend\yutong-common\src\main\java\com\yutong\common\response\Result.java" 'traceId' 'LC-099' 'yutong-common Result 包含 traceId'
Test-FileExists "$root\database\migrations" 'LC-100' 'database/migrations 目录存在'

# ============================================================
# 阶段 7: yutong 10 模块与契约验证
# ============================================================

Write-Host '=== 阶段 7: yutong 10 模块与契约验证 ===' -ForegroundColor Cyan

Test-FileExists "$root\backend\yutong-common" 'LC-101' 'yutong-common 模块存在'
Test-FileExists "$root\backend\yutong-infra" 'LC-102' 'yutong-infra 模块存在'
Test-FileExists "$root\backend\yutong-api" 'LC-103' 'yutong-api 模块存在'
Test-FileExists "$root\backend\yutong-auth-adapter" 'LC-104' 'yutong-auth-adapter 模块存在'
Test-FileExists "$root\backend\yutong-system-service" 'LC-105' 'yutong-system-service 模块存在'
Test-FileExists "$root\backend\yutong-sample-service" 'LC-106' 'yutong-sample-service 模块存在'
Test-FileExists "$root\backend\yutong-ai-service" 'LC-107' 'yutong-ai-service 模块存在'
Test-FileExists "$root\backend\yutong-workflow-service" 'LC-108' 'yutong-workflow-service 模块存在'
Test-FileExists "$root\backend\yutong-boot" 'LC-109' 'yutong-boot 模块存在'
Test-FileExists "$root\YuTong-Java-Docs\contracts\registries\errors.yaml" 'LC-110' 'errors.yaml 错误码注册表存在'
Test-ContentContains "$root\YuTong-Java-Docs\contracts\registries\errors.yaml" 'LC-' 'LC-111' 'errors.yaml 包含 LC- 错误码'
Test-FileExists "$root\YuTong-Java-Docs\14-低代码平台设计" 'LC-112' '14-低代码平台设计 基础文档存在'

# ============================================================
# 阶段 8: 23 追踪记录与证据文件
# ============================================================

Write-Host '=== 阶段 8: 23 追踪记录与证据文件 ===' -ForegroundColor Cyan

$tracker = "$root\YuTong-Java-Docs\23-设计到落地追踪记录\23-设计到落地追踪记录.md"
Test-FileExists $tracker 'LC-113' '23 追踪记录存在'
# LC-114~LC-116: 23 追踪记录内容由主代理统一回写，未命中时记 WARN（非 FAIL）
$trackerContent = Get-Content $tracker -Raw -Encoding UTF8
if ($trackerContent -match '低代码高级能力') {
    Add-Result 'LC-114' '23 追踪记录包含低代码高级能力任务名' 'PASS' '命中: 低代码高级能力'
} else {
    Add-Result 'LC-114' '23 追踪记录包含低代码高级能力任务名' 'WARN' '待主代理回写: 低代码高级能力'
}
if ($trackerContent -match 'L168') {
    Add-Result 'LC-115' '23 追踪记录包含 L168' 'PASS' '命中: L168'
} else {
    Add-Result 'LC-115' '23 追踪记录包含 L168' 'WARN' '待主代理回写: L168'
}
if ($trackerContent -match '36-低代码高级能力设计') {
    Add-Result 'LC-116' '23 追踪记录引用 36 号文档' 'PASS' '命中: 36-低代码高级能力设计'
} else {
    Add-Result 'LC-116' '23 追踪记录引用 36 号文档' 'WARN' '待主代理回写: 36-低代码高级能力设计'
}
Test-FileExists "$root\release-evidence\v1.0.0\ga2-l168-evidence.md" 'LC-117' 'ga2-l168-evidence.md 证据文件存在'

# ============================================================
# 阶段 9: 运行时验证
# ============================================================

Write-Host '=== 阶段 9: 运行时验证 ===' -ForegroundColor Cyan

$containers = docker ps --format '{{.Names}}' 2>$null
if ($containers -match 'yutong-backend-run') {
    Add-Result 'LC-118' 'yutong-backend-run 容器运行' 'PASS' 'yutong-backend-run 运行中'
} else {
    Add-Result 'LC-118' 'yutong-backend-run 容器运行' 'WARN' 'yutong-backend-run 未运行'
}

if ($containers -match 'yutong-redis') {
    Add-Result 'LC-119' 'yutong-redis 容器运行' 'PASS' 'yutong-redis 运行中'
} else {
    Add-Result 'LC-119' 'yutong-redis 容器运行' 'WARN' 'yutong-redis 未运行'
}

if ($containers -match 'yutong-postgres') {
    Add-Result 'LC-120' 'yutong-postgres 容器运行' 'PASS' 'yutong-postgres 运行中'
} else {
    Add-Result 'LC-120' 'yutong-postgres 容器运行' 'WARN' 'yutong-postgres 未运行'
}

if ($containers -match 'yutong-minio') {
    Add-Result 'LC-121' 'yutong-minio 容器运行' 'PASS' 'yutong-minio 运行中'
} else {
    Add-Result 'LC-121' 'yutong-minio 容器运行' 'WARN' 'yutong-minio 未运行'
}

$backendPort = if ($env:BACKEND_PORT) { $env:BACKEND_PORT } else { '8092' }
$healthUrl = "http://localhost:${backendPort}/actuator/health"
try {
    $resp = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 5
    if ($resp.StatusCode -eq 200) {
        Add-Result 'LC-122' "后端健康检查 $healthUrl" 'PASS' "HTTP $($resp.StatusCode)"
    } else {
        Add-Result 'LC-122' "后端健康检查 $healthUrl" 'WARN' "HTTP $($resp.StatusCode)"
    }
} catch {
    Add-Result 'LC-122' "后端健康检查 $healthUrl" 'WARN' '健康端点不可达'
}

# ============================================================
# 阶段 10: 已知偏差与交付物验证
# ============================================================

Write-Host '=== 阶段 10: 已知偏差与交付物验证 ===' -ForegroundColor Cyan

Test-ContentContains $yaml 'DEV-L168-001' 'LC-123' 'YAML 包含 DEV-L168-001 偏差'
Test-ContentContains $yaml 'DEV-L168-002' 'LC-124' 'YAML 包含 DEV-L168-002 偏差'
Test-ContentContains $yaml 'DEV-L168-003' 'LC-125' 'YAML 包含 DEV-L168-003 偏差'
Test-ContentContains $yaml 'DEV-L168-004' 'LC-126' 'YAML 包含 DEV-L168-004 偏差'
Test-ContentContains $yaml 'closeTime' 'LC-127' 'YAML 偏差含 closeTime 关闭路径'
Test-ContentContains $yaml 'deliverables:' 'LC-128' 'YAML 包含 deliverables 交付物'
Test-ContentContains $yaml 'acceptance:' 'LC-129' 'YAML 包含 acceptance 验收'
Test-FileExists "$root\tools\checks\check-lowcode-advanced-capability.ps1" 'LC-130' '检查脚本存在'
Test-ContentContains $yaml 'yutongPrefixRule:' 'LC-131' 'YAML 包含 yutongPrefixRule 前缀规则'
Test-ContentContains $yaml 'aiToolPluginRestriction' 'LC-132' 'YAML 包含 AiToolPlugin 限制'
Test-ContentContains $yaml 'pageConfigMigration' 'LC-133' 'YAML 包含页面配置迁移'
Test-ContentContains $yaml 'bpmnOptional' 'LC-134' 'YAML 包含 BPMN 可选'
Test-ContentContains $yaml 'bigScreenDeferred' 'LC-135' 'YAML 包含大屏后续'

# ============================================================
# 汇总与输出
# ============================================================

Write-Host ''
Write-Host '================================================================' -ForegroundColor Yellow
Write-Host '  GA2-L168 低代码高级能力设计 检查结果汇总' -ForegroundColor Yellow
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
$csvPath = "$reportDir\ga2-l168-check-results.csv"
$script:results | Export-Csv -Path $csvPath -NoTypeInformation -Encoding UTF8
Write-Host "CSV 报告: $csvPath" -ForegroundColor Cyan

# JSON 摘要
$jsonPath = "$reportDir\ga2-l168-check-summary.json"
$summary = @{
    taskId = 'GA2-L168'
    taskName = '低代码高级能力设计'
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
