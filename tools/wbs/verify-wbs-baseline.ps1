# PowerShell 验收脚本：GA2-54 WBS 排期基线验收
# 对齐 61 号文档「实现任务WBS与排期基线」4 项验收标准
# 注意：本脚本必须保存为 UTF-8 BOM 编码，避免 PowerShell 5.1 中文 GBK 乱码

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot\..\..").Path
$wbsYaml = Join-Path $root 'tools\wbs\wbs-baseline.yaml'
$trackRecord = Join-Path $root 'YuTong-Java-Docs\23-设计到落地追踪记录\23-设计到落地追踪记录.md'
$productBaseline = Join-Path $root 'YuTong-Java-Docs\contracts\product-baseline.yaml'
$openapiYaml = Join-Path $root 'YuTong-Java-Docs\contracts\openapi\openapi.yaml'
$doc39 = Join-Path $root 'YuTong-Java-Docs\39-实现基线冻结与开工准入检查\39-实现基线冻结与开工准入检查.md'
$doc49 = Join-Path $root 'YuTong-Java-Docs\49-顶尖产品对标设计完备性门禁\49-顶尖产品对标设计完备性门禁.md'

$script:results = New-Object System.Collections.ArrayList
$script:passCount = 0
$script:failCount = 0
$script:warnCount = 0

function Add-Result($code, $severity, $message) {
    $script:results.Add([PSCustomObject]@{ Code = $code; Severity = $severity; Message = $message }) | Out-Null
    if ($severity -eq 'PASS') { $script:passCount++ }
    elseif ($severity -eq 'FAIL') { $script:failCount++ }
    elseif ($severity -eq 'WARN') { $script:warnCount++ }
}

# ============ WBS-001：WBS YAML 文件存在性 ============
if (Test-Path $wbsYaml) {
    $yamlContent = Get-Content -Path $wbsYaml -Raw -Encoding UTF8
    Add-Result 'WBS-001' 'PASS' "tools/wbs/wbs-baseline.yaml 存在，大小 $($yamlContent.Length) 字符"
} else {
    Add-Result 'WBS-001' 'FAIL' "tools/wbs/wbs-baseline.yaml 不存在"
    return
}

# ============ WBS-002：版本分期完整性 ============
$expectedSlices = @{
    'v0.2' = 9
    'v0.3' = 8
    'v0.4' = 8
    'v0.5' = 5
    'v1.0' = 0
}
$totalExpected = 30
# 统计每个版本 wbsTasks 数量
$versionCount = @{}
$wbsTaskRegex = [regex]"\-\s*wbsId:\s*(P[2-5]-\d{2}|GA-\d{2})"
$matches = $wbsTaskRegex.Matches($yamlContent)
$actualTotal = $matches.Count

# 按版本分组统计
$versionRegex = [regex]"-\s*wbsId:\s*(\S+)\s*\n\s*version:\s*(v[0-9.]+)"
$versionMatches = $versionRegex.Matches($yamlContent)
foreach ($m in $versionMatches) {
    $ver = $m.Groups[2].Value
    if (-not $versionCount.ContainsKey($ver)) { $versionCount[$ver] = 0 }
    $versionCount[$ver]++
}

$sliceOk = $true
$sliceDetail = @()
foreach ($k in $expectedSlices.Keys | Sort-Object) {
    $expected = $expectedSlices[$k]
    $actual = if ($versionCount.ContainsKey($k)) { $versionCount[$k] } else { 0 }
    if ($actual -ne $expected) {
        $sliceOk = $false
        $sliceDetail += "$k 期望 $expected 实际 $actual"
    } else {
        $sliceDetail += "$k=$actual"
    }
}

if ($sliceOk -and $actualTotal -eq $totalExpected) {
    Add-Result 'WBS-002' 'PASS' "版本分期完整：$($sliceDetail -join '，')，总数 $actualTotal"
} else {
    Add-Result 'WBS-002' 'FAIL' "版本分期不完整：$($sliceDetail -join '，')，总数 $actualTotal（期望 $totalExpected）"
}

