# PowerShell 验收脚本：GA2-55 前端组件 API 与状态管理验收
# 对齐 66 号文档「前端组件API与状态管理详设」4 项验收标准 (line 135-140)
# 注意：本脚本必须保存为 UTF-8 BOM 编码，避免 PowerShell 5.1 中文 GBK 乱码
# PS 5.1 兼容: 使用 [regex]::Match() 静态方法, $script:results = New-Object System.Collections.ArrayList

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot\..\..").Path
$componentsDir = Join-Path $root 'web-admin\src\components'
$storesDir = Join-Path $root 'web-admin\src\stores'
$requestFile = Join-Path $root 'web-admin\src\api\request.ts'
$requestListFile = Join-Path $root 'web-admin\src\views\request\RequestList.vue'
$darkCssFile = Join-Path $root 'web-admin\src\styles\dark.css'

$script:results = New-Object System.Collections.ArrayList
$script:passCount = 0
$script:failCount = 0
$script:warnCount = 0

function Add-Result($code, $severity, $message) {
    $script:results.Add([PSCustomObject]@{ Code = $code; Severity = $severity; Message = $message }) | Out-Null
    if ($severity -eq 'PASS') { $script:passCount++ }
    elseif ($severity -eq 'FAIL') { $script:failCount++ }
    elseif ($severity -eq 'WARN') { $script:warnCount++ }
}

# ============ FC-001：7 个组件文件存在性 ============
$expectedComponents = @(
    'BaseTable.vue',
    'SearchForm.vue',
    'FormDrawer.vue',
    'FormDialog.vue',
    'StatusBadge.vue',
    'PageState.vue',
    'FileUploader.vue'
)
$missingComponents = @()
foreach ($c in $expectedComponents) {
    $p = Join-Path $componentsDir $c
    if (-not (Test-Path $p)) {
        $missingComponents += $c
    }
}
if ($missingComponents.Count -eq 0) {
    Add-Result 'FC-001' 'PASS' "7 个通用组件全部存在: $($expectedComponents -join '，')"
} else {
    Add-Result 'FC-001' 'FAIL' "缺少组件: $($missingComponents -join '，')"
}

# ============ FC-002：BaseTable.vue props + events ============
$baseTablePath = Join-Path $componentsDir 'BaseTable.vue'
if (Test-Path $baseTablePath) {
    $content = Get-Content -Path $baseTablePath -Raw -Encoding UTF8
    $requiredProps = @('columns', 'data', 'loading', 'pagination', 'rowKey')
    $requiredEvents = @('page-change', 'sort-change', 'selection-change', 'refresh')
    $missingProps = @()
    foreach ($p in $requiredProps) {
        if ($content -notmatch [regex]::Escape($p)) { $missingProps += $p }
    }
    $missingEvents = @()
    foreach ($e in $requiredEvents) {
        if ($content -notmatch [regex]::Escape($e)) { $missingEvents += $e }
    }
    if ($missingProps.Count -eq 0 -and $missingEvents.Count -eq 0) {
        Add-Result 'FC-002' 'PASS' "BaseTable.vue 含 5 props (columns/data/loading/pagination/rowKey) + 4 events (page-change/sort-change/selection-change/refresh)"
    } else {
        $msg = "BaseTable.vue 缺少: "
        if ($missingProps.Count -gt 0) { $msg += "props $($missingProps -join '，')；" }
        if ($missingEvents.Count -gt 0) { $msg += "events $($missingEvents -join '，')" }
        Add-Result 'FC-002' 'FAIL' $msg
    }
} else {
    Add-Result 'FC-002' 'FAIL' "BaseTable.vue 不存在"
}

