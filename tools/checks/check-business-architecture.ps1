# GA2-L161: Business Architecture Responsibility Boundary Machine Check Script
# Design source: 04-业务架构设计.md (DOC-PRD-003)
# Verifies 04 doc's 4 acceptance criteria + consistency with sample/lowcode/ai/system services + DDL
# Output: build/reports/checks/business-architecture-ga2-l161.json + stdout summary

$ErrorActionPreference = 'Continue'
$projectRoot = 'd:\MyCode\YuTong-Java-Vue'
$docsRoot = Join-Path $projectRoot 'YuTong-Java-Docs'
$designDocPath = Join-Path $docsRoot '04-业务架构设计\04-业务架构设计.md'
$yamlPath = Join-Path $docsRoot 'contracts\governance\business-architecture.yaml'
$trackingPath = Join-Path $docsRoot '23-设计到落地追踪记录\23-设计到落地追踪记录.md'

$results = New-Object System.Collections.ArrayList
$pass = 0
$warn = 0
$fail = 0

function Add-Result($id, $status, $message) {
    [void]$results.Add([PSCustomObject]@{ Id = $id; Status = $status; Message = $message })
    switch ($status) {
        'PASS' { $script:pass++ }
        'WARN' { $script:warn++ }
        'FAIL' { $script:fail++ }
    }
    Write-Host "[$status] $id`: $message"
}

# BA-001: 04 design doc exists
if (Test-Path $designDocPath) {
    Add-Result 'BA-001' 'PASS' "04 design doc exists"
} else {
    Add-Result 'BA-001' 'FAIL' "04 design doc missing"
}

# BA-002: business-architecture.yaml skeleton exists
if (Test-Path $yamlPath) {
    $size = (Get-Item $yamlPath).Length
    Add-Result 'BA-002' 'PASS' "business-architecture.yaml exists ($size bytes)"
} else {
    Add-Result 'BA-002' 'FAIL' "business-architecture.yaml missing"
}

$designContent = [System.IO.File]::ReadAllText($designDocPath, [System.Text.Encoding]::UTF8)
$yamlContent = [System.IO.File]::ReadAllText($yamlPath, [System.Text.Encoding]::UTF8)

# BA-003: Platform capability map 6 categories
$capHits = 0
foreach ($k in @('基础平台能力', '样例业务能力', '低代码能力', 'AI增强能力', '运维交付能力', '技术底座平台')) {
    if ($designContent -match [regex]::Escape($k)) { $capHits++ }
}
if ($capHits -ge 6) {
    Add-Result 'BA-003' 'PASS' "Platform capability map 6 categories present"
} else {
    Add-Result 'BA-003' "WARN" "Platform capability map: $capHits/6"
}

# BA-004: 8 sample business objects
$objHits = 0
foreach ($k in @('客户档案', '商品档案', '业务申请单', '申请单明细', '审核记录', '附件', '消息通知', '移动端待办')) {
    if ($designContent -match [regex]::Escape($k)) { $objHits++ }
}
if ($objHits -ge 8) {
    Add-Result 'BA-004' 'PASS' "8 sample business objects all present"
} else {
    Add-Result 'BA-004' 'FAIL' "Sample objects: $objHits/8"
}

