# GA2-67: Data Scope and Audit Log Machine Check Script
# Design source: 67-数据权限与审计日志详设.md (DOC-SEC-006)
# Verifies 67 doc's 6 acceptance criteria + consistency with permissions.yaml/code/DDL
# Output: build/reports/checks/data-scope-audit-ga2-67.json + stdout summary

$ErrorActionPreference = 'Continue'
$projectRoot = 'd:\MyCode\YuTong-Java-Vue'
$docsRoot = Join-Path $projectRoot 'YuTong-Java-Docs'
$designDocPath = Join-Path $docsRoot '67-数据权限与审计日志详设\67-数据权限与审计日志详设.md'
$yamlPath = Join-Path $docsRoot 'contracts\governance\data-scope-audit.yaml'
$permissionsYamlPath = Join-Path $docsRoot 'contracts\registries\permissions.yaml'
$trackingPath = Join-Path $docsRoot '23-设计到落地追踪记录\23-设计到落地追踪记录.md'

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

# DS-001: 67 design doc exists
if (Test-Path $designDocPath) {
    Add-Result 'DS-001' 'PASS' "67 design doc exists"
} else {
    Add-Result 'DS-001' 'FAIL' "67 design doc missing"
}

# DS-002: data-scope-audit.yaml skeleton exists
if (Test-Path $yamlPath) {
    $size = (Get-Item $yamlPath).Length
    Add-Result 'DS-002' 'PASS' "data-scope-audit.yaml exists ($size bytes)"
} else {
    Add-Result 'DS-002' 'FAIL' "data-scope-audit.yaml missing"
}

$designContent = [System.IO.File]::ReadAllText($designDocPath, [System.Text.Encoding]::UTF8)
$yamlContent = [System.IO.File]::ReadAllText($yamlPath, [System.Text.Encoding]::UTF8)

# DS-003: 7 DataScope types all present
$scopes = @('ALL', 'TENANT', 'SELF', 'DEPT', 'DEPT_AND_CHILD', 'CUSTOM', 'NONE')
$scopeHits = 0
foreach ($s in $scopes) {
    if ($designContent -match "\b$([regex]::Escape($s))\b") { $scopeHits++ }
}
if ($scopeHits -eq 7) {
    Add-Result 'DS-003' 'PASS' "7 DataScope types all present"
} else {
    Add-Result 'DS-003' 'FAIL' "DataScope types: $scopeHits/7"
}

# DS-004: DEPT_TREE deprecated (should NOT appear)
if ($designContent -match 'DEPT_TREE') {
    $prohibitionContext = @('废弃', '不使用', '不得', '禁止', '历史口径')
    $matches = [regex]::Matches($designContent, 'DEPT_TREE')
    $realHit = $false
    foreach ($m in $matches) {
        $start = [Math]::Max(0, $m.Index - 60)
        $len = [Math]::Min(120, $designContent.Length - $start)
        $context = $designContent.Substring($start, $len)
        $isProhibition = $false
        foreach ($p in $prohibitionContext) {
            if ($context -match [regex]::Escape($p)) { $isProhibition = $true; break }
        }
        if (-not $isProhibition) { $realHit = $true; break }
    }
    if ($realHit) {
        Add-Result 'DS-004' 'FAIL' "DEPT_TREE appears in non-prohibition context (should be deprecated)"
    } else {
        Add-Result 'DS-004' 'PASS' "DEPT_TREE only in deprecation context"
    }
} else {
    Add-Result 'DS-004' 'PASS' "DEPT_TREE absent (correctly deprecated)"
}

# DS-005: DataScope SQL conversion rules 7 branches
$sqlRuleHits = 0
foreach ($k in @('tenant_id', 'owner_user_id', 'owner_dept_id', 'owner_dept_path', 'id in', '1 = 0', 'deleted=false')) {
    if ($designContent -match [regex]::Escape($k)) { $sqlRuleHits++ }
}
if ($sqlRuleHits -ge 6) {
    Add-Result 'DS-005' 'PASS' "DataScope SQL conversion rules covered ($sqlRuleHits/7)"
} else {
    Add-Result 'DS-005' "WARN" "DataScope SQL rules: $sqlRuleHits/7"
}

# DS-006: 10 core table DataScope matrix
$matrixHits = 0
foreach ($k in @('biz:request', 'biz:customer', 'biz:product', 'sys:todo', 'sys:file', 'sys:operation-log', 'sys:login-log', 'sys:import-export-task', 'ai:tool-log', 'lc:page')) {
    if ($designContent -match [regex]::Escape($k)) { $matrixHits++ }
}
if ($matrixHits -ge 10) {
    Add-Result 'DS-006' 'PASS' "10 core table DataScope matrix all present"
} else {
    Add-Result 'DS-006' "WARN" "Core table matrix: $matrixHits/10"
}

