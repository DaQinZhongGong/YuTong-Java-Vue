#!/usr/bin/env pwsh
# YuTong 全门禁端到端编排（线性执行，PowerShell 5.1 兼容）
# 端到端 6 步：前端 type-check + mobile vitest + 2 个新门禁 + 后端 mvn test + mvn verify

$ErrorActionPreference = 'Stop'
$root = Resolve-Path "$PSScriptRoot\..\.."
Set-Location $root

$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\azul-25.0.4.1'
$env:PATH = 'C:\Users\Administrator\.jdks\azul-25.0.4.1\bin;' + $env:PATH
$MVN = 'C:\Users\Administrator\.jdks\apache-maven-3.9.11\bin\mvn.cmd'

# 用 global scope 数组存结果（避免 PowerShell 5.1 function 内 $script: 作用域混淆）
$global:results = New-Object System.Collections.ArrayList
$global:overallExit = 0

# 用 hashtable + splat 避免 function/scriptblock 嵌套 PowerShell 5.1 解析 bug
function Run-One {
    param([string]$Name, [string]$Kind, [string]$Target)
    Write-Host ''
    Write-Host ('==> [' + $Name + ']') -ForegroundColor Cyan
    $tmpOut = Join-Path $env:TEMP ('runall-' + $Name.Replace(' ', '_') + '.out')
    $tmpErr = Join-Path $env:TEMP ('runall-' + $Name.Replace(' ', '_') + '.err')
    $startArgs = @{}
    if ($Kind -eq 'cmd') {
        $startArgs['FilePath'] = 'cmd.exe'
        $startArgs['ArgumentList'] = @('/c', $Target)
    } else {
        $startArgs['FilePath'] = 'powershell.exe'
        $startArgs['ArgumentList'] = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $Target)
    }
    $startArgs['NoNewWindow'] = $true
    $startArgs['Wait'] = $true
    $startArgs['PassThru'] = $true
    $startArgs['RedirectStandardOutput'] = $tmpOut
    $startArgs['RedirectStandardError'] = $tmpErr
    $proc = Start-Process @startArgs
    $output = (Get-Content $tmpOut -Raw -ErrorAction SilentlyContinue) + "`n" + (Get-Content $tmpErr -Raw -ErrorAction SilentlyContinue)
    Remove-Item $tmpOut -ErrorAction SilentlyContinue
    Remove-Item $tmpErr -ErrorAction SilentlyContinue
    $exitCode = $proc.ExitCode
    [void]$global:results.Add([PSCustomObject]@{ Name = $Name; ExitCode = $exitCode })
    if ($exitCode -eq 0) {
        Write-Host ('    [' + $Name + '] PASS') -ForegroundColor Green
    } else {
        Write-Host ('    [' + $Name + '] FAIL (exit=' + $exitCode + ')') -ForegroundColor Red
        $lines = $output -split "`n"
        $start = [Math]::Max(0, $lines.Count - 20)
        for ($i = $start; $i -lt $lines.Count; $i++) { Write-Host ('      ' + $lines[$i]) -ForegroundColor Red }
        $global:overallExit = 1
    }
}

# Step 1: Frontend type-check
Run-One 'web type-check' 'cmd' 'pnpm.cmd --dir web type-check'
Run-One 'web-admin type-check' 'cmd' 'pnpm.cmd --dir web-admin type-check'

# Step 2: mobile-uniapp vitest
Run-One 'mobile-uniapp vitest' 'cmd' 'pnpm.cmd --dir mobile-uniapp test --run'

# Step 3: LLM fail-close gate
Run-One 'check-llm-fail-close' 'ps1' (Join-Path $root 'tools/checks/check-llm-fail-close.ps1')

# Step 4: Frontend mock-leakage gate
Run-One 'check-frontend-mock-leakage' 'ps1' (Join-Path $root 'tools/checks/check-frontend-mock-leakage.ps1')

# Step 5: Backend mvn test (12 classes / 84 case)
$testArg = 'ReActDecisionParseTest,AiflowEngineCycleTest,CozeAdapterParseTest,AiChatWebSocketHandlerTest,AiToolRegistryTest,AiMemoryServiceTest,EmbeddingServiceTest,GraphExtractionParserTest,McpMarketInstallParseTest,LlmMessageMultimodalTest,OpenAiCompatibleAdapterMultimodalTest,OpenAiCompatibleAdapterContractTest'
$testCmd = $MVN + ' -pl yutong-ai-service -am test -Dtest=' + $testArg + ' -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false'
Push-Location (Join-Path $root 'backend')
try { Run-One 'mvn test (12 / 84 case)' 'cmd' $testCmd } finally { Pop-Location }

# Step 6: Backend mvn verify (7 modules)
$verifyCmd = $MVN + ' -pl yutong-ai-service -am verify -DskipTests'
Push-Location (Join-Path $root 'backend')
try { Run-One 'mvn verify (7 modules)' 'cmd' $verifyCmd } finally { Pop-Location }

# Summary
Write-Host ''
Write-Host '=== YuTong All-Checks Summary ===' -ForegroundColor Yellow
foreach ($r in $global:results) {
    if ($r.ExitCode -eq 0) { $color = 'Green'; $label = 'PASS' } else { $color = 'Red'; $label = 'FAIL' }
    $line = '  {0,-32} {1} (exit={2})' -f $r.Name, $label, $r.ExitCode
    Write-Host $line -ForegroundColor $color
}

if ($global:overallExit -ne 0) {
    $failCount = ($global:results | Where-Object { $_.ExitCode -ne 0 }).Count
    Write-Host ''
    Write-Host ('OVERALL FAIL: ' + $failCount + ' step(s) failed') -ForegroundColor Red
    exit 1
}
Write-Host ''
Write-Host ('OVERALL PASS: all ' + $global:results.Count + ' steps green') -ForegroundColor Green
exit 0
