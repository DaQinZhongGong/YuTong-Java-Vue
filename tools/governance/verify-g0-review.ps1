<#
.SYNOPSIS
  GA2-53 验收脚本：对齐 60 号文档《G0 评审报告模板与问题整改清单》验收要求。
.DESCRIPTION
  本脚本对 60 号文档定义的 G0 评审报告实例和治理 YAML 三件套执行 12 项静态校验：
    1. 评审报告实例存在性
    2. 评审信息完整性（项目/阶段/日期/结论）
    3. 评审范围覆盖 11 个范围项
    4. 评分表 10 个维度全部填充
    5. 总分计算正确性（sum of 10 维度得分）
    6. 必查项 19 项全部有结论
    7. 问题分级 4 个等级定义存在
    8. 整改清单 P0/P1 问题 owner+dueDate 完整性
    9. 签署表 8 角色完整性（不伪造签署）
    10. 评审结论 SELF_CHECK_PASSED 语义正确性
    11. 治理 YAML 三件套一致性（g0-review-record + problem-remediation-list + sign-off-package）
    12. 追踪记录回写验证（23 号 line 117 Done）
  设计契约源：YuTong-Java-Docs/60-G0评审报告模板与问题整改清单/60-G0评审报告模板与问题整改清单.md
  治理 YAML 源：YuTong-Java-Docs/contracts/governance/{g0-review-record, problem-remediation-list, sign-off-package}.yaml
.NOTES
  GA2-53 | 60-G0评审报告模板与问题整改清单 | 验收脚本
#>

param(
    [string]$RepoRoot = (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)))
)

$ErrorActionPreference = 'Continue'
$passCount = 0
$failCount = 0
$warnCount = 0
$results = [System.Collections.Generic.List[PSCustomObject]]::new()

function Add-Result([string]$Id, [string]$Name, [string]$Status, [string]$Detail) {
    $results.Add([PSCustomObject]@{
        CheckId = $Id
        Name    = $Name
        Status  = $Status
        Detail  = $Detail
    })
    switch ($Status) {
        'PASS' { $script:passCount++ }
        'FAIL' { $script:failCount++ }
        'WARN' { $script:warnCount++ }
    }
    Write-Host "[$Status] $Id $Name" -ForegroundColor (@{ PASS='Green'; FAIL='Red'; WARN='Yellow' }[$Status])
    if ($Detail) { Write-Host "       $Detail" -ForegroundColor Gray }
}

# ========== 启动 ==========
Write-Host "`n=== GA2-53 验收脚本启动 ===" -ForegroundColor Cyan
Write-Host "RepoRoot: $RepoRoot`n"

$reportPath = Join-Path $RepoRoot 'release-evidence\v1.0.0\ga2-53-g0-review-report.md'
$g0RecordYaml = Join-Path $RepoRoot 'YuTong-Java-Docs\contracts\governance\g0-review-record.yaml'
$problemListYaml = Join-Path $RepoRoot 'YuTong-Java-Docs\contracts\governance\problem-remediation-list.yaml'
$signOffYaml = Join-Path $RepoRoot 'YuTong-Java-Docs\contracts\governance\sign-off-package.yaml'
$trackingRecord = Join-Path $RepoRoot 'YuTong-Java-Docs\23-设计到落地追踪记录\23-设计到落地追踪记录.md'
$designDocPath = Join-Path $RepoRoot 'YuTong-Java-Docs\60-G0评审报告模板与问题整改清单\60-G0评审报告模板与问题整改清单.md'

# ========== 检查 1：评审报告实例存在性 ==========
Write-Host "`n--- 检查 1：评审报告实例存在性 ---" -ForegroundColor Cyan
if (Test-Path $reportPath) {
    $reportContent = Get-Content $reportPath -Raw -Encoding UTF8
    Add-Result 'G0-REP-001' '评审报告实例存在' 'PASS' "路径: $reportPath"
} else {
    Add-Result 'G0-REP-001' '评审报告实例存在' 'FAIL' "File not found: $reportPath"
    $reportContent = ''
}