# DS-007: 5 data scope test cases
$testCaseHits = 0
foreach ($k in @('业务人员查询申请单列表', '审核人员打开移动待办', 'fileId 下载他人业务附件', 'CUSTOM 白名单为空', 'AI A3 只读工具')) {
    if ($designContent -match [regex]::Escape($k)) { $testCaseHits++ }
}
if ($testCaseHits -ge 5) {
    Add-Result 'DS-007' 'PASS' "5 DataScope test cases all present"
} else {
    Add-Result 'DS-007' 'FAIL' "Test cases: $testCaseHits/5"
}

# DS-008: 5 field masking rules
$maskingHits = 0
foreach ($k in @('contact_phone', 'email', 'api_key_ref', 'authorization', 'ai prompt')) {
    if ($designContent -match [regex]::Escape($k)) { $maskingHits++ }
}
if ($maskingHits -ge 5) {
    Add-Result 'DS-008' 'PASS' "5 field masking rules all present"
} else {
    Add-Result 'DS-008' "WARN" "Masking rules: $maskingHits/5"
}

# DS-009: sys_operation_log 14+ fields present in DDL
$opLogDdlPath = Join-Path $projectRoot 'database\migrations\V002__init_system_tables.sql'
if (Test-Path $opLogDdlPath) {
    $ddlContent = [System.IO.File]::ReadAllText($opLogDdlPath, [System.Text.Encoding]::UTF8)
    $opLogSection = $ddlContent
    $opLogFields = @('operation_type', 'module', 'biz_type', 'biz_id', 'content', 'before_json', 'after_json', 'result', 'error_code', 'trace_id', 'operator_id', 'operator_name', 'ip', 'user_agent', 'operated_time')
    $opLogHits = 0
    foreach ($f in $opLogFields) {
        if ($opLogSection -match [regex]::Escape($f)) { $opLogHits++ }
    }
    if ($opLogHits -ge 15) {
        Add-Result 'DS-009' 'PASS' "sys_operation_log 15 fields all in DDL"
    } else {
        Add-Result 'DS-009' 'FAIL' "sys_operation_log fields: $opLogHits/15"
    }
} else {
    Add-Result 'DS-009' 'FAIL' "V002 migration missing"
}

# DS-010: sys_login_log fields in DDL
if ($ddlContent) {
    $loginLogFields = @('login_type', 'login_result', 'fail_reason', 'user_id', 'username', 'tenant_id', 'token_id', 'device_type', 'ip', 'user_agent', 'location', 'trace_id', 'login_time', 'logout_time')
    $loginHits = 0
    foreach ($f in $loginLogFields) {
        if ($ddlContent -match [regex]::Escape($f)) { $loginHits++ }
    }
    if ($loginHits -ge 14) {
        Add-Result 'DS-010' 'PASS' "sys_login_log 14 fields all in DDL"
    } else {
        Add-Result 'DS-010' 'FAIL' "sys_login_log fields: $loginHits/14"
    }
}

# DS-011: sys_idempotency_record table exists in DDL
if ($ddlContent -match 'sys_idempotency_record') {
    Add-Result 'DS-011' 'PASS' "sys_idempotency_record table in DDL"
} else {
    Add-Result 'DS-011' 'FAIL' "sys_idempotency_record missing in DDL"
}

# DS-012: 3 login log indexes
$idxHits = 0
foreach ($k in @('idx_login_log_user', 'idx_login_log_result', 'idx_login_log_trace')) {
    if ($ddlContent -match [regex]::Escape($k)) { $idxHits++ }
}
if ($idxHits -ge 3) {
    Add-Result 'DS-012' 'PASS' "3 login log indexes in DDL"
} else {
    Add-Result 'DS-012' 'FAIL' "Login log indexes: $idxHits/3"
}

# DS-013: DataScopeType.java exists with 7 enum values
$dataScopeTypePath = Join-Path $projectRoot 'backend\yutong-common\src\main\java\com\yutong\common\auth\DataScopeType.java'
if (Test-Path $dataScopeTypePath) {
    $javaContent = [System.IO.File]::ReadAllText($dataScopeTypePath, [System.Text.Encoding]::UTF8)
    $enumHits = 0
    foreach ($s in $scopes) {
        if ($javaContent -match "^\s*$([regex]::Escape($s))\s*[,;]" -or $javaContent -match "\b$([regex]::Escape($s))\b") { $enumHits++ }
    }
    if ($enumHits -ge 7) {
        Add-Result 'DS-013' 'PASS' "DataScopeType.java has 7 enum values"
    } else {
        Add-Result 'DS-013' 'FAIL' "DataScopeType.java enum values: $enumHits/7"
    }
} else {
    Add-Result 'DS-013' 'FAIL' "DataScopeType.java missing"
}

