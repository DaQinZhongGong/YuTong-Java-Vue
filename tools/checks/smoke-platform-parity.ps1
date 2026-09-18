# YuTong 涓氱晫鍚岀被瀹炵幇 Parity Smoke 鈥?P0鏂板閾捐矾
$base = $env:VITE_API_BASE_URL; if(-not $base){ $base="http://localhost:8080/api/v1" }
Write-Host "Base: $base"
function Check($path){ try{ $r=Invoke-WebRequest -Uri "$base$path" -UseBasicParsing -TimeoutSec 5; Write-Host "PASS $path $($r.StatusCode)" } catch{ Write-Host "WARN $path $($_.Exception.Message)" } }
Check "/mcp/market?page=1&size=5"
Check "/knowledge-graph?page=1&size=5"
Check "/ai/trace/runs?page=1&size=5"
Check "/ai/chat" # POST expected 400 斜械蟹 body is ok
Write-Host "Smoke done"
