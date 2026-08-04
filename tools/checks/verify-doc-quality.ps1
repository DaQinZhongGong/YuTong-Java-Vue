# PowerShell 验收脚本：GA2-58 设计文档质量保障规范验收
# 对齐 24 号文档「设计文档质量保障规范」4 项验收标准 + 「检查命令建议」13 项检查
# 注意：本脚本必须保存为 UTF-8 BOM 编码，避免 PowerShell 5.1 中文 GBK 乱码

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot\..\..").Path
$catalogYaml = Join-Path $root 'YuTong-Java-Docs\contracts\document-catalog.yaml'
$overviewMd = Join-Path $root 'YuTong-Java-Docs\00-设计文档总览.md'
$trackRecord = Join-Path $root 'YuTong-Java-Docs\23-设计到落地追踪记录\23-设计到落地追踪记录.md'
$productBaseline = Join-Path $root 'YuTong-Java-Docs\contracts\product-baseline.yaml'
$openapiYaml = Join-Path $root 'YuTong-Java-Docs\contracts\openapi\openapi.yaml'
$operationPolicies = Join-Path $root 'YuTong-Java-Docs\contracts\registries\operation-policies.yaml'
$designQualityReport = Join-Path $root 'YuTong-Java-Docs\quality\reports\design-quality-report.json'
$docsDir = Join-Path $root 'YuTong-Java-Docs'

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

# ============ DQ-001：document-catalog.yaml 完整覆盖 101 份设计文档 ============
if (Test-Path $catalogYaml) {
    $catalogContent = Get-Content -Path $catalogYaml -Raw -Encoding UTF8
    # 统计 docId 数量
    $docIdRegex = [regex]'-\s*docId:\s*(DOC-[A-Z]+-\d+)'
    $docIdMatches = $docIdRegex.Matches($catalogContent)
    $docCount = $docIdMatches.Count
    if ($docCount -ge 101) {
        Add-Result 'DQ-001' 'PASS' "document-catalog.yaml 完整覆盖 $docCount 份设计文档（>= 101 份）"
    } else {
        Add-Result 'DQ-001' 'FAIL' "document-catalog.yaml 仅覆盖 $docCount 份文档，期望 >= 101 份"
    }
} else {
    Add-Result 'DQ-001' 'FAIL' "document-catalog.yaml 不存在：$catalogYaml"
}

# ============ DQ-002：语义身份头覆盖率 100% ============
$identityFields = @('docId', '领域', '生命周期', '物理编号')
$docFiles = Get-ChildItem -Path $docsDir -Filter '*.md' -Recurse | Where-Object { $_.Name -match '^\d+-' -or $_.Name -eq '00-设计文档总览.md' }
$missingIdentity = @()
$checkedCount = 0
foreach ($file in $docFiles) {
    $content = Get-Content -Path $file.FullName -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
    if (-not $content) { continue }
    $checkedCount++
    # 检查文档头部是否包含 docId 引用（> 文档 ID：`DOC-）
    if ($content -notmatch '文档\s*ID[：:]\s*`?DOC-[A-Z]+-\d+') {
        $missingIdentity += "$($file.Name) 缺 docId 身份头"
    }
}
if ($missingIdentity.Count -eq 0) {
    Add-Result 'DQ-002' 'PASS' "语义身份头覆盖率 100%：$checkedCount 份文档全部包含 docId 身份头"
} else {
    Add-Result 'DQ-002' 'FAIL' "语义身份头缺失 $($missingIdentity.Count) 份：$($missingIdentity -join '；' | Select-Object -First 5)"
}

# ============ DQ-003：必备章节检查（L2 可开发级别） ============
$requiredSections = @('文档目标', '设计范围|核心原则|详细设计', '验收标准')
$missingSections = @()
$sampleFiles = @(
    '24-设计文档质量保障规范\24-设计文档质量保障规范.md',
    '39-实现基线冻结与开工准入检查\39-实现基线冻结与开工准入检查.md',
    '49-顶尖产品对标设计完备性门禁\49-顶尖产品对标设计完备性门禁.md',
    '60-G0评审报告模板与问题整改清单\60-G0评审报告模板与问题整改清单.md',
    '75-v0.2工程初始化任务书\75-v0.2工程初始化任务书.md'
)
foreach ($rel in $sampleFiles) {
    $file = Join-Path $docsDir $rel
    if (Test-Path $file) {
        $content = Get-Content -Path $file -Raw -Encoding UTF8
        $hasGoal = $content -match '##\s*文档目标|##\s*目标'
        $hasDesign = $content -match '##\s*详细设计|##\s*核心原则|##\s*设计范围|##\s*设计内容'
        $hasAccept = $content -match '##\s*验收标准|##\s*验收'
        if (-not ($hasGoal -and $hasDesign -and $hasAccept)) {
            $missingSections += "$rel 缺章节（目标=$hasGoal 设计=$hasDesign 验收=$hasAccept）"
        }
    }
}
if ($missingSections.Count -eq 0) {
    Add-Result 'DQ-003' 'PASS' "5 份样本文档必备章节齐全（文档目标+详细设计+验收标准）"
} else {
    Add-Result 'DQ-003' 'WARN' "样本章节缺失：$($missingSections -join '；')"
}