# ============ FC-003：SearchForm.vue props + events ============
$searchFormPath = Join-Path $componentsDir 'SearchForm.vue'
if (Test-Path $searchFormPath) {
    $content = Get-Content -Path $searchFormPath -Raw -Encoding UTF8
    $requiredProps = @('schema', 'model', 'loading')
    $requiredEvents = @('search', 'reset')
    $missingProps = @()
    foreach ($p in $requiredProps) {
        if ($content -notmatch [regex]::Escape($p)) { $missingProps += $p }
    }
    $missingEvents = @()
    foreach ($e in $requiredEvents) {
        if ($content -notmatch [regex]::Escape($e)) { $missingEvents += $e }
    }
    if ($missingProps.Count -eq 0 -and $missingEvents.Count -eq 0) {
        Add-Result 'FC-003' 'PASS' "SearchForm.vue 含 3 props (schema/model/loading) + 2 events (search/reset)"
    } else {
        $msg = "SearchForm.vue 缺少: "
        if ($missingProps.Count -gt 0) { $msg += "props $($missingProps -join '，')；" }
        if ($missingEvents.Count -gt 0) { $msg += "events $($missingEvents -join '，')" }
        Add-Result 'FC-003' 'FAIL' $msg
    }
} else {
    Add-Result 'FC-003' 'FAIL' "SearchForm.vue 不存在"
}

# ============ FC-004：FormDrawer.vue + FormDialog.vue props + events ============
$formDrawerPath = Join-Path $componentsDir 'FormDrawer.vue'
$formDialogPath = Join-Path $componentsDir 'FormDialog.vue'
$formCheckOk = $true
$formCheckMsg = @()
$formFiles = @(
    @{ name = 'FormDrawer.vue'; path = $formDrawerPath },
    @{ name = 'FormDialog.vue'; path = $formDialogPath }
)
foreach ($f in $formFiles) {
    if (-not (Test-Path $f.path)) {
        $formCheckOk = $false
        $formCheckMsg += "$($f.name) 不存在"
        continue
    }
    $content = Get-Content -Path $f.path -Raw -Encoding UTF8
    $requiredProps = @('visible', 'title', 'loading')
    $requiredEvents = @('submit', 'cancel', 'update:visible')
    $missingProps = @()
    foreach ($p in $requiredProps) {
        if ($content -notmatch [regex]::Escape($p)) { $missingProps += $p }
    }
    $missingEvents = @()
    foreach ($e in $requiredEvents) {
        if ($content -notmatch [regex]::Escape($e)) { $missingEvents += $e }
    }
    if ($missingProps.Count -gt 0 -or $missingEvents.Count -gt 0) {
        $formCheckOk = $false
        $msg = "$($f.name) 缺少: "
        if ($missingProps.Count -gt 0) { $msg += "props $($missingProps -join '，')；" }
        if ($missingEvents.Count -gt 0) { $msg += "events $($missingEvents -join '，')" }
        $formCheckMsg += $msg
    }
}
if ($formCheckOk) {
    Add-Result 'FC-004' 'PASS' "FormDrawer.vue + FormDialog.vue 各含 3 props (visible/title/loading) + 3 events (submit/cancel/update:visible)"
} else {
    Add-Result 'FC-004' 'FAIL' ($formCheckMsg -join '；')
}

# ============ FC-005：StatusBadge.vue props + CSS 类 ============
$statusBadgePath = Join-Path $componentsDir 'StatusBadge.vue'
if (Test-Path $statusBadgePath) {
    $content = Get-Content -Path $statusBadgePath -Raw -Encoding UTF8
    $hasType = $content -match 'type:'
    $hasLabel = $content -match 'label:'
    $hasClass = $content -match 'yt-status-badge'
    if ($hasType -and $hasLabel -and $hasClass) {
        Add-Result 'FC-005' 'PASS' "StatusBadge.vue 含 type/label props + 使用 yt-status-badge CSS 类"
    } else {
        $missing = @()
        if (-not $hasType) { $missing += 'type prop' }
        if (-not $hasLabel) { $missing += 'label prop' }
        if (-not $hasClass) { $missing += 'yt-status-badge CSS 类' }
        Add-Result 'FC-005' 'FAIL' "StatusBadge.vue 缺少: $($missing -join '，')"
    }
} else {
    Add-Result 'FC-005' 'FAIL' "StatusBadge.vue 不存在"
}

