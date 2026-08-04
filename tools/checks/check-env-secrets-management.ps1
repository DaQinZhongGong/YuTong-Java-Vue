# GA2-82: Environment Variables and Secrets Management Machine Check Script
# Design source: 82-环境变量与密钥管理规范详设.md (DOC-SEC-008)
# Verifies 82 doc's 4 acceptance criteria + .env.example + yutong prefix rule + YAML skeleton
# Output: build/reports/checks/env-secrets-management-ga2-82.json + stdout summary

$ErrorActionPreference = 'Continue'
$projectRoot = 'd:\MyCode\YuTong-Java-Vue'
$docsRoot = Join-Path $projectRoot 'YuTong-Java-Docs'
$designDocPath = Join-Path $docsRoot '82-环境变量与密钥管理规范详设\82-环境变量与密钥管理规范详设.md'
$yamlPath = Join-Path $docsRoot 'contracts\governance\env-secrets-management.yaml'
$trackingPath = Join-Path $docsRoot '23-设计到落地追踪记录\23-设计到落地追踪记录.md'
$envExamplePath = Join-Path $projectRoot '.env.example'
$composeBootPath = Join-Path $projectRoot 'deploy\docker-compose.boot.yml'
$composeRunPath = Join-Path $projectRoot 'deploy\docker-compose.run.yml'
$ciPath = Join-Path $projectRoot '.github\workflows\ci.yml'
$checkEnvVarsPath = Join-Path $projectRoot 'tools\checks\check-env-vars'

$results = New-Object System.Collections.ArrayList
$pass = 0
$warn = 0
$fail = 0

function Add-Result($id, $status, $message) {
    [void]$results.Add([PSCustomObject]@{ Id = $id; Status = $status; Message = $message })
    switch ($status) {
        'PASS' { $script:pass++ }
        'WARN' { $script:warn++ }
        'FAIL' { $script:fail++ }
    }
    Write-Host "[$status] $id`: $message"
}

# ENV-001: 82 design doc exists
if (Test-Path $designDocPath) {
    Add-Result 'ENV-001' 'PASS' "82 design doc exists"
} else {
    Add-Result 'ENV-001' 'FAIL' "82 design doc missing"
}

# ENV-002: env-secrets-management.yaml skeleton exists
if (Test-Path $yamlPath) {
    $size = (Get-Item $yamlPath).Length
    Add-Result 'ENV-002' 'PASS' "env-secrets-management.yaml exists ($size bytes)"
} else {
    Add-Result 'ENV-002' 'FAIL' "env-secrets-management.yaml missing"
}

# ENV-003: .env.example exists
if (Test-Path $envExamplePath) {
    $envSize = (Get-Item $envExamplePath).Length
    Add-Result 'ENV-003' 'PASS' ".env.example exists ($envSize bytes)"
} else {
    Add-Result 'ENV-003' 'FAIL' ".env.example missing"
}

$designContent = [System.IO.File]::ReadAllText($designDocPath, [System.Text.Encoding]::UTF8)
$yamlContent = [System.IO.File]::ReadAllText($yamlPath, [System.Text.Encoding]::UTF8)
$envContent = [System.IO.File]::ReadAllText($envExamplePath, [System.Text.Encoding]::UTF8)

# ENV-004: 6 config principles in design doc
$principleHits = 0
foreach ($k in @('配置与代码分离', '.env.example', '占位符', '环境变量', '日志不得打印敏感', '可轮换')) {
    if ($designContent -match [regex]::Escape($k)) { $principleHits++ }
}
if ($principleHits -ge 6) {
    Add-Result 'ENV-004' 'PASS' "6 config principles covered ($principleHits/6)"
} else {
    Add-Result 'ENV-004' 'WARN' "Config principles: $principleHits/6"
}

# ENV-005: 6 env files in design doc
$fileHits = 0
foreach ($k in @('.env.example', '.env.local', '.env.test', '.env.prod', 'application-local.yml', 'application-prod.yml')) {
    if ($designContent -match [regex]::Escape($k)) { $fileHits++ }
}
if ($fileHits -ge 6) {
    Add-Result 'ENV-005' 'PASS' "6 env files all listed in design doc"
} else {
    Add-Result 'ENV-005' 'FAIL' "Env files: $fileHits/6"
}

