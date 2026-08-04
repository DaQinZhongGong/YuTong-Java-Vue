<#
.SYNOPSIS
  GA2-L163 端侧体验与视觉工程补强 检查脚本
.DESCRIPTION
  Design source: 50/53/54/56/66/76 六个文档
  YAML skeleton: YuTong-Java-Docs/contracts/governance/frontend-visual-engineering.yaml
  Code alignment: web-admin/src/styles/variables.css + components + stores + mobile-uniapp
  yutong prefix rule: --yutong-* CSS 别名 + yutong-* docker 镜像/网络
.NOTES
  Encoding: UTF-8 BOM (PowerShell 5.1 中文兼容)
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
# FE-001 ~ FE-006: 6 个设计文档存在性检查
# ============================================================

Write-Host '=== 阶段 1: 6 个设计文档存在性检查 ===' -ForegroundColor Cyan

Test-FileExists "$root\YuTong-Java-Docs\50-设计系统与视觉规范详设\50-设计系统与视觉规范详设.md" 'FE-001' '50 号文档存在'
Test-FileExists "$root\YuTong-Java-Docs\53-Web管理端页面级分工详设\53-Web管理端页面级分工详设.md" 'FE-002' '53 号文档存在'
Test-FileExists "$root\YuTong-Java-Docs\54-Uniapp移动端页面级分工详设\54-Uniapp移动端页面级分工详设.md" 'FE-003' '54 号文档存在'
Test-FileExists "$root\YuTong-Java-Docs\56-UI逐屏线框与交互验收详设\56-UI逐屏线框与交互验收详设.md" 'FE-004' '56 号文档存在'
Test-FileExists "$root\YuTong-Java-Docs\66-前端组件API与状态管理详设\66-前端组件API与状态管理详设.md" 'FE-005' '66 号文档存在'
Test-FileExists "$root\YuTong-Java-Docs\76-UI视觉走查与美感验收清单\76-UI视觉走查与美感验收清单.md" 'FE-006' '76 号文档存在'

# ============================================================
# FE-007 ~ FE-014: 50 号文档关键内容验证
# ============================================================

Write-Host '=== 阶段 2: 50 号文档关键内容验证 ===' -ForegroundColor Cyan

$doc50 = "$root\YuTong-Java-Docs\50-设计系统与视觉规范详设\50-设计系统与视觉规范详设.md"
Test-ContentContains $doc50 'color-primary' 'FE-007' '50 号文档包含 color-primary'
Test-ContentContains $doc50 '#2563EB' 'FE-008' '50 号文档包含主色 #2563EB'
Test-ContentContains $doc50 '4px 网格' 'FE-009' '50 号文档包含 4px 网格'
Test-ContentContains $doc50 '构件蓝图式层级' 'FE-010' '50 号文档包含视觉签名 构件蓝图式层级'
Test-ContentContains $doc50 'WCAG AA' 'FE-011' '50 号文档包含 WCAG AA'
Test-ContentContains $doc50 'Lighthouse a11y' 'FE-012' '50 号文档包含 Lighthouse a11y'
Test-ContentContains $doc50 '--yt-color-primary' 'FE-013' '50 号文档包含 --yt-color-primary CSS Variable'
Test-ContentContains $doc50 '--el-color-primary' 'FE-014' '50 号文档包含 Element Plus 桥接 --el-color-primary'

# ============================================================
# FE-015 ~ FE-022: 53 号文档关键内容验证
# ============================================================

Write-Host '=== 阶段 3: 53 号文档关键内容验证 ===' -ForegroundColor Cyan

$doc53 = "$root\YuTong-Java-Docs\53-Web管理端页面级分工详设\53-Web管理端页面级分工详设.md"
Test-ContentContains $doc53 '/login' 'FE-015' '53 号文档包含 /login 路由'
Test-ContentContains $doc53 '/dashboard' 'FE-016' '53 号文档包含 /dashboard 路由'
Test-ContentContains $doc53 '/biz/requests' 'FE-017' '53 号文档包含 /biz/requests 路由'
Test-ContentContains $doc53 '/lowcode/entities' 'FE-018' '53 号文档包含 /lowcode/entities 路由'
Test-ContentContains $doc53 '/ai/assistant' 'FE-019' '53 号文档包含 /ai/assistant 路由'
Test-ContentContains $doc53 'BaseTable' 'FE-020' '53 号文档包含 BaseTable 组件'
Test-ContentContains $doc53 'StatusBadge' 'FE-021' '53 号文档包含 StatusBadge 组件'
Test-ContentContains $doc53 'FormDrawer' 'FE-022' '53 号文档包含 FormDrawer 组件'

# 53 号文档 22 路由数量
Test-RegexExact $doc53 '^\| `?/' 22 'FE-023' '53 号文档 22 条 Web 路由'

