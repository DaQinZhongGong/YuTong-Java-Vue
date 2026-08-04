# GA2-77: Database ERD and Relation Check Machine Check Script
# Design source: 77-数据库ERD与关系校验详设.md (DOC-DAT-006)
# Verifies 77 doc's 4 acceptance criteria + DDL/code consistency
# Output: build/reports/checks/erd-verification-ga2-77.json + stdout summary

$ErrorActionPreference = 'Continue'
$projectRoot = 'd:\MyCode\YuTong-Java-Vue'
$docsRoot = Join-Path $projectRoot 'YuTong-Java-Docs'
$designDocPath = Join-Path $docsRoot '77-数据库ERD与关系校验详设\77-数据库ERD与关系校验详设.md'
$yamlPath = Join-Path $docsRoot 'contracts\governance\erd-verification.yaml'
$trackingPath = Join-Path $docsRoot '23-设计到落地追踪记录\23-设计到落地追踪记录.md'
$migrationsDir = Join-Path $projectRoot 'database\migrations'

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

# ERD-001: 77 design doc exists
if (Test-Path $designDocPath) {
    Add-Result 'ERD-001' 'PASS' "77 design doc exists"
} else {
    Add-Result 'ERD-001' 'FAIL' "77 design doc missing"
}

# ERD-002: erd-verification.yaml skeleton exists
if (Test-Path $yamlPath) {
    $size = (Get-Item $yamlPath).Length
    Add-Result 'ERD-002' 'PASS' "erd-verification.yaml exists ($size bytes)"
} else {
    Add-Result 'ERD-002' 'FAIL' "erd-verification.yaml missing"
}

$designContent = [System.IO.File]::ReadAllText($designDocPath, [System.Text.Encoding]::UTF8)
$yamlContent = [System.IO.File]::ReadAllText($yamlPath, [System.Text.Encoding]::UTF8)

# ERD-003: 11 ERD relations present (mermaid erDiagram)
$erdHits = 0
foreach ($k in @('sys_dict_type', 'sys_dict_item', 'sys_file', 'biz_file_rel', 'biz_customer', 'biz_request', 'biz_request_item', 'biz_approval_record', 'sys_todo_task', 'lc_entity', 'lc_field', 'lc_page', 'ai_conversation', 'ai_message', 'ai_knowledge_base', 'ai_document', 'sys_operation_log')) {
    if ($designContent -match [regex]::Escape($k)) { $erdHits++ }
}
if ($erdHits -ge 15) {
    Add-Result 'ERD-003' 'PASS' "ERD entities covered ($erdHits entities)"
} else {
    Add-Result 'ERD-003' 'FAIL' "ERD entities: $erdHits (<15 expected)"
}

# ERD-004: 7 relation check principles
$principleHits = 0
foreach ($k in @('字符串 ID', '数据库强外键', '索引', '状态字段', '字典或枚举', '审计字段', 'tenant_id', 'deleted')) {
    if ($designContent -match [regex]::Escape($k)) { $principleHits++ }
}
if ($principleHits -ge 7) {
    Add-Result 'ERD-004' 'PASS' "Relation check principles covered ($principleHits/8)"
} else {
    Add-Result 'ERD-004' "WARN" "Relation check principles: $principleHits/8"
}

# ERD-005: 7 core relations all present
$relationHits = 0
foreach ($k in @('biz_customer', 'biz_request', 'biz_request_item', 'biz_approval_record', 'sys_file', 'biz_file_rel', 'sys_todo_task', 'lc_entity', 'lc_field', 'ai_conversation', 'ai_message')) {
    if ($designContent -match [regex]::Escape($k)) { $relationHits++ }
}
if ($relationHits -ge 11) {
    Add-Result 'ERD-005' 'PASS' "Core relation tables covered ($relationHits/11)"
} else {
    Add-Result 'ERD-005' 'FAIL' "Core relation tables: $relationHits/11"
}

# ERD-006: 7 core relation indexes
$indexHits = 0
foreach ($k in @('idx_biz_request_customer', 'idx_biz_request_item_request', 'idx_biz_approval_record_request', 'idx_biz_file_rel_file', 'idx_sys_todo_assignee_status', 'idx_field_entity', 'idx_msg_conversation')) {
    if ($designContent -match [regex]::Escape($k)) { $indexHits++ }
}
if ($indexHits -ge 7) {
    Add-Result 'ERD-006' 'PASS' "7 core relation indexes all listed"
} else {
    Add-Result 'ERD-006' "WARN" "Core relation indexes: $indexHits/7"
}