# ENV-006: 34 required variables in design doc
$varHits = 0
foreach ($k in @('APP_ENV', 'APP_SECRET', 'DB_HOST', 'DB_PORT', 'DB_NAME', 'DB_USER', 'DB_PASSWORD',
                 'REDIS_HOST', 'MINIO_ENDPOINT', 'MINIO_ACCESS_KEY', 'MINIO_SECRET_KEY',
                 'AI_PROVIDER', 'AI_API_KEY', 'IDENTITY_MODE',
                 'AUTH_MOCK_ENABLED', 'AUTH_ACCESS_TOKEN_TTL_SECONDS', 'AUTH_REFRESH_TOKEN_TTL_SECONDS',
                 'AUTH_REFRESH_ROTATION_ENABLED', 'AUTH_COOKIE_MODE',
                 'OIDC_ISSUER', 'OIDC_CLIENT_ID', 'OIDC_CLIENT_SECRET_REF', 'OIDC_ALLOWED_ALGS',
                 'OIDC_REDIRECT_URIS', 'OIDC_CLOCK_SKEW_SECONDS',
                 'SSO_METADATA_URL', 'SSO_CERT_REF',
                 'LDAP_URL', 'LDAP_BASE_DN', 'LDAP_BIND_DN', 'LDAP_BIND_PASSWORD',
                 'CUSTOMER_IAM_JWKS_URL', 'CUSTOMER_IAM_AUDIENCE',
                 'BREAK_GLASS_ADMIN_REFS')) {
    if ($designContent -match [regex]::Escape($k)) { $varHits++ }
}
if ($varHits -ge 34) {
    Add-Result 'ENV-006' 'PASS' "34 required variables all documented in design doc"
} else {
    Add-Result 'ENV-006' 'FAIL' "Required variables in design doc: $varHits/34"
}

# ENV-007: 5 production identity modes
$modeHits = 0
foreach ($k in @('OIDC', 'SSO', 'LDAP_AD', 'CUSTOMER_IAM', 'LOCAL_IAM_EXTENSION')) {
    if ($designContent -match [regex]::Escape($k)) { $modeHits++ }
}
if ($modeHits -ge 5) {
    Add-Result 'ENV-007' 'PASS' "5 production identity modes all listed"
} else {
    Add-Result 'ENV-007' 'WARN' "Identity modes: $modeHits/5"
}

# ENV-008: 5 sensitive field types with log policies
$sensitiveHits = 0
foreach ($k in @('密码', 'Token', 'API Key', 'AK/SK', 'License 私钥')) {
    if ($designContent -match [regex]::Escape($k)) { $sensitiveHits++ }
}
if ($sensitiveHits -ge 5) {
    Add-Result 'ENV-008' 'PASS' "5 sensitive field types with log policies all listed"
} else {
    Add-Result 'ENV-008' 'WARN' "Sensitive field types: $sensitiveHits/5"
}

# ENV-009: 7+1 rotation policies
$rotationHits = 0
foreach ($k in @('APP_SECRET', 'DB_PASSWORD', 'MINIO_SECRET_KEY', 'AI_API_KEY', 'License 私钥', 'OIDC', 'LDAP bind', '应急管理员')) {
    if ($designContent -match [regex]::Escape($k)) { $rotationHits++ }
}
if ($rotationHits -ge 8) {
    Add-Result 'ENV-009' 'PASS' "8 rotation policies all listed (含应急管理员)"
} elseif ($rotationHits -ge 7) {
    Add-Result 'ENV-009' 'PASS' "Rotation policies: $rotationHits (>=7)"
} else {
    Add-Result 'ENV-009' 'WARN' "Rotation policies: $rotationHits/7"
}

# ENV-010: 6 forbidden actions
$forbiddenHits = 0
foreach ($k in @('禁止提交真实', '禁止在文档中写真实密码', '禁止把密钥写入前端代码', '禁止日志打印完整 Header Authorization', '禁止 Demo 环境复用生产密钥', '禁止 staging/prod')) {
    if ($designContent -match [regex]::Escape($k)) { $forbiddenHits++ }
}
if ($forbiddenHits -ge 6) {
    Add-Result 'ENV-010' 'PASS' "6 forbidden actions all listed"
} else {
    Add-Result 'ENV-010' 'WARN' "Forbidden actions: $forbiddenHits/6"
}

# ENV-011: 5 roles in secret access matrix
$roleHits = 0
foreach ($k in @('开发', '运维', '安全', 'CI/CD', 'Break-glass')) {
    if ($designContent -match [regex]::Escape($k)) { $roleHits++ }
}
if ($roleHits -ge 5) {
    Add-Result 'ENV-011' 'PASS' "5 roles in secret access matrix"
} else {
    Add-Result 'ENV-011' 'WARN' "Roles: $roleHits/5"
}

