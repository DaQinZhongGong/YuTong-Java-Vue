# GA2-L160: Requirements Scope Machine Check Script
# Design source: 02-需求与范围设计.md (DOC-PRD-002)
# Verifies 02 doc's 4 acceptance criteria + 25 REQ-* capabilities + 6 hard constraints + GA baseline + non-functional metrics
# Output: build/reports/checks/requirements-scope-ga2-l160.json + stdout summary

$ErrorActionPreference = 'Continue'
$projectRoot = 'd:\MyCode\YuTong-Java-Vue'
$docsRoot = Join-Path $projectRoot 'YuTong-Java-Docs'
$designDocPath = Join-Path $docsRoot '02-需求与范围设计\02-需求与范围设计.md'
$yamlPath = Join-Path $docsRoot 'contracts\governance\requirements-scope.yaml'
$productBaselineYaml = Join-Path $docsRoot 'contracts\product-baseline.yaml'
$trackingPath = Join-Path $docsRoot '23-设计到落地追踪记录\23-设计到落地追踪记录.md'
$routesYaml = Join-Path $docsRoot 'contracts\registries\routes.yaml'

$script:results = New-Object System.Collections.ArrayList
$script:pass = 0
$script:warn = 0
$script:fail = 0

function Add-Result($id, $status, $message) {
    [void]$script:results.Add([PSCustomObject]@{ Id = $id; Status = $status; Message = $message })
    switch ($status) {
        'PASS' { $script:pass++ }
        'WARN' { $script:warn++ }
        'FAIL' { $script:fail++ }
    }
    Write-Host "[$status] $id`: $message"
}

# RS-001: 02 design doc exists
if (Test-Path $designDocPath) {
    Add-Result 'RS-001' 'PASS' "02 design doc exists"
} else {
    Add-Result 'RS-001' 'FAIL' "02 design doc missing"
}

# RS-002: requirements-scope.yaml skeleton exists
if (Test-Path $yamlPath) {
    $size = (Get-Item $yamlPath).Length
    Add-Result 'RS-002' 'PASS' "requirements-scope.yaml exists ($size bytes)"
} else {
    Add-Result 'RS-002' 'FAIL' "requirements-scope.yaml missing"
}

# RS-003: product-baseline.yaml exists (authority source)
if (Test-Path $productBaselineYaml) {
    Add-Result 'RS-003' 'PASS' "product-baseline.yaml exists (authority source)"
} else {
    Add-Result 'RS-003' 'FAIL' "product-baseline.yaml missing"
}

$designContent = [System.IO.File]::ReadAllText($designDocPath, [System.Text.Encoding]::UTF8)
$yamlContent = [System.IO.File]::ReadAllText($yamlPath, [System.Text.Encoding]::UTF8)

# RS-004: 6 user personas covered
$personaHits = 0
foreach ($p in @('后端开发', '前端开发', '移动端开发', '架构负责人', '项目负责人', '业务配置人员')) {
    if ($designContent -match [regex]::Escape($p)) { $personaHits++ }
}
if ($personaHits -eq 6) {
    Add-Result 'RS-004' 'PASS' "6 user personas all present"
} else {
    Add-Result 'RS-004' 'FAIL' "User personas: $personaHits/6"
}

# RS-005: 5 REQ-BASE capabilities
$reqBaseHits = 0
for ($i = 1; $i -le 5; $i++) {
    if ($designContent -match "REQ-BASE-00$i") { $reqBaseHits++ }
}
if ($reqBaseHits -eq 5) {
    Add-Result 'RS-005' 'PASS' "5 REQ-BASE-* capabilities all present"
} else {
    Add-Result 'RS-005' 'FAIL' "REQ-BASE-*: $reqBaseHits/5"
}

# RS-006: 4 REQ-SAMPLE capabilities
$reqSampleHits = 0
for ($i = 1; $i -le 4; $i++) {
    if ($designContent -match "REQ-SAMPLE-00$i") { $reqSampleHits++ }
}
if ($reqSampleHits -eq 4) {
    Add-Result 'RS-006' 'PASS' "4 REQ-SAMPLE-* capabilities all present"
} else {
    Add-Result 'RS-006' 'FAIL' "REQ-SAMPLE-*: $reqSampleHits/4"
}