# ERD-007: 8 field consistency checks (no _at suffix)
$fieldHits = 0
foreach ($k in @('varchar(32)', 'ULID', 'tenant_id', 'created_by', 'created_time', 'updated_by', 'updated_time', 'deleted', 'version')) {
    if ($designContent -match [regex]::Escape($k)) { $fieldHits++ }
}
if ($fieldHits -ge 8) {
    Add-Result 'ERD-007' 'PASS' "Field consistency checks covered ($fieldHits/9)"
} else {
    Add-Result 'ERD-007' 'WARN' "Field consistency checks: $fieldHits/9"
}

# ERD-008: forbidden _at suffix explicitly mentioned
if ($designContent -match 'created_at' -and $designContent -match 'updated_at' -and $designContent -match 'G0 阻断问题') {
    Add-Result 'ERD-008' 'PASS' "Forbidden _at suffix explicitly marked as G0 blocker"
} else {
    Add-Result 'ERD-008' 'WARN' "Forbidden _at suffix rule not explicit"
}

# ERD-009: 6 index check scenarios
$scenarioHits = 0
foreach ($k in @('租户隔离', '列表分页', '明细查询', '审计查询', 'AI 会话', '低代码字段')) {
    if ($designContent -match [regex]::Escape($k)) { $scenarioHits++ }
}
if ($scenarioHits -ge 6) {
    Add-Result 'ERD-009' 'PASS' "6 index check scenarios all present"
} else {
    Add-Result 'ERD-009' 'WARN' "Index scenarios: $scenarioHits/6"
}

# ERD-010: 2 DDL audit SQL examples
$sqlHits = 0
if ($designContent -match "information_schema.columns") { $sqlHits++ }
if ($designContent -match "serial.*bigserial|bigserial.*serial") { $sqlHits++ }
if ($sqlHits -ge 1 -and $designContent -match "column_name = 'id'") {
    Add-Result 'ERD-010' 'PASS' "DDL audit SQL examples present"
} else {
    Add-Result 'ERD-010' "WARN" "DDL audit SQL examples incomplete"
}

# ERD-011: 4 acceptance criteria
$criteriaHits = 0
foreach ($k in @('核心实体关系可画出 ERD', '关联字段有索引', '无自增主键', 'DDL 与数据字典字段一致')) {
    if ($designContent -match [regex]::Escape($k)) { $criteriaHits++ }
}
if ($criteriaHits -ge 4) {
    Add-Result 'ERD-011' 'PASS' "4 acceptance criteria all present"
} else {
    Add-Result 'ERD-011' 'FAIL' "Acceptance criteria: $criteriaHits/4"
}

# ERD-012: Flyway migrations directory exists
if (Test-Path $migrationsDir) {
    $migrationCount = (Get-ChildItem -Path $migrationsDir -Filter 'V*.sql').Count
    if ($migrationCount -ge 23) {
        Add-Result 'ERD-012' 'PASS' "Flyway V migrations count: $migrationCount (>=23)"
    } else {
        Add-Result 'ERD-012' "WARN" "Flyway V migrations count: $migrationCount (<23)"
    }
} else {
    Add-Result 'ERD-012' 'FAIL' "Migrations directory missing"
}

# ERD-013: V001~V005 core migrations exist (system + sample + lowcode + ai)
$coreMigrations = @('V001__init_extensions.sql', 'V002__init_system_tables.sql', 'V003__init_sample_tables.sql', 'V004__init_lowcode_tables.sql', 'V005__init_ai_tables.sql')
$coreHits = 0
foreach ($m in $coreMigrations) {
    $p = Join-Path $migrationsDir $m
    if (Test-Path $p) { $coreHits++ }
}
if ($coreHits -eq 5) {
    Add-Result 'ERD-013' 'PASS' "V001~V005 core migrations all exist"
} else {
    Add-Result 'ERD-013' 'FAIL' "Core migrations: $coreHits/5"
}

# ERD-014: V002 contains sys_operation_log, sys_login_log, sys_idempotency_record, biz_file_rel
$v002Path = Join-Path $migrationsDir 'V002__init_system_tables.sql'
if (Test-Path $v002Path) {
    $v002Content = [System.IO.File]::ReadAllText($v002Path, [System.Text.Encoding]::UTF8)
    $tableHits = 0
    foreach ($t in @('sys_operation_log', 'sys_login_log', 'sys_idempotency_record', 'sys_dict_type', 'sys_dict_item', 'sys_file', 'sys_todo_task', 'sys_outbox_event', 'biz_file_rel')) {
        if ($v002Content -match "CREATE TABLE $t") { $tableHits++ }
    }
    if ($tableHits -ge 9) {
        Add-Result 'ERD-014' 'PASS' "V002 contains $tableHits core system+bridge tables (>=9)"
    } else {
        Add-Result 'ERD-014' 'WARN' "V002 tables: $tableHits/9"
    }
} else {
    Add-Result 'ERD-014' 'FAIL' "V002 migration missing"
}