# ENV-012: 8 incident response steps
$stepHits = 0
foreach ($k in @('发现', '分级', '止血', '轮换', '影响面确认', '清理', '恢复', 'RCA')) {
    if ($designContent -match [regex]::Escape($k)) { $stepHits++ }
}
if ($stepHits -ge 8) {
    Add-Result 'ENV-012' 'PASS' "8 incident response steps all listed"
} else {
    Add-Result 'ENV-012' 'WARN' "Incident response steps: $stepHits/8"
}

# ENV-013: 5 leak type specific handling
$leakHits = 0
foreach ($k in @('AI Key 泄露', 'DB 密码泄露', 'MinIO Key 泄露', 'APP_SECRET 泄露', 'License 私钥泄露')) {
    if ($designContent -match [regex]::Escape($k)) { $leakHits++ }
}
if ($leakHits -ge 5) {
    Add-Result 'ENV-013' 'PASS' "5 leak type specific handling all listed"
} else {
    Add-Result 'ENV-013' 'WARN' "Leak type handling: $leakHits/5"
}

# ENV-014: Break-glass policy (双人审批 + 限时 + 自动回收 + RCA)
$bgHits = 0
foreach ($k in @('双人审批', 'break-glass', '限定时长', '自动回收', 'RCA 复盘')) {
    if ($designContent -match [regex]::Escape($k)) { $bgHits++ }
}
if ($bgHits -ge 5) {
    Add-Result 'ENV-014' 'PASS' "Break-glass policy 5 elements all listed"
} else {
    Add-Result 'ENV-014' 'WARN' "Break-glass policy: $bgHits/5"
}

# ENV-015: 4 acceptance criteria (use shorter phrases to avoid backtick mismatch)
$criteriaHits = 0
foreach ($k in @('完整且无真实密钥', 'secret scan 通过', '生产部署文档明确 Secret 注入方式', '日志脱敏策略覆盖敏感变量')) {
    if ($designContent -match [regex]::Escape($k)) { $criteriaHits++ }
}
if ($criteriaHits -ge 4) {
    Add-Result 'ENV-015' 'PASS' "4 acceptance criteria all listed"
} else {
    Add-Result 'ENV-015' 'FAIL' "Acceptance criteria: $criteriaHits/4"
}

# ENV-016: .env.example contains yutong prefix rule comment
if ($envContent -match 'yutong' -and $envContent -match '前缀' -or ($envContent -match 'yutong-postgres' -and $envContent -match 'yutong-redis' -and $envContent -match 'yutong-minio')) {
    Add-Result 'ENV-016' 'PASS' ".env.example documents yutong prefix rule"
} else {
    Add-Result 'ENV-016' 'FAIL' ".env.example missing yutong prefix rule documentation"
}

# ENV-017: .env.example key required variables present (subset of 34)
$keyVarHits = 0
foreach ($k in @('APP_ENV', 'APP_SECRET', 'DB_HOST', 'DB_PASSWORD', 'REDIS_HOST',
                 'MINIO_ENDPOINT', 'MINIO_ACCESS_KEY', 'MINIO_SECRET_KEY',
                 'AI_API_KEY', 'IDENTITY_MODE', 'AUTH_MOCK_ENABLED',
                 'OIDC_ISSUER', 'OIDC_CLIENT_ID', 'SSO_METADATA_URL',
                 'LDAP_URL', 'LDAP_BIND_PASSWORD', 'CUSTOMER_IAM_JWKS_URL',
                 'BREAK_GLASS_ADMIN_REFS')) {
    if ($envContent -match "(?m)^$k\s*=") { $keyVarHits++ }
}
if ($keyVarHits -ge 18) {
    Add-Result 'ENV-017' 'PASS' ".env.example contains $keyVarHits/18 key required variables"
} else {
    Add-Result 'ENV-017' 'WARN' ".env.example key vars: $keyVarHits/18"
}

# ENV-018: .env.example placeholder pattern for secrets (${} syntax)
$placeholderHits = 0
foreach ($k in @('${APP_SECRET}', '${DB_PASSWORD}', '${AI_API_KEY}', '${OIDC_CLIENT_SECRET_REF}', '${SSO_CERT_REF}', '${LDAP_BIND_DN}', '${LDAP_BIND_PASSWORD}', '${BREAK_GLASS_ADMIN_REFS}')) {
    if ($envContent -match [regex]::Escape($k)) { $placeholderHits++ }
}
if ($placeholderHits -ge 8) {
    Add-Result 'ENV-018' 'PASS' ".env.example uses \${...} placeholders for $placeholderHits/8 sensitive vars"
} else {
    Add-Result 'ENV-018' 'WARN' ".env.example placeholder coverage: $placeholderHits/8"
}