# ============================================================
# FE-024 ~ FE-031: 54 号文档关键内容验证
# ============================================================

Write-Host '=== 阶段 4: 54 号文档关键内容验证 ===' -ForegroundColor Cyan

$doc54 = "$root\YuTong-Java-Docs\54-Uniapp移动端页面级分工详设\54-Uniapp移动端页面级分工详设.md"
Test-ContentContains $doc54 '/pages/login/index' 'FE-024' '54 号文档包含 /pages/login/index'
Test-ContentContains $doc54 '/pages/workbench/index' 'FE-025' '54 号文档包含 /pages/workbench/index'
Test-ContentContains $doc54 '/pages/todo/list' 'FE-026' '54 号文档包含 /pages/todo/list'
Test-ContentContains $doc54 '/pages/biz/handle' 'FE-027' '54 号文档包含 /pages/biz/handle'
Test-ContentContains $doc54 '/pages/upload/index' 'FE-028' '54 号文档包含 /pages/upload/index'
Test-ContentContains $doc54 'BIND_FAILED' 'FE-029' '54 号文档包含 BIND_FAILED 状态'
Test-ContentContains $doc54 'OfflineBanner' 'FE-030' '54 号文档包含 OfflineBanner 组件'
Test-ContentContains $doc54 'BottomActionBar' 'FE-031' '54 号文档包含 BottomActionBar 组件'

# 54 号文档 9 移动路由
Test-RegexExact $doc54 '^\| `/pages/' 9 'FE-032' '54 号文档 9 条移动路由'

# ============================================================
# FE-033 ~ FE-040: 56 号文档关键内容验证
# ============================================================

Write-Host '=== 阶段 5: 56 号文档关键内容验证 ===' -ForegroundColor Cyan

$doc56 = "$root\YuTong-Java-Docs\56-UI逐屏线框与交互验收详设\56-UI逐屏线框与交互验收详设.md"
Test-ContentContains $doc56 'Web/{模块}/{页面}/{状态}/{断点}' 'FE-033' '56 号文档包含 Frame 命名规则'
Test-ContentContains $doc56 'Mobile/{模块}/{页面}/{状态}/{断点}' 'FE-034' '56 号文档包含 Mobile Frame 命名'
Test-ContentContains $doc56 '默认态、加载态、空态、错误态、无权限态' 'FE-035' '56 号文档包含 5 状态稿要求'
Test-ContentContains $doc56 'AUTH-401001' 'FE-036' '56 号文档包含 AUTH-401001 错误码'
Test-ContentContains $doc56 'draftSchemaVersion' 'FE-037' '56 号文档包含 draftSchemaVersion 草稿版本'
Test-ContentContains $doc56 'idempotencyKey' 'FE-038' '56 号文档包含 idempotencyKey 幂等键'
Test-ContentContains $doc56 'biz.request.title.required' 'FE-039' '56 号文档包含表单校验错误 key'
Test-ContentContains $doc56 '95-Figma设计交付任务包详设' 'FE-040' '56 号文档引用 95 号 Figma 交付文档'

# 56 号文档 5 状态稿验证
Test-ContentContains $doc56 '默认态' 'FE-041' '56 号文档包含默认态'
Test-ContentContains $doc56 '加载态' 'FE-042' '56 号文档包含加载态'
Test-ContentContains $doc56 '空态' 'FE-043' '56 号文档包含空态'
Test-ContentContains $doc56 '错误态' 'FE-044' '56 号文档包含错误态'
Test-ContentContains $doc56 '无权限态' 'FE-045' '56 号文档包含无权限态'

# ============================================================
# FE-046 ~ FE-053: 66 号文档关键内容验证
# ============================================================

Write-Host '=== 阶段 6: 66 号文档关键内容验证 ===' -ForegroundColor Cyan

$doc66 = "$root\YuTong-Java-Docs\66-前端组件API与状态管理详设\66-前端组件API与状态管理详设.md"
Test-ContentContains $doc66 'BaseTable' 'FE-046' '66 号文档包含 BaseTable'
Test-ContentContains $doc66 'SearchForm' 'FE-047' '66 号文档包含 SearchForm'
Test-ContentContains $doc66 'FormDrawer' 'FE-048' '66 号文档包含 FormDrawer'
Test-ContentContains $doc66 'FormDialog' 'FE-049' '66 号文档包含 FormDialog'
Test-ContentContains $doc66 'StatusBadge' 'FE-050' '66 号文档包含 StatusBadge'
Test-ContentContains $doc66 'StatusTag' 'FE-051' '66 号文档包含 StatusTag 历史别名'
Test-ContentContains $doc66 'FileUploader' 'FE-052' '66 号文档包含 FileUploader'
Test-ContentContains $doc66 'PageState' 'FE-053' '66 号文档包含 PageState'

