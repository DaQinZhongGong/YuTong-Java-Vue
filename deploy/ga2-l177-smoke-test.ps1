$base = "http://localhost:20010/api/v1"

# Prepare: write test keys
docker exec yutong-redis redis-cli SET "yutong:config:test-key-1" "value1" EX 3600 | Out-Null
docker exec yutong-redis redis-cli SET "yutong:config:test-key-2" "value2" EX 3600 | Out-Null
docker exec yutong-redis redis-cli SET "yutong:dict:gender" "[{male,female}]" EX 3600 | Out-Null
docker exec yutong-redis redis-cli SET "yutong:idem:lock-1" "1" EX 60 | Out-Null
Write-Host "===== Prepare: 4 test keys written (config x2, dict x1, idem x1) ====="
$before = docker exec yutong-redis redis-cli KEYS "yutong:*"
Write-Host ("After write yutong:* keys count: " + $before.Count)
$before | ForEach-Object { Write-Host ("  " + $_) }

# T5: clear whitelisted 'config'
Write-Host ""
Write-Host "===== T5: DELETE /monitor/cache/config (whitelisted, expect 200 deletedKeys=2) ====="
$headers5 = @{ "X-Mock-User" = "admin"; "X-Trace-Id" = "ga2-l177-test-005" }
$r5 = Invoke-WebRequest -Uri "$base/monitor/cache/config" -Method DELETE -Headers $headers5 -UseBasicParsing
$j5 = $r5.Content | ConvertFrom-Json
Write-Host ("HTTP " + $r5.StatusCode + " code=" + $j5.code + " cacheName=" + $j5.data.cacheName + " deletedKeys=" + $j5.data.deletedKeys + " clearedAt=" + $j5.data.clearedAt)

# Verify idem key retained
Write-Host ""
Write-Host "===== Verify: idem key retained, config keys cleared ====="
$after = docker exec yutong-redis redis-cli KEYS "yutong:*"
Write-Host ("After clear yutong:* keys count: " + $after.Count)
$after | ForEach-Object { Write-Host ("  " + $_) }

# T6: viewer has no monitor:cache:view
Write-Host ""
Write-Host "===== T6: GET /monitor/cache as viewer (expect 403) ====="
$headers6 = @{ "X-Mock-User" = "viewer"; "X-Trace-Id" = "ga2-l177-test-006" }
try {
    $r6 = Invoke-WebRequest -Uri "$base/monitor/cache" -Method GET -Headers $headers6 -UseBasicParsing -ErrorAction Stop
    Write-Host ("UNEXPECTED HTTP " + $r6.StatusCode + ": " + $r6.Content)
} catch {
    $resp = $_.Exception.Response
    if ($resp) {
        $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
        $body = $sr.ReadToEnd()
        Write-Host ("HTTP " + $resp.StatusCode.value__)
        Write-Host $body
    }
}

# T7: viewer has no monitor:health:view
Write-Host ""
Write-Host "===== T7: GET /monitor/health as viewer (expect 403) ====="
$headers7 = @{ "X-Mock-User" = "viewer"; "X-Trace-Id" = "ga2-l177-test-007" }
try {
    $r7 = Invoke-WebRequest -Uri "$base/monitor/health" -Method GET -Headers $headers7 -UseBasicParsing -ErrorAction Stop
    Write-Host ("UNEXPECTED HTTP " + $r7.StatusCode + ": " + $r7.Content)
} catch {
    $resp = $_.Exception.Response
    if ($resp) {
        $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
        $body = $sr.ReadToEnd()
        Write-Host ("HTTP " + $resp.StatusCode.value__)
        Write-Host $body
    }
}

# T8: biz has no monitor:cache:clear
Write-Host ""
Write-Host "===== T8: DELETE /monitor/cache/config as biz (no monitor:cache:clear, expect 403) ====="
$headers8 = @{ "X-Mock-User" = "biz"; "X-Trace-Id" = "ga2-l177-test-008" }
try {
    $r8 = Invoke-WebRequest -Uri "$base/monitor/cache/config" -Method DELETE -Headers $headers8 -UseBasicParsing -ErrorAction Stop
    Write-Host ("UNEXPECTED HTTP " + $r8.StatusCode + ": " + $r8.Content)
} catch {
    $resp = $_.Exception.Response
    if ($resp) {
        $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
        $body = $sr.ReadToEnd()
        Write-Host ("HTTP " + $resp.StatusCode.value__)
        Write-Host $body
    }
}

# T9: second clear config (no keys left, deletedKeys should be 0)
Write-Host ""
Write-Host "===== T9: DELETE /monitor/cache/config again (expect 200 deletedKeys=0) ====="
$r9 = Invoke-WebRequest -Uri "$base/monitor/cache/config" -Method DELETE -Headers $headers5 -UseBasicParsing
$j9 = $r9.Content | ConvertFrom-Json
Write-Host ("HTTP " + $r9.StatusCode + " code=" + $j9.code + " deletedKeys=" + $j9.data.deletedKeys)

# Cleanup idem test key (restore env)
docker exec yutong-redis redis-cli DEL "yutong:idem:lock-1" "yutong:dict:gender" | Out-Null
Write-Host ""
Write-Host "===== Test key cleanup done ====="