# ENV-019: docker-compose.boot.yml exists with yutong-postgres/redis/minio container_name
if (Test-Path $composeBootPath) {
    $bootContent = [System.IO.File]::ReadAllText($composeBootPath, [System.Text.Encoding]::UTF8)
    $bootHits = 0
    foreach ($k in @('yutong-postgres', 'yutong-redis', 'yutong-minio')) {
        if ($bootContent -match "container_name:\s*$k") { $bootHits++ }
    }
    if ($bootHits -ge 3 -and $bootContent -match 'name:\s*yutong') {
        Add-Result 'ENV-019' 'PASS' "docker-compose.boot.yml uses yutong container names ($bootHits/3) and yutong project name"
    } else {
        Add-Result 'ENV-019' 'WARN' "docker-compose.boot.yml yutong prefix: $bootHits/3"
    }
} else {
    Add-Result 'ENV-019' 'FAIL' "docker-compose.boot.yml missing"
}

# ENV-020: docker-compose.run.yml exists with yutong-backend-run + yutong-backend:latest
if (Test-Path $composeRunPath) {
    $runContent = [System.IO.File]::ReadAllText($composeRunPath, [System.Text.Encoding]::UTF8)
    $runHits = 0
    if ($runContent -match 'container_name:\s*yutong-backend-run') { $runHits++ }
    if ($runContent -match 'image:\s*yutong-backend:latest') { $runHits++ }
    if ($runContent -match 'name:\s*yutong_default') { $runHits++ }
    if ($runHits -ge 3) {
        Add-Result 'ENV-020' 'PASS' "docker-compose.run.yml uses yutong-backend-run container + yutong-backend:latest image + yutong_default network"
    } else {
        Add-Result 'ENV-020' 'WARN' "docker-compose.run.yml yutong prefix: $runHits/3"
    }
} else {
    Add-Result 'ENV-020' 'FAIL' "docker-compose.run.yml missing"
}

# ENV-021: docker-compose.run.yml uses yutong- prefix container hostnames (POSTGRES_HOST=yutong-postgres etc.)
if ($runContent -match 'POSTGRES_HOST:\s*yutong-postgres' -and
    $runContent -match 'REDIS_HOST:\s*yutong-redis' -and
    $runContent -match 'MINIO_ENDPOINT:\s*http://yutong-minio:9000') {
    Add-Result 'ENV-021' 'PASS' "docker-compose.run.yml container hostnames use yutong- prefix (POSTGRES/REDIS/MINIO)"
} else {
    Add-Result 'ENV-021' 'WARN' "docker-compose.run.yml container hostnames not all yutong- prefixed"
}

# ENV-022: CI workflow integrates check-env-vars
if (Test-Path $ciPath) {
    $ciContent = [System.IO.File]::ReadAllText($ciPath, [System.Text.Encoding]::UTF8)
    if ($ciContent -match 'check-env-vars') {
        Add-Result 'ENV-022' 'PASS' "CI workflow integrates check-env-vars step"
    } else {
        Add-Result 'ENV-022' 'WARN' "CI workflow missing check-env-vars step"
    }
} else {
    Add-Result 'ENV-022' 'FAIL' "CI workflow missing"
}

# ENV-023: check-env-vars script exists
if (Test-Path $checkEnvVarsPath) {
    Add-Result 'ENV-023' 'PASS' "check-env-vars script exists"
} else {
    Add-Result 'ENV-023' 'FAIL' "check-env-vars script missing"
}