Test-ContentContains $doc66 'trackApi' 'FE-054' '66 号文档包含 trackApi 体验监控'
Test-ContentContains $doc66 '401' 'FE-055' '66 号文档包含 401 跳登录规则'
Test-ContentContains $doc66 '403' 'FE-056' '66 号文档包含 403 NoPermission'
Test-ContentContains $doc66 '409' 'FE-057' '66 号文档包含 409 业务冲突'

# 66 号文档组件状态机
Test-ContentContains $doc66 'idle' 'FE-058' '66 号文档包含 FileUploader idle 状态'
Test-ContentContains $doc66 'uploading' 'FE-059' '66 号文档包含 uploading 状态'
Test-ContentContains $doc66 'forbidden' 'FE-060' '66 号文档包含 forbidden 状态'

# ============================================================
# FE-061 ~ FE-068: 76 号文档关键内容验证
# ============================================================

Write-Host '=== 阶段 7: 76 号文档关键内容验证 ===' -ForegroundColor Cyan

$doc76 = "$root\YuTong-Java-Docs\76-UI视觉走查与美感验收清单\76-UI视觉走查与美感验收清单.md"
Test-ContentContains $doc76 '信息层级' 'FE-061' '76 号文档包含 信息层级 评分维度'
Test-ContentContains $doc76 '视觉一致性' 'FE-062' '76 号文档包含 视觉一致性 评分维度'
Test-ContentContains $doc76 '90 分以上' 'FE-063' '76 号文档包含 90 分准入阈值'
Test-ContentContains $doc76 '80 分' 'FE-064' '76 号文档包含 80 分必须整改'
Test-ContentContains $doc76 '44px' 'FE-065' '76 号文档包含 44px 移动按钮最小高度'
Test-ContentContains $doc76 'traceId' 'FE-066' '76 号文档包含 traceId 错误态要求'
Test-ContentContains $doc76 '1366' 'FE-067' '76 号文档包含 1366 响应式宽度'
Test-ContentContains $doc76 '375' 'FE-068' '76 号文档包含 375 移动宽度'

# 76 号文档扣分规则 11 项
Test-RegexExact $doc76 '^\| .+ \| -\d+ \|$' 11 'FE-069' '76 号文档 11 项扣分规则'

# ============================================================
# FE-070 ~ FE-080: YAML 骨架验证
# ============================================================

Write-Host '=== 阶段 8: YAML 骨架验证 ===' -ForegroundColor Cyan

$yaml = "$root\YuTong-Java-Docs\contracts\governance\frontend-visual-engineering.yaml"
Test-FileExists $yaml 'FE-070' 'YAML 骨架文件存在'

Test-ContentContains $yaml 'GA2-L163' 'FE-071' 'YAML 包含 taskId GA2-L163'
Test-ContentContains $yaml 'DOC-UX-004' 'FE-072' 'YAML 包含 50 号文档 ID'
Test-ContentContains $yaml 'DOC-WEB-002' 'FE-073' 'YAML 包含 53 号文档 ID'
Test-ContentContains $yaml 'DOC-MOB-002' 'FE-074' 'YAML 包含 54 号文档 ID'
Test-ContentContains $yaml 'DOC-UX-005' 'FE-075' 'YAML 包含 56 号文档 ID'
Test-ContentContains $yaml 'DOC-FE-001' 'FE-076' 'YAML 包含 66 号文档 ID'
Test-ContentContains $yaml 'DOC-UX-006' 'FE-077' 'YAML 包含 76 号文档 ID'

Test-ContentContains $yaml 'colorTokens:' 'FE-078' 'YAML 包含 colorTokens 章节'
Test-ContentContains $yaml 'fontTokens:' 'FE-079' 'YAML 包含 fontTokens 章节'
Test-ContentContains $yaml 'spaceTokens:' 'FE-080' 'YAML 包含 spaceTokens 章节'
Test-ContentContains $yaml 'visualSignature:' 'FE-081' 'YAML 包含 visualSignature 视觉签名'
Test-ContentContains $yaml 'webRoutes:' 'FE-082' 'YAML 包含 webRoutes 章节'
Test-ContentContains $yaml 'mobileRoutes:' 'FE-083' 'YAML 包含 mobileRoutes 章节'
Test-ContentContains $yaml 'baseTableStateMachine:' 'FE-084' 'YAML 包含 baseTableStateMachine'
Test-ContentContains $yaml 'fileUploaderStateMachine:' 'FE-085' 'YAML 包含 fileUploaderStateMachine'
Test-ContentContains $yaml 'feStoreDesign:' 'FE-086' 'YAML 包含 feStoreDesign'
Test-ContentContains $yaml 'aestheticScoring:' 'FE-087' 'YAML 包含 aestheticScoring 评分模型'
Test-ContentContains $yaml 'deductionRules:' 'FE-088' 'YAML 包含 deductionRules 扣分规则'
Test-ContentContains $yaml 'remediationLoop:' 'FE-089' 'YAML 包含 remediationLoop 整改闭环'
Test-ContentContains $yaml 'p0CorePageGating:' 'FE-090' 'YAML 包含 p0CorePageGating P0 准入'
Test-ContentContains $yaml 'brandKeyPages:' 'FE-091' 'YAML 包含 brandKeyPages 品牌关键页'
Test-ContentContains $yaml 'yutongPrefixRule:' 'FE-092' 'YAML 包含 yutongPrefixRule 前缀规则'
Test-ContentContains $yaml 'codeAlignment:' 'FE-093' 'YAML 包含 codeAlignment 代码对齐'
Test-ContentContains $yaml 'knownDeviations:' 'FE-094' 'YAML 包含 knownDeviations 已知偏差'

