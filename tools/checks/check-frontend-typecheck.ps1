#!/usr/bin/env pwsh
# 前后端 TypeScript 严格模式 type-check 门禁
# 设计来源: P1-7 收尾发现 web-admin 30+ 存量 TS 错导致 `pnpm build` 挂
# 覆盖: web (用户端) + web-admin (管理端) — 任何一端有 error TS 都 exit 1
# 已知问题: @vueuse/core Rollup 注释告警（非阻塞）；Vue Flow 1.45 + TS 5.6 strict
# 已用 any 绕开 generic 嵌套过深

$ErrorActionPreference = 'Stop'
$root = Resolve-Path "$PSScriptRoot\..\.."
Set-Location $root

$results = @()
$exit = 0

function Run-TypeCheck {
    param([string]$Dir)
    if (-not (Test-Path "$root\$Dir\package.json")) {
        Write-Warning "[$Dir] package.json 不存在，跳过"
        return
    }
    Write-Host "==> [$Dir] pnpm type-check" -ForegroundColor Cyan
    # 用 Start-Process 拿 stdout/stderr，避免 & pnpm.cmd 触发 native error 干扰
    $proc = Start-Process -FilePath 'pnpm.cmd' -ArgumentList @('type-check') `
        -WorkingDirectory "$root\$Dir" `
        -NoNewWindow -Wait -PassThru `
        -RedirectStandardOutput "$env:TEMP\yutong-typecheck-$Dir.out" `
        -RedirectStandardError "$env:TEMP\yutong-typecheck-$Dir.err"
    $output = (Get-Content "$env:TEMP\yutong-typecheck-$Dir.out" -Raw -ErrorAction SilentlyContinue) + `
              (Get-Content "$env:TEMP\yutong-typecheck-$Dir.err" -Raw -ErrorAction SilentlyContinue)
    Remove-Item "$env:TEMP\yutong-typecheck-$Dir.out" -ErrorAction SilentlyContinue
    Remove-Item "$env:TEMP\yutong-typecheck-$Dir.err" -ErrorAction SilentlyContinue
    $errorCount = ([regex]::Matches($output, 'error TS\d+')).Count
    if ($proc.ExitCode -eq 0 -and $errorCount -eq 0) {
        Write-Host "    [$Dir] PASS" -ForegroundColor Green
        return @{ Dir = $Dir; Ok = $true; Errors = 0 }
    } else {
        Write-Host "    [$Dir] FAIL ($errorCount errors, exit=$($proc.ExitCode))" -ForegroundColor Red
        [regex]::Matches($output, 'error TS\d+') | Select-Object -First 5 | ForEach-Object {
            Write-Host "      $($_.Value)" -ForegroundColor Red
        }
        $script:exit = 1
        return @{ Dir = $Dir; Ok = $false; Errors = $errorCount }
    }
}

$results += Run-TypeCheck 'web'
$results += Run-TypeCheck 'web-admin'

Write-Host ""
Write-Host "=== Type-Check Summary ===" -ForegroundColor Yellow
$results | ForEach-Object {
    $label = if ($_.Ok) { 'PASS' } else { 'FAIL' }
    $color = if ($_.Ok) { 'Green' } else { 'Red' }
    Write-Host ("  {0,-12} {1} ({2} errors)" -f $_.Dir, $label, $_.Errors) -ForegroundColor $color
}

if ($exit -ne 0) { exit 1 }
exit 0
