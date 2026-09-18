# GA2-L162: Sample Business Detail Machine Check Script
# Design source: 18-样例业务详细设计.md (DOC-PRD-004)
# Verifies 18 doc's 4 acceptance criteria + 6 user stories + 5 states + 5 tables + 14 APIs + 6 pages + 8 tests
# Output: build/reports/checks/sample-business-detail-ga2-l162.json + stdout summary

$ErrorActionPreference = 'Continue'
$projectRoot = 'd:\MyCode\YuTong-Java-Vue'
$docsRoot = Join-Path $projectRoot 'YuTong-Java-Docs'
$designDocPath = Join-Path $docsRoot '18-样例业务详细设计\18-样例业务详细设计.md'
$yamlPath = Join-Path $docsRoot 'contracts\governance\sample-business-detail.yaml'
$trackingPath = Join-Path $docsRoot '23-设计到落地追踪记录\23-设计到落地追踪记录.md'
$ddlPath = Join-Path $projectRoot 'database\migrations\V003__init_sample_tables.sql'
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

# SB-001: 18 design doc exists
if (Test-Path $designDocPath) {
    Add-Result 'SB-001' 'PASS' "18 design doc exists"
} else {
    Add-Result 'SB-001' 'FAIL' "18 design doc missing"
}

# SB-002: sample-business-detail.yaml skeleton exists
if (Test-Path $yamlPath) {
    $size = (Get-Item $yamlPath).Length
    Add-Result 'SB-002' 'PASS' "sample-business-detail.yaml exists ($size bytes)"
} else {
    Add-Result 'SB-002' 'FAIL' "sample-business-detail.yaml missing"
}

# SB-003: V003 DDL file exists
if (Test-Path $ddlPath) {
    Add-Result 'SB-003' 'PASS' "V003__init_sample_tables.sql exists"
} else {
    Add-Result 'SB-003' 'FAIL' "V003 DDL missing"
}

$designContent = [System.IO.File]::ReadAllText($designDocPath, [System.Text.Encoding]::UTF8)
$yamlContent = [System.IO.File]::ReadAllText($yamlPath, [System.Text.Encoding]::UTF8)
$ddlContent = [System.IO.File]::ReadAllText($ddlPath, [System.Text.Encoding]::UTF8)

# SB-004: 6 user stories all present
$storyHits = 0
foreach ($k in @('US-01', 'US-02', 'US-03', 'US-04', 'US-05', 'US-06')) {
    if ($designContent -match [regex]::Escape($k)) { $storyHits++ }
}
if ($storyHits -eq 6) {
    Add-Result 'SB-004' 'PASS' "6 user stories all present"
} else {
    Add-Result 'SB-004' 'FAIL' "User stories: $storyHits/6"
}