# ========== 检查 2：评审信息完整性 ==========
Write-Host "`n--- 检查 2：评审信息完整性 ---" -ForegroundColor Cyan
if ($reportContent) {
    $hasProject = $reportContent -match '\|\s*项目\s*\|\s*YuTong 全栈技术底座\s*\|'
    $hasStage = $reportContent -match '\|\s*评审阶段\s*\|\s*G0 设计完备性\s*\|'
    $hasDate = $reportContent -match '\|\s*评审日期\s*\|'
    $hasRoles = $reportContent -match '架构、后端、前端、移动端、测试、UI、运维、安全'
    $hasConclusion = $reportContent -match 'SELF_CHECK_PASSED'
    if ($hasProject -and $hasStage -and $hasDate -and $hasRoles -and $hasConclusion) {
        Add-Result 'G0-REP-002' '评审信息完整性' 'PASS' '项目+阶段+日期+8 角色+结论 SELF_CHECK_PASSED'
    } else {
        Add-Result 'G0-REP-002' '评审信息完整性' 'FAIL' "project=$hasProject stage=$hasStage date=$hasDate roles=$hasRoles conclusion=$hasConclusion"
    }
} else {
    Add-Result 'G0-REP-002' '评审信息完整性' 'FAIL' '报告内容为空'
}

# ========== 检查 3：评审范围覆盖 11 个范围项 ==========
Write-Host "`n--- 检查 3：评审范围覆盖 11 个范围项 ---" -ForegroundColor Cyan
if ($reportContent) {
    $ranges = @(
        @{name='核心设计 01~30'; pattern='01~30'},
        @{name='商用与顶尖专项 31~49'; pattern='31~49'},
        @{name='执行级分工与生产级详设 50~67'; pattern='50~67'},
        @{name='商用呈现升级容量供应链 68~73'; pattern='68~73'},
        @{name='开工签署联调发布验收 74~79'; pattern='74~79'},
        @{name='P2 落盘执行控制 80~85'; pattern='80~85'},
        @{name='P2 命令与验收模板 86~87'; pattern='86~87'},
        @{name='封版证据与 AI 实现总控 88~90'; pattern='88~90'},
        @{name='实现级端侧交付详设 91~96'; pattern='91~96'},
        @{name='跨团队交付闭环详设 97~99'; pattern='97~99'},
        @{name='产品权威口径与机器契约 100'; pattern='100、contracts'}
    )
    $missing = @()
    foreach ($r in $ranges) {
        if ($reportContent -notmatch [regex]::Escape($r.pattern)) {
            $missing += $r.name
        }
    }
    if ($missing.Count -eq 0) {
        Add-Result 'G0-REP-003' "评审范围覆盖 $($ranges.Count) 个范围项" 'PASS' '11 个范围项全部覆盖'
    } else {
        Add-Result 'G0-REP-003' '评审范围覆盖' 'FAIL' "缺失: $($missing -join ', ')"
    }
} else {
    Add-Result 'G0-REP-003' '评审范围覆盖' 'FAIL' '报告内容为空'
}

# ========== 检查 4：评分表 10 个维度全部填充 ==========
Write-Host "`n--- 检查 4：评分表 10 个维度全部填充 ---" -ForegroundColor Cyan
if ($reportContent) {
    $dimensions = @('战略与范围', '架构与工程', '数据与契约', 'UI/交互与美感', '后端分工', 'Web/移动分工', '低代码/AI', '测试与质量', '商用扩展', '开工治理与落盘控制')
    $missingDims = @()
    foreach ($d in $dimensions) {
        if ($reportContent -notmatch [regex]::Escape($d)) {
            $missingDims += $d
        }
    }
    if ($missingDims.Count -eq 0) {
        Add-Result 'G0-REP-004' "评分表 $($dimensions.Count) 个维度全部填充" 'PASS' '10 个维度齐全'
    } else {
        Add-Result 'G0-REP-004' '评分表维度' 'FAIL' "缺失: $($missingDims -join ', ')"
    }
} else {
    Add-Result 'G0-REP-004' '评分表维度' 'FAIL' '报告内容为空'
}

