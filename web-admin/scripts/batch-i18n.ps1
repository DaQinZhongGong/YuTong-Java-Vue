# Batch i18n migration script - processes multiple Vue files
param(
    [string]$WebAdminDir = "d:\MyCode\YuTong-Java-Vue\web-admin"
)

$srcDir = Join-Path $WebAdminDir "src"
$localesZhDir = Join-Path $srcDir "locales\zh-CN"
$localesEnDir = Join-Path $srcDir "locales\en-US"

# Helper: Add useI18n import if not present
function Add-UseI18nImport {
    param([string]$content)
    if ($content -notmatch 'useI18n') {
        $content = $content -replace '(import.*from\s+[''"']vue[''"'])', "`$1`nimport { useI18n } from 'vue-i18n'"
    }
    return $content
}

# Helper: Add const { t } = useI18n() if not present
function Add-TDeclaration {
    param([string]$content)
    if ($content -notmatch 'const\s*\{\s*t\s*\}\s*=\s*useI18n') {
        $content = $content -replace '(const\s+\w+\s*=\s*ref\()', "const { t } = useI18n()`n`$1"
    }
    return $content
}

# Helper: Read JSON file
function Read-JsonFile {
    param([string]$path)
    if (Test-Path $path) {
        return Get-Content $path -Raw | ConvertFrom-Json
    }
    return @{}
}

# Helper: Write JSON file maintaining format
function Write-JsonFile {
    param([string]$path, $obj)
    # Convert to sorted JSON with proper formatting
    $sorted = $obj.PSObject.Properties | Sort-Object Name | ForEach-Object {
        "    `"$($_.Name)`":  `"$($_.Value)`""
    }
    $json = "{$([Environment]::NewLine)" + ($sorted -join ",$([Environment]::NewLine)") + "$([Environment]::NewLine)}"
    Set-Content $path -Value $json -NoNewline
}

# Process DashboardFullscreen.vue
Write-Host "Processing DashboardFullscreen.vue..." -NoNewline
$file = Join-Path $srcDir "views\dashboard\DashboardFullscreen.vue"
$content = Get-Content $file -Raw
$content = Add-UseI18nImport $content
$content = Add-TDeclaration $content
$content = $content -replace "ElMessage\.error\('加载大屏失败'\)", "ElMessage.error(t('dashboard.msg.loadFailed'))"
Set-Content $file -Value $content -NoNewline
Write-Host " OK"

# Process InventoryList.vue
Write-Host "Processing InventoryList.vue..." -NoNewline
$file = Join-Path $srcDir "views\inventory\InventoryList.vue"
$content = Get-Content $file -Raw
$content = Add-UseI18nImport $content
$content = Add-TDeclaration $content
$content = $content -replace "ElMessage\.warning\('物料编码和名称不能为空'\)", "ElMessage.warning(t('inventory.msg.materialRequired'))"
$content = $content -replace "ElMessage\.success\('物料创建成功'\)", "ElMessage.success(t('inventory.msg.materialCreated'))"
# Also replace MATERIAL_TYPE_OPTIONS
$content = $content -replace "'电子件'", "t('inventory.materialType.electronic')"
$content = $content -replace "'机械件'", "t('inventory.materialType.mechanical')"
$content = $content -replace "'附件'", "t('inventory.materialType.accessory')"
$content = $content -replace "'通用'", "t('inventory.materialType.general')"
Set-Content $file -Value $content -NoNewline
Write-Host " OK"

# Process AiProviders.vue
Write-Host "Processing AiProviders.vue..." -NoNewline
$file = Join-Path $srcDir "views\ai\AiProviders.vue"
$content = Get-Content $file -Raw
$content = Add-UseI18nImport $content
$content = Add-TDeclaration $content
$content = $content -replace "ElMessage\.error\('查询健康状态失败, 请稍后重试'\)", "ElMessage.error(t('ai.msg.healthQueryFailed'))"
$content = $content -replace "ElMessage\.success\(`健康检查完成, 共 \$\{arr\.length\} 个供应商`\)", "ElMessage.success(t('ai.msg.healthCheckDone', { count: arr.length }))"
$content = $content -replace "ElMessage\.error\('健康检查失败, 请稍后重试'\)", "ElMessage.error(t('ai.msg.healthCheckFailed'))"
Set-Content $file -Value $content -NoNewline
Write-Host " OK"