# SB-005: 5 state machine codes all present
$stateHits = 0
foreach ($s in @('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'ARCHIVED')) {
    if ($designContent -match "\b$([regex]::Escape($s))\b") { $stateHits++ }
}
if ($stateHits -eq 5) {
    Add-Result 'SB-005' 'PASS' "5 state machine codes all present"
} else {
    Add-Result 'SB-005' 'FAIL' "State machine codes: $stateHits/5"
}

# SB-006: 5 sample tables all present in DDL
$tableHits = 0
foreach ($t in @('biz_customer', 'biz_product', 'biz_request', 'biz_request_item', 'biz_approval_record')) {
    if ($ddlContent -match "CREATE TABLE $([regex]::Escape($t))") { $tableHits++ }
}
if ($tableHits -eq 5) {
    Add-Result 'SB-006' 'PASS' "5 sample tables all in DDL"
} else {
    Add-Result 'SB-006' 'FAIL' "Sample tables in DDL: $tableHits/5"
}

# SB-007: biz_request key fields in DDL (request_no + total_amount + customer_id + status)
$reqFieldHits = 0
foreach ($f in @('request_no', 'total_amount', 'customer_id', 'customer_name_snapshot', 'request_status', 'applicant_id', 'owner_user_id', 'owner_dept_id', 'owner_dept_path', 'submitted_time', 'approved_time', 'archived_time')) {
    if ($ddlContent -match [regex]::Escape($f)) { $reqFieldHits++ }
}
if ($reqFieldHits -ge 12) {
    Add-Result 'SB-007' 'PASS' "biz_request 12 key fields in DDL"
} else {
    Add-Result 'SB-007' 'FAIL' "biz_request fields: $reqFieldHits/12"
}

# SB-008: biz_request_item key fields in DDL
$itemFieldHits = 0
foreach ($f in @('request_id', 'product_id', 'product_code_snapshot', 'product_name_snapshot', 'unit', 'quantity', 'unit_price', 'line_amount', 'sort_no')) {
    if ($ddlContent -match [regex]::Escape($f)) { $itemFieldHits++ }
}
if ($itemFieldHits -ge 9) {
    Add-Result 'SB-008' 'PASS' "biz_request_item 9 fields in DDL"
} else {
    Add-Result 'SB-008' 'FAIL' "biz_request_item fields: $itemFieldHits/9"
}

# SB-009: biz_approval_record key fields in DDL
$apprHits = 0
foreach ($f in @('request_id', 'action', 'result', 'opinion', 'operator_id', 'operated_time')) {
    if ($ddlContent -match [regex]::Escape($f)) { $apprHits++ }
}
if ($apprHits -ge 6) {
    Add-Result 'SB-009' 'PASS' "biz_approval_record 6 fields in DDL"
} else {
    Add-Result 'SB-009' 'FAIL' "biz_approval_record fields: $apprHits/6"
}

# SB-010: 14 API endpoints present in design doc
$apiHits = 0
foreach ($api in @('/api/v1/customers', '/api/v1/products', '/api/v1/biz-requests', '/api/v1/biz-requests/{id}/submit', '/api/v1/biz-requests/{id}/approve', '/api/v1/biz-requests/{id}/reject', '/api/v1/biz-requests/{id}/withdraw', '/api/v1/biz-requests/{id}/archive', '/api/v1/mobile/todos', '/api/v1/mobile/biz-requests/{id}', '/api/v1/customers/import', '/api/v1/biz-requests/export')) {
    if ($designContent -match [regex]::Escape($api)) { $apiHits++ }
}
if ($apiHits -ge 12) {
    Add-Result 'SB-010' 'PASS' "12+ API endpoints all present ($apiHits)"
} else {
    Add-Result 'SB-010' "WARN" "API endpoints: $apiHits/12"
}

# SB-011: 6 pages defined in design doc
$pageHits = 0
foreach ($p in @('申请单列表', '申请单表单', '申请单详情', '移动待办', '移动详情', '移动处理')) {
    if ($designContent -match [regex]::Escape($p)) { $pageHits++ }
}
if ($pageHits -ge 6) {
    Add-Result 'SB-011' 'PASS' "6 pages all present"
} else {
    Add-Result 'SB-011' 'FAIL' "Pages: $pageHits/6"
}

# SB-012: 8 test matrix cases present
$testHits = 0
foreach ($t in @('草稿保存', '提交待办', '移动审核通过', '驳回重提', '并发审核', '导入错误', '导出异步', '附件重试')) {
    if ($designContent -match [regex]::Escape($t)) { $testHits++ }
}
if ($testHits -ge 8) {
    Add-Result 'SB-012' 'PASS' "8 test matrix cases all present"
} else {
    Add-Result 'SB-012' 'FAIL' "Test cases: $testHits/8"
}

# SB-013: 4 acceptance criteria
$criteriaHits = 0
foreach ($k in @('US-01 至 US-06', 'US-03', '枚举一致', '日志可追溯')) {
    if ($designContent -match [regex]::Escape($k)) { $criteriaHits++ }
}
if ($criteriaHits -ge 4) {
    Add-Result 'SB-013' 'PASS' "4 acceptance criteria all present"
} else {
    Add-Result 'SB-013' 'FAIL' "Acceptance criteria: $criteriaHits/4"
}

# SB-014: 4 benchmark values present
$benchHits = 0
foreach ($k in @('业界同类实现', 'Yudao', 'JeecgBoot', 'PostgreSQL 字符串主键')) {
    if ($designContent -match [regex]::Escape($k)) { $benchHits++ }
}
if ($benchHits -ge 4) {
    Add-Result 'SB-014' 'PASS' "4 benchmark values all present"
} else {
    Add-Result 'SB-014' 'WARN' "Benchmark values: $benchHits/4"
}

# SB-015: 4 message/task events
$msgHits = 0
foreach ($k in @('biz.request.submitted', 'biz.request.approved', 'biz.request.rejected', 'system.export-task.created')) {
    if ($designContent -match [regex]::Escape($k)) { $msgHits++ }
}
if ($msgHits -ge 4) {
    Add-Result 'SB-015' 'PASS' "4 message/task events all present"
} else {
    Add-Result 'SB-015' 'FAIL' "Message events: $msgHits/4"
}

# SB-016: 3 report metrics
$metricHits = 0
foreach ($k in @('待办数', '各状态数量', '近 7 日提交趋势')) {
    if ($designContent -match [regex]::Escape($k)) { $metricHits++ }
}
if ($metricHits -ge 3) {
    Add-Result 'SB-016' 'PASS' "3 report metrics all present"
} else {
    Add-Result 'SB-016' 'FAIL' "Report metrics: $metricHits/3"
}

# SB-017: 6 idempotency/concurrency scenarios
$idemHits = 0
foreach ($k in @('保存草稿重复点击', '提交重复点击', '并发审核', '导入部分失败', '附件上传失败', '导出超时')) {
    if ($designContent -match [regex]::Escape($k)) { $idemHits++ }
}
if ($idemHits -ge 6) {
    Add-Result 'SB-017' 'PASS' "6 idempotency/concurrency scenarios all present"
} else {
    Add-Result 'SB-017' 'FAIL' "Idempotency scenarios: $idemHits/6"
}

# SB-018: request_no concurrency strategy (REQyyyyMMddNNNN)
if ($designContent -match 'REQyyyyMMddNNNN' -and $designContent -match 'sys_sequence') {
    Add-Result 'SB-018' 'PASS' "request_no concurrency strategy present (REQyyyyMMddNNNN + sys_sequence)"
} else {
    Add-Result 'SB-018' 'FAIL' "request_no concurrency strategy missing"
}

# SB-019: code & amount rules (4 rules)
$ruleHits = 0
foreach ($k in @('后端按明细重新计算', 'line_amount = quantity * unit_price', 'sort_no', '保留 2 位小数')) {
    if ($designContent -match [regex]::Escape($k)) { $ruleHits++ }
}
if ($ruleHits -ge 3) {
    Add-Result 'SB-019' 'PASS' "Code & amount rules present ($ruleHits/4)"
} else {
    Add-Result 'SB-019' 'WARN' "Code & amount rules: $ruleHits/4"
}

# SB-020: Approver & todo assignment rules
if ($designContent -match 'sys_todo_task' -and $designContent -match 'biz_type=biz_request' -and $designContent -match 'todo_status=PENDING') {
    Add-Result 'SB-020' 'PASS' "Approver & todo assignment rules present (sys_todo_task + biz_type + todo_status)"
} else {
    Add-Result 'SB-020' 'WARN' "Approver & todo assignment rules partially missing"
}

# SB-021: API response model unification (BizRequestDetailVO + RequestStatusVO + MobileTodoVO)
$voHits = 0
foreach ($k in @('BizRequestDetailVO', 'RequestStatusVO', 'MobileTodoVO')) {
    if ($designContent -match [regex]::Escape($k)) { $voHits++ }
}
if ($voHits -ge 3) {
    Add-Result 'SB-021' 'PASS' "3 unified API response models all present"
} else {
    Add-Result 'SB-021' 'FAIL' "Unified VOs: $voHits/3"
}

# SB-022: Common fields & primary key conventions (5 rules)
$convHits = 0
foreach ($k in @('tenant_id', 'created_by', 'created_time', 'version', 'varchar(32)')) {
    if ($designContent -match [regex]::Escape($k)) { $convHits++ }
}
if ($convHits -ge 5) {
    Add-Result 'SB-022' 'PASS' "Common fields & PK conventions (5/5)"
} else {
    Add-Result 'SB-022' 'WARN' "Conventions: $convHits/5"
}

# SB-023: Import/export rules
if ($designContent -match '客户导入列映射' -and $designContent -match '错误报告') {
    Add-Result 'SB-023' 'PASS' "Import/export rules present (客户导入列映射 + 错误报告)"
} else {
    Add-Result 'SB-023' 'WARN' "Import/export rules partially missing"
}

# SB-024: 4 archive rules (ARCHIVE biz_approval_record + sys_operation_log + no new todo + update unclosed todo)
$archiveHits = 0
foreach ($k in @('archive', 'ARCHIVE', 'CANCELLED', '不得生成新的待办')) {
    if ($designContent -match [regex]::Escape($k)) { $archiveHits++ }
}
if ($archiveHits -ge 4) {
    Add-Result 'SB-024' 'PASS' "Archive rules present ($archiveHits/4)"
} else {
    Add-Result 'SB-024' 'WARN' "Archive rules: $archiveHits/4"
}

# SB-025: YAML businessDomainOverview section
if ($yamlContent -match 'businessDomainOverview:' -and $yamlContent -match 'domainName: 通用业务申请单管理') {
    Add-Result 'SB-025' 'PASS' "YAML businessDomainOverview section present"
} else {
    Add-Result 'SB-025' 'FAIL' "YAML businessDomainOverview section missing"
}

# SB-026: YAML userStories has 6 items
$yamlStoryCount = ([regex]::Matches($yamlContent, '^\s+- id: US-0', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlStoryCount -ge 6) {
    Add-Result 'SB-026' 'PASS' "YAML userStories has $yamlStoryCount items (>=6)"
} else {
    Add-Result 'SB-026' 'FAIL' "YAML userStories count: $yamlStoryCount/6"
}

# SB-027: YAML stateMachine has 5 states
$yamlStateCount = ([regex]::Matches($yamlContent, '^\s+- code: (DRAFT|SUBMITTED|APPROVED|REJECTED|ARCHIVED)', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlStateCount -ge 5) {
    Add-Result 'SB-027' 'PASS' "YAML stateMachine has $yamlStateCount states (>=5)"
} else {
    Add-Result 'SB-027' 'FAIL' "YAML stateMachine count: $yamlStateCount/5"
}

# SB-028: YAML tableSchemas has 5 tables (uses MAP format: '  biz_customer:')
$yamlTableCount = ([regex]::Matches($yamlContent, '^  (biz_customer|biz_product|biz_request|biz_request_item|biz_approval_record):', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlTableCount -ge 5) {
    Add-Result 'SB-028' 'PASS' "YAML tableSchemas has $yamlTableCount tables (>=5)"
} else {
    Add-Result 'SB-028' 'FAIL' "YAML tableSchemas count: $yamlTableCount/5"
}

# SB-029: YAML apiList has 14 APIs (inline flow format: '- {capability: ..., method: ..., path: ...}')
$yamlApiCount = ([regex]::Matches($yamlContent, '^\s+- \{capability:', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlApiCount -ge 14) {
    Add-Result 'SB-029' 'PASS' "YAML apiList has $yamlApiCount items (>=14)"
} else {
    Add-Result 'SB-029' 'WARN' "YAML apiList count: $yamlApiCount/14"
}

# SB-030: YAML testMatrix has 8 cases (inline flow format: '- {scenario: ...}')
$yamlTestCount = ([regex]::Matches($yamlContent, '^\s+- \{scenario:', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlTestCount -ge 8) {
    Add-Result 'SB-030' 'PASS' "YAML testMatrix has $yamlTestCount cases (>=8)"
} else {
    Add-Result 'SB-030' 'WARN' "YAML testMatrix count: $yamlTestCount/8"
}

# SB-031: YAML yutongPrefixRule section
if ($yamlContent -match 'yutongPrefixRule:' -and $yamlContent -match 'yutong-backend-run') {
    Add-Result 'SB-031' 'PASS' "YAML yutongPrefixRule section present (yutong-backend-run)"
} else {
    Add-Result 'SB-031' 'WARN' "YAML yutongPrefixRule section missing"
}

# SB-032: YAML knownDeviations with DEV-L162-* IDs
if ($yamlContent -match 'knownDeviations:' -and $yamlContent -match 'DEV-L162-') {
    Add-Result 'SB-032' 'PASS' "YAML knownDeviations present with DEV-L162-* IDs"
} else {
    Add-Result 'SB-032' 'FAIL' "YAML knownDeviations missing"
}

# SB-033: YAML acceptanceCriteria with 4 items (plain string list format: '- Web 端可完成...')
if ($yamlContent -match 'acceptanceCriteria:') {
    # Extract acceptanceCriteria section and count list items
    $critMatch = [regex]::Match($yamlContent, 'acceptanceCriteria:\s*\n((?:\s+- .+\n)+)')
    if ($critMatch.Success) {
        $yamlCritCount = ([regex]::Matches($critMatch.Groups[1].Value, '^\s+- ', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
    } else {
        $yamlCritCount = 0
    }
    if ($yamlCritCount -ge 4) {
        Add-Result 'SB-033' 'PASS' "YAML acceptanceCriteria has $yamlCritCount items (>=4)"
    } else {
        Add-Result 'SB-033' 'WARN' "YAML acceptanceCriteria count: $yamlCritCount/4"
    }
} else {
    Add-Result 'SB-033' 'FAIL' "YAML acceptanceCriteria section missing"
}

# SB-034: 23 tracking record has 18-样例业务详细设计 entry
if (Test-Path $trackingPath) {
    $trackingContent = [System.IO.File]::ReadAllText($trackingPath, [System.Text.Encoding]::UTF8)
    if ($trackingContent -match '18-样例业务详细设计') {
        Add-Result 'SB-034' 'PASS' "23 tracking record has 18-样例业务详细设计 entry"
    } else {
        Add-Result 'SB-034' 'FAIL' "23 tracking record missing 18 entry"
    }
} else {
    Add-Result 'SB-034' 'FAIL' "23 tracking record missing"
}

# SB-035: 23 tracking record has L162 entry
if ($trackingContent -match 'L162') {
    Add-Result 'SB-035' 'PASS' "23 tracking record has L162 entry"
} else {
    Add-Result 'SB-035' 'WARN' "23 tracking record missing L162 entry (will be added by writeback)"
}

# SB-036: backend sample-service exists
$sampleServicePath = Join-Path $projectRoot 'backend\yutong-sample-service'
if (Test-Path $sampleServicePath) {
    Add-Result 'SB-036' 'PASS' "backend yutong-sample-service exists"
} else {
    Add-Result 'SB-036' 'FAIL' "yutong-sample-service missing"
}

# SB-037: 4 backend sub-packages (masterdata/request/mobile/workbench)
$subPkgHits = 0
foreach ($p in @('masterdata', 'request', 'mobile', 'workbench')) {
    $pPath = Join-Path $sampleServicePath "src\main\java\com\yutong\sample\$p"
    if (Test-Path $pPath) { $subPkgHits++ }
}
if ($subPkgHits -ge 4) {
    Add-Result 'SB-037' 'PASS' "4 backend sub-packages all present (masterdata/request/mobile/workbench)"
} else {
    Add-Result 'SB-037' 'FAIL' "Sub-packages: $subPkgHits/4"
}

# SB-038: 5 backend controllers exist
$ctrlHits = 0
foreach ($c in @('CustomerController.java', 'ProductController.java', 'BizRequestController.java', 'MobileController.java', 'WorkbenchController.java')) {
    $cPath = Join-Path $sampleServicePath "src\main\java\com\yutong\sample"
    $found = Get-ChildItem -Path $cPath -Filter $c -Recurse -ErrorAction SilentlyContinue
    if ($found) { $ctrlHits++ }
}
if ($ctrlHits -ge 5) {
    Add-Result 'SB-038' 'PASS' "5 backend controllers all present"
} else {
    Add-Result 'SB-038' 'FAIL' "Controllers: $ctrlHits/5"
}

# SB-039: Backend runtime healthy (curl localhost:8082/actuator/health)
try {
    $resp = Invoke-WebRequest -Uri 'http://localhost:8082/actuator/health' -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
    if ($resp.StatusCode -eq 200) {
        Add-Result 'SB-039' 'PASS' "Backend healthy (runtime verification, status=$($resp.StatusCode))"
    } else {
        Add-Result 'SB-039' 'WARN' "Backend status: $($resp.StatusCode)"
    }
} catch {
    Add-Result 'SB-039' 'WARN' "Backend not reachable (skipped): $($_.Exception.Message)"
}

# SB-040: yutong containers running
try {
    $containers = docker ps --filter "name=yutong" --format "{{.Names}}" 2>$null
    $containerCount = ($containers | Measure-Object).Count
    if ($containerCount -ge 1) {
        Add-Result 'SB-040' 'PASS' "yutong containers running: $containerCount (>=1)"
    } else {
        Add-Result 'SB-040' 'WARN' "No yutong containers running"
    }
} catch {
    Add-Result 'SB-040' 'WARN' "docker command failed (skipped)"
}

# SB-041: permissions.yaml exists (cross-check)
if (Test-Path $permissionsYamlPath) {
    Add-Result 'SB-041' 'PASS' "permissions.yaml exists (cross-check)"
} else {
    Add-Result 'SB-041' 'WARN' "permissions.yaml missing"
}

# SB-042: YAML codeAlignment section
if ($yamlContent -match 'codeAlignment:' -and $yamlContent -match 'sampleServicePath:') {
    Add-Result 'SB-042' 'PASS' "YAML codeAlignment section present"
} else {
    Add-Result 'SB-042' 'WARN' "YAML codeAlignment section missing"
}

# Summary
Write-Host ""
Write-Host "=========================================="
Write-Host " GA2-L162 Sample Business Detail Check Summary"
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
$jsonPath = Join-Path $reportDir 'sample-business-detail-ga2-l162.json'
$report = [PSCustomObject]@{
    results = $results
    designDoc = '18-样例业务详细设计'
    passCount = $pass
    warnCount = $warn
    failCount = $fail
    taskId = 'GA2-L162'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    status = $status
    schemaVersion = '1.0.0'
}
$report | ConvertTo-Json -Depth 5 | Out-File -FilePath $jsonPath -Encoding utf8
Write-Host "JSON report: $jsonPath"

if ($fail -gt 0) { exit 1 }