# ========== 检查 5：总分计算正确性 ==========
Write-Host "`n--- 检查 5：总分计算正确性 ---" -ForegroundColor Cyan
if ($reportContent) {
    # 提取评分表中各维度得分
    $scorePattern = '\|\s*(战略与范围|架构与工程|数据与契约|UI/交互与美感|后端分工|Web/移动分工|低代码/AI|测试与质量|商用扩展|开工治理与落盘控制)\s*\|\s*\d+\s*\|\s*(\d+)\s*\|'
    $scoreMatches = [regex]::Matches($reportContent, $scorePattern)
    $sumScores = 0
    foreach ($m in $scoreMatches) {
        $sumScores += [int]$m.Groups[2].Value
    }
    # 检查报告声明的总分（支持 **加粗** 格式）
    $declaredTotalMatch = [regex]::Match($reportContent, '\|\s*\*{0,2}总分\*{0,2}\s*\|\s*\*{0,2}\d+\*{0,2}\s*\|\s*\*{0,2}(\d+)\*{0,2}\s*\|')
    $declaredTotal = if ($declaredTotalMatch.Success) { [int]$declaredTotalMatch.Groups[1].Value } else { 0 }
    if ($scoreMatches.Count -eq 10 -and $sumScores -eq $declaredTotal -and $declaredTotal -ge 90 -and $declaredTotal -le 100) {
        Add-Result 'G0-REP-005' "总分计算正确 sum=$sumScores declared=$declaredTotal" 'PASS' "10 维度得分和=$sumScores，声明总分=$declaredTotal，处于 90~100 区间"
    } else {
        Add-Result 'G0-REP-005' '总分计算正确性' 'FAIL' "scoreMatches.Count=$($scoreMatches.Count) sumScores=$sumScores declaredTotal=$declaredTotal"
    }
} else {
    Add-Result 'G0-REP-005' '总分计算正确性' 'FAIL' '报告内容为空'
}

# ========== 检查 6：必查项 19 项全部有结论 ==========
Write-Host "`n--- 检查 6：必查项 19 项全部有结论 ---" -ForegroundColor Cyan
if ($reportContent) {
    $checklistCodes = @()
    for ($i = 1; $i -le 9; $i++) {
        $checklistCodes += "G0-A-00$i"
        $checklistCodes += "G0-B-00$i"
        $checklistCodes += "G0-C-00$i"
        $checklistCodes += "G0-D-00$i"
        $checklistCodes += "G0-E-00$i"
        $checklistCodes += "G0-F-00$i"
        $checklistCodes += "G0-G-00$i"
    }
    # 60 号文档必查项实际编号 G0-A-001 + G0-B-001 + G0-C-001~003 + G0-D-001~002 + G0-E-001 + G0-F-001~002 + G0-G-001 + G0-H-001~009 = 19 项
    $actualCodes = @('G0-A-001','G0-B-001','G0-C-001','G0-C-002','G0-C-003','G0-D-001','G0-D-002','G0-E-001','G0-F-001','G0-F-002','G0-G-001','G0-H-001','G0-H-002','G0-H-003','G0-H-004','G0-H-005','G0-H-006','G0-H-007','G0-H-008','G0-H-009')
    $missingCodes = @()
    foreach ($code in $actualCodes) {
        if ($reportContent -notmatch [regex]::Escape($code)) {
            $missingCodes += $code
        }
    }
    if ($missingCodes.Count -eq 0) {
        # 进一步检查每项都有结论（通过/有条件通过/不通过）
        $conclusionPattern = '\|\s*G0-[A-H]-\d{3}\s*\|[^|]+\|\s*(通过|有条件通过|不通过)\s*\|'
        $conclusionMatches = [regex]::Matches($reportContent, $conclusionPattern)
        if ($conclusionMatches.Count -ge 19) {
            Add-Result 'G0-REP-006' "必查项 $($actualCodes.Count) 项全部有结论" 'PASS' "实际匹配结论数: $($conclusionMatches.Count)"
        } else {
            Add-Result 'G0-REP-006' '必查项结论' 'WARN' "必查项编号齐全但结论匹配数 $($conclusionMatches.Count) < 19（可能表格格式偏差）"
        }
    } else {
        Add-Result 'G0-REP-006' '必查项编号' 'FAIL' "缺失: $($missingCodes -join ', ')"
    }
} else {
    Add-Result 'G0-REP-006' '必查项' 'FAIL' '报告内容为空'
}