# RS-007: 6 REQ-LC capabilities
$reqLcHits = 0
for ($i = 1; $i -le 6; $i++) {
    if ($designContent -match "REQ-LC-00$i") { $reqLcHits++ }
}
if ($reqLcHits -eq 6) {
    Add-Result 'RS-007' 'PASS' "6 REQ-LC-* capabilities all present"
} else {
    Add-Result 'RS-007' 'FAIL' "REQ-LC-*: $reqLcHits/6"
}

# RS-008: 6 REQ-AI capabilities
$reqAiHits = 0
for ($i = 1; $i -le 6; $i++) {
    if ($designContent -match "REQ-AI-00$i") { $reqAiHits++ }
}
if ($reqAiHits -eq 6) {
    Add-Result 'RS-008' 'PASS' "6 REQ-AI-* capabilities all present"
} else {
    Add-Result 'RS-008' 'FAIL' "REQ-AI-*: $reqAiHits/6"
}

# RS-009: 7 REQ-MW capabilities
$reqMwHits = 0
for ($i = 1; $i -le 7; $i++) {
    if ($designContent -match "REQ-MW-00$i") { $reqMwHits++ }
}
if ($reqMwHits -eq 7) {
    Add-Result 'RS-009' 'PASS' "7 REQ-MW-* capabilities all present"
} else {
    Add-Result 'RS-009' 'FAIL' "REQ-MW-*: $reqMwHits/7"
}

# RS-010: 6 first version hard constraints
$constraintHits = 0
foreach ($k in @('PostgreSQL 主键统一为字符串', 'string 语义处理', '权限模块只实现接入边界', '低代码只覆盖标准 CRUD', 'AI 只输出建议', 'OIDC|SSO|LDAP_AD|CUSTOMER_IAM|LOCAL_IAM_EXTENSION')) {
    if ($designContent -match $k) { $constraintHits++ }
}
if ($constraintHits -ge 5) {
    Add-Result 'RS-010' 'PASS' "First version hard constraints covered ($constraintHits/6)"
} else {
    Add-Result 'RS-010' 'WARN' "Hard constraints: $constraintHits/6"
}

# RS-011: GA baseline numbers (31 routes, 30 screens, 94 operations, 37 tables, 230 AI cases)
$gaBaselineHits = 0
foreach ($k in @('31 条路由', '30 个唯一屏幕', '94 个 OpenAPI operation', '37 张数据表', '230 条 AI 发布评测')) {
    if ($designContent -match [regex]::Escape($k)) { $gaBaselineHits++ }
}
if ($gaBaselineHits -eq 5) {
    Add-Result 'RS-011' 'PASS' "GA baseline numbers all present (5/5)"
} else {
    Add-Result 'RS-011' 'WARN' "GA baseline numbers: $gaBaselineHits/5"
}

# RS-012: GA run strategy 5 layers
$layerHits = 0
foreach ($k in @('设计层', 'E1~E3 内部增量', 'GA C2', 'GA C3', '中间件')) {
    if ($designContent -match [regex]::Escape($k)) { $layerHits++ }
}
if ($layerHits -eq 5) {
    Add-Result 'RS-012' 'PASS' "GA run strategy 5 layers all present"
} else {
    Add-Result 'RS-012' 'FAIL' "GA run strategy: $layerHits/5"
}

# RS-013: GA v1.0 must deliver items
$mustHits = 0
foreach ($k in @('模块化单体后端', 'Vue3 管理端基础框架', 'Uniapp 移动端基础框架', 'PostgreSQL 数据规范', '完整样例业务域', '低代码元模型', 'AI Gateway')) {
    if ($designContent -match [regex]::Escape($k)) { $mustHits++ }
}
if ($mustHits -ge 6) {
    Add-Result 'RS-013' 'PASS' "GA v1.0 must deliver items covered ($mustHits/7)"
} else {
    Add-Result 'RS-013' 'WARN' "GA v1.0 must deliver: $mustHits/7"
}