# ERD-015: V003 contains biz_customer, biz_request, biz_request_item, biz_approval_record (biz_file_rel in V002)
$v003Path = Join-Path $migrationsDir 'V003__init_sample_tables.sql'
if (Test-Path $v003Path) {
    $v003Content = [System.IO.File]::ReadAllText($v003Path, [System.Text.Encoding]::UTF8)
    $bizHits = 0
    foreach ($t in @('biz_customer', 'biz_product', 'biz_request', 'biz_request_item', 'biz_approval_record')) {
        if ($v003Content -match "CREATE TABLE $t") { $bizHits++ }
    }
    if ($bizHits -ge 5) {
        Add-Result 'ERD-015' 'PASS' "V003 contains $bizHits biz tables (>=5, biz_file_rel in V002)"
    } else {
        Add-Result 'ERD-015' 'WARN' "V003 biz tables: $bizHits/5"
    }
} else {
    Add-Result 'ERD-015' 'FAIL' "V003 migration missing"
}

# ERD-016: V004 contains lc_entity, lc_field, lc_page
$v004Path = Join-Path $migrationsDir 'V004__init_lowcode_tables.sql'
if (Test-Path $v004Path) {
    $v004Content = [System.IO.File]::ReadAllText($v004Path, [System.Text.Encoding]::UTF8)
    $lcHits = 0
    foreach ($t in @('lc_entity', 'lc_field', 'lc_page')) {
        if ($v004Content -match "CREATE TABLE $t") { $lcHits++ }
    }
    if ($lcHits -ge 3) {
        Add-Result 'ERD-016' 'PASS' "V004 contains $lcHits lc tables (>=3)"
    } else {
        Add-Result 'ERD-016' 'WARN' "V004 lc tables: $lcHits/3"
    }
} else {
    Add-Result 'ERD-016' 'FAIL' "V004 migration missing"
}

# ERD-017: V005 contains ai_conversation, ai_message, ai_knowledge_base
$v005Path = Join-Path $migrationsDir 'V005__init_ai_tables.sql'
if (Test-Path $v005Path) {
    $v005Content = [System.IO.File]::ReadAllText($v005Path, [System.Text.Encoding]::UTF8)
    $aiHits = 0
    foreach ($t in @('ai_conversation', 'ai_message', 'ai_knowledge_base', 'ai_document')) {
        if ($v005Content -match "CREATE TABLE $t") { $aiHits++ }
    }
    if ($aiHits -ge 4) {
        Add-Result 'ERD-017' 'PASS' "V005 contains $aiHits ai tables (>=4)"
    } else {
        Add-Result 'ERD-017' 'WARN' "V005 ai tables: $aiHits/4"
    }
} else {
    Add-Result 'ERD-017' 'FAIL' "V005 migration missing"
}

# ERD-018: No serial/bigserial in V001~V005
$serialHits = 0
foreach ($m in $coreMigrations) {
    $p = Join-Path $migrationsDir $m
    if (Test-Path $p) {
        $c = [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8)
        if ($c -match '\bserial\b' -or $c -match '\bbigserial\b') {
            $serialHits++
        }
    }
}
if ($serialHits -eq 0) {
    Add-Result 'ERD-018' 'PASS' "No serial/bigserial in V001~V005 (符合 77 号文档要求)"
} else {
    Add-Result 'ERD-018' 'FAIL' "Found serial/bigserial in $serialHits core migrations"
}

# ERD-019: No created_at/updated_at in V001~V005
$atHits = 0
foreach ($m in $coreMigrations) {
    $p = Join-Path $migrationsDir $m
    if (Test-Path $p) {
        $c = [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8)
        # Look for column definitions like "created_at" or "updated_at" but exclude "created_time" / "updated_time"
        $atMatches = [regex]::Matches($c, '\b(created_at|updated_at)\b')
        foreach ($am in $atMatches) {
            # Check context - if it's in a comment about forbidden, skip
            $start = [Math]::Max(0, $am.Index - 60)
            $len = [Math]::Min(120, $c.Length - $start)
            $context = $c.Substring($start, $len)
            if ($context -notmatch '禁止' -and $context -notmatch '不得' -and $context -notmatch 'forbidden') {
                $atHits++
            }
        }
    }
}
if ($atHits -eq 0) {
    Add-Result 'ERD-019' 'PASS' "No created_at/updated_at in V001~V005 (符合字段一致性要求)"
} else {
    Add-Result 'ERD-019' 'FAIL' "Found $atHits created_at/updated_at in core migrations"
}

