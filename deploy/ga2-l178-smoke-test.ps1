$ErrorActionPreference = "Continue"
$base = "http://localhost:20010"
$pass = "demo123"
$results = @()

function Test-Case($name, $script) {
    Write-Host "=== $name ===" -ForegroundColor Cyan
    try {
        & $script
        Write-Host "  PASS" -ForegroundColor Green
        $script:results += "PASS"
    } catch {
        Write-Host "  FAIL: $($_.Exception.Message)" -ForegroundColor Red
        $script:results += "FAIL"
    }
}

# T1: admin_demo login
Write-Host "=== T1: admin_demo login ===" -ForegroundColor Cyan
try {
    $body = @{username="admin_demo";password=$pass} | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body $body -ContentType "application/json"
    if ($r.data.username -ne "admin") { throw "username mismatch: $($r.data.username)" }
    if ($r.data.mockUserType -ne "admin") { throw "mockUserType mismatch: $($r.data.mockUserType)" }
    if (-not ($r.data.roles -contains "ADMIN")) { throw "role mismatch: $($r.data.roles)" }
    Write-Host "  username=$($r.data.username) mockUserType=$($r.data.mockUserType) roles=$($r.data.roles -join ',')"
    Write-Host "  PASS" -ForegroundColor Green
    $results += "PASS"
} catch {
    Write-Host "  FAIL: $($_.Exception.Message)" -ForegroundColor Red
    $results += "FAIL"
}

# T2: reviewer_demo login
Write-Host "=== T2: reviewer_demo login ===" -ForegroundColor Cyan
try {
    $body = @{username="reviewer_demo";password=$pass} | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body $body -ContentType "application/json"
    if ($r.data.mockUserType -ne "approver") { throw "mockUserType mismatch: $($r.data.mockUserType)" }
    if (-not ($r.data.roles -contains "APPROVER")) { throw "role mismatch" }
    Write-Host "  username=$($r.data.username) mockUserType=$($r.data.mockUserType)"
    Write-Host "  PASS" -ForegroundColor Green
    $results += "PASS"
} catch {
    Write-Host "  FAIL: $($_.Exception.Message)" -ForegroundColor Red
    $results += "FAIL"
}

# T3: user_demo login
Write-Host "=== T3: user_demo login ===" -ForegroundColor Cyan
try {
    $body = @{username="user_demo";password=$pass} | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body $body -ContentType "application/json"
    if ($r.data.mockUserType -ne "biz") { throw "mockUserType mismatch: $($r.data.mockUserType)" }
    if (-not ($r.data.roles -contains "BIZ_USER")) { throw "role mismatch" }
    Write-Host "  username=$($r.data.username) mockUserType=$($r.data.mockUserType)"
    Write-Host "  PASS" -ForegroundColor Green
    $results += "PASS"
} catch {
    Write-Host "  FAIL: $($_.Exception.Message)" -ForegroundColor Red
    $results += "FAIL"
}

# T4: viewer_demo login
Write-Host "=== T4: viewer_demo login ===" -ForegroundColor Cyan
try {
    $body = @{username="viewer_demo";password=$pass} | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body $body -ContentType "application/json"
    if ($r.data.mockUserType -ne "viewer") { throw "mockUserType mismatch: $($r.data.mockUserType)" }
    if (-not ($r.data.roles -contains "VIEWER")) { throw "role mismatch" }
    Write-Host "  username=$($r.data.username) mockUserType=$($r.data.mockUserType)"
    Write-Host "  PASS" -ForegroundColor Green
    $results += "PASS"
} catch {
    Write-Host "  FAIL: $($_.Exception.Message)" -ForegroundColor Red
    $results += "FAIL"
}

# T5: wrong password rejected
Write-Host "=== T5: wrong password rejected ===" -ForegroundColor Cyan
try {
    $body = @{username="admin_demo";password="wrong"} | ConvertTo-Json
    try {
        Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body $body -ContentType "application/json"
        throw "should reject"
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if ($code -ne 401 -and $code -ne 403) { throw "HTTP $code, expected 401/403" }
        Write-Host "  HTTP $code (rejected)"
    }
    Write-Host "  PASS" -ForegroundColor Green
    $results += "PASS"
} catch {
    Write-Host "  FAIL: $($_.Exception.Message)" -ForegroundColor Red
    $results += "FAIL"
}

