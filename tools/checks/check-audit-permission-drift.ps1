# check-audit-permission-drift.ps1
# YuTong 机械门禁: @RequiresPermission / @Auditable 注解参数与 permissions.yaml 注册表 drift 校验
# Design source: 67-数据权限与审计日志详设 + YuTong-Java-Docs/contracts/registries/permissions.yaml
# 背景: WorkflowApplicationService.applyDataScope 泛型重载冲突曾导致编译失败连锁 100+ 假错误 (2026-08-03)
# 输出: build/reports/checks/audit-permission-drift.json + stdout 摘要
# 退出码: 有 FAIL 则 1, 否则 0

$ErrorActionPreference = 'Continue'
$projectRoot = 'd:\MyCode\YuTong-Java-Vue'
$permissionsYamlPath = Join-Path $projectRoot 'YuTong-Java-Docs\contracts\registries\permissions.yaml'
$backendRoot = Join-Path $projectRoot 'backend'

$results = @()
$pass = 0
$warn = 0
$fail = 0

function Add-Result($id, $status, $message) {
    $script:results += [PSCustomObject]@{ Id = $id; Status = $status; Message = $message }
    switch ($status) {
        'PASS' { $script:pass++ }
        'WARN' { $script:warn++ }
        'FAIL' { $script:fail++ }
    }
    Write-Host "[$status] $id`: $message"
}