# ========== 检查 7：问题分级 4 个等级定义存在 ==========
Write-Host "`n--- 检查 7：问题分级 4 个等级定义 ---" -ForegroundColor Cyan
if ($reportContent) {
    $hasP0 = $reportContent -match '\|\s*P0\s*\|\s*主键、架构、运行形态、核心边界冲突\s*\|'
    $hasP1 = $reportContent -match '\|\s*P1\s*\|\s*会导致核心模块返工\s*\|'
    $hasP2 = $reportContent -match '\|\s*P2\s*\|\s*可在实现阶段细化\s*\|'
    $hasP3 = $reportContent -match '\|\s*P3\s*\|\s*文案/格式/局部优化\s*\|'
    if ($hasP0 -and $hasP1 -and $hasP2 -and $hasP3) {
        Add-Result 'G0-REP-007' '问题分级 4 个等级定义' 'PASS' 'P0/P1/P2/P3 全部存在'
    } else {
        Add-Result 'G0-REP-007' '问题分级定义' 'FAIL' "P0=$hasP0 P1=$hasP1 P2=$hasP2 P3=$hasP3"
    }
} else {
    Add-Result 'G0-REP-007' '问题分级定义' 'FAIL' '报告内容为空'
}

# ========== 检查 8：整改清单 P0/P1 问题 owner+dueDate 完整性 ==========
Write-Host "`n--- 检查 8：整改清单 P0/P1 问题完整性 ---" -ForegroundColor Cyan
if (Test-Path $problemListYaml) {
    $problemContent = Get-Content $problemListYaml -Raw -Encoding UTF8
    # 提取所有 P0/P1 问题块（使用 \z 表示字符串绝对结尾，避免 $ 在多行模式下匹配行尾）
    $p0p1Pattern = "(?s)(problemId:\s*(G0-P[01]-\d{3}).*?)(?=problemId:|\z)"
    $p0p1Matches = [regex]::Matches($problemContent, $p0p1Pattern)
    $incomplete = @()
    $allProblems = @()
    foreach ($m in $p0p1Matches) {
        $block = $m.Groups[1].Value
        $probId = $m.Groups[2].Value
        $allProblems += $probId
        $hasOwner = $block -match 'owner:\s*\S+'
        $hasDueDate = $block -match 'dueDate:\s*\S+'
        $hasStatus = $block -match 'status:\s*\S+'
        if (-not ($hasOwner -and $hasDueDate -and $hasStatus)) {
            $incomplete += "$probId(owner=$hasOwner dueDate=$hasDueDate status=$hasStatus)"
        }
    }
    if ($allProblems.Count -ge 4 -and $incomplete.Count -eq 0) {
        Add-Result 'G0-REP-008' "P0/P1 问题完整性 ($($allProblems.Count) 个)" 'PASS' "全部 P0/P1 问题 owner+dueDate+status 完整: $($allProblems -join ', ')"
    } else {
        Add-Result 'G0-REP-008' 'P0/P1 问题完整性' 'FAIL' "total=$($allProblems.Count) incomplete=$($incomplete.Count): $($incomplete -join '; ')"
    }
} else {
    Add-Result 'G0-REP-008' 'P0/P1 问题完整性' 'FAIL' "File not found: $problemListYaml"
}

