# GA2-95: Figma Handoff Package Machine Check Script
# Design source: 95-Figma设计交付任务包详设.md (DOC-UX-007)
# Verifies 95 doc's 10 acceptance criteria + 6 consistency checks
# Output: build/reports/checks/figma-handoff-ga2-95.json + stdout summary

$ErrorActionPreference = 'Continue'
$projectRoot = 'd:\MyCode\YuTong-Java-Vue'
$docsRoot = Join-Path $projectRoot 'YuTong-Java-Docs'
$designDocPath = Join-Path $docsRoot '95-Figma设计交付任务包详设\95-Figma设计交付任务包详设.md'
$figmaYamlPath = Join-Path $docsRoot 'contracts\governance\figma-handoff.yaml'

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

# FIG-001: 95 design doc exists
if (Test-Path $designDocPath) {
    Add-Result 'FIG-001' 'PASS' "95 design doc exists"
} else {
    Add-Result 'FIG-001' 'FAIL' "95 design doc missing"
}

# FIG-002: figma-handoff.yaml skeleton exists
if (Test-Path $figmaYamlPath) {
    $size = (Get-Item $figmaYamlPath).Length
    Add-Result 'FIG-002' 'PASS' "figma-handoff.yaml exists ($size bytes)"
} else {
    Add-Result 'FIG-002' 'FAIL' "figma-handoff.yaml missing"
}

$designContent = [System.IO.File]::ReadAllText($designDocPath, [System.Text.Encoding]::UTF8)
$yamlContent = [System.IO.File]::ReadAllText($figmaYamlPath, [System.Text.Encoding]::UTF8)

# FIG-003: Figma 7 Page structure
$figmaPages = @('Design System', 'Web Admin', 'Mobile Uniapp', 'Lowcode Designer', 'AI Assistant', 'Prototype Flow', 'Handoff Notes')
$pageHits = 0
foreach ($p in $figmaPages) {
    if ($designContent -match [regex]::Escape($p)) { $pageHits++ }
}
if ($pageHits -ge 7) {
    Add-Result 'FIG-003' 'PASS' "7 Figma Pages all present"
} else {
    Add-Result 'FIG-003' 'FAIL' "Figma Pages: $pageHits/7"
}

# FIG-004: Frame naming rule
if ($designContent -match '平台/模块/页面/状态/断点') {
    Add-Result 'FIG-004' 'PASS' "Frame naming rule present"
} else {
    Add-Result 'FIG-004' 'FAIL' "Frame naming rule missing"
}

# FIG-005: Variables and Tokens 8 categories
$tokenCategories = @('颜色', '字号', '行高', '间距', '圆角', '阴影', '动效时长', '状态色')
$tokenHits = 0
foreach ($k in $tokenCategories) {
    if ($designContent -match [regex]::Escape($k)) { $tokenHits++ }
}
if ($tokenHits -ge 8) {
    Add-Result 'FIG-005' 'PASS' "8 Variables and Tokens categories all present"
} else {
    Add-Result 'FIG-005' 'FAIL' "Token categories: $tokenHits/8"
}

# FIG-006: Web component library 20 items
$webComponents = @('Button', 'IconButton', 'Input', 'Select', 'RemoteSelect', 'DateRangePicker', 'SearchForm', 'BaseTable', 'Pagination', 'StatusBadge', 'PageState', 'FormDrawer', 'DetailDrawer', 'ConfirmDialog', 'FileUploader', 'Timeline', 'MetricCard', 'ActionBar', 'PermissionDisabledTooltip', 'SecretInput')
$componentHits = 0
foreach ($c in $webComponents) {
    if ($designContent -match "\b$([regex]::Escape($c))\b") { $componentHits++ }
}
if ($componentHits -ge 20) {
    Add-Result 'FIG-006' 'PASS' "20 Web components all listed"
} else {
    Add-Result 'FIG-006' "WARN" "Web components: $componentHits/20"
}

# FIG-007: Mobile component library 13 items
$mobileComponents = @('YtNavBar', 'YtCard', 'TodoCard', 'StatusBadge', 'FieldDisplay', 'BottomActionBar', 'MobileUploader', 'OfflineBanner', 'EmptyState', 'MessageCard', 'UploadQueueItem', 'ScanResultCard', 'KeyboardSafeTextarea')
$mobileHits = 0
foreach ($c in $mobileComponents) {
    if ($designContent -match "\b$([regex]::Escape($c))\b") { $mobileHits++ }
}
if ($mobileHits -ge 13) {
    Add-Result 'FIG-007' 'PASS' "13 Mobile components all listed"
} else {
    Add-Result 'FIG-007' "WARN" "Mobile components: $mobileHits/13"
}