# ERD-020: YAML erdRelations section with 11 items
$yamlRelCount = ([regex]::Matches($yamlContent, '^\s+- from: ', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlRelCount -ge 11) {
    Add-Result 'ERD-020' 'PASS' "YAML erdRelations has $yamlRelCount items (>=11)"
} else {
    Add-Result 'ERD-020' 'WARN' "YAML erdRelations: $yamlRelCount/11"
}

# ERD-021: YAML coreRelations section with 7 items
$yamlCoreCount = ([regex]::Matches($yamlContent, '^\s*- mainTable: ', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
if ($yamlCoreCount -ge 7) {
    Add-Result 'ERD-021' 'PASS' "YAML coreRelations has $yamlCoreCount items (>=7)"
} else {
    Add-Result 'ERD-021' 'WARN' "YAML coreRelations: $yamlCoreCount/7"
}

# ERD-022: YAML fieldConsistencyChecks section
if ($yamlContent -match 'fieldConsistencyChecks:' -and $yamlContent -match 'created_time' -and $yamlContent -match 'updated_time') {
    Add-Result 'ERD-022' 'PASS' "YAML fieldConsistencyChecks section present"
} else {
    Add-Result 'ERD-022' 'FAIL' "YAML fieldConsistencyChecks section missing"
}

# ERD-023: YAML forbiddenPatterns section
if ($yamlContent -match 'forbiddenPatterns:' -and $yamlContent -match 'serial/bigserial' -and $yamlContent -match 'created_at' -and $yamlContent -match 'updated_at') {
    Add-Result 'ERD-023' 'PASS' "YAML forbiddenPatterns section present"
} else {
    Add-Result 'ERD-023' 'WARN' "YAML forbiddenPatterns section incomplete"
}

# ERD-024: YAML tableBaseline with 37 tables
if ($yamlContent -match 'tableBaseline:' -and $yamlContent -match 'total: 37') {
    Add-Result 'ERD-024' 'PASS' "YAML tableBaseline 37 tables documented"
} else {
    Add-Result 'ERD-024' 'FAIL' "YAML tableBaseline missing"
}

# ERD-025: 23 tracking record has 77-数据库ERD entry
if (Test-Path $trackingPath) {
    $trackingContent = [System.IO.File]::ReadAllText($trackingPath, [System.Text.Encoding]::UTF8)
    if ($trackingContent -match '77-数据库ERD') {
        Add-Result 'ERD-025' 'PASS' "23 tracking record has 77-数据库ERD entry"
    } else {
        Add-Result 'ERD-025' 'FAIL' "23 tracking record missing 77 entry"
    }
} else {
    Add-Result 'ERD-025' 'FAIL' "23 tracking record missing"
}

# ERD-026: 37 tables baseline match (ai 10 + biz 6 + lc 7 + sys 14)
if ($trackingContent -match '37/37 表完整匹配 GA 基线') {
    Add-Result 'ERD-026' 'PASS' "37/37 tables baseline match documented in tracking record"
} else {
    Add-Result 'ERD-026' 'WARN' "37 tables baseline match not documented"
}

# ERD-027: ULID primary key documented
if ($trackingContent -match '主键全 varchar ULID') {
    Add-Result 'ERD-027' 'PASS' "ULID primary key documented in tracking record"
} else {
    Add-Result 'ERD-027' 'WARN' "ULID primary key not documented"
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
    taskId = 'GA2-77'
    designDoc = '77-数据库ERD与关系校验详设'
    status = if ($fail -eq 0) { 'PASS' } elseif ($fail -le 2) { 'WARN' } else { 'FAIL' }
    passCount = $pass
    warnCount = $warn
    failCount = $fail
    results = $results
}
$reportPath = Join-Path $reportDir 'erd-verification-ga2-77.json'
$utf8Bom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($reportPath, ($report | ConvertTo-Json -Depth 10), $utf8Bom)
Write-Host "Report saved: $reportPath"

if ($fail -gt 0) { exit 1 } else { exit 0 }