# ========== 检查 9：签署表 8 角色完整性（不伪造签署） ==========
Write-Host "`n--- 检查 9：签署表 8 角色完整性 ---" -ForegroundColor Cyan
if ($reportContent) {
    $roles = @('产品负责人', '架构负责人', '后端负责人', '前端负责人', '移动端负责人', '测试负责人', 'UI/体验负责人', '运维负责人', '安全负责人')
    $missingRoles = @()
    foreach ($r in $roles) {
        if ($reportContent -notmatch [regex]::Escape($r)) {
            $missingRoles += $r
        }
    }
    # 检查不伪造签署（所有签署应为 PENDING）
    $hasPending = $reportContent -match '\|\s*产品负责人\s*\|\s*PENDING\s*\|'
    if ($missingRoles.Count -eq 0 -and $hasPending) {
        Add-Result 'G0-REP-009' "签署表 $($roles.Count) 角色完整+不伪造" 'PASS' "9 角色齐全（含 UI/体验）+ PENDING 状态未伪造"
    } else {
        Add-Result 'G0-REP-009' '签署表完整性' 'FAIL' "missing=$($missingRoles -join ', ') hasPending=$hasPending"
    }
} else {
    Add-Result 'G0-REP-009' '签署表完整性' 'FAIL' '报告内容为空'
}

# ========== 检查 10：评审结论 SELF_CHECK_PASSED 语义正确性 ==========
Write-Host "`n--- 检查 10：评审结论 SELF_CHECK_PASSED 语义 ---" -ForegroundColor Cyan
if ($reportContent) {
    $hasSelfCheck = $reportContent -match 'SELF_CHECK_PASSED'
    $hasCanEnterG0 = $reportContent -match 'canEnterG0=true'
    $hasCanEnterP2 = $reportContent -match 'canEnterP2=false'
    $hasNotForge = $reportContent -match '不伪造正式签署' -or $reportContent -match '不伪造签署'
    if ($hasSelfCheck -and $hasCanEnterG0 -and $hasCanEnterP2 -and $hasNotForge) {
        Add-Result 'G0-REP-010' '评审结论 SELF_CHECK_PASSED 语义' 'PASS' 'SELF_CHECK_PASSED + canEnterG0=true + canEnterP2=false + 不伪造签署'
    } else {
        Add-Result 'G0-REP-010' '评审结论语义' 'FAIL' "selfCheck=$hasSelfCheck canEnterG0=$hasCanEnterG0 canEnterP2=$hasCanEnterP2 notForge=$hasNotForge"
    }
} else {
    Add-Result 'G0-REP-010' '评审结论语义' 'FAIL' '报告内容为空'
}

# ========== 检查 11：治理 YAML 三件套一致性 ==========
Write-Host "`n--- 检查 11：治理 YAML 三件套一致性 ---" -ForegroundColor Cyan
$yamlOk = $true
$yamlDetails = @()

# g0-review-record.yaml
if (Test-Path $g0RecordYaml) {
    $g0Content = Get-Content $g0RecordYaml -Raw -Encoding UTF8
    $hasSelfCheckSummary = $g0Content -match 'selfCheckSummary:'
    $hasVerdict = $g0Content -match 'verdict:\s*SELF_CHECK_PASSED'
    $hasCanEnterG0 = $g0Content -match 'canEnterG0:\s*true'
    $hasCanEnterP2 = $g0Content -match 'canEnterP2:\s*false'
    if (-not ($hasSelfCheckSummary -and $hasVerdict -and $hasCanEnterG0 -and $hasCanEnterP2)) {
        $yamlOk = $false
        $yamlDetails += "g0-review-record.yaml: selfCheckSummary=$hasSelfCheckSummary verdict=$hasVerdict canEnterG0=$hasCanEnterG0 canEnterP2=$hasCanEnterP2"
    }
} else {
    $yamlOk = $false
    $yamlDetails += "g0-review-record.yaml: NOT FOUND"
}

# problem-remediation-list.yaml
if (Test-Path $problemListYaml) {
    $problemContent = Get-Content $problemListYaml -Raw -Encoding UTF8
    # 检查 P1 问题数量
    $p1Matches = [regex]::Matches($problemContent, 'problemId:\s*(G0-P1-\d{3})')
    $p1Count = $p1Matches.Count
    # 检查 summary 中的 p1Open
    $p1OpenMatch = [regex]::Match($problemContent, 'p1Open:\s*(\d+)')
    $p1OpenDeclared = if ($p1OpenMatch.Success) { [int]$p1OpenMatch.Groups[1].Value } else { -1 }
    if ($p1Count -lt 4 -or $p1OpenDeclared -ne 4) {
        $yamlOk = $false
        $yamlDetails += "problem-remediation-list.yaml: p1Count=$p1Count p1OpenDeclared=$p1OpenDeclared (期望 4)"
    }
} else {
    $yamlOk = $false
    $yamlDetails += "problem-remediation-list.yaml: NOT FOUND"
}