# Process ReportDetail.vue
Write-Host "Processing ReportDetail.vue..." -NoNewline
$file = Join-Path $srcDir "views\report\ReportDetail.vue"
$content = Get-Content $file -Raw
$content = Add-UseI18nImport $content
$content = Add-TDeclaration $content
$content = $content -replace "ElMessage\.success\(`导出完成，共 \$\{task\.totalRows \?\? 0\} 行`\)", "ElMessage.success(t('report.msg.exportDone', { rows: task.totalRows ?? 0 }))"
$content = $content -replace "ElMessage\.error\(`导出失败: \$\{task\.errorMessage \|\| '未知错误'\}`\)", "ElMessage.error(t('report.msg.exportFailed', { msg: task.errorMessage || t('report.msg.unknownError') }))"
$content = $content -replace "'未知错误'", "t('report.msg.unknownError')"
Set-Content $file -Value $content -NoNewline
Write-Host " OK"

# Process ReportList.vue
Write-Host "Processing ReportList.vue..." -NoNewline
$file = Join-Path $srcDir "views\report\ReportList.vue"
$content = Get-Content $file -Raw
$content = Add-UseI18nImport $content
$content = Add-TDeclaration $content
$content = $content -replace "ElMessage\.success\('删除成功'\)", "ElMessage.success(t('report.msg.deleteSuccess'))"
$content = $content -replace "ElMessage\.success\(`物化视图 \$\{mvName\} 刷新成功`\)", "ElMessage.success(t('report.msg.mvRefreshSuccess', { name: mvName }))"
# REPORT_TYPE_OPTIONS
$content = $content -replace "'图表'", "t('report.type.chart')"
$content = $content -replace "'混合'", "t('report.type.mix')"
$content = $content -replace "'表格'", "t('report.type.table')"
$content = $content -replace "'指标卡'", "t('report.type.metric')"
# STATUS_OPTIONS
$content = $content -replace "'草稿'", "t('report.status.draft')"
$content = $content -replace "'已发布'", "t('report.status.published')"
$content = $content -replace "'已归档'", "t('report.status.archived')"
Set-Content $file -Value $content -NoNewline
Write-Host " OK"

# Update locale JSON files
Write-Host "`nUpdating locale JSON files..."

# dashboard.json
$zh = Get-Content (Join-Path $localesZhDir "dashboard.json") -Raw | ConvertFrom-Json
$en = Get-Content (Join-Path $localesEnDir "dashboard.json") -Raw | ConvertFrom-Json
$zh | Add-Member -NotePropertyName 'dashboard.msg.loadFailed' -NotePropertyValue '加载大屏失败' -Force
$en | Add-Member -NotePropertyName 'dashboard.msg.loadFailed' -NotePropertyValue 'Failed to load dashboard' -Force
$zh | ConvertTo-Json -Depth 3 | Set-Content (Join-Path $localesZhDir "dashboard.json")
$en | ConvertTo-Json -Depth 3 | Set-Content (Join-Path $localesEnDir "dashboard.json")
Write-Host "  dashboard.json updated"

# inventory.json
$zh = Get-Content (Join-Path $localesZhDir "inventory.json") -Raw | ConvertFrom-Json
$en = Get-Content (Join-Path $localesEnDir "inventory.json") -Raw | ConvertFrom-Json
$zh | Add-Member -NotePropertyName 'inventory.msg.materialRequired' -NotePropertyValue '物料编码和名称不能为空' -Force
$en | Add-Member -NotePropertyName 'inventory.msg.materialRequired' -NotePropertyValue 'Material code and name cannot be empty' -Force
$zh | Add-Member -NotePropertyName 'inventory.msg.materialCreated' -NotePropertyValue '物料创建成功' -Force
$en | Add-Member -NotePropertyName 'inventory.msg.materialCreated' -NotePropertyValue 'Material created successfully' -Force
$zh | Add-Member -NotePropertyName 'inventory.materialType.electronic' -NotePropertyValue '电子件' -Force
$en | Add-Member -NotePropertyName 'inventory.materialType.electronic' -NotePropertyValue 'Electronic' -Force
$zh | Add-Member -NotePropertyName 'inventory.materialType.mechanical' -NotePropertyValue '机械件' -Force
$en | Add-Member -NotePropertyName 'inventory.materialType.mechanical' -NotePropertyValue 'Mechanical' -Force
$zh | Add-Member -NotePropertyName 'inventory.materialType.accessory' -NotePropertyValue '附件' -Force
$en | Add-Member -NotePropertyName 'inventory.materialType.accessory' -NotePropertyValue 'Accessory' -Force
$zh | Add-Member -NotePropertyName 'inventory.materialType.general' -NotePropertyValue '通用' -Force
$en | Add-Member -NotePropertyName 'inventory.materialType.general' -NotePropertyValue 'General' -Force
$zh | ConvertTo-Json -Depth 3 | Set-Content (Join-Path $localesZhDir "inventory.json")
$en | ConvertTo-Json -Depth 3 | Set-Content (Join-Path $localesEnDir "inventory.json")
Write-Host "  inventory.json updated"

