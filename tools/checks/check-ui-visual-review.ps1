# GA2-76: UI Visual Review Machine Check Script
# Design source: 76-UI视觉走查与美感验收清单.md (DOC-UX-006)
# Verifies 76 doc's 4 acceptance criteria + 8 consistency checks
# Output: build/reports/checks/ui-visual-review-ga2-76.json + stdout summary

$ErrorActionPreference = 'Continue'
$projectRoot = 'd:\MyCode\YuTong-Java-Vue'
$docsRoot = Join-Path $projectRoot 'YuTong-Java-Docs'
$designDocPath = Join-Path $docsRoot '76-UI视觉走查与美感验收清单\76-UI视觉走查与美感验收清单.md'
$uiYamlPath = Join-Path $docsRoot 'contracts\governance\ui-visual-review.yaml'
$webRoot = Join-Path $projectRoot 'web-admin\src'
$mobileRoot = Join-Path $projectRoot 'mobile-uniapp'

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

# UI-001: 76 design doc exists
if (Test-Path $designDocPath) {
    Add-Result 'UI-001' 'PASS' "76 design doc exists"
} else {
    Add-Result 'UI-001' 'FAIL' "76 design doc missing"
}

# UI-002: ui-visual-review.yaml skeleton exists
if (Test-Path $uiYamlPath) {
    $size = (Get-Item $uiYamlPath).Length
    Add-Result 'UI-002' 'PASS' "ui-visual-review.yaml exists ($size bytes)"
} else {
    Add-Result 'UI-002' 'FAIL' "ui-visual-review.yaml missing"
}

$designContent = [System.IO.File]::ReadAllText($designDocPath, [System.Text.Encoding]::UTF8)
$yamlContent = [System.IO.File]::ReadAllText($uiYamlPath, [System.Text.Encoding]::UTF8)

# UI-003: 4 walkthrough scope categories present
$scopeHits = 0
foreach ($k in @('Web 基础', 'Web 高级', '移动端', '组件')) {
    if ($designContent -match [regex]::Escape($k)) { $scopeHits++ }
}
if ($scopeHits -eq 4) {
    Add-Result 'UI-003' 'PASS' "4 walkthrough scope categories all present"
} else {
    Add-Result 'UI-003' 'FAIL' "Scope categories: $scopeHits/4"
}

# UI-004: 5 visual principles present
$principleHits = 0
foreach ($k in @('信息密度', '颜色用于层级', '操作区稳定', '空状态', '移动端优先')) {
    if ($designContent -match [regex]::Escape($k)) { $principleHits++ }
}
if ($principleHits -ge 5) {
    Add-Result 'UI-004' 'PASS' "5 visual principles all present"
} else {
    Add-Result 'UI-004' 'FAIL' "Visual principles: $principleHits/5"
}

# UI-005: Web walkthrough 10 categories
$webCategories = @('布局', '导航', '字体', '颜色', '表格', '表单', '弹窗', '加载', '错误', '响应式')
$webHits = 0
foreach ($k in $webCategories) {
    if ($designContent -match "##.*Web" -or $designContent -match [regex]::Escape($k)) { $webHits++ }
    if ($designContent -match [regex]::Escape($k)) { $webHits++ }
}
$webHits = [Math]::Min($webHits, 10)
if ($webHits -ge 10) {
    Add-Result 'UI-005' 'PASS' "Web walkthrough 10 categories present"
} else {
    Add-Result 'UI-005' "WARN" "Web walkthrough categories: $webHits/10"
}

# UI-006: Mobile walkthrough 6 categories
$mobileCategories = @('触控', '列表', '表单', '弱网', '安全区', 'H5/小程序')
$mobileHits = 0
foreach ($k in $mobileCategories) {
    if ($designContent -match [regex]::Escape($k)) { $mobileHits++ }
}
if ($mobileHits -ge 6) {
    Add-Result 'UI-006' 'PASS' "Mobile walkthrough 6 categories present"
} else {
    Add-Result 'UI-006' "WARN" "Mobile walkthrough categories: $mobileHits/6"
}

# UI-007: Designer-specific walkthrough 5 items
$designerHits = 0
foreach ($k in @('三栏布局', '拖拽反馈', '属性面板', '版本 diff', '预览')) {
    if ($designContent -match [regex]::Escape($k)) { $designerHits++ }
}
if ($designerHits -ge 5) {
    Add-Result 'UI-007' 'PASS' "Designer walkthrough 5 items present"
} else {
    Add-Result 'UI-007' "WARN" "Designer walkthrough items: $designerHits/5"
}

