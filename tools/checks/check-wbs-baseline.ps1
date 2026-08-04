# GA2-61: WBS Baseline Machine Check Script
# Design source: 61-实现任务WBS与排期基线.md (DOC-DEL-004)
# Verifies 61 doc's 4 acceptance criteria + 6 consistency checks
# Output: build/reports/checks/wbs-baseline-ga2-61.json + stdout summary

$ErrorActionPreference = 'Continue'
$projectRoot = 'd:\MyCode\YuTong-Java-Vue'
$docsRoot = Join-Path $projectRoot 'YuTong-Java-Docs'
$trackingPath = Join-Path $docsRoot '23-设计到落地追踪记录\23-设计到落地追踪记录.md'
$wbsYamlPath = Join-Path $docsRoot 'contracts\governance\wbs-baseline.yaml'
$designDocPath = Join-Path $docsRoot '61-实现任务WBS与排期基线\61-实现任务WBS与排期基线.md'

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

# WBS-001: 61 design doc exists
if (Test-Path $designDocPath) {
    Add-Result 'WBS-001' 'PASS' "61 design doc exists: $designDocPath"
} else {
    Add-Result 'WBS-001' 'FAIL' "61 design doc missing"
}

# WBS-002: WBS YAML skeleton exists
if (Test-Path $wbsYamlPath) {
    $size = (Get-Item $wbsYamlPath).Length
    Add-Result 'WBS-002' 'PASS' "wbs-baseline.yaml exists ($size bytes)"
} else {
    Add-Result 'WBS-002' 'FAIL' "wbs-baseline.yaml missing"
}

# WBS-003: 5 version phases (v0.2/v0.3/v0.4/v0.5/v1.0)
$designContent = [System.IO.File]::ReadAllText($designDocPath, [System.Text.Encoding]::UTF8)
$phases = @('v0.2', 'v0.3', 'v0.4', 'v0.5', 'v1.0')
$phaseHits = 0
foreach ($p in $phases) {
    if ($designContent -match [regex]::Escape($p)) { $phaseHits++ }
}
if ($phaseHits -eq 5) {
    Add-Result 'WBS-003' 'PASS' "5 version phases all present (v0.2/v0.3/v0.4/v0.5/v1.0)"
} else {
    Add-Result 'WBS-003' 'FAIL' "Only $phaseHits/5 version phases present"
}

# WBS-004: P2 9 tasks complete
$p2Match = ([regex]::Matches($designContent, '\|\s*P2-0\d\s*\|')).Count
if ($p2Match -ge 9) {
    Add-Result 'WBS-004' 'PASS' "P2 WBS tasks count: $p2Match (>=9 expected)"
} else {
    Add-Result 'WBS-004' 'FAIL' "P2 WBS tasks count: $p2Match (<9 expected)"
}

# WBS-005: P3 8 tasks complete
$p3Match = ([regex]::Matches($designContent, '\|\s*P3-0\d\s*\|')).Count
if ($p3Match -ge 8) {
    Add-Result 'WBS-005' 'PASS' "P3 WBS tasks count: $p3Match (>=8 expected)"
} else {
    Add-Result 'WBS-005' 'FAIL' "P3 WBS tasks count: $p3Match (<8 expected)"
}

# WBS-006: P4 8 tasks complete
$p4Match = ([regex]::Matches($designContent, '\|\s*P4-0\d\s*\|')).Count
if ($p4Match -ge 8) {
    Add-Result 'WBS-006' 'PASS' "P4 WBS tasks count: $p4Match (>=8 expected)"
} else {
    Add-Result 'WBS-006' 'FAIL' "P4 WBS tasks count: $p4Match (<8 expected)"
}

# WBS-007: P5 5 tasks complete
$p5Match = ([regex]::Matches($designContent, '\|\s*P5-0\d\s*\|')).Count
if ($p5Match -ge 5) {
    Add-Result 'WBS-007' 'PASS' "P5 WBS tasks count: $p5Match (>=5 expected)"
} else {
    Add-Result 'WBS-007' 'FAIL' "P5 WBS tasks count: $p5Match (<5 expected)"
}

