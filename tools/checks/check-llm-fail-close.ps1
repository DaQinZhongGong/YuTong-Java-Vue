#!/usr/bin/env pwsh
# LLM 严格失败关闭门禁 — 禁止 mock / 占位 / 假成功兜底
# 设计来源: 业界同类实现 AI 对标 P0/P1 收尾时确立的"LLM 失败 = 显式 LlmResponse.error，禁止 mock 假成功"
# 扫描范围: backend/yutong-ai-service/src/main/java/com/yutong/ai/chat/service/llm/ 全部 .java
# 命中关键字即 FAIL，阻止未来回退兜底逻辑
# 已知豁免:
#   - backend/.../src/test/ 测试目录（允许 mock fixture）
#   - LlmProviderSelector 内部的 mock-local 过滤逻辑（业务上跳过 mock provider，not 兜底回复）

$ErrorActionPreference = 'Stop'
$root = Resolve-Path "$PSScriptRoot\..\.."

$scanPath = "$root\backend\yutong-ai-service\src\main\java\com\yutong\ai\chat\service\llm"
if (-not (Test-Path $scanPath)) { Write-Error "scan path not found: $scanPath" }

# 关键字: 命中即视为引入 mock 兜底（PowerShell 5.1 不支持跨行 array 字面量，单行写）
$patternRegexes = @('"mock-local"', 'mockReply\s*\(', 'generateMockReply\s*\(', '占位回复', 'TODO.*LLM', 'FIXME.*LLM', 'placeholder.*reply')
$patternNames = @('mock-local 作为 providerCode', 'mockReply 兜底函数', 'generateMockReply 兜底函数', '占位回复', 'TODO LLM', 'FIXME LLM', 'placeholder reply')

# 文件级豁免（这些文件里的 mock-local 是业务逻辑而非兜底）
$allowFilePatterns = @()
$allowFilePatterns += 'LlmProviderSelector.java'

$findings = @()
$javaFiles = @(Get-ChildItem -Recurse -Path $scanPath -Filter '*.java')
foreach ($item in $javaFiles) {
    $file = $item.FullName
    $content = Get-Content $file -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
    if (-not $content) { continue }
    $allowed = $false
    foreach ($allow in $allowFilePatterns) {
        $p = '*' + $allow + '*'
        if ($file -like $p) { $allowed = $true; break }
    }
    if ($allowed) { continue }
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
            if ($isComment) { continue }
            $finding = New-Object PSObject -Property @{File = $file.Substring($root.Length + 1); Line = $lineNum; Pattern = $patternNames[$i]; Text = $lineText}
            $findings += $finding
        }
    }
}

Write-Host "==> LLM 严格失败关闭门禁" -ForegroundColor Cyan
if ($findings.Count -eq 0) {
    Write-Host "    PASS (无 mock / 占位 / 假成功兜底)" -ForegroundColor Green
    exit 0
}

Write-Host "    FAIL ($($findings.Count) 处命中)" -ForegroundColor Red
Write-Host ""
$findings | ForEach-Object {
    Write-Host "  $($_.File):$($_.Line)  [$($_.Pattern)]" -ForegroundColor Red
    Write-Host "    $($_.Text)" -ForegroundColor Red
}
Write-Host ""
Write-Host "禁止在 LLM 路径引入 mock 兜底 — 失败必须返回 LlmResponse.error / 抛 AI_PROVIDER_ERROR" -ForegroundColor Yellow
exit 1