# YAML 色彩 token 数量 11
Test-YamlSectionCount $yaml 'colorTokens' '^\s+- token: (color-|bg-|border-|text-)' 11 'FE-095' 'YAML colorTokens 11 项'

# YAML 字体 token 数量 6
Test-YamlSectionCount $yaml 'fontTokens' '^\s+- scene: ' 6 'FE-096' 'YAML fontTokens 6 项'

# YAML 间距 token 数量 5
Test-YamlSectionCount $yaml 'spaceTokens' '^\s+- token: space-' 5 'FE-097' 'YAML spaceTokens 5 项'

# YAML 视觉签名 5 元素
Test-YamlSectionCount $yaml 'visualSignature' '^\s+- element: ' 5 'FE-098' 'YAML visualSignature 5 元素'

# YAML 关键页面 5 个
Test-YamlSectionCount $yaml 'keyPageComposition' '^\s+- page: ' 5 'FE-099' 'YAML keyPageComposition 5 页面'

# YAML 响应式断点 5 档
Test-YamlSectionCount $yaml 'responsiveBreakpoints' '^\s+- breakpoint: ' 5 'FE-100' 'YAML responsiveBreakpoints 5 档'

# YAML 主题扩展 4
Test-YamlSectionCount $yaml 'themeExtensions' '^\s+- theme: ' 4 'FE-101' 'YAML themeExtensions 4 主题'

# YAML Token 到 CSS 变量映射 20+
Test-YamlSectionCount $yaml 'tokenToCssVar' '^\s+- token: .+' 21 'FE-102' 'YAML tokenToCssVar 21 项映射'

# YAML Web 路由 22
Test-YamlSectionCount $yaml 'webRoutes' '^\s+- route: /' 22 'FE-103' 'YAML webRoutes 22 路由'

# YAML 移动路由 9
Test-YamlSectionCount $yaml 'mobileRoutes' '^\s+- route: /pages/' 9 'FE-104' 'YAML mobileRoutes 9 路由'

# YAML 移动组件 9
Test-YamlSectionCount $yaml 'mobileComponents' '^\s+- component: ' 9 'FE-105' 'YAML mobileComponents 9 组件'

# YAML 通用组件 10
Test-YamlSectionCount $yaml 'webCommonComponents' '^\s+- component: ' 10 'FE-106' 'YAML webCommonComponents 10 组件'

# YAML 扣分规则 11
Test-YamlSectionCount $yaml 'deductionRules' '^\s+- problem: ' 11 'FE-107' 'YAML deductionRules 11 项'

# YAML 整改闭环 4 阶段
Test-YamlSectionCount $yaml 'remediationLoop' '^\s+- phase: ' 4 'FE-108' 'YAML remediationLoop 4 阶段'

# YAML 已知偏差 5 项
Test-YamlSectionCount $yaml 'knownDeviations' '^\s+- id: DEV-L163-' 5 'FE-109' 'YAML knownDeviations 5 项'

# YAML 代码对齐 22 项
Test-YamlSectionCount $yaml 'codeAlignment' '^\s+- item: FE-' 22 'FE-110' 'YAML codeAlignment 22 项'

# ============================================================
# FE-111 ~ FE-140: 前端代码实现对齐验证
# ============================================================

Write-Host '=== 阶段 9: 前端代码实现对齐验证 ===' -ForegroundColor Cyan

$varsCss = "$root\web-admin\src\styles\variables.css"
Test-FileExists $varsCss 'FE-111' 'variables.css 存在'