# T6: data volume (20/50/100/50/3)
Write-Host "=== T6: data volume (20/50/100/50/3) ===" -ForegroundColor Cyan
try {
    $counts = docker exec yutong-postgres psql -U yutong -d yutong -t -A -c "SELECT (SELECT COUNT(*) FROM biz_customer WHERE id LIKE '01JYYDEMO%' OR id LIKE '01JYYYYYYCUSTSAMPLE%') || '|' || (SELECT COUNT(*) FROM biz_product WHERE id LIKE '01JYYDEMO%' OR id LIKE '01JYYYYYYPRODSAMPLE%') || '|' || (SELECT COUNT(*) FROM biz_request WHERE id LIKE '01JYYDEMO%') || '|' || (SELECT COUNT(*) FROM sys_message WHERE id LIKE '01JYYDEMO%') || '|' || (SELECT COUNT(*) FROM lc_entity WHERE id LIKE '01JYYDEMO%')"
    $parts = $counts.Trim().Split("|")
    $c=[int]$parts[0]; $p=[int]$parts[1]; $r=[int]$parts[2]; $m=[int]$parts[3]; $l=[int]$parts[4]
    if ($c -lt 20) { throw "customers $c < 20" }
    if ($p -lt 50) { throw "products $p < 50" }
    if ($r -lt 100) { throw "requests $r < 100" }
    if ($m -lt 50) { throw "messages $m < 50" }
    if ($l -lt 3) { throw "lc_entities $l < 3" }
    Write-Host "  customers=$c products=$p requests=$r messages=$m lc_entities=$l"
    Write-Host "  PASS" -ForegroundColor Green
    $results += "PASS"
} catch {
    Write-Host "  FAIL: $($_.Exception.Message)" -ForegroundColor Red
    $results += "FAIL"
}

# T7: admin_demo access workbench
Write-Host "=== T7: admin_demo access workbench ===" -ForegroundColor Cyan
try {
    $body = @{username="admin_demo";password=$pass} | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body $body -ContentType "application/json"
    $headers = @{Authorization="Bearer $($r.data.token)"; "X-Mock-User"=$r.data.mockUserType}
    $wb = Invoke-RestMethod -Uri "$base/api/v1/workbench/stats" -Headers $headers -Method Get
    Write-Host "  totalCustomers=$($wb.data.totalCustomers) totalRequests=$($wb.data.totalRequests)"
    Write-Host "  PASS" -ForegroundColor Green
    $results += "PASS"
} catch {
    Write-Host "  FAIL: $($_.Exception.Message)" -ForegroundColor Red
    $results += "FAIL"
}

# T8: viewer_demo access workbench (TENANT scope)
Write-Host "=== T8: viewer_demo access workbench ===" -ForegroundColor Cyan
try {
    $body = @{username="viewer_demo";password=$pass} | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body $body -ContentType "application/json"
    $headers = @{Authorization="Bearer $($r.data.token)"; "X-Mock-User"=$r.data.mockUserType}
    $wb = Invoke-RestMethod -Uri "$base/api/v1/workbench/stats" -Headers $headers -Method Get
    Write-Host "  viewer totalCustomers=$($wb.data.totalCustomers) (TENANT scope)"
    Write-Host "  PASS" -ForegroundColor Green
    $results += "PASS"
} catch {
    Write-Host "  FAIL: $($_.Exception.Message)" -ForegroundColor Red
    $results += "FAIL"
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
$pass_count = ($results | Where-Object { $_ -eq "PASS" }).Count
Write-Host "  Total: $pass_count / $($results.Count) PASS" -ForegroundColor $(if ($pass_count -eq $results.Count) {"Green"} else {"Yellow"})
Write-Host "========================================" -ForegroundColor Cyan