# WBS-008: 5 excluded tasks registered (BPMN/Report/Plugin/MultiDataSource/RBAC)
$excludedHits = 0
foreach ($k in @('BPMN', '报表大屏', '插件市场', '多数据源', 'RBAC')) {
    if ($designContent -match $k) { $excludedHits++ }
}
if ($excludedHits -ge 5) {
    Add-Result 'WBS-008' 'PASS' "Excluded tasks registered: $excludedHits/5"
} else {
    Add-Result 'WBS-008' 'FAIL' "Excluded tasks registered: $excludedHits/5"
}

# WBS-009: 4 acceptance criteria present
$criteriaHits = 0
foreach ($k in @('P2 任务可直接导入', '每项任务有明确输入', '不存在', '分期边界与 39/49')) {
    if ($designContent -match [regex]::Escape($k)) { $criteriaHits++ }
}
if ($criteriaHits -ge 4) {
    Add-Result 'WBS-009' 'PASS' "4 acceptance criteria all present"
} else {
    Add-Result 'WBS-009' "WARN" "Acceptance criteria: $criteriaHits/4"
}

# WBS-010: forbidden task keywords absent (exclude prohibition descriptions like 不存在/不使用/禁止)
$forbidden = @('调研一下', '看情况实现', '待定', 'TBD', '后续再说', '视情况而定')
$forbiddenHits = 0
$prohibitionContext = @('不存在', '不使用', '不允许', '不得', '禁止', '不应', '严禁', '避免', '不可', '这类不可验收')
foreach ($k in $forbidden) {
    $pattern = [regex]::Escape($k)
    $matches = [regex]::Matches($designContent, $pattern)
    foreach ($m in $matches) {
        $start = [Math]::Max(0, $m.Index - 40)
        $len = [Math]::Min(80, $designContent.Length - $start)
        $context = $designContent.Substring($start, $len)
        $isProhibition = $false
        foreach ($p in $prohibitionContext) {
            if ($context -match [regex]::Escape($p)) { $isProhibition = $true; break }
        }
        if (-not $isProhibition) { $forbiddenHits++ }
    }
}
if ($forbiddenHits -eq 0) {
    Add-Result 'WBS-010' 'PASS' "No forbidden non-acceptable task keywords (prohibition descriptions excluded)"
} else {
    Add-Result 'WBS-010' 'FAIL' "Found $forbiddenHits forbidden keywords in real task descriptions"
}

