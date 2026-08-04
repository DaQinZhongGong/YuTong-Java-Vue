# GA2-30 yutong Container Prefix Smoke Test
# Runs 12 verification steps and saves evidence to release-evidence/v1.0.0/ga2-30-smoke.txt
$ErrorActionPreference = 'Continue'
$out = @()
$out += "=== GA2-30 yutong Container Prefix Smoke Test ==="
$out += "Timestamp: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')"
$out += ""

# Step 1: Container names all use yutong- prefix
$out += "--- STEP 1: Container name prefix verification (yutong-) ---"
$containers = docker ps --format "{{.Names}}" | Where-Object { $_ -match "yutong" }
foreach ($c in $containers) {
  if ($c -like "yutong-*") {
    $out += "PASS: $c (yutong- prefix)"
  } else {
    $out += "FAIL: $c (no yutong- prefix)"
  }
}
$out += ""

# Step 2: Docker image uses yutong prefix
$out += "--- STEP 2: Image prefix verification (yutong-backend:latest) ---"
$images = docker images --format "{{.Repository}}:{{.Tag}}" | Where-Object { $_ -match "yutong" }
foreach ($i in $images) {
  if ($i -like "yutong*") {
    $out += "PASS: $i"
  } else {
    $out += "FAIL: $i"
  }
}
$out += ""

# Step 3: Network uses yutong_default
$out += "--- STEP 3: Network name verification (yutong_default) ---"
$networks = docker network ls --format "{{.Name}}" | Where-Object { $_ -match "yutong" }
foreach ($n in $networks) {
  if ($n -eq "yutong_default") {
    $out += "PASS: $n"
  } else {
    $out += "FAIL: $n (expected yutong_default)"
  }
}
$out += ""

# Step 4: All containers healthy
$out += "--- STEP 4: Container health status ---"
$statuses = docker ps --format "{{.Names}}|{{.Status}}" | Where-Object { $_ -match "yutong" }
foreach ($s in $statuses) {
  $parts = $s -split '\|'
  if ($parts[1] -match "healthy") {
    $out += "PASS: $($parts[0]) = $($parts[1])"
  } else {
    $out += "FAIL: $($parts[0]) = $($parts[1])"
  }
}
$out += ""

# Step 5: Backend env uses yutong- hostnames
$out += "--- STEP 5: Container env hostnames (yutong-postgres/redis/minio) ---"
$envOutput = docker inspect yutong-backend-run --format "{{range .Config.Env}}{{println .}}{{end}}" | Out-String
$expected = @(
  "POSTGRES_HOST=yutong-postgres",
  "REDIS_HOST=yutong-redis",
  "MINIO_ENDPOINT=http://yutong-minio:9000"
)
foreach ($exp in $expected) {
  if ($envOutput -match [regex]::Escape($exp)) {
    $out += "PASS: $exp"
  } else {
    $out += "FAIL: not found $exp"
  }
}
$out += ""

# Step 6: Backend logs show yutong-postgres JDBC URL
$out += "--- STEP 6: Backend logs JDBC URL verification ---"
# GA2-30: 长稳运行后启动日志可能被大量 DEBUG 日志推到 2000 行之外，改为读取全部日志
$logs = docker logs yutong-backend-run 2>&1 | Out-String
if ($logs -match "yutong-postgres:5432/yutong") {
  $out += "PASS: backend connects to jdbc:postgresql://yutong-postgres:5432/yutong"
} else {
  $out += "FAIL: yutong-postgres JDBC URL not found in logs"
}
if ($logs -match "Started YutongApplication") {
  $out += "PASS: YutongApplication started successfully"
} else {
  $out += "FAIL: YutongApplication startup marker not found"
}
$out += ""

# Step 7: HEALTHCHECK uses wget (not curl)
$out += "--- STEP 7: Dockerfile HEALTHCHECK uses wget ---"
$dockerfile = Get-Content "d:\MyCode\YuTong-Java-Vue\backend\Dockerfile" -Raw
if ($dockerfile -match "wget -q -O /dev/null http://localhost:8080/actuator/health") {
  $out += "PASS: HEALTHCHECK uses wget (JRE image has no curl)"
} else {
  $out += "FAIL: HEALTHCHECK does not use wget"
}
if ($dockerfile -match "apt-get install -y --no-install-recommends wget") {
  $out += "PASS: wget installed in Dockerfile"
} else {
  $out += "FAIL: wget not installed in Dockerfile"
}
$out += ""