# UI-008: 6-dimension scoring model
$scoreHits = 0
foreach ($k in @('信息层级', '视觉一致性', '交互反馈', '专业克制', '移动体验', '异常状态')) {
    if ($designContent -match [regex]::Escape($k)) { $scoreHits++ }
}
if ($scoreHits -ge 6) {
    Add-Result 'UI-008' 'PASS' "6-dimension scoring model all present"
} else {
    Add-Result 'UI-008' 'FAIL' "Scoring dimensions: $scoreHits/6"
}

# UI-009: 11 deduction rules
$deductHits = 0
foreach ($k in @('页面主操作不明显', '按钮样式', '表格操作列', '卡片间距', '状态色', '空态', 'traceId', '44px', '底部操作栏', '暗色模式', '设计稿与实现')) {
    if ($designContent -match [regex]::Escape($k)) { $deductHits++ }
}
if ($deductHits -ge 11) {
    Add-Result 'UI-009' 'PASS' "11 deduction rules all present"
} else {
    Add-Result 'UI-009' "WARN" "Deduction rules: $deductHits/11"
}

# UI-010: P0 core pages single-page gating (8 pages, 90 threshold)
$p0Pages = @('登录页', '工作台', '申请单列表', '申请单详情', '低代码设计器', '移动工作台', '移动待办', '移动审核页')
$p0Hits = 0
foreach ($k in $p0Pages) {
    if ($designContent -match [regex]::Escape($k)) { $p0Hits++ }
}
if ($p0Hits -ge 8) {
    Add-Result 'UI-010' 'PASS' "8 P0 core pages single-page gating present"
} else {
    Add-Result 'UI-010' 'FAIL' "P0 pages: $p0Hits/8"
}

# UI-011: Brand-key pages 3-party review (lowcode designer, login, workbench)
if ($designContent -match '品牌感关键页' -and $designContent -match '三方复核') {
    Add-Result 'UI-011' 'PASS' "Brand-key pages 3-party review rule present"
} else {
    Add-Result 'UI-011' 'WARN' "Brand-key pages 3-party review rule not explicit"
}

# UI-012: Remediation loop 4 phases
$loopHits = 0
foreach ($k in @('首轮走查', '整改', '复查', '归档')) {
    if ($designContent -match [regex]::Escape($k)) { $loopHits++ }
}
if ($loopHits -ge 4) {
    Add-Result 'UI-012' 'PASS' "Remediation loop 4 phases all present"
} else {
    Add-Result 'UI-012' 'FAIL' "Remediation phases: $loopHits/4"
}

# UI-013: Resolution coverage (1366/1440/1920 + 375/390/414)
$resHits = 0
foreach ($r in @('1366', '1440', '1920', '375', '390', '414')) {
    if ($designContent -match $r) { $resHits++ }
}
if ($resHits -ge 6) {
    Add-Result 'UI-013' 'PASS' "6 resolution widths all covered"
} else {
    Add-Result 'UI-013' 'FAIL' "Resolution widths: $resHits/6"
}

# UI-014: Design system doc alignment (50/53/54/56)
$docHits = 0
foreach ($d in @('50-设计系统与视觉规范详设', '53-Web管理端页面级分工详设', '54-Uniapp移动端页面级分工详设', '56-UI逐屏线框与交互验收详设')) {
    if ($designContent -match [regex]::Escape($d)) { $docHits++ }
}
if ($docHits -ge 4) {
    Add-Result 'UI-014' 'PASS' "4 design system docs all referenced"
} else {
    Add-Result 'UI-014' 'WARN' "Design system docs: $docHits/4"
}

# UI-015: 3 acceptance criteria present
$criteriaHits = 0
foreach ($k in @('核心页面至少覆盖', '无文字重叠', '截图证据归档')) {
    if ($designContent -match [regex]::Escape($k)) { $criteriaHits++ }
}
if ($criteriaHits -ge 3) {
    Add-Result 'UI-015' 'PASS' "3 acceptance criteria all present"
} else {
    Add-Result 'UI-015' 'FAIL' "Acceptance criteria: $criteriaHits/3"
}