# ai.json
$zh = Get-Content (Join-Path $localesZhDir "ai.json") -Raw | ConvertFrom-Json
$en = Get-Content (Join-Path $localesEnDir "ai.json") -Raw | ConvertFrom-Json
$zh | Add-Member -NotePropertyName 'ai.msg.healthQueryFailed' -NotePropertyValue '查询健康状态失败, 请稍后重试' -Force
$en | Add-Member -NotePropertyName 'ai.msg.healthQueryFailed' -NotePropertyValue 'Health query failed, please try again later' -Force
$zh | Add-Member -NotePropertyName 'ai.msg.healthCheckDone' -NotePropertyValue '健康检查完成, 共 {count} 个供应商' -Force
$en | Add-Member -NotePropertyName 'ai.msg.healthCheckDone' -NotePropertyValue 'Health check completed, {count} providers' -Force
$zh | Add-Member -NotePropertyName 'ai.msg.healthCheckFailed' -NotePropertyValue '健康检查失败, 请稍后重试' -Force
$en | Add-Member -NotePropertyName 'ai.msg.healthCheckFailed' -NotePropertyValue 'Health check failed, please try again later' -Force
$zh | ConvertTo-Json -Depth 3 | Set-Content (Join-Path $localesZhDir "ai.json")
$en | ConvertTo-Json -Depth 3 | Set-Content (Join-Path $localesEnDir "ai.json")
Write-Host "  ai.json updated"

# report.json
$zh = Get-Content (Join-Path $localesZhDir "report.json") -Raw | ConvertFrom-Json
$en = Get-Content (Join-Path $localesEnDir "report.json") -Raw | ConvertFrom-Json
$zh | Add-Member -NotePropertyName 'report.msg.exportDone' -NotePropertyValue '导出完成，共 {rows} 行' -Force
$en | Add-Member -NotePropertyName 'report.msg.exportDone' -NotePropertyValue 'Export completed, {rows} rows' -Force
$zh | Add-Member -NotePropertyName 'report.msg.exportFailed' -NotePropertyValue '导出失败: {msg}' -Force
$en | Add-Member -NotePropertyName 'report.msg.exportFailed' -NotePropertyValue 'Export failed: {msg}' -Force
$zh | Add-Member -NotePropertyName 'report.msg.unknownError' -NotePropertyValue '未知错误' -Force
$en | Add-Member -NotePropertyName 'report.msg.unknownError' -NotePropertyValue 'Unknown error' -Force
$zh | Add-Member -NotePropertyName 'report.msg.deleteSuccess' -NotePropertyValue '删除成功' -Force
$en | Add-Member -NotePropertyName 'report.msg.deleteSuccess' -NotePropertyValue 'Delete successful' -Force
$zh | Add-Member -NotePropertyName 'report.msg.mvRefreshSuccess' -NotePropertyValue '物化视图 {name} 刷新成功' -Force
$en | Add-Member -NotePropertyName 'report.msg.mvRefreshSuccess' -NotePropertyValue 'Materialized view {name} refreshed successfully' -Force
$zh | Add-Member -NotePropertyName 'report.type.chart' -NotePropertyValue '图表' -Force
$en | Add-Member -NotePropertyName 'report.type.chart' -NotePropertyValue 'Chart' -Force
$zh | Add-Member -NotePropertyName 'report.type.mix' -NotePropertyValue '混合' -Force
$en | Add-Member -NotePropertyName 'report.type.mix' -NotePropertyValue 'Mix' -Force
$zh | Add-Member -NotePropertyName 'report.type.table' -NotePropertyValue '表格' -Force
$en | Add-Member -NotePropertyName 'report.type.table' -NotePropertyValue 'Table' -Force
$zh | Add-Member -NotePropertyName 'report.type.metric' -NotePropertyValue '指标卡' -Force
$en | Add-Member -NotePropertyName 'report.type.metric' -NotePropertyValue 'Metric' -Force
$zh | Add-Member -NotePropertyName 'report.status.draft' -NotePropertyValue '草稿' -Force
$en | Add-Member -NotePropertyName 'report.status.draft' -NotePropertyValue 'Draft' -Force
$zh | Add-Member -NotePropertyName 'report.status.published' -NotePropertyValue '已发布' -Force
$en | Add-Member -NotePropertyName 'report.status.published' -NotePropertyValue 'Published' -Force
$zh | Add-Member -NotePropertyName 'report.status.archived' -NotePropertyValue '已归档' -Force
$en | Add-Member -NotePropertyName 'report.status.archived' -NotePropertyValue 'Archived' -Force
$zh | ConvertTo-Json -Depth 3 | Set-Content (Join-Path $localesZhDir "report.json")
$en | ConvertTo-Json -Depth 3 | Set-Content (Join-Path $localesEnDir "report.json")
Write-Host "  report.json updated"

Write-Host "`nAll done!"