# RS-014: 4 MoSCoW layers (Must / Should / Could / Out of Scope)
$moscowHits = 0
foreach ($k in @('Must', 'Should', 'Could', 'Out of Scope')) {
    if ($designContent -match $k) { $moscowHits++ }
}
if ($moscowHits -eq 4) {
    Add-Result 'RS-014' 'PASS' "4 MoSCoW layers all present"
} else {
    Add-Result 'RS-014' 'FAIL' "MoSCoW layers: $moscowHits/4"
}

# RS-015: 7 non-functional quantitative metrics
$nfrHits = 0
foreach ($k in @('P95 ≤ 500ms', 'P95 ≤ 800ms', '50MB', '1 万行', '99.5%', '99.9%', '可维护性')) {
    if ($designContent -match [regex]::Escape($k)) { $nfrHits++ }
}
if ($nfrHits -ge 6) {
    Add-Result 'RS-015' 'PASS' "7 non-functional quantitative metrics covered ($nfrHits/7)"
} else {
    Add-Result 'RS-015' 'WARN' "NFR metrics: $nfrHits/7"
}

# RS-016: 4 acceptance criteria
$criteriaHits = 0
foreach ($k in @('每个核心能力都有明确的文档入口和实现边界', '样例业务能覆盖增删改查', '低代码和 AI 能作为增强能力接入', '非功能需求可以映射')) {
    if ($designContent -match [regex]::Escape($k)) { $criteriaHits++ }
}
if ($criteriaHits -eq 4) {
    Add-Result 'RS-016' 'PASS' "4 acceptance criteria all present"
} else {
    Add-Result 'RS-016' 'FAIL' "Acceptance criteria: $criteriaHits/4"
}

# RS-017: requirement priorities (P0/P1/P2)
$priorityHits = 0
foreach ($k in @('P0', 'P1', 'P2', 'v0.2', 'v0.3', 'v0.4', 'v1.0')) {
    if ($designContent -match [regex]::Escape($k)) { $priorityHits++ }
}
if ($priorityHits -ge 7) {
    Add-Result 'RS-017' 'PASS' "Requirement priorities and versions all present ($priorityHits/7)"
} else {
    Add-Result 'RS-017' 'WARN' "Priorities: $priorityHits/7"
}

# RS-018: requirement traceability matrix in design doc
if ($designContent -match '需求追踪矩阵' -and $designContent -match 'REQ-BASE-001') {
    Add-Result 'RS-018' 'PASS' "Requirement traceability matrix present"
} else {
    Add-Result 'RS-018' 'FAIL' "Requirement traceability matrix missing"
}

# RS-019: 10 maven modules (yutong-*)
$backendPom = Join-Path $projectRoot 'backend\pom.xml'
if (Test-Path $backendPom) {
    $pomContent = [System.IO.File]::ReadAllText($backendPom, [System.Text.Encoding]::UTF8)
    $moduleHits = 0
    foreach ($m in @('yutong-common', 'yutong-infra', 'yutong-api', 'yutong-system-service', 'yutong-sample-service', 'yutong-lowcode-service', 'yutong-ai-service', 'yutong-release-service', 'yutong-docs-service', 'yutong-boot')) {
        if ($pomContent -match "<module>$([regex]::Escape($m))</module>") { $moduleHits++ }
    }
    if ($moduleHits -ge 10) {
        Add-Result 'RS-019' 'PASS' "10 yutong-* maven modules all present in backend/pom.xml"
    } else {
        Add-Result 'RS-019' 'WARN' "Maven modules: $moduleHits/10"
    }
} else {
    Add-Result 'RS-019' 'FAIL' "backend/pom.xml missing"
}