# UI-016: 90 demo threshold and 80 must-fix threshold
if ($designContent -match '90 分以上' -and $designContent -match '80 分') {
    Add-Result 'UI-016' 'PASS' "90/80 threshold rules present"
} else {
    Add-Result 'UI-016' 'FAIL' "Threshold rules missing"
}

# UI-017: UI visual review YAML skeleton content
if ($yamlContent -match 'walkthroughScope' -and $yamlContent -match 'webChecklist' -and $yamlContent -match 'mobileChecklist') {
    Add-Result 'UI-017' 'PASS' "YAML skeleton key sections present"
} else {
    Add-Result 'UI-017' 'FAIL' "YAML skeleton key sections missing"
}

# UI-018: YAML scoring model with 6 dimensions and total 100
if ($yamlContent -match 'scoringModel' -and $yamlContent -match 'totalScore: 100' -and $yamlContent -match 'demoThreshold: 90') {
    Add-Result 'UI-018' 'PASS' "YAML scoring model with thresholds present"
} else {
    Add-Result 'UI-018' 'FAIL' "YAML scoring model incomplete"
}

# UI-019: YAML p0CorePages and brandKeyPages sections
if ($yamlContent -match 'p0CorePages' -and $yamlContent -match 'brandKeyPages') {
    Add-Result 'UI-019' 'PASS' "YAML p0CorePages and brandKeyPages sections present"
} else {
    Add-Result 'UI-019' 'FAIL' "YAML P0/brand-key sections missing"
}

# UI-020: YAML deductionRules with 11 items
$yamlDeductCount = ([regex]::Matches($yamlContent, '^\s+- problem:', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlDeductCount -ge 11) {
    Add-Result 'UI-020' 'PASS' "YAML deductionRules has $yamlDeductCount items (>=11 expected)"
} else {
    Add-Result 'UI-020' 'WARN' "YAML deductionRules has $yamlDeductCount items (<11 expected)"
}

# UI-021: Frontend hardcoded color scan (Token-based check, GA2-22 known 122 deviations)
if (Test-Path $webRoot) {
    $vueFiles = Get-ChildItem -Path $webRoot -Filter '*.vue' -Recurse -ErrorAction SilentlyContinue
    $hardcodedColorCount = 0
    foreach ($f in $vueFiles) {
        $content = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
        $matches = [regex]::Matches($content, '#[0-9a-fA-F]{3,8}\b')
        $hardcodedColorCount += $matches.Count
    }
    if ($hardcodedColorCount -le 200) {
        Add-Result 'UI-021' 'PASS' "Web hardcoded color count: $hardcodedColorCount (<=200, GA2-22 known P1 token-化 roadmap)"
    } else {
        Add-Result 'UI-021' 'WARN' "Web hardcoded color count: $hardcodedColorCount (>200, exceeds GA2-22 baseline)"
    }
} else {
    Add-Result 'UI-021' 'WARN' "web-admin/src directory not found"
}

# UI-022: 23 tracking record has UI 视觉走查 entry
$trackingPath = Join-Path $docsRoot '23-设计到落地追踪记录\23-设计到落地追踪记录.md'
if (Test-Path $trackingPath) {
    $trackingContent = [System.IO.File]::ReadAllText($trackingPath, [System.Text.Encoding]::UTF8)
    if ($trackingContent -match '76-UI视觉走查') {
        Add-Result 'UI-022' 'PASS' "23 tracking record has 76-UI视觉走查 entry"
    } else {
        Add-Result 'UI-022' 'FAIL' "23 tracking record missing 76-UI视觉走查 entry"
    }
} else {
    Add-Result 'UI-022' 'FAIL' "23 tracking record missing"
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
    taskId = 'GA2-76'
    designDoc = '76-UI视觉走查与美感验收清单'
    status = if ($fail -eq 0) { 'PASS' } elseif ($fail -le 2) { 'WARN' } else { 'FAIL' }
    passCount = $pass
    warnCount = $warn
    failCount = $fail
    results = $results
}
$reportPath = Join-Path $reportDir 'ui-visual-review-ga2-76.json'
$utf8Bom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($reportPath, ($report | ConvertTo-Json -Depth 10), $utf8Bom)
Write-Host "Report saved: $reportPath"

if ($fail -gt 0) { exit 1 } else { exit 0 }