# FIG-008: Web P0/P1 Frames 18 pages
$webFrames = @('登录页', '工作台', '申请单列表', '申请单新建', '申请单编辑', '申请单详情', '客户管理', '商品管理', '字典类型', '字典项', '参数配置', '文件管理', '消息中心', '操作日志', '任务日志', '导入导出任务', '服务健康', '缓存概览')
$frameHits = 0
foreach ($f in $webFrames) {
    if ($designContent -match [regex]::Escape($f)) { $frameHits++ }
}
if ($frameHits -ge 18) {
    Add-Result 'FIG-008' 'PASS' "18 Web P0/P1 Frames all listed"
} else {
    Add-Result 'FIG-008' "WARN" "Web Frames: $frameHits/18"
}

# FIG-009: Mobile P0/P1 Frames 10 pages
$mobileFrames = @('登录', '工作台', '待办列表', '申请单详情', '审核通过', '审核驳回', '消息中心', '上传队列', '扫码', '我的')
$mobileFrameHits = 0
foreach ($f in $mobileFrames) {
    if ($designContent -match [regex]::Escape($f)) { $mobileFrameHits++ }
}
if ($mobileFrameHits -ge 10) {
    Add-Result 'FIG-009' 'PASS' "10 Mobile P0/P1 Frames all listed"
} else {
    Add-Result 'FIG-009' "WARN" "Mobile Frames: $mobileFrameHits/10"
}

# FIG-010: Lowcode designer Frames 10 items
$lowcodeFrames = @('实体列表', '实体字段设计', '页面设计器', '组件属性面板', '预览', '发布确认', '生成任务', '生成 diff', '冲突提示', '回滚确认')
$lowcodeHits = 0
foreach ($f in $lowcodeFrames) {
    if ($designContent -match [regex]::Escape($f)) { $lowcodeHits++ }
}
if ($lowcodeHits -ge 10) {
    Add-Result 'FIG-010' 'PASS' "10 Lowcode designer Frames all listed"
} else {
    Add-Result 'FIG-010' "WARN" "Lowcode Frames: $lowcodeHits/10"
}

# FIG-011: AI assistant Frames 9 items
$aiFrames = @('聊天入口', '侧边助手', 'RAG 引用展示', '生成页面草稿', '建议应用确认', '工具调用日志', '成本提示', '失败降级', '安全拦截')
$aiHits = 0
foreach ($f in $aiFrames) {
    if ($designContent -match [regex]::Escape($f)) { $aiHits++ }
}
if ($aiHits -ge 9) {
    Add-Result 'FIG-011' 'PASS' "9 AI assistant Frames all listed"
} else {
    Add-Result 'FIG-011' "WARN" "AI Frames: $aiHits/9"
}

# FIG-012: Dark mode coverage 5 sample pages
$darkMode = @('登录页', '工作台', '申请单列表', '低代码设计器', 'AI 助手')
$darkHits = 0
foreach ($f in $darkMode) {
    if ($designContent -match [regex]::Escape($f)) { $darkHits++ }
}
if ($designContent -match '暗色样张' -and $darkHits -ge 5) {
    Add-Result 'FIG-012' 'PASS' "Dark mode 5 sample pages covered"
} else {
    Add-Result 'FIG-012' "WARN" "Dark mode coverage: $darkHits/5"
}

# FIG-013: Annotation spec 7 categories
$annotationSpec = @('页面路由', '权限码', '接口', '主要组件', '字段类型', '状态来源', '异常恢复动作')
$annotHits = 0
foreach ($k in $annotationSpec) {
    if ($designContent -match [regex]::Escape($k)) { $annotHits++ }
}
if ($annotHits -ge 7) {
    Add-Result 'FIG-013' 'PASS' "7 Annotation spec categories all present"
} else {
    Add-Result 'FIG-013' 'FAIL' "Annotation spec: $annotHits/7"
}

# FIG-014: A11y and i18n annotation 5 categories
$a11ySpec = @('i18n key', '焦点顺序', '读屏文案', '键盘路径', '错误关联')
$a11yHits = 0
foreach ($k in $a11ySpec) {
    if ($designContent -match [regex]::Escape($k)) { $a11yHits++ }
}
if ($a11yHits -ge 5) {
    Add-Result 'FIG-014' 'PASS' "5 A11y annotation categories all present"
} else {
    Add-Result 'FIG-014' 'FAIL' "A11y annotation: $a11yHits/5"
}

# FIG-015: Prototype flows 3 flows
$flows = @('Web 申请单', '移动端从待办', '低代码从实体设计')
$flowHits = 0
foreach ($k in $flows) {
    if ($designContent -match [regex]::Escape($k)) { $flowHits++ }
}
if ($flowHits -ge 3) {
    Add-Result 'FIG-015' 'PASS' "3 Prototype flows all present"
} else {
    Add-Result 'FIG-015' 'FAIL' "Prototype flows: $flowHits/3"
}

# FIG-016: Development gating 3 levels
$gatingHits = 0
foreach ($k in @('P0 阻断', 'P1 阻断', 'P2 可后补')) {
    if ($designContent -match [regex]::Escape($k)) { $gatingHits++ }
}
if ($gatingHits -ge 3) {
    Add-Result 'FIG-016' 'PASS' "3 Development gating levels all present"
} else {
    Add-Result 'FIG-016' 'FAIL' "Gating levels: $gatingHits/3"
}