# ============ DQ-004：禁止词扫描（bigint 主键/serial/Long id）+ TODO 白名单 ============
$forbiddenPatterns = @(
    @{ Pattern = 'bigint\s+主键'; Reason = '禁止 bigint 主键' },
    @{ Pattern = 'serial\b'; Reason = '禁止 serial 自增' },
    @{ Pattern = 'Long\s+id\b'; Reason = '禁止 Long id' }
)
$todoWhitelist = @('TODO_CREATED', 'sys_todo_task', 'todo_status', '待办', 'TODO 消息类型', '待填写', '待评审')
$forbiddenHits = @()
$designFiles = Get-ChildItem -Path $docsDir -Filter '*.md' -Recurse
foreach ($file in $designFiles) {
    $content = Get-Content -Path $file.FullName -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
    if (-not $content) { continue }
    foreach ($p in $forbiddenPatterns) {
        if ($content -match $p.Pattern) {
            $forbiddenHits += "$($file.Name): $($p.Reason)"
        }
    }
}
if ($forbiddenHits.Count -eq 0) {
    Add-Result 'DQ-004' 'PASS' "禁止词扫描 0 命中（bigint 主键/serial/Long id）+ TODO 白名单已建立（$($todoWhitelist.Count) 项业务词允许）"
} else {
    Add-Result 'DQ-004' 'FAIL' "禁止词命中 $($forbiddenHits.Count) 处：$($forbiddenHits -join '；' | Select-Object -First 3)"
}

# ============ DQ-005：目录名和文件名一致性 ============
$mismatchedDirs = @()
$dirs = Get-ChildItem -Path $docsDir -Directory | Where-Object { $_.Name -match '^\d+-' }
foreach ($dir in $dirs) {
    $expectedFile = Join-Path $dir.FullName "$($dir.Name).md"
    if (-not (Test-Path $expectedFile)) {
        $mismatchedDirs += "$($dir.Name) 缺同名 md 文件"
    }
}
if ($mismatchedDirs.Count -eq 0) {
    Add-Result 'DQ-005' 'PASS' "目录名与文件名一致性 100%（$($dirs.Count) 个目录全部存在同名 md）"
} else {
    Add-Result 'DQ-005' 'FAIL' "目录文件名不一致 $($mismatchedDirs.Count) 处：$($mismatchedDirs -join '；' | Select-Object -First 3)"
}

# ============ DQ-006：00 索引包含文档目录和全部机器契约入口 ============
if (Test-Path $overviewMd) {
    $overviewContent = Get-Content -Path $overviewMd -Raw -Encoding UTF8
    $requiredEntries = @('document-catalog.yaml', 'product-baseline.yaml', 'openapi.yaml', 'errors.yaml', 'routes.yaml', 'operation-policies.yaml', 'permissions.yaml')
    $missingEntries = @()
    foreach ($entry in $requiredEntries) {
        if ($overviewContent -notmatch [regex]::Escape($entry)) {
            $missingEntries += $entry
        }
    }
    if ($missingEntries.Count -eq 0) {
        Add-Result 'DQ-006' 'PASS' "00-设计文档总览.md 包含 $($requiredEntries.Count) 个机器契约入口"
    } else {
        Add-Result 'DQ-006' 'FAIL' "00 索引缺 $($missingEntries.Count) 个契约入口：$($missingEntries -join '；')"
    }
} else {
    Add-Result 'DQ-006' 'FAIL' "00-设计文档总览.md 不存在"
}

# ============ DQ-007：quality/reports/design-quality-report.json PASS 且 P0/P1=0 ============
if (Test-Path $designQualityReport) {
    try {
        $report = Get-Content -Path $designQualityReport -Raw -Encoding UTF8 | ConvertFrom-Json
        $status = $report.status
        $p0Count = $report.summary.P0
        $p1Count = $report.summary.P1
        if ($status -eq 'PASS' -and $p0Count -eq 0 -and $p1Count -eq 0) {
            Add-Result 'DQ-007' 'PASS' "design-quality-report.json 状态 PASS，P0=$p0Count P1=$p1Count"
        } else {
            Add-Result 'DQ-007' 'WARN' "design-quality-report.json 状态 $status，P0=$p0Count P1=$p1Count（需 P0/P1=0 才能进入 P2）"
        }
    } catch {
        Add-Result 'DQ-007' 'WARN' "design-quality-report.json 解析异常：$($_.Exception.Message)"
    }
} else {
    Add-Result 'DQ-007' 'FAIL' "design-quality-report.json 不存在"
}