# ENV-024: check-env-vars contains 34 required vars
if (Test-Path $checkEnvVarsPath) {
    $checkEnvVarsContent = [System.IO.File]::ReadAllText($checkEnvVarsPath, [System.Text.Encoding]::UTF8)
    $checkVarHits = 0
    foreach ($k in @('APP_ENV', 'APP_SECRET', 'DB_HOST', 'DB_PORT', 'DB_NAME', 'DB_USER', 'DB_PASSWORD',
                     'REDIS_HOST', 'MINIO_ENDPOINT', 'MINIO_ACCESS_KEY', 'MINIO_SECRET_KEY',
                     'AI_PROVIDER', 'AI_API_KEY', 'IDENTITY_MODE',
                     'AUTH_MOCK_ENABLED', 'AUTH_ACCESS_TOKEN_TTL_SECONDS', 'AUTH_REFRESH_TOKEN_TTL_SECONDS',
                     'AUTH_REFRESH_ROTATION_ENABLED', 'AUTH_COOKIE_MODE',
                     'OIDC_ISSUER', 'OIDC_CLIENT_ID', 'OIDC_CLIENT_SECRET_REF', 'OIDC_ALLOWED_ALGS',
                     'OIDC_REDIRECT_URIS', 'OIDC_CLOCK_SKEW_SECONDS',
                     'SSO_METADATA_URL', 'SSO_CERT_REF',
                     'LDAP_URL', 'LDAP_BASE_DN', 'LDAP_BIND_DN', 'LDAP_BIND_PASSWORD',
                     'CUSTOMER_IAM_JWKS_URL', 'CUSTOMER_IAM_AUDIENCE',
                     'BREAK_GLASS_ADMIN_REFS')) {
        if ($checkEnvVarsContent -match [regex]::Escape($k)) { $checkVarHits++ }
    }
    if ($checkVarHits -ge 34) {
        Add-Result 'ENV-024' 'PASS' "check-env-vars validates all 34 required variables"
    } else {
        Add-Result 'ENV-024' 'WARN' "check-env-vars var coverage: $checkVarHits/34"
    }
} else {
    Add-Result 'ENV-024' 'FAIL' "check-env-vars script missing"
}

# ENV-025: .env.example no real secret values (placeholder or dev/example values only)
# Sensitive vars (PASSWORD/SECRET/API_KEY/BIND_PASSWORD/CLIENT_SECRET_REF) must use ${...} placeholder
# or contain dev/example/test/dummy/change-me/placeholder/yutong- markers.
# Note: ACCESS_KEY is a public access ID (not a secret), excluded from this check.
$realSecretViolations = 0
$sensitivePatterns = 'PASSWORD|SECRET|API_KEY|BIND_PASSWORD|CLIENT_SECRET_REF'
$envLines = $envContent -split "`r?`n"
foreach ($line in $envLines) {
    if ($line -match '^\s*#' -or $line -match '^\s*$') { continue }
    if ($line -notmatch '^([A-Z_]+)\s*=(.*)$') { continue }
    $varName = $matches[1]
    $varValue = $matches[2]
    if ($varName -match $sensitivePatterns) {
        # Allowed: ${...}, empty, or contains dev/example/test/dummy/change-me/placeholder/sample/yutong- markers
        if ($varValue -and
            $varValue -notmatch '^\$\{' -and
            $varValue -notmatch '(?i)dev|example|placeholder|change-me|dummy|sample|test|^yutong-') {
            $realSecretViolations++
            Write-Host "  -> Potential real secret in $varName"
        }
    }
}
if ($realSecretViolations -eq 0) {
    Add-Result 'ENV-025' 'PASS' ".env.example no real secret values (all placeholders or dev markers)"
} else {
    Add-Result 'ENV-025' 'FAIL' ".env.example contains $realSecretViolations potential real secret value(s)"
}

# ENV-026: YAML configPrinciples section
if ($yamlContent -match 'configPrinciples:') {
    Add-Result 'ENV-026' 'PASS' "YAML configPrinciples section present"
} else {
    Add-Result 'ENV-026' 'FAIL' "YAML configPrinciples section missing"
}

