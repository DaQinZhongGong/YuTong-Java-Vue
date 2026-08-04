# Smoke verification v4: avoid Chinese path chars, use wildcards
$docsRoot = 'd:\MyCode\YuTong-Java-Vue\YuTong-Java-Docs'
$trackingFile = Get-ChildItem -Path $docsRoot -Filter '23-*.md' -Recurse -File | Select-Object -First 1
if ($null -eq $trackingFile) {
    Write-Host '[FAIL] Tracking record not found'
    exit 1
}
$trackingPath = $trackingFile.FullName
Write-Host "[SMOKE] Tracking file: $trackingPath"
$content = [System.IO.File]::ReadAllText($trackingPath, [System.Text.Encoding]::UTF8)
$done = ([regex]::Matches($content, '\| Done \|')).Count
$ready = ([regex]::Matches($content, '\| Ready \|')).Count
$verifying = ([regex]::Matches($content, '\| Verifying \|')).Count
Write-Host "[SMOKE] Tracking record: Done=$done Ready=$ready Verifying=$verifying"
Write-Host ""
$ga258 = ([regex]::Matches($content, 'GA2-58')).Count
$ga259 = ([regex]::Matches($content, 'GA2-59')).Count
$ga260 = ([regex]::Matches($content, 'GA2-60')).Count
$ga261 = ([regex]::Matches($content, 'GA2-61')).Count
$ga276 = ([regex]::Matches($content, 'GA2-76')).Count
$ga295 = ([regex]::Matches($content, 'GA2-95')).Count
$ga267 = ([regex]::Matches($content, 'GA2-67')).Count
$ga277 = ([regex]::Matches($content, 'GA2-77')).Count
$ga282 = ([regex]::Matches($content, 'GA2-82')).Count
Write-Host "[SMOKE] GA2-58 refs: $ga258  GA2-59 refs: $ga259  GA2-60 refs: $ga260"
Write-Host "[SMOKE] GA2-61 refs: $ga261  GA2-76 refs: $ga276  GA2-95 refs: $ga295"
Write-Host "[SMOKE] GA2-67 refs: $ga267  GA2-77 refs: $ga277  GA2-82 refs: $ga282"
Write-Host ""
Write-Host "[SMOKE] Evidence files:"
$evidenceDir = 'd:\MyCode\YuTong-Java-Vue\release-evidence\v1.0.0'
foreach ($f in @('ga2-58-evidence.md','ga2-59-evidence.md','ga2-60-evidence.md','ga2-61-evidence.md','ga2-76-evidence.md','ga2-95-evidence.md','ga2-67-evidence.md','ga2-77-evidence.md','ga2-82-evidence.md')) {
    $p = Join-Path $evidenceDir $f
    if (Test-Path $p) {
        $size = (Get-Item $p).Length
        Write-Host ("  [PASS] " + $f + " (" + $size + " bytes)")
    } else {
        Write-Host ("  [FAIL] " + $f + " missing")
    }
}
Write-Host ""
Write-Host "[SMOKE] Machine check JSON reports:"
$reportsDir = 'd:\MyCode\YuTong-Java-Vue\build\reports\checks'
foreach ($f in @('wbs-baseline-ga2-61.json','ui-visual-review-ga2-76.json','figma-handoff-ga2-95.json','data-scope-audit-ga2-67.json','erd-verification-ga2-77.json','env-secrets-management-ga2-82.json')) {
    $p = Join-Path $reportsDir $f
    if (Test-Path $p) {
        $size = (Get-Item $p).Length
        Write-Host ("  [PASS] " + $f + " (" + $size + " bytes)")
    } else {
        Write-Host ("  [FAIL] " + $f + " missing")
    }
}
Write-Host ""
Write-Host "[SMOKE] Docker containers:"
docker ps --filter "name=yutong" --format "{{.Names}}: {{.Status}}" 2>$null | ForEach-Object { Write-Host ("  " + $_) }
Write-Host ""
Write-Host "[SMOKE] Backend health:"
try {
    $resp = (Invoke-WebRequest -Uri "http://localhost:8082/actuator/health" -UseBasicParsing -TimeoutSec 5).Content
    Write-Host ("  [PASS] " + $resp)
} catch {
    Write-Host ("  [FAIL] " + $_.Exception.Message)
}
Write-Host ""
Write-Host "[SMOKE] Regression smoke verification done"