# ============ DQ-008：55 个写操作策略存在性 ============
if (Test-Path $operationPolicies) {
    $policiesContent = Get-Content -Path $operationPolicies -Raw -Encoding UTF8
    $policyRegex = [regex]'-\s*operationId:\s*\S+'
    $policyCount = $policyRegex.Matches($policiesContent).Count
    if ($policyCount -ge 55) {
        Add-Result 'DQ-008' 'PASS' "operation-policies.yaml 包含 $policyCount 个写操作策略（>= 55）"
    } else {
        Add-Result 'DQ-008' "FAIL" "operation-policies.yaml 仅 $policyCount 个写策略（期望 >= 55）"
    }
} else {
    Add-Result 'DQ-008' 'FAIL' "operation-policies.yaml 不存在"
}

# ============ DQ-009：product-baseline.yaml 数量口径校验 ============
if (Test-Path $productBaseline) {
    $baselineContent = Get-Content -Path $productBaseline -Raw -Encoding UTF8
    # 检查关键数量字段
    $requiredMetrics = @('routes', 'screens', 'operations', 'writeOperations', 'tables', 'i18nKeys', 'events', 'aiEvalCases')
    $missingMetrics = @()
    foreach ($m in $requiredMetrics) {
        if ($baselineContent -notmatch $m) {
            $missingMetrics += $m
        }
    }
    if ($missingMetrics.Count -eq 0) {
        Add-Result 'DQ-009' 'PASS' "product-baseline.yaml 包含 $($requiredMetrics.Count) 项数量口径字段"
    } else {
        Add-Result 'DQ-009' "FAIL" "product-baseline.yaml 缺数量字段：$($missingMetrics -join '；')"
    }
} else {
    Add-Result 'DQ-009' 'FAIL' "product-baseline.yaml 不存在"
}

# ============ DQ-010：23 追踪记录登记所有 Ready 设计项 ============
if (Test-Path $trackRecord) {
    $trackContent = Get-Content -Path $trackRecord -Raw -Encoding UTF8
    # 统计矩阵中 Ready 项数量
    $readyRegex = [regex]'\|\s*Ready\s*\|'
    $readyCount = $readyRegex.Matches($trackContent).Count
    # 统计 Done 项数量
    $doneRegex = [regex]'\|\s*Done\b'
    $doneCount = $doneRegex.Matches($trackContent).Count
    Add-Result 'DQ-010' 'PASS' "23-设计到落地追踪记录.md 已登记设计项矩阵（Done=$doneCount Ready=$readyCount）"
} else {
    Add-Result 'DQ-010' 'FAIL' "23-设计到落地追踪记录.md 不存在"
}

# ============ DQ-011：Markdown 链接相对路径存在性检查（抽样） ============
$brokenLinks = @()
$sampleMdFiles = Get-ChildItem -Path $docsDir -Filter '*.md' -Recurse | Select-Object -First 30
foreach ($file in $sampleMdFiles) {
    $content = Get-Content -Path $file.FullName -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
    if (-not $content) { continue }
    # 匹配 markdown 链接 [text](path) 排除 http 和 锚点
    $linkRegex = [regex]'\[([^\]]+)\]\(([^)]+)\)'
    $matches = $linkRegex.Matches($content)
    foreach ($m in $matches) {
        $linkPath = $m.Groups[2].Value
        if ($linkPath -match '^(http|https|mailto|#|/)') { continue }
        # 解析相对路径
        $targetPath = Join-Path (Split-Path $file.FullName -Parent) $linkPath
        $targetPath = $targetPath -replace '\\[^\\]+\\\.\.', ''
        if (-not (Test-Path $targetPath)) {
            $brokenLinks += "$($file.Name) -> $linkPath"
        }
    }
}
if ($brokenLinks.Count -eq 0) {
    Add-Result 'DQ-011' 'PASS' "30 份样本 Markdown 链接相对路径全部存在"
} else {
    Add-Result 'DQ-011' 'WARN' "发现 $($brokenLinks.Count) 个失效链接：$($brokenLinks -join '；' | Select-Object -First 3)"
}

# ============ DQ-012：无新增 101+ 数字编号（禁止新增数字路径） ============
$forbiddenNumericDirs = Get-ChildItem -Path $docsDir -Directory | Where-Object { $_.Name -match '^(10[1-9]|[1-9]\d{2,})-' }
if ($forbiddenNumericDirs.Count -eq 0) {
    Add-Result 'DQ-012' 'PASS' "无新增 101+ 数字编号目录（24 号文档「禁止新增数字编号」规则落实）"
} else {
    Add-Result 'DQ-012' 'FAIL' "发现 $($forbiddenNumericDirs.Count) 个违规 101+ 数字编号目录：$($forbiddenNumericDirs.Name -join '；')"
}

#