# ============ FC-006：PageState.vue props + events + 4 type ============
$pageStatePath = Join-Path $componentsDir 'PageState.vue'
if (Test-Path $pageStatePath) {
    $content = Get-Content -Path $pageStatePath -Raw -Encoding UTF8
    $requiredProps = @('type', 'traceId', 'message', 'permissionCode')
    $requiredTypes = @('loading', 'empty', 'error', 'no-permission')
    $hasRetry = $content -match "'retry'"
    $missingProps = @()
    foreach ($p in $requiredProps) {
        if ($content -notmatch [regex]::Escape($p)) { $missingProps += $p }
    }
    $missingTypes = @()
    foreach ($t in $requiredTypes) {
        if ($content -notmatch [regex]::Escape($t)) { $missingTypes += $t }
    }
    if ($missingProps.Count -eq 0 -and $missingTypes.Count -eq 0 -and $hasRetry) {
        Add-Result 'FC-006' 'PASS' "PageState.vue 含 4 props (type/traceId/message/permissionCode) + retry event + 4 type (loading/empty/error/no-permission)"
    } else {
        $missing = @()
        if ($missingProps.Count -gt 0) { $missing += "props $($missingProps -join '，')" }
        if ($missingTypes.Count -gt 0) { $missing += "type $($missingTypes -join '，')" }
        if (-not $hasRetry) { $missing += "retry event" }
        Add-Result 'FC-006' 'FAIL' "PageState.vue 缺少: $($missing -join '，')"
    }
} else {
    Add-Result 'FC-006' 'FAIL' "PageState.vue 不存在"
}

# ============ FC-007：FileUploader.vue 5 状态机关键字 ============
$fileUploaderPath = Join-Path $componentsDir 'FileUploader.vue'
if (Test-Path $fileUploaderPath) {
    $content = Get-Content -Path $fileUploaderPath -Raw -Encoding UTF8
    $requiredStates = @('idle', 'selected', 'uploading', 'success', 'failed')
    $missingStates = @()
    foreach ($s in $requiredStates) {
        if ($content -notmatch [regex]::Escape($s)) { $missingStates += $s }
    }
    if ($missingStates.Count -eq 0) {
        Add-Result 'FC-007' 'PASS' "FileUploader.vue 含 5 状态机关键字 (idle/selected/uploading/success/failed)"
    } else {
        Add-Result 'FC-007' 'FAIL' "FileUploader.vue 缺少状态: $($missingStates -join '，')"
    }
} else {
    Add-Result 'FC-007' 'FAIL' "FileUploader.vue 不存在"
}

# ============ FC-008：5 个 Store 文件存在性 + 敏感关键字扫描 ============
$expectedStores = @('auth.ts', 'app.ts', 'dict.ts', 'lowcode.ts', 'ai.ts')
$missingStores = @()
$sensitiveKw = @('password', 'secret', 'token-plaintext')
$sensitiveHits = @()
foreach ($s in $expectedStores) {
    $p = Join-Path $storesDir $s
    if (-not (Test-Path $p)) {
        $missingStores += $s
        continue
    }
    $content = Get-Content -Path $p -Raw -Encoding UTF8
    foreach ($kw in $sensitiveKw) {
        if ($content -match [regex]::Escape($kw)) {
            $sensitiveHits += "$s 命中 '$kw'"
        }
    }
}
if ($missingStores.Count -eq 0 -and $sensitiveHits.Count -eq 0) {
    Add-Result 'FC-008' 'PASS' "5 个 Store 文件全部存在 (auth/app/dict/lowcode/ai) + 0 个敏感关键字命中 (password/secret/token-plaintext)"
} else {
    $msg = ""
    if ($missingStores.Count -gt 0) { $msg += "缺少 store: $($missingStores -join '，')；" }
    if ($sensitiveHits.Count -gt 0) { $msg += "敏感关键字: $($sensitiveHits -join '，')" }
    Add-Result 'FC-008' 'FAIL' $msg
}