# ============ WBS-003：5 项不进入 v0.2/v0.3 任务清单存在性 ============
$expectedNotIn = @('NOT-V02-01', 'NOT-V02-02', 'NOT-V02-03', 'NOT-V02-04', 'NOT-V02-05')
$notInRegex = [regex]"itemId:\s*(NOT-V02-\d{2})"
$notInMatches = $notInRegex.Matches($yamlContent)
$actualNotIn = $notInMatches | ForEach-Object { $_.Groups[1].Value }
$missingNotIn = $expectedNotIn | Where-Object { $_ -notin $actualNotIn }
if ($missingNotIn.Count -eq 0) {
    Add-Result 'WBS-003' 'PASS' "5 项不进入 v0.2/v0.3 任务清单全部存在：$($actualNotIn -join '，')"
} else {
    Add-Result 'WBS-003' 'FAIL' "缺少不进入项：$($missingNotIn -join '，')"
}

# ============ WBS-004：每个 WBS 任务 5 要素齐全 ============
# 5 要素：inputDocs（docId+path）、codeDirectory、openApiOperationIds、testCaseIds、evidence
$taskBlockRegex = [regex]"(?s)-\s*wbsId:\s*(P[2-5]-\d{2}|GA-\d{2}).*?(?=-\s*wbsId:|\z)"
$taskBlocks = $taskBlockRegex.Matches($yamlContent)
$missingFive = @()
foreach ($tb in $taskBlocks) {
    $block = $tb.Value
    $wbsIdMatch = [regex]::Match($block, "wbsId:\s*(\S+)")
    $wbsId = $wbsIdMatch.Groups[1].Value
    # 检查 5 要素
    $hasInputDocs = $block -match 'inputDocs:' -and $block -match 'docId:' -and $block -match 'path:\s*docs/'
    $hasCodeDir = $block -match 'codeDirectory:\s*\S+'
    $hasOpIds = $block -match 'openApiOperationIds:\s*\[?'
    $hasTestCaseIds = $block -match 'testCaseIds:\s*\[?'
    $hasEvidence = $block -match 'evidence:\s*release-evidence/'
    if (-not ($hasInputDocs -and $hasCodeDir -and $hasOpIds -and $hasTestCaseIds -and $hasEvidence)) {
        $missing = @()
        if (-not $hasInputDocs) { $missing += 'inputDocs/docId/path' }
        if (-not $hasCodeDir) { $missing += 'codeDirectory' }
        if (-not $hasOpIds) { $missing += 'openApiOperationIds' }
        if (-not $hasTestCaseIds) { $missing += 'testCaseIds' }
        if (-not $hasEvidence) { $missing += 'evidence' }
        $missingFive += "$wbsId 缺少: $($missing -join '，')"
    }
}
if ($missingFive.Count -eq 0) {
    Add-Result 'WBS-004' 'PASS' "全部 $($taskBlocks.Count) 个 WBS 任务 5 要素齐全（inputDocs+codeDirectory+openApiOperationIds+testCaseIds+evidence）"
} else {
    Add-Result 'WBS-004' 'FAIL' "$($missingFive.Count) 个任务 5 要素不完整: $($missingFive -join '；')"
}

# ============ WBS-005：每个 WBS 任务有明确输入/输出/验收 ============
$missingOutputAcceptance = @()
foreach ($tb in $taskBlocks) {
    $block = $tb.Value
    $wbsIdMatch = [regex]::Match($block, "wbsId:\s*(\S+)")
    $wbsId = $wbsIdMatch.Groups[1].Value
    $hasOutput = $block -match 'output:\s*\S+'
    $hasAcceptance = $block -match 'acceptance:\s*\S+'
    $hasRole = $block -match 'role:\s*\S+'
    $hasStatus = $block -match 'status:\s*(Done|Ready|InProgress|Not Started)'
    $hasTrackingRow = $block -match 'trackingRecordRow:\s*\S+'
    if (-not ($hasOutput -and $hasAcceptance -and $hasRole -and $hasStatus -and $hasTrackingRow)) {
        $missing = @()
        if (-not $hasOutput) { $missing += 'output' }
        if (-not $hasAcceptance) { $missing += 'acceptance' }
        if (-not $hasRole) { $missing += 'role' }
        if (-not $hasStatus) { $missing += 'status' }
        if (-not $hasTrackingRow) { $missing += 'trackingRecordRow' }
        $missingOutputAcceptance += "$wbsId 缺少: $($missing -join '，')"
    }
}
if ($missingOutputAcceptance.Count -eq 0) {
    Add-Result 'WBS-005' 'PASS' "全部 $($taskBlocks.Count) 个 WBS 任务 output/acceptance/role/status/trackingRecordRow 字段完整"
} else {
    Add-Result 'WBS-005' 'FAIL' "$($missingOutputAcceptance.Count) 个任务字段不完整: $($missingOutputAcceptance -join '；')"
}