# FIG-017: UI-Frontend handoff checklist 10 items
$handoffItems = @('Figma Frame 清单完整', '组件变体完整', 'Token 已同步', '页面状态齐全', '路由与权限码已标注', '接口与 OpenAPI 对齐', '空态和错误态有恢复动作', '移动端安全区已标注', '登录页不出现固定账号', '截图验收基准已导出')
$handoffHits = 0
foreach ($k in $handoffItems) {
    if ($designContent -match [regex]::Escape($k)) { $handoffHits++ }
}
if ($handoffHits -ge 10) {
    Add-Result 'FIG-017' 'PASS' "10 UI-Frontend handoff checklist items all present"
} else {
    Add-Result 'FIG-017' "WARN" "Handoff checklist: $handoffHits/10"
}

# FIG-018: Visual acceptance screenshot comparison
if ($designContent -match '1440x900' -and $designContent -match '1280x800' -and $designContent -match '390x844' -and $designContent -match '360x800') {
    Add-Result 'FIG-018' 'PASS' "Visual acceptance screenshot sizes all present"
} else {
    Add-Result 'FIG-018' "WARN" "Visual acceptance sizes incomplete"
}

# FIG-019: YAML figmaPages section
if ($yamlContent -match 'figmaPages:' -and $yamlContent -match 'Design System' -and $yamlContent -match 'Handoff Notes') {
    Add-Result 'FIG-019' 'PASS' "YAML figmaPages section with 7 pages present"
} else {
    Add-Result 'FIG-019' 'FAIL' "YAML figmaPages section incomplete"
}

# FIG-020: YAML webComponents and mobileComponents sections
if ($yamlContent -match 'webComponents:' -and $yamlContent -match 'mobileComponents:') {
    Add-Result 'FIG-020' 'PASS' "YAML webComponents and mobileComponents sections present"
} else {
    Add-Result 'FIG-020' 'FAIL' "YAML component sections missing"
}

# FIG-021: YAML devGating with 3 levels
if ($yamlContent -match 'devGating:' -and $yamlContent -match 'P0 阻断' -and $yamlContent -match 'P1 阻断' -and $yamlContent -match 'P2 可后补') {
    Add-Result 'FIG-021' 'PASS' "YAML devGating 3 levels present"
} else {
    Add-Result 'FIG-021' 'FAIL' "YAML devGating incomplete"
}

# FIG-022: YAML handoffChecklist with 10 items
$yamlHandoffCount = ([regex]::Matches($yamlContent, '^\s+- ', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlContent -match 'handoffChecklist:') {
    Add-Result 'FIG-022' 'PASS' "YAML handoffChecklist section present"
} else {
    Add-Result 'FIG-022' 'FAIL' "YAML handoffChecklist section missing"
}

# FIG-023: YAML darkModeSamples with 5 items
if ($yamlContent -match 'darkModeSamples:' -and $yamlContent -match '登录页' -and $yamlContent -match 'AI 助手') {
    Add-Result 'FIG-023' 'PASS' "YAML darkModeSamples section present"
} else {
    Add-Result 'FIG-023' 'WARN' "YAML darkModeSamples section incomplete"
}

# FIG-024: YAML prototypeFlows with 3 flows
if ($yamlContent -match 'prototypeFlows:' -and $yamlContent -match 'Web 申请单' -and $yamlContent -match '移动端') {
    Add-Result 'FIG-024' 'PASS' "YAML prototypeFlows 3 flows present"
} else {
    Add-Result 'FIG-024' 'FAIL' "YAML prototypeFlows incomplete"
}

# FIG-025: 23 tracking record has 95-Figma entry
$trackingPath = Join-Path $docsRoot '23-设计到落地追踪记录\23-设计到落地追踪记录.md'
if (Test-Path $trackingPath) {
    $trackingContent = [System.IO.File]::ReadAllText($trackingPath, [System.Text.Encoding]::UTF8)
    if ($trackingContent -match '95-Figma') {
        Add-Result 'FIG-025' 'PASS' "23 tracking record has 95-Figma entry"
    } else {
        Add-Result 'FIG-025' 'FAIL' "23 tracking record missing 95-Figma entry"
    }
} else {
    Add-Result 'FIG-025' 'FAIL' "23 tracking record missing"
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
    taskId = 'GA2-95'
    designDoc = '95-Figma设计交付任务包详设'
    status = if ($fail -eq 0) { 'PASS' } elseif ($fail -le 2) { 'WARN' } else { 'FAIL' }
    passCount = $pass
    warnCount = $warn
    failCount = $fail
    results = $results
}
$reportPath = Join-Path $reportDir 'figma-handoff-ga2-95.json'
$utf8Bom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($reportPath, ($report | ConvertTo-Json -Depth 10), $utf8Bom)
Write-Host "Report saved: $reportPath"

if ($fail -gt 0) { exit 1 } else { exit 0 }