# ============ FC-009：RequestList.vue 已重构使用通用组件 ============
if (Test-Path $requestListFile) {
    $content = Get-Content -Path $requestListFile -Raw -Encoding UTF8
    $hasBaseTable = $content -match 'BaseTable'
    $hasSearchForm = $content -match 'SearchForm'
    $hasStatusBadge = $content -match 'StatusBadge'
    # 检查不再直接使用 <el-table 或 <el-pagination 标签 (允许 <el-table-column 在 BaseTable 内)
    # <el-table 后必须跟空白或 >, 不能是 - (避免误匹配 el-table-column)
    $hasDirectElTable = $content -match '<el-table[\s>]'
    $hasDirectElPagination = $content -match '<el-pagination[\s>]'
    if ($hasBaseTable -and $hasSearchForm -and $hasStatusBadge -and -not $hasDirectElTable -and -not $hasDirectElPagination) {
        Add-Result 'FC-009' 'PASS' "RequestList.vue 已重构使用 BaseTable + SearchForm + StatusBadge，不再直接使用 el-table+el-pagination 分页"
    } else {
        $missing = @()
        if (-not $hasBaseTable) { $missing += 'BaseTable 引用' }
        if (-not $hasSearchForm) { $missing += 'SearchForm 引用' }
        if (-not $hasStatusBadge) { $missing += 'StatusBadge 引用' }
        if ($hasDirectElTable) { $missing += '直接使用 <el-table>' }
        if ($hasDirectElPagination) { $missing += '直接使用 <el-pagination>' }
        Add-Result 'FC-009' 'FAIL' "RequestList.vue 不符合要求: $($missing -join '，')"
    }
} else {
    Add-Result 'FC-009' 'FAIL' "RequestList.vue 不存在"
}

# ============ FC-010：request.ts 含 401/403/409 + tenantId/traceId 注入 ============
if (Test-Path $requestFile) {
    $content = Get-Content -Path $requestFile -Raw -Encoding UTF8
    # 401: code === '401' 或 status === 401
    $has401 = $content -match "('401'|status === 401)"
    $has403 = $content -match "('403'|status === 403)"
    $has409 = $content -match "('409'|status === 409)"
    $hasTenantId = $content -match 'X-Tenant-Id'
    $hasTraceId = $content -match 'X-Trace-Id'
    if ($has401 -and $has403 -and $has409 -and $hasTenantId -and $hasTraceId) {
        Add-Result 'FC-010' 'PASS' "request.ts 含 401/403/409 处理 + X-Tenant-Id/X-Trace-Id 请求头注入"
    } else {
        $missing = @()
        if (-not $has401) { $missing += '401 处理' }
        if (-not $has403) { $missing += '403 处理' }
        if (-not $has409) { $missing += '409 处理' }
        if (-not $hasTenantId) { $missing += 'X-Tenant-Id 注入' }
        if (-not $hasTraceId) { $missing += 'X-Trace-Id 注入' }
        Add-Result 'FC-010' 'FAIL' "request.ts 缺少: $($missing -join '，')"
    }
} else {
    Add-Result 'FC-010' 'FAIL' "request.ts 不存在"
}

# ============ FC-011：dark.css 存在 + html.dark + 5 个 --yt- 覆盖 ============
if (Test-Path $darkCssFile) {
    $content = Get-Content -Path $darkCssFile -Raw -Encoding UTF8
    $hasHtmlDark = $content -match 'html\.dark'
    # 提取 html.dark { ... } 块内的 --yt- 变量数量
    $ytCount = 0
    $blockMatch = [regex]::Match($content, "(?s)html\.dark\s*\{([^}]*)\}")
    if ($blockMatch.Success) {
        $block = $blockMatch.Groups[1].Value
        $ytRegex = [regex]'--yt-[a-z0-9-]+:'
        $ytCount = $ytRegex.Matches($block).Count
    }
    if ($hasHtmlDark -and $ytCount -ge 5) {
        Add-Result 'FC-011' 'PASS' "dark.css 存在 + html.dark 选择器 + $ytCount 个 --yt- 暗色覆盖 (>=5)"
    } else {
        $missing = @()
        if (-not $hasHtmlDark) { $missing += 'html.dark 选择器' }
        if ($ytCount -lt 5) { $missing += "--yt- 覆盖仅 $ytCount 个 (<5)" }
        Add-Result 'FC-011' 'FAIL' "dark.css 不符合要求: $($missing -join '，')"
    }
} else {
    Add-Result 'FC-011' 'FAIL' "dark.css 不存在"
}

