# YuTong Gateway + Backend 冒烟测试脚本
# 测试地址: 网关 http://localhost:8090, 后端直连 http://localhost:20010
# 默认账号: admin_demo / demo123 (MOCK 模式)
param(
    [string]$Gateway = "http://localhost:8090",
    [string]$Backend = "http://localhost:20010",
    [string]$Username = "admin_demo",
    [string]$Password = "demo123"
)

$pass = 0
$fail = 0
$results = @()

function Test-Api($name, $method, $url, $expectedStatus, $headers=@{}, $body=$null) {
    $status = 0
    $rspBody = $null
    $traceId = $null
    try {
        if ($body) {
            $rsp = Invoke-WebRequest -Uri $url -Method $method -Headers $headers -Body ($body | ConvertTo-Json) -ContentType "application/json" -UseBasicParsing -ErrorAction Stop
        } else {
            $rsp = Invoke-WebRequest -Uri $url -Method $method -Headers $headers -UseBasicParsing -ErrorAction Stop
        }
        $status = [int]$rsp.StatusCode
        $traceId = $rsp.Headers['X-Trace-Id']
        $rspBody = $rsp.Content
    } catch {
        $rsp = $_.Exception.Response
        if ($rsp) {
            $status = [int]$rsp.StatusCode
            try { $stream = $rsp.GetResponseStream(); $reader = New-Object System.IO.StreamReader($stream); $rspBody = $reader.ReadToEnd() } catch {}
        } else {
            $status = 0
            $rspBody = $_.Exception.Message
        }
    }
    $ok = ($status -eq $expectedStatus)
    if ($ok) { $script:pass++ } else { $script:fail++ }
    $label = if ($ok) { "PASS" } else { "FAIL" }
    Write-Host "$label [$status] $name : $url"
    if ($traceId) { Write-Host "     traceId=$traceId" }
    $script:results += [PSCustomObject]@{ Test=$name; Status=$label; HttpStatus=$status; Url=$url; TraceId=$traceId }
}

Write-Host "=== YuTong Gateway + Backend Smoke Test ==="
Write-Host "Gateway: $Gateway"
Write-Host "Backend: $Backend"
Write-Host ""

Test-Api "T1 Gateway Actuator Health" GET "$Gateway/actuator/health" 200
Test-Api "T2 Backend Actuator Health" GET "$Backend/actuator/health" 200
Test-Api "T3 Login via Gateway" POST "$Gateway/api/v1/auth/login" 200 @{ "X-Mock-User"=$Username } @{ "username"=$Username; "password"=$Password }
Test-Api "T4 Login via Backend Direct" POST "$Backend/api/v1/auth/login" 200 @{ "X-Mock-User"=$Username } @{ "username"=$Username; "password"=$Password }
Test-Api "T5 Customers via Gateway" GET "$Gateway/api/v1/customers?page=1&size=3" 200 @{ "X-Mock-User"=$Username }
Test-Api "T6 Customers via Backend Direct" GET "$Backend/api/v1/customers?page=1&size=3" 200 @{ "X-Mock-User"=$Username }
Test-Api "T7 Workflow Definitions via Gateway" GET "$Gateway/api/v1/workflow/definitions?page=1&size=3" 200 @{ "X-Mock-User"=$Username }
Test-Api "T8 AI Governance Stats via Gateway" GET "$Gateway/api/v1/ai-governance/stats" 200 @{ "X-Mock-User"=$Username }

Write-Host ""
Write-Host "=== Result: PASS=$pass FAIL=$fail ==="

if ($fail -gt 0) { exit 1 }