# ============ WBS-006：不可验收关键字扫描（仅扫描 wbsTasks 节，不扫描 forbiddenTaskDescriptions 声明本身）============
$forbiddenKeywords = @('调研一下', '看情况实现', '待定', 'TBD', 'TODO 待补', '稍后决定')
# 提取 wbsTasks 节内容：从 "wbsTasks:" 开始到 "governance:" 之前
$wbsTasksSectionMatch = [regex]::Match($yamlContent, "(?s)wbsTasks:\s*(.*?)governance:")
$wbsTasksSection = if ($wbsTasksSectionMatch.Success) { $wbsTasksSectionMatch.Groups[1].Value } else { '' }
$foundForbidden = @()
foreach ($kw in $forbiddenKeywords) {
    if ($wbsTasksSection -match [regex]::Escape($kw)) {
        $foundForbidden += $kw
    }
}
if ($foundForbidden.Count -eq 0) {
    Add-Result 'WBS-006' 'PASS' "未发现不可验收关键字（wbsTasks 节 6 项全部未匹配）"
} else {
    Add-Result 'WBS-006' 'FAIL' "发现不可验收关键字: $($foundForbidden -join '，')"
}

# ============ WBS-007：版本分期与 product-baseline.yaml releaseTaxonomy 对齐 ============
if (Test-Path $productBaseline) {
    $pbContent = Get-Content -Path $productBaseline -Raw -Encoding UTF8
    # 校验 releaseTaxonomy 中 E1~E4/GA 的 version 字段对齐 v0.2~v0.5/v1.0
    $expectedMapping = @{
        'E1' = 'v0.2'
        'E2' = 'v0.3'
        'E3' = 'v0.4'
        'E4' = 'v0.5'
        'GA' = 'v1.0'
    }
    $mismatched = @()
    foreach ($k in $expectedMapping.Keys) {
        $pattern = "$k`:\s*\n\s*version:\s*($($expectedMapping[$k]))"
        if ($pbContent -notmatch $pattern) {
            $mismatched += "$k 期望 $($expectedMapping[$k])"
        }
    }
    if ($mismatched.Count -eq 0) {
        Add-Result 'WBS-007' 'PASS' "product-baseline.yaml releaseTaxonomy 5 版本对齐：E1=v0.2 E2=v0.3 E3=v0.4 E4=v0.5 GA=v1.0"
    } else {
        Add-Result 'WBS-007' 'FAIL' "releaseTaxonomy 版本不对齐: $($mismatched -join '，')"
    }
} else {
    Add-Result 'WBS-007' 'FAIL' "product-baseline.yaml 不存在"
}

# ============ WBS-008：5 项不进入任务与 39/49 分期边界一致性 ============
# 39 号文档期望文本：BPMN/报表/插件/多数据源/完整 RBAC 5 项均不在 E1/E2 强制实现
# 49 号文档期望文本：41 v1.1~v1.5 / 42 v1.0/v1.5 / 45 v2.0 / 46 v1.5 / 32 v1.5+
$expected39Keywords = @{
    'NOT-V02-01' = 'BPMN'
    'NOT-V02-02' = '报表大屏'
    'NOT-V02-03' = '插件市场'
    'NOT-V02-04' = '多数据源'
    'NOT-V02-05' = 'RBAC'
}
$expected49Keywords = @{
    'NOT-V02-01' = '41 工作流'
    'NOT-V02-02' = '42 报表大屏'
    'NOT-V02-03' = '45 插件生态'
    'NOT-V02-04' = '46 多数据源'
    'NOT-V02-05' = '32'
}