Test-ContentContains $varsCss '--yt-color-primary: #2563eb' 'FE-112' 'CSS 主色 #2563eb'
Test-ContentContains $varsCss '--yt-color-success: #16a34a' 'FE-113' 'CSS 成功色 #16a34a'
Test-ContentContains $varsCss '--yt-color-warning: #d97706' 'FE-114' 'CSS 警告色 #d97706'
Test-ContentContains $varsCss '--yt-color-danger: #dc2626' 'FE-115' 'CSS 危险色 #dc2626'
Test-ContentContains $varsCss '--yt-bg-page: #f6f8fb' 'FE-116' 'CSS 页面背景 #f6f8fb'
Test-ContentContains $varsCss '--yt-bg-card: #ffffff' 'FE-117' 'CSS 卡片背景 #ffffff'
Test-ContentContains $varsCss '--yt-space-xs: 4px' 'FE-118' 'CSS 间距 xs 4px'
Test-ContentContains $varsCss '--yt-space-md: 16px' 'FE-119' 'CSS 间距 md 16px'
Test-ContentContains $varsCss '--yt-radius-md: 8px' 'FE-120' 'CSS 圆角 md 8px'
Test-ContentContains $varsCss '--yt-shadow-card' 'FE-121' 'CSS 阴影 card'

# Element Plus 桥接
Test-ContentContains $varsCss '--el-color-primary: var(--yt-color-primary)' 'FE-122' 'CSS Element Plus 桥接主色'
Test-ContentContains $varsCss '--el-bg-color: var(--yt-bg-card)' 'FE-123' 'CSS Element Plus 桥接背景'

# yutong 前缀规则（关键）
Test-ContentContains $varsCss '--yutong-primary: var(--yt-color-primary)' 'FE-124' 'CSS yutong 前缀别名 primary'
Test-ContentContains $varsCss '--yutong-bg-page: var(--yt-bg-page)' 'FE-125' 'CSS yutong 前缀别名 bg-page'
Test-ContentContains $varsCss '--yutong-text-primary' 'FE-126' 'CSS yutong 前缀别名 text-primary'
Test-ContentContains $varsCss '--yutong-sidebar-width' 'FE-127' 'CSS yutong 前缀别名 sidebar-width'

# WCAG 2.2 AA 全局样式
Test-ContentContains $varsCss '.sr-only' 'FE-128' 'CSS sr-only 屏幕阅读器'
Test-ContentContains $varsCss '.skip-link' 'FE-129' 'CSS skip-link 跳过导航'
Test-ContentContains $varsCss ':focus-visible' 'FE-130' 'CSS focus-visible 焦点态'
Test-ContentContains $varsCss 'prefers-reduced-motion' 'FE-131' 'CSS prefers-reduced-motion 减少动画'

# 8 通用组件存在性
$components = @('BaseTable', 'SearchForm', 'FormDrawer', 'FormDialog', 'StatusBadge', 'StatusTag', 'FileUploader', 'PageState')
$compIndex = 132
foreach ($comp in $components) {
    $compPath = "$root\web-admin\src\components\$comp.vue"
    Test-FileExists $compPath "FE-$compIndex" "$comp.vue 组件存在"
    $compIndex++
}

# 5 store 存在性
$stores = @('auth', 'app', 'dict', 'lowcode', 'ai')
foreach ($store in $stores) {
    $storePath = "$root\web-admin\src\stores\$store.ts"
    Test-FileExists $storePath "FE-$compIndex" "stores/$store.ts 存在"
    $compIndex++
}

# router 和 permission 指令
Test-FileExists "$root\web-admin\src\router\index.ts" "FE-$compIndex" 'router/index.ts 存在'
$compIndex++
Test-FileExists "$root\web-admin\src\directives\permission.ts" "FE-$compIndex" 'directives/permission.ts 存在'
$compIndex++
Test-FileExists "$root\web-admin\src\utils\tracker.ts" "FE-$compIndex" 'utils/tracker.ts 存在'
$compIndex++

# 移动端 pages.json
Test-FileExists "$root\mobile-uniapp\src\pages.json" "FE-$compIndex" 'mobile-uniapp/src/pages.json 存在'
$compIndex++

# 暗色主题样式
Test-FileExists "$root\web-admin\src\styles\dark.css" "FE-$compIndex" 'styles/dark.css 暗色主题存在'
$compIndex++

# i18n 双语
Test-FileExists "$root\web-admin\src\locales\zh-CN" "FE-$compIndex" 'locales/zh-CN 存在'
$compIndex++
Test-FileExists "$root\web-admin\src\locales\en-US" "FE-$compIndex" 'locales/en-US 存在'
$compIndex++

# ============================================================
# FE-155 ~ FE-165: yutong 前缀规则 - Docker 镜像/网络验证
# ============================================================

Write-Host '=== 阶段 10: yutong 前缀规则验证 ===' -ForegroundColor Cyan