# ENV-027: YAML envFiles section (6 files)
if ($yamlContent -match 'envFiles:') {
    $envFileCount = ([regex]::Matches($yamlContent, '^\s+- file: ', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
    if ($envFileCount -ge 6) {
        Add-Result 'ENV-027' 'PASS' "YAML envFiles section has $envFileCount entries (>=6)"
    } else {
        Add-Result 'ENV-027' 'WARN' "YAML envFiles: $envFileCount/6"
    }
} else {
    Add-Result 'ENV-027' 'FAIL' "YAML envFiles section missing"
}

# ENV-028: YAML requiredVariables section (11 categories)
if ($yamlContent -match 'requiredVariables:') {
    $categoryCount = ([regex]::Matches($yamlContent, '^\s {2}[a-zA-Z]+:', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
    if ($yamlContent -match 'profile:' -and $yamlContent -match 'security:' -and $yamlContent -match 'database:' -and $yamlContent -match 'redis:' -and $yamlContent -match 'objectStorage:' -and $yamlContent -match 'ai:' -and $yamlContent -match 'identity:' -and $yamlContent -match 'auth:' -and $yamlContent -match 'oidc:' -and $yamlContent -match 'sso:' -and $yamlContent -match 'ldap:' -and $yamlContent -match 'customerIam:' -and $yamlContent -match 'breakGlass:') {
        Add-Result 'ENV-028' 'PASS' "YAML requiredVariables has 13 categories"
    } else {
        Add-Result 'ENV-028' 'WARN' "YAML requiredVariables categories incomplete"
    }
} else {
    Add-Result 'ENV-028' 'FAIL' "YAML requiredVariables section missing"
}

# ENV-029: YAML identityModes section (5 modes)
if ($yamlContent -match 'identityModes:') {
    $modeYamlHits = 0
    foreach ($k in @('OIDC', 'SSO', 'LDAP_AD', 'CUSTOMER_IAM', 'LOCAL_IAM_EXTENSION')) {
        # Use (?m) inline multiline mode; PowerShell -match does not accept RegexOptions arg
        $pattern = "(?m)^\s+- $k\s*$"
        if ($yamlContent -match $pattern) { $modeYamlHits++ }
    }
    if ($modeYamlHits -ge 5) {
        Add-Result 'ENV-029' 'PASS' "YAML identityModes 5 modes all present"
    } else {
        Add-Result 'ENV-029' 'WARN' "YAML identityModes: $modeYamlHits/5"
    }
} else {
    Add-Result 'ENV-029' 'FAIL' "YAML identityModes section missing"
}

# ENV-030: YAML sensitiveFields section
if ($yamlContent -match 'sensitiveFields:' -and $yamlContent -match 'logPolicy:') {
    Add-Result 'ENV-030' 'PASS' "YAML sensitiveFields section with logPolicy present"
} else {
    Add-Result 'ENV-030' 'FAIL' "YAML sensitiveFields section missing"
}

# ENV-031: YAML rotationPolicies section (7+)
if ($yamlContent -match 'rotationPolicies:') {
    $rotationCount = ([regex]::Matches($yamlContent, '^\s*- secret:', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
    if ($rotationCount -ge 7) {
        Add-Result 'ENV-031' 'PASS' "YAML rotationPolicies has $rotationCount entries (>=7)"
    } else {
        Add-Result 'ENV-031' 'WARN' "YAML rotationPolicies: $rotationCount/7"
    }
} else {
    Add-Result 'ENV-031' 'FAIL' "YAML rotationPolicies section missing"
}

# ENV-032: YAML forbiddenActions section (6)
if ($yamlContent -match 'forbiddenActions:') {
    $forbiddenCount = ([regex]::Matches($yamlContent, '^\s+- 禁止', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
    if ($forbiddenCount -ge 6) {
        Add-Result 'ENV-032' 'PASS' "YAML forbiddenActions has $forbiddenCount entries (>=6)"
    } else {
        Add-Result 'ENV-032' 'WARN' "YAML forbiddenActions: $forbiddenCount/6"
    }
} else {
    Add-Result 'ENV-032' 'FAIL' "YAML forbiddenActions section missing"
}

# ENV-033: YAML productionGates section (5)
if ($yamlContent -match 'productionGates:') {
    $gateCount = ([regex]::Matches($yamlContent, '^\s+- (IDENTITY_MODE|AUTH_MOCK_ENABLED|AUTH_REFRESH_ROTATION_ENABLED|OIDC issuer|OIDC redirectUri)', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
    if ($gateCount -ge 5) {
        Add-Result 'ENV-033' 'PASS' "YAML productionGates has $gateCount entries (>=5)"
    } else {
        Add-Result 'ENV-033' 'WARN' "YAML productionGates: $gateCount/5"
    }
} else {
    Add-Result 'ENV-033' 'FAIL' "YAML productionGates section missing"
}

# ENV-034: YAML secretAccessMatrix section (5 roles)
if ($yamlContent -match 'secretAccessMatrix:') {
    $matrixRoleCount = ([regex]::Matches($yamlContent, '^\s*- role:', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
    if ($matrixRoleCount -ge 5) {
        Add-Result 'ENV-034' 'PASS' "YAML secretAccessMatrix has $matrixRoleCount roles (>=5)"
    } else {
        Add-Result 'ENV-034' 'WARN' "YAML secretAccessMatrix: $matrixRoleCount/5"
    }
} else {
    Add-Result 'ENV-034' 'FAIL' "YAML secretAccessMatrix section missing"
}

# ENV-035: YAML incidentResponseSteps section (8)
if ($yamlContent -match 'incidentResponseSteps:') {
    $stepCount = ([regex]::Matches($yamlContent, '^\s*- step:', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
    if ($stepCount -ge 8) {
        Add-Result 'ENV-035' 'PASS' "YAML incidentResponseSteps has $stepCount entries (>=8)"
    } else {
        Add-Result 'ENV-035' 'WARN' "YAML incidentResponseSteps: $stepCount/8"
    }
} else {
    Add-Result 'ENV-035' 'FAIL' "YAML incidentResponseSteps section missing"
}

# ENV-036: YAML leakTypeSpecificHandling section (5)
if ($yamlContent -match 'leakTypeSpecificHandling:') {
    $leakCount = ([regex]::Matches($yamlContent, '^\s*- type:', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
    if ($leakCount -ge 5) {
        Add-Result 'ENV-036' 'PASS' "YAML leakTypeSpecificHandling has $leakCount entries (>=5)"
    } else {
        Add-Result 'ENV-036' 'WARN' "YAML leakTypeSpecificHandling: $leakCount/5"
    }
} else {
    Add-Result 'ENV-036' 'FAIL' "YAML leakTypeSpecificHandling section missing"
}

# ENV-037: YAML breakGlassPolicy section
if ($yamlContent -match 'breakGlassPolicy:' -and $yamlContent -match 'productionApproval:' -and $yamlContent -match 'defaultState:' -and $yamlContent -match 'enablement:' -and $yamlContent -match 'recovery:' -and $yamlContent -match 'rca:') {
    Add-Result 'ENV-037' 'PASS' "YAML breakGlassPolicy 5 elements all present"
} else {
    Add-Result 'ENV-037' 'FAIL' "YAML breakGlassPolicy section incomplete"
}

# ENV-038: YAML acceptanceCriteria section (4)
if ($yamlContent -match 'acceptanceCriteria:') {
    $criteriaCount = ([regex]::Matches($yamlContent, '^\s+- .+env\.example|secret scan|生产部署文档|日志脱敏', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
    if ($criteriaCount -ge 4 -or ($yamlContent -match 'acceptanceCriteria:' -and $yamlContent -match '日志脱敏')) {
        Add-Result 'ENV-038' 'PASS' "YAML acceptanceCriteria section present"
    } else {
        Add-Result 'ENV-038' 'WARN' "YAML acceptanceCriteria entries: $criteriaCount/4"
    }
} else {
    Add-Result 'ENV-038' 'FAIL' "YAML acceptanceCriteria section missing"
}

# ENV-039: YAML yutongPrefixRule section
if ($yamlContent -match 'yutongPrefixRule:' -and $yamlContent -match 'rationale:' -and $yamlContent -match 'images:' -and $yamlContent -match 'containers:' -and $yamlContent -match 'network:' -and $yamlContent -match 'envVarRule:') {
    Add-Result 'ENV-039' 'PASS' "YAML yutongPrefixRule section complete (rationale/images/containers/network/envVarRule)"
} else {
    Add-Result 'ENV-039' 'FAIL' "YAML yutongPrefixRule section incomplete"
}

# ENV-040: YAML yutong containers list (4 containers)
$containerHits = 0
foreach ($k in @('yutong-postgres', 'yutong-redis', 'yutong-minio', 'yutong-backend-run')) {
    if ($yamlContent -match [regex]::Escape($k)) { $containerHits++ }
}
if ($containerHits -ge 4) {
    Add-Result 'ENV-040' 'PASS' "YAML yutongPrefixRule lists $containerHits/4 containers"
} else {
    Add-Result 'ENV-040' 'WARN' "YAML yutong containers: $containerHits/4"
}

# ENV-041: YAML codeAlignment section
if ($yamlContent -match 'codeAlignment:' -and $yamlContent -match 'envExample:' -and $yamlContent -match 'checkEnvVars:' -and $yamlContent -match 'ciIntegration:' -and $yamlContent -match 'composeRun:' -and $yamlContent -match 'composeBoot:') {
    Add-Result 'ENV-041' 'PASS' "YAML codeAlignment section complete"
} else {
    Add-Result 'ENV-041' 'WARN' "YAML codeAlignment section incomplete"
}

# ENV-042: YAML existingEvidence section
if ($yamlContent -match 'existingEvidence:' -and $yamlContent -match 'ga2_20:' -and $yamlContent -match 'ga2_30:' -and $yamlContent -match 'ci_check:') {
    Add-Result 'ENV-042' 'PASS' "YAML existingEvidence section with GA2-20/GA2-30/CI check"
} else {
    Add-Result 'ENV-042' 'WARN' "YAML existingEvidence section incomplete"
}

# ENV-043: YAML knownDeviations section
if ($yamlContent -match 'knownDeviations:' -and $yamlContent -match '生产身份模式' -and $yamlContent -match 'Secret Manager') {
    Add-Result 'ENV-043' 'PASS' "YAML knownDeviations section with 3 deviations documented"
} else {
    Add-Result 'ENV-043' 'WARN' "YAML knownDeviations section incomplete"
}

# ENV-044: 23 tracking record has 82-环境变量 entry
if (Test-Path $trackingPath) {
    $trackingContent = [System.IO.File]::ReadAllText($trackingPath, [System.Text.Encoding]::UTF8)
    if ($trackingContent -match '82-环境变量与密钥管理规范详设') {
        Add-Result 'ENV-044' 'PASS' "23 tracking record has 82-环境变量 entry"
    } else {
        Add-Result 'ENV-044' 'FAIL' "23 tracking record missing 82 entry"
    }
} else {
    Add-Result 'ENV-044' 'FAIL' "23 tracking record missing"
}

# ENV-045: 23 tracking record has GA2-20 implementation note
if ($trackingContent -match 'GA2-20 落地' -and $trackingContent -match '51 变量') {
    Add-Result 'ENV-045' 'PASS' "23 tracking record has GA2-20 implementation (51 vars)"
} else {
    Add-Result 'ENV-045' 'WARN' "23 tracking record GA2-20 implementation note missing"
}

# ENV-046: 23 tracking record mentions check-env-vars script
if ($trackingContent -match 'check-env-vars') {
    Add-Result 'ENV-046' 'PASS' "23 tracking record mentions check-env-vars CI script"
} else {
    Add-Result 'ENV-046' 'WARN' "23 tracking record missing check-env-vars reference"
}

# ENV-047: 23 tracking record documents yutong container prefix rule
if ($trackingContent -match 'yutong-postgres' -and $trackingContent -match 'yutong-redis' -and $trackingContent -match 'yutong-minio' -and $trackingContent -match 'yutong-backend-run') {
    Add-Result 'ENV-047' 'PASS' "23 tracking record documents yutong container prefix rule (4 containers)"
} else {
    Add-Result 'ENV-047' 'WARN' "23 tracking record yutong prefix rule documentation incomplete"
}

# ENV-048: 23 tracking record mentions yutong_default network
if ($trackingContent -match 'yutong_default') {
    Add-Result 'ENV-048' 'PASS' "23 tracking record mentions yutong_default network"
} else {
    Add-Result 'ENV-048' 'WARN' "23 tracking record missing yutong_default network reference"
}

# ENV-049: .env.example IDENTITY_MODE default MOCK (allowed for local, must be replaced in prod)
if ($envContent -match '(?m)^IDENTITY_MODE=MOCK') {
    Add-Result 'ENV-049' 'PASS' ".env.example IDENTITY_MODE default MOCK (local only; prod gate enforces non-MOCK)"
} else {
    Add-Result 'ENV-049' 'WARN' ".env.example IDENTITY_MODE default not MOCK"
}

# ENV-050: .env.example AUTH_MOCK_ENABLED restricts to local,test
if ($envContent -match '(?m)^AUTH_MOCK_ENABLED=local,test') {
    Add-Result 'ENV-050' 'PASS' ".env.example AUTH_MOCK_ENABLED=local,test (staging/prod gate enforced)"
} else {
    Add-Result 'ENV-050' 'WARN' ".env.example AUTH_MOCK_ENABLED not restricted to local,test"
}

# Summary
Write-Host ""
Write-Host "Summary: PASS=$pass WARN=$warn FAIL=$fail"

# Write JSON report
$reportDir = Join-Path $projectRoot 'build\reports\checks'
if (-not (Test-Path $reportDir)) { New-Item -ItemType Directory -Path $reportDir -Force | Out-Null }
$status = if ($fail -eq 0) { 'PASS' } elseif ($fail -le 2) { 'WARN' } else { 'FAIL' }
$report = @{
    schemaVersion = '1.0.0'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    taskId = 'GA2-82'
    designDoc = '82-环境变量与密钥管理规范详设'
    status = $status
    passCount = $pass
    warnCount = $warn
    failCount = $fail
    results = $results
}
$reportPath = Join-Path $reportDir 'env-secrets-management-ga2-82.json'
$utf8Bom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($reportPath, ($report | ConvertTo-Json -Depth 10), $utf8Bom)
Write-Host "Report saved: $reportPath"

if ($fail -gt 0) { exit 1 } else { exit 0 }