if ((Test-Path $doc39) -and (Test-Path $doc49)) {
    $doc39Content = Get-Content -Path $doc39 -Raw -Encoding UTF8
    $doc49Content = Get-Content -Path $doc49 -Raw -Encoding UTF8
    $mismatchedBoundary = @()
    foreach ($k in $expected39Keywords.Keys) {
        $kw39 = $expected39Keywords[$k]
        $kw49 = $expected49Keywords[$k]
        if ($doc39Content -notmatch [regex]::Escape($kw39)) {
            $mismatchedBoundary += "$k 39号缺少 '$kw39'"
        }
        if ($doc49Content -notmatch [regex]::Escape($kw49)) {
            $mismatchedBoundary += "$k 49号缺少 '$kw49'"
        }
    }
    # 同时校验 wbs-baseline.yaml 的 boundaryConsistencyWith39And49 中 5 项 status 都是 ALIGNED
    $boundaryRegex = [regex]"(?s)itemId:\s*(NOT-V02-\d{2}).*?status:\s*(ALIGNED|MISALIGNED)"
    $boundaryMatches = $boundaryRegex.Matches($yamlContent)
    $alignedCount = 0
    foreach ($m in $boundaryMatches) {
        if ($m.Groups[2].Value -eq 'ALIGNED') { $alignedCount++ }
    }
    if ($mismatchedBoundary.Count -eq 0 -and $alignedCount -eq 5) {
        Add-Result 'WBS-008' 'PASS' "5 项不进入任务全部与 39/49 分期边界对齐（boundaryConsistencyWith39And49 5/5 ALIGNED）"
    } else {
        $msg = "对齐失败: $($mismatchedBoundary -join '，'); ALIGNED 计数=$alignedCount/5"
        Add-Result 'WBS-008' 'FAIL' $msg
    }
} else {
    Add-Result 'WBS-008' 'WARN' "39 或 49 号文档不存在，跳过边界一致性校验"
}

# ============ WBS-009：WBS 任务状态分布与 23 号追踪记录对齐 ============
# 期望：30 Done + 0 Ready = 30（GA 不进入 WBS，由 79/100 号文档驱动）
$doneRegex = [regex]"status:\s*Done"
$readyRegex = [regex]"status:\s*Ready"
$doneCount = $doneRegex.Matches($yamlContent).Count
$readyCount = $readyRegex.Matches($yamlContent).Count
if ($doneCount -eq 30 -and $readyCount -eq 0) {
    Add-Result 'WBS-009' 'PASS' "状态分布正确：Done=$doneCount Ready=$readyCount（30/30 全部 Done，GA 不进入 WBS）"
} else {
    Add-Result 'WBS-009' 'FAIL' "状态分布异常：Done=$doneCount（期望 30） Ready=$readyCount（期望 0）"
}

# ============ WBS-010：operationId 在 openapi.yaml 中存在性校验 ============
if (Test-Path $openapiYaml) {
    $openapiContent = Get-Content -Path $openapiYaml -Raw -Encoding UTF8
    # 提取 openapi.yaml 中所有 operationId
    $opIdRegex = [regex]"operationId:\s*(\S+)"
    $opIdMatches = $opIdRegex.Matches($openapiContent)
    $openapiOpIds = $opIdMatches | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
    
    # 从 wbs-baseline.yaml 提取所有非空、非 all-96-operationIds 的 operationId
    $wbsOpIdsRegex = [regex]"openApiOperationIds:\s*\[([^\]]*)\]"
    $wbsOpIdsMatches = $wbsOpIdsRegex.Matches($yamlContent)
    $allWbsOpIds = @()
    foreach ($m in $wbsOpIdsMatches) {
        $list = $m.Groups[1].Value
        if ($list -eq '' -or $list -match 'all-96-operationIds') { continue }
        $ids = $list -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }
        $allWbsOpIds += $ids
    }
    $allWbsOpIds = $allWbsOpIds | Sort-Object -Unique
    
    $missingOpIds = @()
    foreach ($opId in $allWbsOpIds) {
        if ($opId -notin $openapiOpIds) {
            $missingOpIds += $opId
        }
    }
    if ($missingOpIds.Count -eq 0) {
        Add-Result 'WBS-010' 'PASS' "WBS 引用的 $($allWbsOpIds.Count) 个 operationId 全部在 openapi.yaml 中存在"
    } else {
        Add-Result 'WBS-010' 'FAIL' "缺少 $($missingOpIds.Count) 个 operationId: $($missingOpIds -join '，')"
    }
} else {
    Add-Result 'WBS-010' 'FAIL' "openapi.yaml 不存在"
}

