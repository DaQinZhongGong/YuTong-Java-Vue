$files = @(
    'd:\MyCode\YuTong-Java-Vue\tools\checks\check-wbs-baseline.ps1',
    'd:\MyCode\YuTong-Java-Vue\tools\checks\check-ui-visual-review.ps1',
    'd:\MyCode\YuTong-Java-Vue\tools\checks\check-figma-handoff.ps1',
    'd:\MyCode\YuTong-Java-Vue\tools\checks\check-data-scope-audit.ps1',
    'd:\MyCode\YuTong-Java-Vue\tools\checks\check-erd-verification.ps1',
    'd:\MyCode\YuTong-Java-Vue\tools\checks\check-env-secrets-management.ps1',
    'd:\MyCode\YuTong-Java-Vue\tools\checks\check-test-matrix.ps1',
    'd:\MyCode\YuTong-Java-Vue\tools\checks\smoke-verify-tracking.ps1'
)
foreach ($p in $files) {
    if (Test-Path $p) {
        $c = [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8)
        $bom = New-Object System.Text.UTF8Encoding($true)
        [System.IO.File]::WriteAllText($p, $c, $bom)
        Write-Host "Converted to UTF-8 BOM: $p"
    } else {
        Write-Host "File not found: $p"
    }
}