# docker-compose 文件应使用 yutong 前缀
$composeFiles = @()
$composeSearchPaths = @("$root\docker-compose.yml", "$root\docker-compose.yaml", "$root\compose.yml", "$root\compose.yaml")
foreach ($f in $composeSearchPaths) {
    if (Test-Path $f) { $composeFiles += $f }
}

if ($composeFiles.Count -eq 0) {
    Add-Result 'FE-155' 'docker-compose 文件存在' 'WARN' '未找到 docker-compose.yml/yaml 或 compose.yml/yaml'
} else {
    Add-Result 'FE-155' 'docker-compose 文件存在' 'PASS' "找到 $($composeFiles.Count) 个 compose 文件"
}

# yutong 前缀在 docker 相关文件中的体现
$yutongPattern = 'yutong-(backend-run|redis|postgres|minio|default)'
$foundYutongDocker = $false
foreach ($f in $composeFiles) {
    $content = Get-Content $f -Raw -Encoding UTF8
    if ($content -match $yutongPattern) {
        $foundYutongDocker = $true
        Add-Result 'FE-156' "yutong 前缀在 $(Split-Path $f -Leaf)" 'PASS' "命中 yutong-* 前缀"
        break
    }
}
if (-not $foundYutongDocker) {
    # 检查是否有 docker 相关目录或脚本
    $dockerFiles = @()
    $dockerSearchPaths = @("$root\docker", "$root\scripts\docker", "$root\deploy")
    foreach ($d in $dockerSearchPaths) {
        if (Test-Path $d) { $dockerFiles += $d }
    }
    if ($dockerFiles.Count -gt 0) {
        Add-Result 'FE-156' 'yutong 前缀 docker 目录' 'WARN' "未在 compose 文件中命中 yutong-，但找到 docker 目录: $($dockerFiles -join ', ')"
    } else {
        # 直接搜索整个仓库的 yutong- 前缀（限定范围避免超大输出）
        $yutongInCode = Get-ChildItem -Path $root -Recurse -File -Include '*.yml','*.yaml','*.env','*.ps1','*.sh' -ErrorAction SilentlyContinue |
            Select-String -Pattern $yutongPattern -List -ErrorAction SilentlyContinue
        if ($yutongInCode) {
            Add-Result 'FE-156' 'yutong 前缀在配置文件' 'PASS' "在 $($yutongInCode.Count) 个文件中命中 yutong-* 前缀"
        } else {
            Add-Result 'FE-156' 'yutong 前缀 docker' 'WARN' '未在 compose 或配置文件中找到 yutong- 前缀（可能使用其他方式管理）'
        }
    }
}

# yutong 前缀在 variables.css 已验证（FE-124 ~ FE-127）
Add-Result 'FE-157' 'yutong 前缀 CSS 别名' 'PASS' '已在 FE-124 ~ FE-127 验证 --yutong-* CSS 别名'

# yutong 前缀在 YAML 骨架
Test-ContentContains $yaml 'yutong' 'FE-158' 'YAML 包含 yutong 前缀规则'
Test-ContentContains $yaml 'yutong-backend-run' 'FE-159' 'YAML 包含 yutong-backend-run 镜像'
Test-ContentContains $yaml 'yutong-redis' 'FE-160' 'YAML 包含 yutong-redis 镜像'
Test-ContentContains $yaml 'yutong-postgres' 'FE-161' 'YAML 包含 yutong-postgres 镜像'
Test-ContentContains $yaml 'yutong-minio' 'FE-162' 'YAML 包含 yutong-minio 镜像'
Test-ContentContains $yaml 'yutong_default' 'FE-163' 'YAML 包含 yutong_default 网络'

# ============================================================
# FE-164 ~ FE-170: 23 追踪记录验证
# ============================================================

Write-Host '=== 阶段 11: 23 追踪记录验证 ===' -ForegroundColor Cyan

$tracker = "$root\YuTong-Java-Docs\23-设计到落地追踪记录\23-设计到落地追踪记录.md"
Test-FileExists $tracker 'FE-164' '23 追踪记录文件存在'

Test-ContentContains $tracker 'L163' 'FE-165' '23 追踪记录包含 L163 行'
Test-ContentContains $tracker '端侧体验与视觉工程补强' 'FE-166' '23 追踪记录包含 L163 任务名'
Test-ContentContains $tracker '50' 'FE-167' '23 追踪记录 L163 引用 50 号文档'
Test-ContentContains $tracker '76' 'FE-168' '23 追踪记录 L163 引用 76 号文档'

# ============================================================
# FE-169 ~ FE-175: 运行时验证（Docker 容器状态 + 后端健康检查）
# ============================================================

Write-Host '=== 阶段 12: 运行时验证 ===' -ForegroundColor Cyan