# ============ FC-012：request.ts 含 resolveErrorMessage + messageKey 优先 + i18n 回退 ============
if (Test-Path $requestFile) {
    $content = Get-Content -Path $requestFile -Raw -Encoding UTF8
    $hasResolveFn = $content -match 'function\s+resolveErrorMessage'
    $hasMessageKey = $content -match 'messageKey'
    $hasI18n = $content -match 'i18n\.global\.t'
    $hasPriority = $content -match 'if\s*\(\s*res\.messageKey'
    $hasFallback = $content -match ([regex]::Escape("i18n.global.t('common.error.internal')"))
    if ($hasResolveFn -and $hasMessageKey -and $hasI18n -and $hasPriority -and $hasFallback) {
        Add-Result 'FC-012' 'PASS' "request.ts 含 resolveErrorMessage 函数 + messageKey 优先 + i18n 回退 (common.error.internal)"
    } else {
        $missing = @()
        if (-not $hasResolveFn) { $missing += 'resolveErrorMessage 函数' }
        if (-not $hasPriority) { $missing += 'messageKey 优先逻辑' }
        if (-not $hasFallback) { $missing += "i18n 回退 common.error.internal" }
        Add-Result 'FC-012' 'FAIL' "request.ts 缺少: $($missing -join '，')"
    }
} else {
    Add-Result 'FC-012' 'FAIL' "request.ts 不存在"
}

# ============ FC-013：所有 store 不保存大对象/敏感 token 明文 ============
# 扫描大对象类型关键字 (Blob/ArrayBuffer/FormData/Stream/base64/dataURL/multipart)
$largeObjKw = @('Blob', 'ArrayBuffer', 'FormData', 'ReadableStream', 'WritableStream', 'base64', 'dataURL', 'multipart')
$largeObjHits = @()
foreach ($s in $expectedStores) {
    $p = Join-Path $storesDir $s
    if (-not (Test-Path $p)) { continue }
    $content = Get-Content -Path $p -Raw -Encoding UTF8
    foreach ($kw in $largeObjKw) {
        # 区分大小写匹配类型关键字 (cmatch 区分大小写)
        if ($content -cmatch [regex]::Escape($kw)) {
            $largeObjHits += "$s 命中 '$kw'"
        }
    }
}
if ($largeObjHits.Count -eq 0) {
    Add-Result 'FC-013' 'PASS' "5 个 store 均未保存大对象 (Blob/ArrayBuffer/FormData/Stream/base64/dataURL/multipart 0 命中)"
} else {
    Add-Result 'FC-013' 'FAIL' "store 含大对象关键字: $($largeObjHits -join '，')"
}

# ============ 输出结果 ============
Write-Host "========== GA2-55 前端组件 API 与状态管理验收 =========="
Write-Host ""
$script:results | Format-Table -AutoSize
Write-Host ""
Write-Host "汇总: PASS=$script:passCount FAIL=$script:failCount WARN=$script:warnCount"
if ($script:failCount -eq 0) {
    Write-Host "结论: PASS"
} else {
    Write-Host "结论: FAIL（请修复上述 FAIL 项）"
}

# ============ 写入汇总文件 ============
$summaryFile = Join-Path $root 'release-evidence\v1.0.0\ga2-55-smoke.txt'
$summaryDir = Split-Path $summaryFile -Parent
if (-not (Test-Path $summaryDir)) { New-Item -ItemType Directory -Path $summaryDir -Force | Out-Null }
$lines = @()
$lines += "========== GA2-55 前端组件 API 与状态管理验收 =========="
$lines += "时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
$lines += ""
$lines += "Code`tSeverity`tMessage"
$lines += "----`t--------`t-------"
foreach ($r in $script:results) {
    $lines += "$($r.Code)`t$($r.Severity)`t$($r.Message)"
}
$lines += ""
$lines += "汇总: PASS=$script:passCount FAIL=$script:failCount WARN=$script:warnCount"
$lines += "结论: $(if ($script:failCount -eq 0) { 'PASS' } else { 'FAIL' })"
[System.IO.File]::WriteAllLines($summaryFile, $lines, (New-Object System.Text.UTF8Encoding($true)))
Write-Host ""
Write-Host "汇总已写入: $summaryFile"
