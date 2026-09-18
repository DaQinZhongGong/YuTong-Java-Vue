#!/usr/bin/env pwsh
# 前端禁止硬编码 demo / mock / fake data 兜底
# 设计来源: P1-1~P1-6 收尾发现前端有 "演示兜底" / catch 块填假数据 / 列表 mock 数组
# 扫描范围: web/src / web-admin/src / mobile-uniapp/src
# 命中关键字即 FAIL, 阻止未来回退
# 已知豁免:
#   - 测试文件 (__tests__ / *.test.ts / *.spec.ts)
#   - mobile-uniapp 真实单测文件

$ErrorActionPreference = 'Stop'
$root = Resolve-Path "$PSScriptRoot\..\.."

$scanPaths = @()
$scanPaths += Join-Path $root 'web/src'
$scanPaths += Join-Path $root 'web-admin/src'
$scanPaths += Join-Path $root 'mobile-uniapp/src'

# 关键字: 命中即视为引入 mock / fake data 兜底
# 涵盖常见英文写法 + 中文写法 (中文字符串在 PowerShell 5.1 有编码问题, 单独走 UTF-8 scan)
$patternRegexes = @(
    'mockReply\s*\(',
    'generateMockReply\s*\(',
    'mockList\s*[:=]',
    'demoData\s*[:=]',
    'fakeData\s*[:=]',
    'placeholder\s+data\s*[\:=]',
    'fallback\s+mock',
    'sample\s+list\s*[:=]'
)
$patternNames = @(
    'mockReply function',
    'generateMockReply function',
    'mockList variable',
    'demoData variable',
    'fakeData variable',
    'placeholder data 兜底',
    'fallback mock',
    'sample list 兜底'
)

# 文件级豁免 (mobile-uniapp 真实单测文件)
$allowFilePatterns = @()
$allowFilePatterns += 'mobile-uniapp/src/utils/__tests__/format.test.ts'
$allowFilePatterns += 'mobile-uniapp/src/utils/__tests__/relativeTime.test.ts'
$allowFilePatterns += 'mobile-uniapp/src/utils/__tests__/route-alias.test.ts'
$allowFilePatterns += 'mobile-uniapp/src/utils/__tests__/permission.test.ts'
$allowFilePatterns += 'mobile-uniapp/src/utils/__tests__/offlineCache.test.ts'

$findings = @()
foreach ($scanPath in $scanPaths) {
    if (-not (Test-Path $scanPath)) { continue }
    $jsFiles = @(Get-ChildItem -Recurse -Path $scanPath -Include '*.vue', '*.ts' -ErrorAction SilentlyContinue)
    foreach ($item in $jsFiles) {
        $file = $item.FullName
        $allowed = $false
        foreach ($allow in $allowFilePatterns) {
            $p = '*' + $allow + '*'
            if ($file -like $p) { $allowed = $true; break }
        }
        if ($allowed) { continue }
        if ($file -like '*__tests__*') { continue }
        if ($file -like '*.test.ts') { continue }
        if ($file -like '*.spec.ts') { continue }

        $content = Get-Content $file -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
        if (-not $content) { continue }
        for ($i = 0; $i -lt $patternRegexes.Count; $i++) {
            $regex = [regex]$patternRegexes[$i]
            $matches = $regex.Matches($content)
            foreach ($m in $matches) {
                $lineNum = ($content.Substring(0, $m.Index) -split "`n").Count
                $lineText = ($content -split "`n")[$lineNum - 1].Trim()
                $isComment = $false
                if ($lineText -match '^\s*//') { $isComment = $true }
                if ($lineText -match '^\s*\*') { $isComment = $true }
                if ($lineText -match '^\s*/\*') { $isComment = $true }
                if ($lineText -match '^\s*<!--') { $isComment = $true }
                if ($isComment) { continue }
                $finding = New-Object PSObject -Property @{File = $file.Substring($root.Length + 1); Line = $lineNum; Pattern = $patternNames[$i]; Text = $lineText}
                $findings += $finding
            }
        }
    }
}

Write-Host "==> Frontend mock / fake data fallback gate" -ForegroundColor Cyan
if ($findings.Count -eq 0) {
    Write-Host "    PASS (no mock / fake data fallback)" -ForegroundColor Green
    exit 0
}

Write-Host "    FAIL ($($findings.Count) hits)" -ForegroundColor Red
Write-Host ""
$findings | ForEach-Object {
    Write-Host "  $($_.File):$($_.Line)  [$($_.Pattern)]" -ForegroundColor Red
    Write-Host "    $($_.Text)" -ForegroundColor Red
}
Write-Host ""
Write-Host "Frontend MUST NOT hardcode demo / fake data fallback. Use empty state + ErrorMessage instead." -ForegroundColor Yellow
exit 1