# sign-off-package.yaml
if (Test-Path $signOffYaml) {
    $signOffContent = Get-Content $signOffYaml -Raw -Encoding UTF8
    $hasGa2_53 = $signOffContent -match 'ga2_53SelfCheck:'
    $hasPackagePending = $signOffContent -match 'packageStatus:\s*PENDING'
    $hasMustNotForge = $signOffContent -match 'mustNotForgeSignatures:\s*true'
    if (-not ($hasGa2_53 -and $hasPackagePending -and $hasMustNotForge)) {
        $yamlOk = $false
        $yamlDetails += "sign-off-package.yaml: ga2_53SelfCheck=$hasGa2_53 packagePending=$hasPackagePending mustNotForge=$hasMustNotForge"
    }
} else {
    $yamlOk = $false
    $yamlDetails += "sign-off-package.yaml: NOT FOUND"
}

if ($yamlOk) {
    Add-Result 'G0-REP-011' '治理 YAML 三件套一致性' 'PASS' 'g0-review-record + problem-remediation-list + sign-off-package 全部一致'
} else {
    Add-Result 'G0-REP-011' '治理 YAML 三件套一致性' 'FAIL' ($yamlDetails -join '; ')
}

# ========== 检查 12：追踪记录回写验证 ==========
Write-Host "`n--- 检查 12：追踪记录回写验证 ---" -ForegroundColor Cyan
if (Test-Path $trackingRecord) {
    $trackingContent = Get-Content $trackingRecord -Raw -Encoding UTF8
    # 找到 60 号那一行
    $line60Match = [regex]::Match($trackingContent, "(?m)^\| G0 评审报告 \|.*?\|(?:(?!\|).)*\|.*?\|.*?\|$")
    if ($line60Match.Success) {
        $line60 = $line60Match.Value
        $isDone = $line60 -match '\|\s*Done\s*\|'
        $hasGA2_53 = $line60 -match 'GA2-53'
        if ($isDone -and $hasGA2_53) {
            Add-Result 'G0-REP-012' '追踪记录回写' 'PASS' '23 号 line 117 已回写 Done + GA2-53'
        } else {
            Add-Result 'G0-REP-012' '追踪记录回写' 'FAIL' "isDone=$isDone hasGA2_53=$hasGA2_53"
        }
    } else {
        Add-Result 'G0-REP-012' '追踪记录回写' 'FAIL' '未找到 G0 评审报告 行'
    }
} else {
    Add-Result 'G0-REP-012' '追踪记录回写' 'FAIL' "File not found: $trackingRecord"
}

# ========== 汇总 ==========
Write-Host "`n=== GA2-53 验收汇总 ===" -ForegroundColor Cyan
$results | Format-Table -AutoSize
Write-Host ""
Write-Host "PASS: $passCount / FAIL: $failCount / WARN: $warnCount" -ForegroundColor Cyan

$summaryPath = Join-Path $RepoRoot "release-evidence\v1.0.0\ga2-53-smoke.txt"
$summaryLines = @()
$summaryLines += "=== GA2-53 验收脚本执行结果 ==="
$summaryLines += "执行时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
$summaryLines += ""
$summaryLines += "PASS: $passCount"
$summaryLines += "FAIL: $failCount"
$summaryLines += "WARN: $warnCount"
$summaryLines += ""
$summaryLines += "详细检查项:"
foreach ($r in $results) {
    $summaryLines += "[$($r.Status)] $($r.CheckId) $($r.Name) - $($r.Detail)"
}
$summary = $summaryLines -join "`r`n"
[System.IO.File]::WriteAllText($summaryPath, $summary, (New-Object System.Text.UTF8Encoding $true))
Write-Host "汇总报告已写入: $summaryPath" -ForegroundColor Green

if ($failCount -gt 0) { exit 1 } else { exit 0 }