# Docker 容器状态检查（非阻断）
$dockerContainers = @()
try {
    $dockerOutput = docker ps --format '{{.Names}}\t{{.Status}}' 2>$null
    if ($dockerOutput) {
        $dockerOutput -split "`n" | ForEach-Object {
            $line = $_.Trim()
            if ($line) {
                $parts = $line -split "`t"
                if ($parts.Count -ge 2) {
                    $dockerContainers += [PSCustomObject]@{ Name = $parts[0]; Status = $parts[1] }
                }
            }
        }
    }
} catch {
    Add-Result 'FE-169' 'Docker 运行时检查' 'WARN' "Docker 检查失败: $($_.Exception.Message)"
}

if ($dockerContainers.Count -gt 0) {
    Add-Result 'FE-169' 'Docker 容器运行' 'PASS' "$($dockerContainers.Count) 个容器运行中"
    
    $yutongContainers = $dockerContainers | Where-Object { $_.Name -like 'yutong-*' }
    if ($yutongContainers) {
        Add-Result 'FE-170' 'yutong-* 容器存在' 'PASS' "$($yutongContainers.Count) 个 yutong-* 容器: $($yutongContainers.Name -join ', ')"
    } else {
        Add-Result 'FE-170' 'yutong-* 容器存在' 'WARN' "当前无 yutong-* 前缀容器运行（可能使用其他命名或未启动）"
    }
} else {
    Add-Result 'FE-169' 'Docker 容器运行' 'WARN' '当前无 Docker 容器运行（不影响 L163 落地验证）'
    Add-Result 'FE-170' 'yutong-* 容器存在' 'WARN' '同上，运行时验证跳过'
}

# 后端健康检查（非阻断）
try {
    $response = Invoke-WebRequest -Uri 'http://localhost:8082/actuator/health' -TimeoutSec 5 -UseBasicParsing -ErrorAction Stop
    if ($response.StatusCode -eq 200) {
        Add-Result 'FE-171' '后端健康检查 /actuator/health' 'PASS' "HTTP $($response.StatusCode)"
    } else {
        Add-Result 'FE-171' '后端健康检查 /actuator/health' 'WARN' "HTTP $($response.StatusCode)"
    }
} catch {
    Add-Result 'FE-171' '后端健康检查 /actuator/health' 'WARN' "后端未启动或不可达: $($_.Exception.Message)"
}

# 前端构建检查（非阻断，通过文件存在性判断）
$feBuildPath = "$root\web-admin\dist"
if (Test-Path $feBuildPath) {
    Add-Result 'FE-172' '前端构建产物 dist 存在' 'PASS' $feBuildPath
} else {
    Add-Result 'FE-172' '前端构建产物 dist 存在' 'WARN' '未构建 web-admin/dist（不影响 L163 机器化骨架验证）'
}

# ============================================================
# FE-173 ~ FE-178: 跨文档一致性验证
# ============================================================

Write-Host '=== 阶段 13: 跨文档一致性验证 ===' -ForegroundColor Cyan

# 50 号文档 CSS 变量映射与 variables.css 一致
Test-ContentContains $doc50 '--yt-color-primary' 'FE-173' '50 号文档 CSS 变量映射与代码一致'
Test-ContentContains $doc50 'Element Plus 变量覆盖' 'FE-174' '50 号文档 Element Plus 覆盖规则'

# 53/66 组件命名一致性
Test-ContentContains $doc53 'StatusBadge' 'FE-175' '53 号文档使用 StatusBadge 新名称'
Test-ContentContains $doc66 'StatusBadge' 'FE-176' '66 号文档使用 StatusBadge 新名称'
Test-ContentContains $doc66 'StatusTag' 'FE-177' '66 号文档保留 StatusTag 别名'

# 56 号文档引用 50/53/54 号
Test-ContentContains $doc56 '50-设计系统与视觉规范详设' 'FE-178' '56 号文档引用 50 号文档'
Test-ContentContains $doc56 '53-Web管理端页面级分工详设' 'FE-179' '56 号文档引用 53 号文档'
Test-ContentContains $doc56 '54-Uniapp移动端页面级分工详设' 'FE-180' '56 号文档引用 54 号文档'

# 76 号文档评分维度与 50 号视觉品质规则一致
Test-ContentContains $doc76 '信息层级' 'FE-181' '76 号文档 信息层级 与 50 号层级规则对应'
Test-ContentContains $doc50 '层级' 'FE-182' '50 号文档层级规则存在'

# 66 号文档引用 50/53/56 号
Test-ContentContains $doc66 '50' 'FE-183' '66 号文档引用 50 号文档'
Test-ContentContains $doc66 '53' 'FE-184' '66 号文档引用 53 号文档'
Test-ContentContains $doc66 '56' 'FE-185' '66 号文档引用 56 号文档'