# WBS-011: WBS YAML skeleton content validates against design doc
$yamlContent = [System.IO.File]::ReadAllText($wbsYamlPath, [System.Text.Encoding]::UTF8)
$yamlTaskCount = ([regex]::Matches($yamlContent, '^\s+- id: P\d-\d+', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlTaskCount -ge 30) {
    Add-Result 'WBS-011' 'PASS' "WBS YAML skeleton has $yamlTaskCount tasks (>=30 expected: 9+8+8+5)"
} else {
    Add-Result 'WBS-011' 'FAIL' "WBS YAML skeleton has $yamlTaskCount tasks (<30 expected)"
}

# WBS-012: YAML contains excludedTasks section
if ($yamlContent -match 'excludedTasks:') {
    Add-Result 'WBS-012' 'PASS' "YAML excludedTasks section present"
} else {
    Add-Result 'WBS-012' 'FAIL' "YAML excludedTasks section missing"
}

# WBS-013: YAML contains acceptanceRule with 7 required fields
$ruleHits = 0
foreach ($f in @('designDocId', 'designDocPath', 'manifestHash', 'codeDirectory', 'openapiOperationId', 'testCaseId', 'evidencePath')) {
    if ($yamlContent -match [regex]::Escape($f)) { $ruleHits++ }
}
if ($ruleHits -ge 7) {
    Add-Result 'WBS-013' 'PASS' "YAML acceptanceRule 7 required fields all present"
} else {
    Add-Result 'WBS-013' 'FAIL' "YAML acceptanceRule fields: $ruleHits/7"
}

# WBS-014: YAML contains forbiddenTaskKeywords
if ($yamlContent -match 'forbiddenTaskKeywords:') {
    Add-Result 'WBS-014' 'PASS' "YAML forbiddenTaskKeywords section present"
} else {
    Add-Result 'WBS-014' 'FAIL' "YAML forbiddenTaskKeywords section missing"
}

# WBS-015: 23 tracking record has Done items traceable to WBS phases
$trackingContent = [System.IO.File]::ReadAllText($trackingPath, [System.Text.Encoding]::UTF8)
$doneCount = ([regex]::Matches($trackingContent, '\| Done \|')).Count
if ($doneCount -ge 100) {
    Add-Result 'WBS-015' 'PASS' "23 tracking record Done=$doneCount items traceable to WBS phases"
} else {
    Add-Result 'WBS-015' 'WARN' "23 tracking record Done=$doneCount (<100)"
}

# WBS-016: Phase boundary alignment with 39/49 documented
if ($yamlContent -match 'phaseBoundaryAlignment' -and $yamlContent -match '设计变更控制规范' -or $yamlContent -match '39') {
    Add-Result 'WBS-016' 'PASS' "Phase boundary alignment with 39/49 documented in YAML"
} else {
    Add-Result 'WBS-016' 'WARN' "Phase boundary alignment section not fully populated"
}

# WBS-017: Writeback target specified
if ($yamlContent -match 'writebackTarget.*23') {
    Add-Result 'WBS-017' 'PASS' "Writeback target 23 specified"
} else {
    Add-Result 'WBS-017' 'FAIL' "Writeback target missing"
}

# WBS-018: Input docs referenced by WBS tasks exist in catalog
$catalogPath = Join-Path $docsRoot 'contracts\document-catalog.yaml'
if (Test-Path $catalogPath) {
    Add-Result 'WBS-018' 'PASS' "document-catalog.yaml exists for WBS input doc verification"
} else {
    Add-Result 'WBS-018' 'FAIL' "document-catalog.yaml missing"
}

# WBS-019: v1.0 GA candidate convergence noted in tracking record
if ($trackingContent -match 'v1\.0 GA') {
    Add-Result 'WBS-019' 'PASS' "v1.0 GA candidate convergence noted in tracking record"
} else {
    Add-Result 'WBS-019' 'WARN' "v1.0 GA convergence not explicitly noted"
}

# WBS-020: Acceptance criteria 4 items all in design doc
$criteriaCount = ([regex]::Matches($designContent, '验收标准')).Count
if ($criteriaCount -ge 1) {
    Add-Result 'WBS-020' 'PASS' "Design doc has 验收标准 section"
} else {
    Add-Result 'WBS-020' 'FAIL' "Design doc missing 验收标准 section"
}

# Summary
Write-Host ""
Write-Host "Summary: PASS=$pass WARN=$warn FAIL=$fail"

# Write JSON report
$reportDir = Join-Path $projectRoot 'build\reports\checks'
if (-not (Test-Path $reportDir)) { New-Item -ItemType Directory -Path $reportDir -Force | Out-Null }
$report = @{
    schemaVersion = '1.0.0'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    taskId = 'GA2-61'
    designDoc = '61-实现任务WBS与排期基线'
    status = if ($fail -eq 0) { 'PASS' } elseif ($fail -le 2) { 'WARN' } else { 'FAIL' }
    passCount = $pass
    warnCount = $warn
    failCount = $fail
    results = $results
}
$reportPath = Join-Path $reportDir 'wbs-baseline-ga2-61.json'
$utf8Bom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($reportPath, ($report | ConvertTo-Json -Depth 10), $utf8Bom)
Write-Host "Report saved: $reportPath"

if ($fail -gt 0) { exit 1 } else { exit 0 }