# DS-014: DataScope.java record with 9 fields
$dataScopePath = Join-Path $projectRoot 'backend\yutong-common\src\main\java\com\yutong\common\auth\DataScope.java'
if (Test-Path $dataScopePath) {
    $dsContent = [System.IO.File]::ReadAllText($dataScopePath, [System.Text.Encoding]::UTF8)
    $dsFieldHits = 0
    foreach ($f in @('scopeType', 'userId', 'tenantId', 'resourceCode', 'deptIds', 'deptPathPrefixes', 'resourceIds', 'ownerUserIds', 'includeSensitive')) {
        if ($dsContent -match [regex]::Escape($f)) { $dsFieldHits++ }
    }
    if ($dsFieldHits -ge 9) {
        Add-Result 'DS-014' 'PASS' "DataScope.java record 9 fields present"
    } else {
        Add-Result 'DS-014' 'FAIL' "DataScope.java fields: $dsFieldHits/9"
    }
} else {
    Add-Result 'DS-014' 'FAIL' "DataScope.java missing"
}

# DS-015: DataScopeFilter.java exists
$dataScopeFilterPath = Join-Path $projectRoot 'backend\yutong-infra\src\main\java\com\yutong\infra\persistence\DataScopeFilter.java'
if (Test-Path $dataScopeFilterPath) {
    Add-Result 'DS-015' 'PASS' "DataScopeFilter.java exists"
} else {
    Add-Result 'DS-015' 'FAIL' "DataScopeFilter.java missing"
}

# DS-016: @Auditable annotation exists
$auditablePath = Join-Path $projectRoot 'backend\yutong-system-service\src\main\java\com\yutong\system\log\auditable\Auditable.java'
if (Test-Path $auditablePath) {
    Add-Result 'DS-016' 'PASS' "@Auditable annotation exists"
} else {
    Add-Result 'DS-016' 'FAIL' "@Auditable annotation missing"
}

# DS-017: AuditableAspect AOP exists
$aspectPath = Join-Path $projectRoot 'backend\yutong-boot\src\main\java\com\yutong\boot\config\AuditableAspect.java'
if (Test-Path $aspectPath) {
    Add-Result 'DS-017' 'PASS' "AuditableAspect AOP exists"
} else {
    Add-Result 'DS-017' 'FAIL' "AuditableAspect missing"
}

# DS-018: permissions.yaml scopes.data aligns with 7 types
if (Test-Path $permissionsYamlPath) {
    $permContent = [System.IO.File]::ReadAllText($permissionsYamlPath, [System.Text.Encoding]::UTF8)
    if ($permContent -match 'scopes:\s*\n\s*data:\s*\[ALL,\s*TENANT,\s*DEPT_AND_CHILD,\s*DEPT,\s*SELF,\s*CUSTOM,\s*NONE\]') {
        Add-Result 'DS-018' 'PASS' "permissions.yaml scopes.data aligns with 7 DataScope types"
    } else {
        $permHits = 0
        foreach ($s in $scopes) {
            if ($permContent -match "\b$([regex]::Escape($s))\b") { $permHits++ }
        }
        if ($permHits -ge 7) {
            Add-Result 'DS-018' 'PASS' "permissions.yaml contains 7 DataScope types"
        } else {
            Add-Result 'DS-018' 'FAIL' "permissions.yaml DataScope types: $permHits/7"
        }
    }
} else {
    Add-Result 'DS-018' 'FAIL' "permissions.yaml missing"
}

# DS-019: 3 audit query APIs present in design doc
$apiHits = 0
foreach ($k in @('/api/v1/operation-logs', '/api/v1/operation-logs/{id}', '/api/v1/ai/tool-call-logs')) {
    if ($designContent -match [regex]::Escape($k)) { $apiHits++ }
}
if ($apiHits -ge 3) {
    Add-Result 'DS-019' 'PASS' "3 audit query APIs all present"
} else {
    Add-Result 'DS-019' 'WARN' "Audit query APIs: $apiHits/3"
}