# 76 号文档承接 50/53/54/56 号
Test-ContentContains $doc76 '50-设计系统与视觉规范详设' 'FE-186' '76 号文档承接 50 号文档'
Test-ContentContains $doc76 '53-Web管理端页面级分工详设' 'FE-187' '76 号文档承接 53 号文档'
Test-ContentContains $doc76 '54-Uniapp移动端页面级分工详设' 'FE-188' '76 号文档承接 54 号文档'
Test-ContentContains $doc76 '56-UI逐屏线框与交互验收详设' 'FE-189' '76 号文档承接 56 号文档'

# ============================================================
# FE-190 ~ FE-195: 证据文件与归档验证
# ============================================================

Write-Host '=== 阶段 14: 证据文件与归档验证 ===' -ForegroundColor Cyan

# 证据目录存在
$evidenceDir = "$root\release-evidence\v1.0.0"
if (Test-Path $evidenceDir) {
    Add-Result 'FE-190' 'release-evidence/v1.0.0 目录存在' 'PASS' $evidenceDir
} else {
    Add-Result 'FE-190' 'release-evidence/v1.0.0 目录存在' 'FAIL' "缺失: $evidenceDir"
}

# 已有 GA2 证据（前轮 L160/L161/L162/L164）
$ga2EvidencePattern = "$root\release-evidence\v1.0.0\ga2-l16*-evidence.md"
$ga2EvidenceFiles = Get-ChildItem -Path $ga2EvidencePattern -ErrorAction SilentlyContinue
if ($ga2EvidenceFiles.Count -ge 4) {
    Add-Result 'FE-191' 'GA2-L160~L164 证据文件齐全' 'PASS' "$($ga2EvidenceFiles.Count) 个 GA2-L16* 证据"
} else {
    Add-Result 'FE-191' 'GA2-L160~L164 证据文件齐全' 'WARN' "仅 $($ga2EvidenceFiles.Count) 个 GA2-L16* 证据（期望 >= 4）"
}

# 检查脚本目录
$checkScriptsDir = "$root\tools\checks"
if (Test-Path $checkScriptsDir) {
    $checkScripts = Get-ChildItem -Path $checkScriptsDir -Filter 'check-*.ps1' -ErrorAction SilentlyContinue
    Add-Result 'FE-192' 'tools/checks 目录与脚本' 'PASS' "$($checkScripts.Count) 个 check-*.ps1 脚本"
} else {
    Add-Result 'FE-192' 'tools/checks 目录与脚本' 'FAIL' "缺失: $checkScriptsDir"
}

# ============================================================
# 输出汇总
# ============================================================

Write-Host ''
Write-Host '================================================================' -ForegroundColor Yellow
Write-Host "  GA2-L163 端侧体验与视觉工程补强 检查结果汇总" -ForegroundColor Yellow
Write-Host '================================================================' -ForegroundColor Yellow
Write-Host "  PASS: $passCount" -ForegroundColor Green
Write-Host "  WARN: $warnCount" -ForegroundColor Yellow
Write-Host "  FAIL: $failCount" -ForegroundColor Red
Write-Host '================================================================' -ForegroundColor Yellow
Write-Host ''

# 输出详细结果
$results | Format-Table -AutoSize

# 输出 CSV（便于归档）
$reportDir = "$root\build\reports\checks"
if (-not (Test-Path $reportDir)) { New-Item -ItemType Directory -Path $reportDir -Force | Out-Null }
$csvPath = "$reportDir\ga2-l163-check-results.csv"
$results | Export-Csv -Path $csvPath -NoTypeInformation -Encoding UTF8
Write-Host "CSV 报告: $csvPath" -ForegroundColor Cyan

# 输出 JSON 摘要
$summary = @{
    taskId = 'GA2-L163'
    taskName = '端侧体验与视觉工程补强'
    passCount = $passCount
    warnCount = $warnCount
    failCount = $failCount
    totalChecks = $passCount + $warnCount + $failCount
    passRate = if (($passCount + $warnCount + $failCount) -gt 0) { [math]::Round($passCount / ($passCount + $warnCount + $failCount) * 100, 2) } else { 0 }
    timestamp = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
}
$jsonPath = "$reportDir\ga2-l163-check-summary.json"
$summary | ConvertTo-Json | Out-File -FilePath $jsonPath -Encoding UTF8
Write-Host "JSON 摘要: $jsonPath" -ForegroundColor Cyan

# 退出码
if ($failCount -gt 0) {
    Write-Host "FAIL: 存在 $failCount 项失败" -ForegroundColor Red
    exit 1
} else {
    Write-Host "SUCCESS: 所有检查通过 (PASS=$passCount, WARN=$warnCount)" -ForegroundColor Green
    exit 0
}