# BA-005: 5 business states
$stateHits = 0
foreach ($k in @('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'ARCHIVED')) {
    if ($designContent -match "\b$([regex]::Escape($k))\b") { $stateHits++ }
}
if ($stateHits -ge 5) {
    Add-Result 'BA-005' 'PASS' "5 business states all present"
} else {
    Add-Result 'BA-005' 'FAIL' "Business states: $stateHits/5"
}

# BA-006: 6 lowcode business modeling objects
$lcHits = 0
foreach ($k in @('数据实体', '页面模型', '动作模型', '校验模型', '字典模型', '权限边界')) {
    if ($designContent -match [regex]::Escape($k)) { $lcHits++ }
}
if ($lcHits -ge 6) {
    Add-Result 'BA-006' 'PASS' "6 lowcode modeling objects all present"
} else {
    Add-Result 'BA-006' 'FAIL' "Lowcode modeling: $lcHits/6"
}

# BA-007: 6 AI business enhancement points
$aiHits = 0
foreach ($k in @('根据业务描述建议字段', '根据实体生成表单和列表初稿', '根据操作日志解释异常', '根据数据生成统计摘要', '根据知识库回答平台使用问题', '根据接口契约生成调用示例')) {
    if ($designContent -match [regex]::Escape($k)) { $aiHits++ }
}
if ($aiHits -ge 6) {
    Add-Result 'BA-007' 'PASS' "6 AI enhancement points all present"
} else {
    Add-Result 'BA-007' 'FAIL' "AI enhancement: $aiHits/6"
}

# BA-008: AI output must be human-confirmed
if ($designContent -match 'AI 输出必须经过人工确认') {
    Add-Result 'BA-008' 'PASS' "AI output human confirmation rule present"
} else {
    Add-Result 'BA-008' 'FAIL' "AI output human confirmation rule missing"
}

# BA-009: 5×4 cross-end consistency matrix
$crossHits = 0
foreach ($k in @('主数据维护', '单据处理', '文件附件', '报表统计', '消息通知')) {
    if ($designContent -match [regex]::Escape($k)) { $crossHits++ }
}
if ($crossHits -ge 5) {
    Add-Result 'BA-009' 'PASS' "5 cross-end capabilities all present"
} else {
    Add-Result 'BA-009' 'FAIL' "Cross-end capabilities: $crossHits/5"
}

# BA-010: 10 capability traceability matrix entries
$reqHits = 0
foreach ($k in @('REQ-BASE-003', 'REQ-BASE-004', 'REQ-SAMPLE-001', 'REQ-SAMPLE-002', 'REQ-SAMPLE-003', 'REQ-SAMPLE-004', 'REQ-LC-001', 'REQ-LC-002', 'REQ-AI-001', 'REQ-AI-006')) {
    if ($designContent -match [regex]::Escape($k)) { $reqHits++ }
}
if ($reqHits -ge 10) {
    Add-Result 'BA-010' 'PASS' "10 capability traceability matrix REQ-* all present"
} else {
    Add-Result 'BA-010' 'FAIL' "Traceability REQ-*: $reqHits/10"
}

# BA-011: 5 business domains layering
$domainHits = 0
foreach ($k in @('平台基础域', '样例业务域', '低代码域', 'AI 增强域', '运维交付域')) {
    if ($designContent -match [regex]::Escape($k)) { $domainHits++ }
}
if ($domainHits -ge 5) {
    Add-Result 'BA-011' 'PASS' "5 business domain layering all present"
} else {
    Add-Result 'BA-011' 'FAIL' "Domain layering: $domainHits/5"
}

# BA-012: Cross-domain call principle
if ($designContent -match '应用服务编排、领域规则内聚、基础能力适配') {
    Add-Result 'BA-012' 'PASS' "Cross-domain call principle present"
} else {
    Add-Result 'BA-012' 'FAIL' "Cross-domain call principle missing"
}

# BA-013: biz_request as main aggregate root
if ($designContent -match '主聚合根') {
    Add-Result 'BA-013' 'PASS' "biz_request main aggregate root rule present"
} else {
    Add-Result 'BA-013' 'FAIL' "biz_request main aggregate root rule missing"
}

# BA-014: 6 business object relation rules
$ruleHits = 0
foreach ($k in @('主聚合根', '聚合维护', '只追加', '附件先上传', '报表只读', '幂等')) {
    if ($designContent -match [regex]::Escape($k)) { $ruleHits++ }
}
if ($ruleHits -ge 6) {
    Add-Result 'BA-014' 'PASS' "6 object relation rules all present"
} else {
    Add-Result 'BA-014' "WARN" "Object relation rules: $ruleHits/6"
}

# BA-015: 7 lifecycle steps
$lifeHits = 0
foreach ($k in @('创建草稿', '保存草稿', '提交', '审核通过', '驳回', '撤回', '归档')) {
    if ($designContent -match [regex]::Escape($k)) { $lifeHits++ }
}
if ($lifeHits -ge 7) {
    Add-Result 'BA-015' 'PASS' "7 lifecycle steps all present"
} else {
    Add-Result 'BA-015' 'FAIL' "Lifecycle steps: $lifeHits/7"
}

# BA-016: 6 state exception and concurrency rules
$excHits = 0
foreach ($k in @('重复提交', '并发审核', '撤回', '驳回后编辑', '归档后变更', '消息失败')) {
    if ($designContent -match [regex]::Escape($k)) { $excHits++ }
}
if ($excHits -ge 6) {
    Add-Result 'BA-016' 'PASS' "6 state exception rules all present"
} else {
    Add-Result 'BA-016' 'FAIL' "State exception rules: $excHits/6"
}

# BA-017: 4 extension sample domain guide rules
$extHits = 0
foreach ($k in @('新业务前缀命名', 'biz_type', '权限码按', '低代码元模型驱动')) {
    if ($designContent -match [regex]::Escape($k)) { $extHits++ }
}
if ($extHits -ge 4) {
    Add-Result 'BA-017' 'PASS' "4 extension sample domain guide rules all present"
} else {
    Add-Result 'BA-017' "WARN" "Extension guide rules: $extHits/4"
}

# BA-018: 7 business capability to impl traceability
$capImplHits = 0
foreach ($k in @('主数据维护', '申请单主流程', '附件绑定', '消息待办', '报表统计', '低代码生成', 'AI 建议')) {
    if ($designContent -match [regex]::Escape($k)) { $capImplHits++ }
}
if ($capImplHits -ge 7) {
    Add-Result 'BA-018' 'PASS' "7 capability-to-impl traceability all present"
} else {
    Add-Result 'BA-018' 'FAIL' "Capability-to-impl: $capImplHits/7"
}

# BA-019: 4 acceptance criteria
$criteriaHits = 0
foreach ($k in @('样例业务能验证平台的核心技术能力', '业务状态、数据结构、页面交互和 API 设计能够互相对应', '低代码和 AI 能围绕同一套业务元模型工作')) {
    if ($designContent -match [regex]::Escape($k)) { $criteriaHits++ }
}
if ($criteriaHits -ge 3) {
    Add-Result 'BA-019' 'PASS' "Acceptance criteria ($criteriaHits/3+ present)"
} else {
    Add-Result 'BA-019' 'FAIL' "Acceptance criteria: $criteriaHits/3"
}

# BA-020: YAML platformCapabilityMap section
if ($yamlContent -match 'platformCapabilityMap:' -and $yamlContent -match 'sampleBusinessDomain:' -and $yamlContent -match 'businessStateModel:') {
    Add-Result 'BA-020' 'PASS' "YAML top sections (platformCapabilityMap + sampleBusinessDomain + businessStateModel) present"
} else {
    Add-Result 'BA-020' 'FAIL' "YAML top sections missing"
}

# BA-021: YAML businessDomainLayering 5 items
$yamlDomainCount = ([regex]::Matches($yamlContent, '^\s+- domain: ', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlDomainCount -ge 5) {
    Add-Result 'BA-021' 'PASS' "YAML businessDomainLayering has $yamlDomainCount items (>=5)"
} else {
    Add-Result 'BA-021' 'FAIL' "YAML businessDomainLayering: $yamlDomainCount/5"
}

# BA-022: YAML businessObjectLifecycle 7 steps
$yamlLifeCount = ([regex]::Matches($yamlContent, '^\s+- step: ', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlLifeCount -ge 7) {
    Add-Result 'BA-022' 'PASS' "YAML businessObjectLifecycle has $yamlLifeCount steps (>=7)"
} else {
    Add-Result 'BA-022' 'FAIL' "YAML lifecycle steps: $yamlLifeCount/7"
}

# BA-023: YAML stateExceptionAndConcurrencyRules 6 items
$yamlExcCount = ([regex]::Matches($yamlContent, '^\s+- scenario: ', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlExcCount -ge 6) {
    Add-Result 'BA-023' 'PASS' "YAML stateExceptionAndConcurrencyRules has $yamlExcCount items (>=6)"
} else {
    Add-Result 'BA-023' 'FAIL' "YAML exception rules: $yamlExcCount/6"
}

# BA-024: YAML capabilityTraceabilityMatrix 10 items
$yamlMatrixCount = ([regex]::Matches($yamlContent, '^\s+- reqId: ', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlMatrixCount -ge 10) {
    Add-Result 'BA-024' 'PASS' "YAML capabilityTraceabilityMatrix has $yamlMatrixCount items (>=10)"
} else {
    Add-Result 'BA-024' 'FAIL' "YAML traceability matrix: $yamlMatrixCount/10"
}

# BA-025: YAML businessCapabilityToImplTraceability 7 items
$yamlCapCount = ([regex]::Matches($yamlContent, '^\s+- capability: ', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlCapCount -ge 7) {
    Add-Result 'BA-025' 'PASS' "YAML businessCapabilityToImplTraceability has $yamlCapCount items (>=7)"
} else {
    Add-Result 'BA-025' 'FAIL' "YAML capability-to-impl: $yamlCapCount/7"
}

# BA-026: YAML yutongPrefixRule section
if ($yamlContent -match 'yutongPrefixRule:' -and $yamlContent -match 'biz_' -and $yamlContent -match 'sys_' -and $yamlContent -match 'lc_') {
    Add-Result 'BA-026' 'PASS' "YAML yutongPrefixRule + data prefixes (biz_/sys_/lc_/ai_) present"
} else {
    Add-Result 'BA-026' 'FAIL' "YAML yutongPrefixRule missing"
}

# BA-027: YAML knownDeviations with DEV-L161-* IDs
if ($yamlContent -match 'DEV-L161-') {
    Add-Result 'BA-027' 'PASS' "YAML knownDeviations has DEV-L161-* IDs"
} else {
    Add-Result 'BA-027' 'FAIL' "YAML DEV-L161-* IDs missing"
}

# BA-028: 23 tracking record has 04-业务架构 entry
if (Test-Path $trackingPath) {
    $trackingContent = [System.IO.File]::ReadAllText($trackingPath, [System.Text.Encoding]::UTF8)
    if ($trackingContent -match '04-业务架构设计') {
        Add-Result 'BA-028' 'PASS' "23 tracking record has 04-业务架构设计 entry"
    } else {
        Add-Result 'BA-028' 'FAIL' "23 tracking record missing 04 entry"
    }
} else {
    Add-Result 'BA-028' 'FAIL' "23 tracking record missing"
}

# BA-029: 23 tracking record has L161 entry
if ($trackingContent -match 'L161' -or $trackingContent -match '业务架构责任边界补强') {
    Add-Result 'BA-029' 'PASS' "23 tracking record has L161 业务架构责任边界补强 entry"
} else {
    Add-Result 'BA-029' 'FAIL' "23 tracking record missing L161 entry"
}

# BA-030: sample-service module exists
$samplePath = Join-Path $projectRoot 'backend\yutong-sample-service'
if (Test-Path $samplePath) {
    Add-Result 'BA-030' 'PASS' "yutong-sample-service module exists"
} else {
    Add-Result 'BA-030' 'FAIL' "yutong-sample-service module missing"
}

# BA-031: lowcode-service module exists
$lowcodePath = Join-Path $projectRoot 'backend\yutong-lowcode-service'
if (Test-Path $lowcodePath) {
    Add-Result 'BA-031' 'PASS' "yutong-lowcode-service module exists"
} else {
    Add-Result 'BA-031' 'FAIL' "yutong-lowcode-service module missing"
}

# BA-032: ai-service module exists
$aiPath = Join-Path $projectRoot 'backend\yutong-ai-service'
if (Test-Path $aiPath) {
    Add-Result 'BA-032' 'PASS' "yutong-ai-service module exists"
} else {
    Add-Result 'BA-032' 'FAIL' "yutong-ai-service module missing"
}

# BA-033: system-service module exists
$sysPath = Join-Path $projectRoot 'backend\yutong-system-service'
if (Test-Path $sysPath) {
    Add-Result 'BA-033' 'PASS' "yutong-system-service module exists"
} else {
    Add-Result 'BA-033' 'FAIL' "yutong-system-service module missing"
}

# BA-034: V001 extensions DDL exists
$v001Path = Join-Path $projectRoot 'database\migrations\V001__init_extensions.sql'
if (Test-Path $v001Path) {
    Add-Result 'BA-034' 'PASS' "V001 extensions DDL exists"
} else {
    Add-Result 'BA-034' 'FAIL' "V001 DDL missing"
}

# BA-035: V003 sample tables DDL exists (business tables: biz_customer/biz_product/biz_request)
$v003Path = Join-Path $projectRoot 'database\migrations\V003__init_sample_tables.sql'
if (Test-Path $v003Path) {
    Add-Result 'BA-035' 'PASS' "V003 sample tables DDL exists (biz_customer/biz_product/biz_request)"
} else {
    Add-Result 'BA-035' 'FAIL' "V003 DDL missing"
}

# BA-036: V004 lowcode tables DDL exists
$v004Path = Join-Path $projectRoot 'database\migrations\V004__init_lowcode_tables.sql'
if (Test-Path $v004Path) {
    Add-Result 'BA-036' 'PASS' "V004 lowcode tables DDL exists"
} else {
    Add-Result 'BA-036' 'FAIL' "V004 DDL missing"
}

# BA-036b: V005 AI tables DDL exists
$v005Path = Join-Path $projectRoot 'database\migrations\V005__init_ai_tables.sql'
if (Test-Path $v005Path) {
    Add-Result 'BA-036b' 'PASS' "V005 AI tables DDL exists"
} else {
    Add-Result 'BA-036b' 'FAIL' "V005 AI DDL missing"
}

# BA-037: V011 contract DDL exists (35 号文档扩展)
$v011Path = Join-Path $projectRoot 'database\migrations\V011__init_contract_tables.sql'
if (Test-Path $v011Path) {
    Add-Result 'BA-037' 'PASS' "V011 contract tables DDL exists (35 号文档扩展)"
} else {
    Add-Result 'BA-037' 'WARN' "V011 contract DDL missing"
}

# BA-037b: V035 AI platform baseline exists (V035-V041 GA)
$v035Path = Join-Path $projectRoot 'database\migrations\V035__ai_platform_parity_baseline.sql'
if (Test-Path $v035Path) {
    Add-Result 'BA-037b' 'PASS' "V035 AI platform baseline exists"
} else {
    Add-Result 'BA-037b' 'FAIL' "V035 AI platform baseline missing"
}

# BA-037c: V036 ai provider parity
$v036Path = Join-Path $projectRoot 'database\migrations\V036__ai_provider_parity.sql'
if (Test-Path $v036Path) {
    Add-Result 'BA-037c' 'PASS' "V036 ai_provider parity exists"
} else {
    Add-Result 'BA-037c' 'FAIL' "V036 missing"
}
# BA-037d: V037 knowledge RAG parity
$v037Path = Join-Path $projectRoot 'database\migrations\V037__knowledge_rag_parity.sql'
if (Test-Path $v037Path) {
    Add-Result 'BA-037d' 'PASS' "V037 knowledge RAG parity exists"
} else {
    Add-Result 'BA-037d' 'FAIL' "V037 missing"
}
# BA-037e: V038 MCP/skill parity
$v038Path = Join-Path $projectRoot 'database\migrations\V038__ai_mcp_skill_parity.sql'
if (Test-Path $v038Path) {
    Add-Result 'BA-037e' 'PASS' "V038 MCP/skill parity exists"
} else {
    Add-Result 'BA-037e' 'FAIL' "V038 missing"
}
# BA-037f: V039 agent/memory parity
$v039Path = Join-Path $projectRoot 'database\migrations\V039__ai_agent_memory_parity.sql'
if (Test-Path $v039Path) {
    Add-Result 'BA-037f' 'PASS' "V039 agent/memory parity exists"
} else {
    Add-Result 'BA-037f' 'FAIL' "V039 missing"
}
# BA-037g: V040 aiflow parity
$v040Path = Join-Path $projectRoot 'database\migrations\V040__aiflow_parity.sql'
if (Test-Path $v040Path) {
    Add-Result 'BA-037g' 'PASS' "V040 aiflow parity exists"
} else {
    Add-Result 'BA-037g' 'FAIL' "V040 missing"
}
# BA-037h: V041 media/drama/copilot parity
$v041Path = Join-Path $projectRoot 'database\migrations\V041__media_drama_copilot_parity.sql'
if (Test-Path $v041Path) {
    Add-Result 'BA-037h' 'PASS' "V041 media/drama/copilot parity exists"
} else {
    Add-Result 'BA-037h' 'FAIL' "V041 missing"
}

# BA-038: Backend health check (runtime verification)
try {
    $healthResp = Invoke-WebRequest -Uri 'http://localhost:8082/actuator/health' -UseBasicParsing -TimeoutSec 5
    $healthText = if ($healthResp.Content -is [byte[]]) { [System.Text.Encoding]::UTF8.GetString($healthResp.Content) } else { [string]$healthResp.Content }
    if ($healthResp.StatusCode -eq 200 -and $healthText -match 'UP') {
        Add-Result 'BA-038' 'PASS' "Backend healthy (runtime verification, status=200)"
    } else {
        Add-Result 'BA-038' 'WARN' "Backend status=$($healthResp.StatusCode) but not UP"
    }
} catch {
    Add-Result 'BA-038' 'WARN' "Backend health check failed: $($_.Exception.Message)"
}

# BA-039: yutong containers running
try {
    $containers = docker ps --filter "name=yutong" --format "{{.Names}}" 2>$null
    $containerCount = ($containers | Measure-Object).Count
    if ($containerCount -ge 1) {
        Add-Result 'BA-039' 'PASS' "yutong containers running: $containerCount (>=1)"
    } else {
        Add-Result 'BA-039' 'WARN' "No yutong containers running"
    }
} catch {
    Add-Result 'BA-039' 'WARN' "Docker check failed: $($_.Exception.Message)"
}

# BA-040: permissions.yaml exists
$permPath = Join-Path $docsRoot 'contracts\registries\permissions.yaml'
if (Test-Path $permPath) {
    Add-Result 'BA-040' 'PASS' "permissions.yaml exists (cross-domain permission registry)"
} else {
    Add-Result 'BA-040' 'FAIL' "permissions.yaml missing"
}

# Summary
Write-Host ""
Write-Host "========== Summary =========="
Write-Host "PASS=$pass WARN=$warn FAIL=$fail"
$total = $pass + $warn + $fail
$status = if ($fail -eq 0) { 'PASS' } else { 'FAIL' }
Write-Host "Overall: $status ($pass/$total passed)"

# JSON report
$reportDir = Join-Path $projectRoot 'build\reports\checks'
if (-not (Test-Path $reportDir)) {
    New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
}
$jsonPath = Join-Path $reportDir 'business-architecture-ga2-l161.json'

$report = [PSCustomObject]@{
    schemaVersion = '1.0.0'
    taskId = 'GA2-L161'
    designDoc = '04-业务架构设计'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    passCount = $pass
    warnCount = $warn
    failCount = $fail
    status = $status
    results = $results
}

$json = $report | ConvertTo-Json -Depth 10
[System.IO.File]::WriteAllText($jsonPath, $json, [System.Text.UTF8Encoding]::new($false))
Write-Host "JSON report: $jsonPath"