# DS-020: Internal-only APIs not exposed (/login-logs, /idempotency-records)
if ($designContent -match '不向普通管理端公开' -and $designContent -match '/login-logs' -and $designContent -match '/idempotency-records') {
    Add-Result 'DS-020' 'PASS' "Internal-only APIs documented as not exposed"
} else {
    Add-Result 'DS-020' 'WARN' "Internal-only API policy not fully documented"
}

# DS-021: 6 acceptance criteria all present
$criteriaHits = 0
foreach ($k in @('DataScope 枚举', '数据权限测试', 'sys_login_log', '操作审计和幂等记录', '联系电话在列表页默认脱敏', 'AI 工具调用可追踪')) {
    if ($designContent -match [regex]::Escape($k)) { $criteriaHits++ }
}
if ($criteriaHits -ge 6) {
    Add-Result 'DS-021' 'PASS' "6 acceptance criteria all present"
} else {
    Add-Result 'DS-021' 'FAIL' "Acceptance criteria: $criteriaHits/6"
}

# DS-022: YAML dataScopeTypes section with 7 items
$yamlScopeCount = ([regex]::Matches($yamlContent, '^\s+- code: (ALL|TENANT|SELF|DEPT|DEPT_AND_CHILD|CUSTOM|NONE)', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlScopeCount -ge 7) {
    Add-Result 'DS-022' 'PASS' "YAML dataScopeTypes has 7 items"
} else {
    Add-Result 'DS-022' 'FAIL' "YAML dataScopeTypes count: $yamlScopeCount/7"
}

# DS-023: YAML coreTableDataScopeMatrix with 10 items
$yamlMatrixCount = ([regex]::Matches($yamlContent, '^\s+- resource: ', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlMatrixCount -ge 10) {
    Add-Result 'DS-023' 'PASS' "YAML coreTableDataScopeMatrix has $yamlMatrixCount items (>=10)"
} else {
    Add-Result 'DS-023' 'WARN' "YAML matrix items: $yamlMatrixCount/10"
}

# DS-024: YAML operationLogFields and loginLogFields sections
if ($yamlContent -match 'operationLogFields:' -and $yamlContent -match 'loginLogFields:') {
    Add-Result 'DS-024' 'PASS' "YAML operationLogFields and loginLogFields sections present"
} else {
    Add-Result 'DS-024' 'FAIL' "YAML log fields sections missing"
}

# DS-025: YAML auditPolicies section with 9 items
$yamlPolicyCount = ([regex]::Matches($yamlContent, '^\s*- operation: ', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlPolicyCount -ge 9) {
    Add-Result 'DS-025' 'PASS' "YAML auditPolicies has $yamlPolicyCount items (>=9)"
} else {
    Add-Result 'DS-025' 'WARN' "YAML auditPolicies: $yamlPolicyCount/9"
}

# DS-026: 23 tracking record has 67-数据权限 entry
if (Test-Path $trackingPath) {
    $trackingContent = [System.IO.File]::ReadAllText($trackingPath, [System.Text.Encoding]::UTF8)
    if ($trackingContent -match '67-数据权限') {
        Add-Result 'DS-026' 'PASS' "23 tracking record has 67-数据权限 entry"
    } else {
        Add-Result 'DS-026' 'FAIL' "23 tracking record missing 67 entry"
    }
} else {
    Add-Result 'DS-026' 'FAIL' "23 tracking record missing"
}

# DS-027: CUSTOM empty whitelist security default (must not fallback to ALL)
if ($designContent -match '白名单为空' -and $designContent -match '不得降级为 ALL') {
    Add-Result 'DS-027' 'PASS' "CUSTOM empty whitelist security default documented (no fallback to ALL)"
} else {
    Add-Result 'DS-027' 'FAIL' "CUSTOM security default not documented"
}

# DS-028: NONE security default (must not fallback to TENANT/ALL)
if ($designContent -match '禁止回退 TENANT/ALL' -or $designContent -match '禁止回退') {
    Add-Result 'DS-028' 'PASS' "NONE security default documented (no fallback to TENANT/ALL)"
} else {
    Add-Result 'DS-028' 'WARN' "NONE security default not fully documented"
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
    taskId = 'GA2-67'
    designDoc = '67-数据权限与审计日志详设'
    status = if ($fail -eq 0) { 'PASS' } elseif ($fail -le 2) { 'WARN' } else { 'FAIL' }
    passCount = $pass
    warnCount = $warn
    failCount = $fail
    results = $results
}
$reportPath = Join-Path $reportDir 'data-scope-audit-ga2-67.json'
$utf8Bom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($reportPath, ($report | ConvertTo-Json -Depth 10), $utf8Bom)
Write-Host "Report saved: $reportPath"

if ($fail -gt 0) { exit 1 } else { exit 0 }