# Step 8: docker-compose.run.yml
$out += "--- STEP 8: docker-compose.run.yml spec verification ---"
$runYml = Get-Content "d:\MyCode\YuTong-Java-Vue\deploy\docker-compose.run.yml" -Raw
if ($runYml -match "(?m)^name:\s*yutong\s*$") {
  $out += "PASS: compose project name = yutong"
} else {
  $out += "FAIL: compose project name not set to yutong"
}
if ($runYml -match "image:\s*yutong-backend:latest") {
  $out += "PASS: image = yutong-backend:latest"
} else {
  $out += "FAIL: image not yutong-backend:latest"
}
if ($runYml -match "container_name:\s*yutong-backend-run") {
  $out += "PASS: container_name = yutong-backend-run"
} else {
  $out += "FAIL: container_name not yutong-backend-run"
}
if ($runYml -match "name:\s*yutong_default") {
  $out += "PASS: network name = yutong_default"
} else {
  $out += "FAIL: network name not yutong_default"
}
$out += ""

# Step 9: docker-compose.boot.yml uses yutong- hostnames
$out += "--- STEP 9: docker-compose.boot.yml backend-mvn hostnames ---"
$bootYml = Get-Content "d:\MyCode\YuTong-Java-Vue\deploy\docker-compose.boot.yml" -Raw
$badPatterns = @("POSTGRES_HOST:\s+postgres\b", "REDIS_HOST:\s+redis\b", "MINIO_ENDPOINT:\s+http://minio:9000")
$badFound = $false
foreach ($bad in $badPatterns) {
  if ($bootYml -match $bad) {
    $out += "FAIL: found non-yutong prefix hostname: $bad"
    $badFound = $true
  }
}
if (-not $badFound) {
  $out += "PASS: no non-yutong prefix hostname residue"
}
if ($bootYml -match "POSTGRES_HOST:\s*yutong-postgres") {
  $out += "PASS: POSTGRES_HOST = yutong-postgres"
} else {
  $out += "FAIL: POSTGRES_HOST not yutong-postgres"
}
if ($bootYml -match "REDIS_HOST:\s*yutong-redis") {
  $out += "PASS: REDIS_HOST = yutong-redis"
} else {
  $out += "FAIL: REDIS_HOST not yutong-redis"
}
if ($bootYml -match "MINIO_ENDPOINT:\s*http://yutong-minio:9000") {
  $out += "PASS: MINIO_ENDPOINT = http://yutong-minio:9000"
} else {
  $out += "FAIL: MINIO_ENDPOINT not http://yutong-minio:9000"
}
$out += ""

# Step 10: .env.example
$out += "--- STEP 10: .env.example GA2-30 rule comment ---"
$envExample = Get-Content "d:\MyCode\YuTong-Java-Vue\.env.example" -Raw
if ($envExample -match "GA2-30") {
  $out += "PASS: .env.example contains GA2-30 container prefix rule comment"
} else {
  $out += "FAIL: .env.example missing GA2-30 rule comment"
}
if ($envExample -match "yutong-backend:latest" -and $envExample -match "yutong_default") {
  $out += "PASS: rule comment includes image/network naming examples"
} else {
  $out += "FAIL: rule comment missing naming examples"
}
$out += ""

# Step 11: run-backend.ps1
$out += "--- STEP 11: deploy/run-backend.ps1 script verification ---"
$ps1 = Get-Content "d:\MyCode\YuTong-Java-Vue\deploy\run-backend.ps1" -Raw
if ($ps1 -match '\$ImageName\s*=\s*"yutong-backend"') {
  $out += "PASS: ImageName = yutong-backend"
} else {
  $out += "FAIL: ImageName not yutong-backend"
}
if ($ps1 -match "yutong_default") {
  $out += "PASS: script references yutong_default network"
} else {
  $out += "FAIL: script does not reference yutong_default network"
}
$out += ""