# ============ WBS-011：summary 字段统计正确性 ============
$expectedSummary = @{
    totalTasks = 30
    v02Tasks = 9
    v03Tasks = 8
    v04Tasks = 8
    v05Tasks = 5
    gaTasks = 0
    doneTasks = 30
    readyTasks = 0
    notStartedTasks = 0
    notInV02V03Items = 5
    alignedWith39 = 'true'
    alignedWith49 = 'true'
}
$summaryOk = $true
$summaryMismatch = @()
foreach ($k in $expectedSummary.Keys) {
    $expected = $expectedSummary[$k]
    $pattern = "${k}:\s*(\S+)"
    $m = [regex]::Match($yamlContent, $pattern)
    if ($m.Success) {
        $actual = $m.Groups[1].Value
        if ($actual -ne $expected) {
            $summaryOk = $false
            $summaryMismatch += "$k 期望=$expected 实际=$actual"
        }
    } else {
        $summaryOk = $false
        $summaryMismatch += "$k 字段缺失"
    }
}
if ($summaryOk) {
    Add-Result 'WBS-011' 'PASS' "summary 11 项统计字段全部正确（30 任务 + 30 Done + 0 Ready + 5 不进入项 + 39/49 对齐）"
} else {
    Add-Result 'WBS-011' 'FAIL' "summary 字段不正确: $($summaryMismatch -join '；')"
}

# ============ WBS-012：23 号追踪记录回写验证 ============
# 期望 line 118 在执行后被改为 Done
if (Test-Path $trackRecord) {
    $trackContent = Get-Content -Path $trackRecord -Encoding UTF8
    if ($trackContent.Count -ge 118) {
        $line118 = $trackContent[117]
        if ($line118 -match '61-实现任务WBS' -and $line118 -match '\| Done \|') {
            Add-Result 'WBS-012' 'PASS' "23 号追踪记录 line 118 已回写为 Done"
        } elseif ($line118 -match '61-实现任务WBS' -and $line118 -match '\| Ready \|') {
            Add-Result 'WBS-012' 'WARN' "23 号追踪记录 line 118 仍为 Ready，本批次执行后需更新为 Done"
        } else {
            Add-Result 'WBS-012' 'WARN' "23 号追踪记录 line 118 内容: $($line118.Substring(0, [Math]::Min(80, $line118.Length)))..."
        }
    } else {
        Add-Result 'WBS-012' 'FAIL' "23 号追踪记录行数不足 118"
    }
} else {
    Add-Result 'WBS-012' 'FAIL' "23 号追踪记录不存在"
}

# ============ 输出结果 ============
Write-Host "========== GA2-54 WBS 排期基线验收 =========="
Write-Host ""
$script:results | Format-Table -AutoSize
Write-Host ""
Write-Host "汇总: PASS=$script:passCount FAIL=$script:failCount WARN=$script:warnCount"
if ($script:failCount -eq 0) {
    Write-Host "结论: PASS"
} else {
    Write-Host "结论: FAIL（请修复上述 FAIL 项）"
}

# ============ 写入汇总文件 ============
$summaryFile = Join-Path $root 'release-evidence\v1.0.0\ga2-54-smoke.txt'
$summaryDir = Split-Path $summaryFile -Parent
if (-not (Test-Path $summaryDir)) { New-Item -ItemType Directory -Path $summaryDir -Force | Out-Null }
$lines = @()
$lines += "========== GA2-54 WBS 排期基线验收 =========="
$lines += "时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
$lines += ""
$lines += "Code`tSeverity`tMessage"
$lines += "----`t--------`t-------"
foreach ($r in $script:results) {
    $lines += "$($r.Code)`t$($r.Severity)`t$($r.Message)"
}
$lines += ""
$lines += "汇总: PASS=$script:passCount FAIL=$script:failCount WARN=$script:warnCount"
$lines += "结论: $(if ($script:failCount -eq 0) { 'PASS' } else { 'FAIL' })"
[System.IO.File]::WriteAllLines($summaryFile, $lines, (New-Object System.Text.UTF8Encoding($true)))
Write-Host ""
Write-Host "汇总已写入: $summaryFile"