# ---- 1. 解析 permissions.yaml 注册表 ----
$permContent = [System.IO.File]::ReadAllText($permissionsYamlPath, [System.Text.Encoding]::UTF8)
$registered = [System.Collections.Generic.HashSet[string]]::new()
[regex]::Matches($permContent, '\[(.*?)\]') | ForEach-Object {
    $_.Groups[1].Value -split ',' | ForEach-Object {
        $p = $_.Trim().Trim('"').Trim("'")
        if ($p -match '^[a-z]+(:[a-z0-9-]+)+$') { [void]$registered.Add($p) }
    }
}
if ($registered.Count -eq 0) {
    Add-Result 'PERM-000' 'FAIL' "permissions.yaml 未解析出任何权限条目"
    $report = @{ schemaVersion = '1.0.0'; generatedAt = (Get-Date).ToUniversalTime().ToString('o'); taskId = 'AUDIT-PERM-DRIFT'; status = 'FAIL'; passCount = $pass; warnCount = $warn; failCount = $fail; results = $results }
    $reportDir = Join-Path $projectRoot 'build\reports\checks'
    if (-not (Test-Path $reportDir)) { New-Item -ItemType Directory -Path $reportDir -Force | Out-Null }
    $reportPath = Join-Path $reportDir 'audit-permission-drift.json'
    [System.IO.File]::WriteAllText($reportPath, ($report | ConvertTo-Json -Depth 10), (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "Report saved: $reportPath"
    exit 1
}
Add-Result 'PERM-001' 'PASS' "permissions.yaml 注册表解析成功, 共 $($registered.Count) 个权限"

# ---- 2. 收集所有 Controller 注解 ----
$controllers = Get-ChildItem -Path $backendRoot -Recurse -Filter '*Controller.java' | Where-Object { $_.FullName -notmatch '\\target\\' }
$usedPerms = [System.Collections.Generic.HashSet[string]]::new()
$usedPermOwners = @{}
$auditableModules = [System.Collections.Generic.HashSet[string]]::new()
$auditableOps = [System.Collections.Generic.HashSet[string]]::new()
$auditableCount = 0
$auditableMissingContent = @()

foreach ($f in $controllers) {
    $c = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
    foreach ($m in [regex]::Matches($c, '@RequiresPermission\("([^"]+)"\)')) {
        $p = $m.Groups[1].Value
        [void]$usedPerms.Add($p)
        if (-not $usedPermOwners.ContainsKey($p)) { $usedPermOwners[$p] = @() }
        $usedPermOwners[$p] += $f.Name
    }
    foreach ($m in [regex]::Matches($c, '@Auditable\(([^)]*)\)')) {
        $auditableCount++
        $argText = $m.Groups[1].Value
        if ($argText -match 'module\s*=\s*"([^"]+)"') { [void]$auditableModules.Add($Matches[1]) }
        if ($argText -match 'operationType\s*=\s*"([^"]+)"') { [void]$auditableOps.Add($Matches[1]) }
        if ($argText -notmatch 'content\s*=') { $auditableMissingContent += ($f.Name + ': @Auditable 缺少 content 参数') }
    }
}

# ---- 3. @RequiresPermission 与注册表一致性 ----
$drift = $usedPerms | Where-Object { -not $registered.Contains($_) }
if ($drift.Count -eq 0) {
    Add-Result 'PERM-002' 'PASS' "@RequiresPermission 全部已注册 ($($usedPerms.Count) 个)"
} else {
    foreach ($d in $drift) {
        $owners = ($usedPermOwners[$d] -join ', ')
        Add-Result 'PERM-002' 'FAIL' "未注册权限: $d (使用于: $owners)"
    }
}

# 注册表中未被代码使用 (冗余提示, 不阻断)
$unused = $registered | Where-Object { -not $usedPerms.Contains($_) }
Add-Result 'PERM-003' 'WARN' "注册表冗余权限 $($unused.Count) 个 (已注册但未被 Controller 使用, 含移动端/预留权限)"

# ---- 4. @Auditable module/operationType 规范检查 ----
# 基线 = 代码实际使用集合 (2026-08-03 盘点) + 预留模块; 新增值将触发 WARN/FAIL 供人工确认
$allowedModules = @('system', 'sample', 'ai', 'workflow', 'lowcode', 'plugin', 'datasource', 'monitor', 'gateway', 'template', 'report', 'dashboard', 'widget', 'license', 'notification', 'ext-sync')
$badModules = $auditableModules | Where-Object { $_ -notin $allowedModules }
if ($badModules.Count -eq 0) {
    Add-Result 'PERM-004' 'PASS' "@Auditable module 均在规范集合内: $((($auditableModules | Sort-Object) -join '/'))"
} else {
    Add-Result 'PERM-004' 'FAIL' "@Auditable module 越界: $(($badModules -join ', ')) (需在脚本 allowedModules 中确认登记)"
}

$allowedOps = @('CREATE', 'UPDATE', 'DELETE', 'SUBMIT', 'APPROVE', 'REJECT', 'ARCHIVE', 'ACTION', 'SIGN', 'CANCEL', 'PUBLISH', 'DEPLOY', 'ENABLE', 'DISABLE', 'CHAT', 'APPLY', 'EXPORT', 'IMPORT', 'UPLOAD', 'DOWNLOAD', 'BIND', 'UNBIND', 'SEND', 'RESET', 'REFRESH', 'RETRY', 'TAG_ADD', 'TAG_REMOVE', 'LOGIN', 'LOGOUT', 'TRANSFER', 'DELEGATE', 'CALLBACK', 'TEST', 'SYNC', 'RESOLVE', 'GENERATE', 'ROLLBACK', 'TRIGGER', 'START', 'TERMINATE', 'COMPLETE', 'MASK', 'VIEW', 'READ', 'CHECK', 'ACTIVATE', 'DEACTIVATE', 'VERIFY', 'EVAL', 'PROCESS', 'REINDEX', 'ASK', 'RETRIEVE', 'HANDLE', 'RUN', 'INSTALL', 'UNINSTALL', 'EVALUATE', 'VALIDATE', 'CONFIRM', 'COMPENSATE', 'CLOSE', 'REFUND', 'EXPLAIN', 'WITHDRAW', 'START_COLLECTING', 'AI_GENERATE', 'MARK_READ', 'MARK_ALL_READ', 'CLEAR', 'SAVE')
$badOps = $auditableOps | Where-Object { $_ -notin $allowedOps }
if ($badOps.Count -eq 0) {
    Add-Result 'PERM-005' 'PASS' "@Auditable operationType 均在规范枚举内 ($($auditableOps.Count) 种)"
} else {
    Add-Result 'PERM-005' 'FAIL' "@Auditable operationType 越界: $(($badOps -join ', ')) (需在脚本 allowedOps 中确认登记)"
}

# ---- 5. @Auditable content 建议 (有默认值, 非阻断) ----
if ($auditableMissingContent.Count -eq 0) {
    Add-Result 'PERM-006' 'PASS' "@Auditable 全部含 content 参数 ($auditableCount 处)"
} else {
    Add-Result 'PERM-006' 'WARN' "$($auditableMissingContent.Count) 处 @Auditable 未显式指定 content (将使用 operationType 默认摘要): $($auditableMissingContent[0])"
}

# ---- 6. DataScope 分页覆盖 (启发式 WARN) ----
# 已知豁免: 平台级/租户级共享资源服务 (不在 67 号文档 10 个核心 DataScope 矩阵中, 已按 tenant_id 过滤)
#   依据: contracts/governance/data-scope-audit.yaml coreTableDataScopeMatrix
$dsExempt = @('DocumentIngestApplicationService', 'GeneratorTaskApplicationService', 'ComponentRegistryApplicationService', 'CodeTemplateApplicationService', 'PluginApplicationService', 'PluginRegistryApplicationService', 'TemplateApplicationService')
$serviceFiles = Get-ChildItem -Path $backendRoot -Recurse -Filter '*ApplicationService.java' | Where-Object { $_.FullName -notmatch '\\target\\' }
$dsCovered = 0
$dsTotal = 0
$dsExemptHit = 0
$dsMissing = @()
foreach ($f in $serviceFiles) {
    $c = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
    $hasPage = $c -match 'selectPage\(' -or $c -match 'public .*Page.*\('
    if ($hasPage) {
        $dsTotal++
        if ($c -match 'dataScopeResolver|applyDataScope|DataScopeFilter|checkDataScope') { $dsCovered++ }
        elseif ($f.BaseName -in $dsExempt) { $dsExemptHit++ }
        else { $dsMissing += $f.Name }
    }
}
Add-Result 'PERM-007' 'WARN' "分页查询服务 DataScope 覆盖 $dsCovered/$dsTotal; 豁免(平台级资源) $dsExemptHit; 未覆盖未豁免: $(($dsMissing -join ', ') -replace '^$', '无')"

# ---- 7. applyDataScope 方法定义重载冲突扫描 (2026-08-03 事故回归防护) ----
$overloadRisk = @()
foreach ($f in $serviceFiles) {
    $c = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
    $defs = [regex]::Matches($c, '(?:private|public|protected)\s+(?:static\s+)?[\w<>,\s]+?\bapplyDataScope\s*\(')
    if ($defs.Count -gt 1) {
        $signatures = @()
        foreach ($d in $defs) { $signatures += $d.Value.Trim() }
        $overloadRisk += "$($f.Name): $($signatures -join ' | ')"
    }
}
if ($overloadRisk.Count -eq 0) {
    Add-Result 'PERM-008' 'PASS' "无 applyDataScope 重载冲突 (泛型擦除回归防护通过)"
} else {
    Add-Result 'PERM-008' 'FAIL' "applyDataScope 重载冲突风险: $($overloadRisk -join '; ')"
}

# ---- 汇总 ----
Write-Host ""
Write-Host "Summary: PASS=$pass WARN=$warn FAIL=$fail"
$reportDir = Join-Path $projectRoot 'build\reports\checks'
if (-not (Test-Path $reportDir)) { New-Item -ItemType Directory -Path $reportDir -Force | Out-Null }
$report = @{
    schemaVersion = '1.0.0'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    taskId = 'AUDIT-PERM-DRIFT'
    designSource = '67-数据权限与审计日志详设 + permissions.yaml'
    status = if ($fail -eq 0) { 'PASS' } elseif ($fail -le 2) { 'WARN' } else { 'FAIL' }
    passCount = $pass
    warnCount = $warn
    failCount = $fail
    registeredPermissionCount = $registered.Count
    usedPermissionCount = $usedPerms.Count
    auditableCount = $auditableCount
    results = $results
}
$reportPath = Join-Path $reportDir 'audit-permission-drift.json'
[System.IO.File]::WriteAllText($reportPath, ($report | ConvertTo-Json -Depth 10), (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Report saved: $reportPath"

if ($fail -gt 0) { exit 1 } else { exit 0 }