# Step 12: API smoke test
$out += "--- STEP 12: API functional smoke test ---"
$base = "http://localhost:20010"
$headers = @{ "X-Mock-User" = "admin_demo" }

# 12.1 Health
try {
  $health = Invoke-RestMethod -Uri "$base/actuator/health" -Method Get -TimeoutSec 5
  if ($health.status -eq "UP") {
    $out += "PASS: Step 12.1 health = UP"
  } else {
    $out += "FAIL: Step 12.1 health = $($health.status)"
  }
} catch {
  $out += "FAIL: Step 12.1 health error: $($_.Exception.Message)"
}

# 12.2 Auth Login (data.username field, code is string "0")
$loginBody = @{ username = "admin_demo"; password = "demo123" } | ConvertTo-Json
try {
  $resp = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body $loginBody -ContentType "application/json" -TimeoutSec 5
  if ($resp.code -eq "0" -and $resp.data.username -eq "admin_demo") {
    $out += "PASS: Step 12.2 auth login code=0 username=admin_demo"
  } else {
    $out += "FAIL: Step 12.2 auth login code=$($resp.code) username=$($resp.data.username)"
  }
} catch {
  $out += "FAIL: Step 12.2 auth login error: $($_.Exception.Message)"
}

# 12.3 Lowcode Pages
try {
  $pages = Invoke-RestMethod -Uri "$base/api/v1/lowcode/pages" -Method Get -Headers $headers -TimeoutSec 5
  if ($pages.code -eq "0") {
    $out += "PASS: Step 12.3 lowcode pages code=0 total=$($pages.data.total)"
  } else {
    $out += "FAIL: Step 12.3 lowcode pages code=$($pages.code)"
  }
} catch {
  $out += "FAIL: Step 12.3 lowcode pages error: $($_.Exception.Message)"
}

# 12.4 Workbench (endpoint: /workbench/stats, not /summary)
try {
  $wb = Invoke-RestMethod -Uri "$base/api/v1/workbench/stats" -Method Get -Headers $headers -TimeoutSec 5
  if ($wb.code -eq "0") {
    $out += "PASS: Step 12.4 workbench stats code=0 customers=$($wb.data.totalCustomers)"
  } else {
    $out += "FAIL: Step 12.4 workbench code=$($wb.code)"
  }
} catch {
  $out += "FAIL: Step 12.4 workbench error: $($_.Exception.Message)"
}

# 12.5 AI Chat (endpoint: /ai/chat, body needs scenario field)
$chatBody = @{ message = "hello"; scenario = "PLATFORM_QA" } | ConvertTo-Json
try {
  $chat = Invoke-RestMethod -Uri "$base/api/v1/ai/chat" -Method Post -Body $chatBody -ContentType "application/json" -Headers $headers -TimeoutSec 10
  if ($chat.code -eq "0") {
    $out += "PASS: Step 12.5 ai chat code=0 content_length=$($chat.data.content.Length)"
  } else {
    $out += "FAIL: Step 12.5 ai chat code=$($chat.code)"
  }
} catch {
  $out += "FAIL: Step 12.5 ai chat error: $($_.Exception.Message)"
}
$out += ""

# Summary
$failCount = ($out | Where-Object { $_ -match "^FAIL" }).Count
$result = $out -join "`n"
Write-Host $result
Write-Host ""
if ($failCount -eq 0) {
  Write-Host "=== ALL 12 STEPS PASS ==="
} else {
  Write-Host "=== FAILED: $failCount step(s) ==="
}

# Save evidence
$evidencePath = "d:\MyCode\YuTong-Java-Vue\release-evidence\v1.0.0\ga2-30-smoke.txt"
$result | Out-File -FilePath $evidencePath -Encoding utf8 -Force
if ($failCount -eq 0) {
  Add-Content -Path $evidencePath -Value ""
  Add-Content -Path $evidencePath -Value "=== ALL 12 STEPS PASS ==="
} else {
  Add-Content -Path $evidencePath -Value ""
  Add-Content -Path $evidencePath -Value "=== FAILED: $failCount step(s) ==="
}
Write-Host ""
Write-Host "Evidence saved to: $evidencePath"