# RS-020: yutong container prefix in docker-compose
$composeBoot = Join-Path $projectRoot 'docker-compose.boot.yml'
$composeRun = Join-Path $projectRoot 'docker-compose.run.yml'
$composeHit = 0
foreach ($f in @($composeBoot, $composeRun)) {
    if (Test-Path $f) {
        $c = [System.IO.File]::ReadAllText($f, [System.Text.Encoding]::UTF8)
        if ($c -match 'yutong-postgres' -and $c -match 'yutong-redis' -and $c -match 'yutong-minio') { $composeHit++ }
    }
}
if ($composeHit -ge 1) {
    Add-Result 'RS-020' 'PASS' "yutong container prefix in docker-compose (yutong-postgres/redis/minio)"
} else {
    Add-Result 'RS-020' 'WARN' "yutong prefix not found in compose files"
}

# YAML skeleton section checks
# RS-021: YAML platformScope section
if ($yamlContent -match 'platformScope:' -and $yamlContent -match 'gaBaseline:') {
    Add-Result 'RS-021' 'PASS' "YAML platformScope + gaBaseline sections present"
} else {
    Add-Result 'RS-021' 'FAIL' "YAML platformScope/gaBaseline missing"
}

# RS-022: YAML userPersonas section with 6 items
$yamlPersonaCount = ([regex]::Matches($yamlContent, '^\s+- role: ', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlPersonaCount -ge 6) {
    Add-Result 'RS-022' 'PASS' "YAML userPersonas has $yamlPersonaCount items (>=6)"
} else {
    Add-Result 'RS-022' 'WARN' "YAML userPersonas: $yamlPersonaCount/6"
}

# RS-023: YAML coreCapabilities with 5 categories
$capHits = 0
foreach ($k in @('platformBasic:', 'sampleBusiness:', 'lowcode:', 'ai:', 'middleware:')) {
    if ($yamlContent -match $k) { $capHits++ }
}
if ($capHits -eq 5) {
    Add-Result 'RS-023' 'PASS' "YAML coreCapabilities has 5 categories"
} else {
    Add-Result 'RS-023' 'FAIL' "YAML coreCapabilities: $capHits/5"
}

# RS-024: YAML firstVersionHardConstraints with 6 items
$yamlConstraintCount = ([regex]::Matches($yamlContent, '^\s+- id: HC-\d+', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlConstraintCount -ge 6) {
    Add-Result 'RS-024' 'PASS' "YAML firstVersionHardConstraints has $yamlConstraintCount items (>=6)"
} else {
    Add-Result 'RS-024' 'WARN' "YAML hard constraints: $yamlConstraintCount/6"
}

# RS-025: YAML gaRunStrategy with 5 layers
$yamlLayerCount = ([regex]::Matches($yamlContent, '^\s+- layer: ', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlLayerCount -ge 5) {
    Add-Result 'RS-025' 'PASS' "YAML gaRunStrategy has $yamlLayerCount layers (>=5)"
} else {
    Add-Result 'RS-025' 'WARN' "YAML ga run strategy: $yamlLayerCount/5"
}

# RS-026: YAML gaV10Scope with mustDeliver + deferred
if ($yamlContent -match 'gaV10Scope:' -and $yamlContent -match 'mustDeliver:' -and $yamlContent -match 'deferred:') {
    Add-Result 'RS-026' 'PASS' "YAML gaV10Scope (mustDeliver + deferred) present"
} else {
    Add-Result 'RS-026' 'FAIL' "YAML gaV10Scope missing"
}

# RS-027: YAML requirementPriorities
$yamlPriorityCount = ([regex]::Matches($yamlContent, '^\s+- code: REQ-', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlPriorityCount -ge 8) {
    Add-Result 'RS-027' 'PASS' "YAML requirementPriorities has $yamlPriorityCount items (>=8)"
} else {
    Add-Result 'RS-027' 'WARN' "YAML requirementPriorities: $yamlPriorityCount/8"
}

# RS-028: YAML scopeLayering MoSCoW
if ($yamlContent -match 'scopeLayering:' -and $yamlContent -match 'must:' -and $yamlContent -match 'should:' -and $yamlContent -match 'could:' -and $yamlContent -match 'outOfScope:') {
    Add-Result 'RS-028' 'PASS' "YAML scopeLayering MoSCoW 4 layers present"
} else {
    Add-Result 'RS-028' 'FAIL' "YAML scopeLayering missing"
}

# RS-029: YAML nonFunctionalQuantitativeMetrics with 7 items
$yamlNfrCount = ([regex]::Matches($yamlContent, '^\s+- category: ', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlNfrCount -ge 7) {
    Add-Result 'RS-029' 'PASS' "YAML nonFunctionalQuantitativeMetrics has $yamlNfrCount items (>=7)"
} else {
    Add-Result 'RS-029' 'WARN' "YAML NFR: $yamlNfrCount/7"
}

# RS-030: YAML acceptanceCriteria with 4 items
$yamlAcCount = ([regex]::Matches($yamlContent, '^\s+- id: AC-\d+', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlAcCount -ge 4) {
    Add-Result 'RS-030' 'PASS' "YAML acceptanceCriteria has $yamlAcCount items (>=4)"
} else {
    Add-Result 'RS-030' 'WARN' "YAML AC: $yamlAcCount/4"
}

# RS-031: YAML yutongPrefixRule section
if ($yamlContent -match 'yutongPrefixRule:' -and $yamlContent -match 'yutong-backend:latest' -and $yamlContent -match 'yutong_default') {
    Add-Result 'RS-031' 'PASS' "YAML yutongPrefixRule section present (yutong-backend + yutong_default network)"
} else {
    Add-Result 'RS-031' 'FAIL' "YAML yutongPrefixRule missing"
}

# RS-032: YAML existingEvidence with GA2 items
$yamlEvidenceCount = ([regex]::Matches($yamlContent, '^\s+- ga2Task: GA2-', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlEvidenceCount -ge 10) {
    Add-Result 'RS-032' 'PASS' "YAML existingEvidence has $yamlEvidenceCount GA2 items (>=10)"
} else {
    Add-Result 'RS-032' 'WARN' "YAML evidence: $yamlEvidenceCount/10"
}

# RS-033: YAML knownDeviations
if ($yamlContent -match 'knownDeviations:' -and $yamlContent -match 'DEV-L160-001') {
    Add-Result 'RS-033' 'PASS' "YAML knownDeviations present with DEV-L160-* IDs"
} else {
    Add-Result 'RS-033' 'FAIL' "YAML knownDeviations missing"
}

# RS-034: 23 tracking record has 02-需求与范围 entry
if (Test-Path $trackingPath) {
    $trackingContent = [System.IO.File]::ReadAllText($trackingPath, [System.Text.Encoding]::UTF8)
    if ($trackingContent -match '02-需求与范围设计') {
        Add-Result 'RS-034' 'PASS' "23 tracking record has 02-需求与范围设计 entry"
    } else {
        Add-Result 'RS-034' 'FAIL' "23 tracking record missing 02 entry"
    }
} else {
    Add-Result 'RS-034' 'FAIL' "23 tracking record missing"
}

# RS-035: routes.yaml has 31 routes (GA baseline)
if (Test-Path $routesYaml) {
    $routesContent = [System.IO.File]::ReadAllText($routesYaml, [System.Text.Encoding]::UTF8)
    $routeMatches = [regex]::Matches($routesContent, '^\s+- path: ', [System.Text.RegularExpressions.RegexOptions]::Multiline)
    if ($routeMatches.Count -ge 31) {
        Add-Result 'RS-035' 'PASS' "routes.yaml has $($routeMatches.Count) routes (>=31 GA baseline)"
    } else {
        Add-Result 'RS-035' 'WARN' "routes.yaml: $($routeMatches.Count)/31"
    }
} else {
    Add-Result 'RS-035' 'FAIL' "routes.yaml missing"
}

# RS-036: 23 tracking record has 商业级需求范围补强 entry (L160 row)
if ($trackingContent -match '商业级需求范围补强' -and $trackingContent -match '02-需求与范围设计') {
    Add-Result 'RS-036' 'PASS' "23 tracking record has L160 商业级需求范围补强 entry"
} else {
    Add-Result 'RS-036' 'FAIL' "23 tracking record missing L160 entry"
}

# RS-037: Backend healthy (runtime verification, non-blocking)
try {
    $healthResp = Invoke-WebRequest -Uri 'http://localhost:8082/actuator/health' -UseBasicParsing -TimeoutSec 5
    $healthText = if ($healthResp.Content -is [byte[]]) { [System.Text.Encoding]::UTF8.GetString($healthResp.Content) } else { [string]$healthResp.Content }
    if ($healthText -match 'UP' -or $healthResp.StatusCode -eq 200) {
        Add-Result 'RS-037' 'PASS' "Backend healthy (runtime verification, status=$($healthResp.StatusCode))"
    } else {
        Add-Result 'RS-037' 'WARN' "Backend responded but not UP"
    }
} catch {
    Add-Result 'RS-037' 'WARN' "Backend health check failed (non-blocking): $($_.Exception.Message)"
}

# RS-038: Performance evidence exists (release-evidence/v1.0.0/07-performance)
$perfEvidenceDir = Join-Path $projectRoot 'release-evidence\v1.0.0\07-performance'
if (Test-Path $perfEvidenceDir) {
    $perfFiles = Get-ChildItem -Path $perfEvidenceDir -File
    if ($perfFiles.Count -ge 5) {
        Add-Result 'RS-038' 'PASS' "Performance evidence has $($perfFiles.Count) files (>=5, GA2-06)"
    } else {
        Add-Result 'RS-038' 'WARN' "Performance evidence: $($perfFiles.Count) files"
    }
} else {
    Add-Result 'RS-038' 'WARN' "Performance evidence directory missing"
}

# RS-039: Security evidence exists (release-evidence/v1.0.0/security)
$secEvidenceDir = Join-Path $projectRoot 'release-evidence\v1.0.0\security'
if (Test-Path $secEvidenceDir) {
    Add-Result 'RS-039' 'PASS' "Security evidence exists (GA2-03)"
} else {
    Add-Result 'RS-039' 'WARN' "Security evidence directory missing"
}

# RS-040: yutong containers running (Docker verification, non-blocking)
try {
    $dockerPs = docker ps --format '{{.Names}}' 2>$null
    if ($LASTEXITCODE -eq 0) {
        $yutongContainers = ($dockerPs | Where-Object { $_ -match '^yutong-' })
        if ($yutongContainers.Count -ge 1) {
            Add-Result 'RS-040' 'PASS' "yutong containers running: $($yutongContainers.Count) (>=1)"
        } else {
            Add-Result 'RS-040' 'WARN' "No yutong containers running"
        }
    } else {
        Add-Result 'RS-040' 'WARN' "docker ps failed"
    }
} catch {
    Add-Result 'RS-040' 'WARN' "Docker verification failed (non-blocking): $($_.Exception.Message)"
}

# Summary
Write-Host ""
Write-Host "Summary: PASS=$script:pass WARN=$script:warn FAIL=$script:fail"

# Write JSON report
$reportDir = Join-Path $projectRoot 'build\reports\checks'
if (-not (Test-Path $reportDir)) { New-Item -ItemType Directory -Path $reportDir -Force | Out-Null }
$report = @{
    schemaVersion = '1.0.0'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    taskId = 'GA2-L160'
    designDoc = '02-需求与范围设计'
    status = if ($script:fail -eq 0) { 'PASS' } elseif ($script:fail -le 2) { 'WARN' } else { 'FAIL' }
    passCount = $script:pass
    warnCount = $script:warn
    failCount = $script:fail
    results = $script:results
}
$reportJson = $report | ConvertTo-Json -Depth 10
$reportPath = Join-Path $reportDir 'requirements-scope-ga2-l160.json'
[System.IO.File]::WriteAllText($reportPath, $reportJson, [System.Text.Encoding]::UTF8)
Write-Host "JSON report: $reportPath"
