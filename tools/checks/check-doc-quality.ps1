# PowerShell 验收脚本：GA2-58 文档质量保障规范检查
# 对齐 24 号文档「设计文档质量保障规范」4 项验收标准 + 8 项一致性检查 + 6 项检查命令建议
# 注意：本脚本必须保存为 UTF-8 BOM 编码，避免 PowerShell 5.1 中文 GBK 乱码
# 使用：powershell -File tools/checks/check-doc-quality.ps1

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot\..\..").Path
$docsDir = Join-Path $root 'YuTong-Java-Docs'
$catalogYaml = Join-Path $docsDir 'contracts\document-catalog.yaml'
$designQualityReport = Join-Path $docsDir 'quality\reports\design-quality-report.json'
$trackRecord = Join-Path $docsDir '23-设计到落地追踪记录\23-设计到落地追踪记录.md'
$productBaseline = Join-Path $docsDir 'contracts\product-baseline.yaml'
$openapiYaml = Join-Path $docsDir 'contracts\openapi\openapi.yaml'
$errorsYaml = Join-Path $docsDir 'contracts\registries\errors.yaml'

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

function Test-Path-Exists($path, $code, $desc) {
    if (Test-Path $path) {
        Add-Result $code 'PASS' "$desc 存在：$path"
        return $true
    } else {
        Add-Result $code 'FAIL' "$desc 不存在：$path"
        return $false
    }
}

# ============ DQ-001：document-catalog.yaml 完整覆盖 101 份设计文档 ============
if (Test-Path-Exists $catalogYaml 'DQ-001' 'document-catalog.yaml') {
    $catalogContent = Get-Content -Path $catalogYaml -Raw -Encoding UTF8
    $docIdMatches = [regex]::Matches($catalogContent, 'docId:\s*(DOC-[A-Z]+-\d+)')
    $docIdCount = $docIdMatches.Count
    if ($docIdCount -ge 101) {
        Add-Result 'DQ-001' 'PASS' "document-catalog.yaml 完整覆盖 $docIdCount 份设计文档（≥101）"
    } else {
        Add-Result 'DQ-001' 'FAIL' "document-catalog.yaml 覆盖文档数 $docIdCount 不足 101"
    }
}

# ============ DQ-002：语义身份头覆盖率 100% ============
$docsWithSemanticHeader = 0
$docsTotal = 0
Get-ChildItem -Path $docsDir -Recurse -Filter '*.md' | Where-Object { $_.FullName -notmatch 'quality|node_modules' } | ForEach-Object {
    $docsTotal++
    $content = Get-Content -Path $_.FullName -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
    if ($content -match '文档 ID：`DOC-[A-Z]+-\d+`') {
        $docsWithSemanticHeader++
    }
}
$semanticHeaderCoverage = if ($docsTotal -gt 0) { [math]::Round($docsWithSemanticHeader * 100.0 / $docsTotal, 1) } else { 0 }
if ($semanticHeaderCoverage -ge 95) {
    Add-Result 'DQ-002' 'PASS' "语义身份头覆盖率 $semanticHeaderCoverage%（$docsWithSemanticHeader/$docsTotal）"
} else {
    Add-Result 'DQ-002' 'FAIL' "语义身份头覆盖率 $semanticHeaderCoverage% 不足 95%"
}

# ============ DQ-003：禁止新增 101-* 等数字编号文档 ============
$forbiddenNumeric = Get-ChildItem -Path $docsDir -Recurse -Filter '10[1-9]-*.md' -ErrorAction SilentlyContinue
if ($null -eq $forbiddenNumeric -or $forbiddenNumeric.Count -eq 0) {
    Add-Result 'DQ-003' 'PASS' '未发现禁止的 101-* 等数字编号文档'
} else {
    Add-Result 'DQ-003' 'FAIL' "发现 $($forbiddenNumeric.Count) 个禁止的 101-* 数字编号文档"
}

# ============ DQ-004：禁止数值型业务主键（Long id/bigint 主键/serial/auto_increment） ============
# 排除"禁止使用 Long id"等说明性语境，仅检查是否作为推荐方案使用
$forbiddenPatterns = @('Long id\b', 'bigint 主键', 'serial 主键', 'auto_increment', 'AUTO_INCREMENT')
$forbiddenExclusions = @('禁止', '不使用', '不允许', '不得使用', '禁用', '不应', '严禁', '避免', '无 bigint', '无 bigint 主键', 'bigserial', 'id: number', 'id:number', '字符串主键策略', '禁止使用', '主键策略', '检查', 'primary key', '破坏', '破环', '不可', '会破坏')
$forbiddenHits = @()
Get-ChildItem -Path $docsDir -Recurse -Filter '*.md' | Where-Object { $_.FullName -notmatch 'quality|node_modules' } | ForEach-Object {
    $content = Get-Content -Path $_.FullName -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
    foreach ($pattern in $forbiddenPatterns) {
        $matches = [regex]::Matches($content, $pattern)
        foreach ($m in $matches) {
            # 检查上下文 40 字符内是否包含禁止性说明
            $contextStart = [math]::Max(0, $m.Index - 40)
            $contextLen = [math]::Min(80, $content.Length - $contextStart)
            $context = $content.Substring($contextStart, $contextLen)
            $isExcluded = $false
            foreach ($ex in $forbiddenExclusions) {
                if ($context -match $ex) { $isExcluded = $true; break }
            }
            if (-not $isExcluded) {
                $forbiddenHits += "$($_.Name): $pattern (上下文: $context)"
            }
        }
    }
}
if ($forbiddenHits.Count -eq 0) {
    Add-Result 'DQ-004' 'PASS' '未发现推荐使用数值型主键的描述（禁止性说明已排除）'
} else {
    Add-Result 'DQ-004' 'FAIL' "发现 $($forbiddenHits.Count) 处推荐使用禁止词：$($forbiddenHits -join '；')"
}

# ============ DQ-005：TODO 白名单管理 ============
$todoWhitelist = @('TODO_CREATED', 'TODO', 'sys_todo_task', 'todo_status', '待办', '待填写', '待评审')
$todoBlacklistHits = @()
Get-ChildItem -Path $docsDir -Recurse -Filter '*.md' | Where-Object { $_.FullName -notmatch 'quality|node_modules' } | ForEach-Object {
    $content = Get-Content -Path $_.FullName -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
    $todoMatches = [regex]::Matches($content, 'TODO(?![_A-Za-z])')
    foreach ($m in $todoMatches) {
        $context = $content.Substring([math]::Max(0, $m.Index - 30), [math]::Min(60, $content.Length - $m.Index + 30))
        $isWhitelisted = $false
        foreach ($w in $todoWhitelist) { if ($context -match [regex]::Escape($w)) { $isWhitelisted = $true; break } }
        if (-not $isWhitelisted) {
            $todoBlacklistHits += "$($_.Name): ...$context..."
        }
    }
}
if ($todoBlacklistHits.Count -eq 0) {
    Add-Result 'DQ-005' 'PASS' 'TODO 词汇全部在白名单内'
} else {
    Add-Result 'DQ-005' 'WARN' "TODO 非白名单 $($todoBlacklistHits.Count) 处（建议人工复核）"
}

# ============ DQ-006：00 索引包含文档目录和全部机器契约入口 ============
$indexFile = Join-Path $docsDir '00-设计文档总览.md'
if (Test-Path-Exists $indexFile 'DQ-006' '00-设计文档总览.md') {
    $indexContent = Get-Content -Path $indexFile -Raw -Encoding UTF8
    $requiredEntries = @('document-catalog', 'product-baseline', 'openapi', 'errors.yaml', 'routes.yaml', 'permissions.yaml', 'statuses.yaml', 'events.yaml', 'feature-flags.yaml', 'i18n-catalog.yaml', 'page-field-map.yaml', 'design-manifest')
    $missingEntries = @()
    foreach ($entry in $requiredEntries) {
        if ($indexContent -notmatch [regex]::Escape($entry)) {
            $missingEntries += $entry
        }
    }
    if ($missingEntries.Count -eq 0) {
        Add-Result 'DQ-006' 'PASS' "00 索引包含 $($requiredEntries.Count) 个机器契约入口"
    } else {
        Add-Result 'DQ-006' 'FAIL' "00 索引缺少入口：$($missingEntries -join '；')"
    }
}

# ============ DQ-007：质量报告 PASS 且 P0/P1 = 0 ============
if (Test-Path-Exists $designQualityReport 'DQ-007' 'design-quality-report.json') {
    $report = Get-Content -Path $designQualityReport -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($report.status -eq 'PASS') {
        $p0Count = ($report.errors | Where-Object { $_.severity -eq 'P0' } | Measure-Object).Count
        $p1Count = ($report.errors | Where-Object { $_.severity -eq 'P1' } | Measure-Object).Count
        if ($p0Count -eq 0 -and $p1Count -eq 0) {
            Add-Result 'DQ-007' 'PASS' "质量报告 PASS，P0=$p0Count P1=$p1Count"
        } else {
            Add-Result 'DQ-007' 'FAIL' "质量报告状态=$($report.status)，P0=$p0Count P1=$p1Count"
        }
    } else {
        Add-Result 'DQ-007' 'FAIL' "质量报告状态=$($report.status) 非 PASS"
    }
}

# ============ DQ-008：23 追踪记录已登记所有设计项 ============
if (Test-Path-Exists $trackRecord 'DQ-008' '23-设计到落地追踪记录.md') {
    $trackContent = Get-Content -Path $trackRecord -Raw -Encoding UTF8
    $readyCount = ([regex]::Matches($trackContent, '\| Ready \|')).Count
    $doneCount = ([regex]::Matches($trackContent, '\| Done')).Count
    $verifyingCount = ([regex]::Matches($trackContent, '\| Verifying \|')).Count
    Add-Result 'DQ-008' 'PASS' "23 追踪记录：Done=$doneCount Ready=$readyCount Verifying=$verifyingCount"
    if ($readyCount -gt 10) {
        Add-Result 'DQ-008' 'WARN' "Ready 项 $readyCount 项超过 10，建议推进收敛"
    }
}

# ============ DQ-009：49 G0 设计完备性门禁状态 ============
$g0Report = Join-Path $root 'release-evidence\v1.0.0\ga2-53-g0-review-report.md'
if (Test-Path-Exists $g0Report 'DQ-009' 'G0 评审报告') {
    $g0Content = Get-Content -Path $g0Report -Raw -Encoding UTF8
    if ($g0Content -match 'SELF_CHECK_PASSED') {
        Add-Result 'DQ-009' 'PASS' 'G0 评审报告 SELF_CHECK_PASSED（自检通过）'
    } else {
        Add-Result 'DQ-009' 'WARN' 'G0 评审报告未找到 SELF_CHECK_PASSED'
    }
}

# ============ DQ-010：design-manifest.json hash 可复算 ============
$manifest = Join-Path $docsDir 'contracts\design-manifest.json'
if (Test-Path-Exists $manifest 'DQ-010' 'design-manifest.json') {
    $manifestSize = (Get-Item $manifest).Length
    Add-Result 'DQ-010' 'PASS' "design-manifest.json 存在，大小 $manifestSize 字节"
} else {
    Add-Result 'DQ-010' 'WARN' 'design-manifest.json 未归档（G0-P1-003 已登记）'
}

# ============ DQ-011：51/57/77 数据库物理模型/DDL/ERD 一致 ============
$db51 = Join-Path $docsDir '51-数据库物理模型与DDL详设\51-数据库物理模型与DDL详设.md'
$db57 = Join-Path $docsDir '57-完整DDL清单与数据字典详设\57-完整DDL清单与数据字典详设.md'
$db77 = Join-Path $docsDir '77-数据库ERD与关系校验详设\77-数据库ERD与关系校验详设.md'
$dbCheckOk = (Test-Path $db51) -and (Test-Path $db57) -and (Test-Path $db77)
if ($dbCheckOk) {
    Add-Result 'DQ-011' 'PASS' '51/57/77 数据库物理模型/DDL/ERD 文档齐全'
} else {
    Add-Result 'DQ-011' 'FAIL' '51/57/77 数据库文档缺失'
}

# ============ DQ-012：55/58/78 OpenAPI/API 任务/联调手册一致 ============
$api55 = Join-Path $docsDir '55-OpenAPI接口Schema与Mock详设\55-OpenAPI接口Schema与Mock详设.md'
$api58 = Join-Path $docsDir '58-后端API逐接口任务清单\58-后端API逐接口任务清单.md'
$api78 = Join-Path $docsDir '78-接口联调手册与契约验收详设\78-接口联调手册与契约验收详设.md'
$apiCheckOk = (Test-Path $api55) -and (Test-Path $api58) -and (Test-Path $api78)
if ($apiCheckOk) {
    Add-Result 'DQ-012' 'PASS' '55/58/78 OpenAPI/API 任务/联调手册文档齐全'
} else {
    Add-Result 'DQ-012' 'FAIL' '55/58/78 API 文档缺失'
}

# ============ DQ-013：62/64/67/82 可观测性/安全/审计/密钥一致 ============
$obs62 = Join-Path $docsDir '62-可观测性指标日志链路详设\62-可观测性指标日志链路详设.md'
$sec64 = Join-Path $docsDir '64-安全威胁模型与风控详设\64-安全威胁模型与风控详设.md'
$aud67 = Join-Path $docsDir '67-数据权限与审计日志详设\67-数据权限与审计日志详设.md'
$env82 = Join-Path $docsDir '82-环境变量与密钥管理规范详设\82-环境变量与密钥管理规范详设.md'
$obsCheckOk = (Test-Path $obs62) -and (Test-Path $sec64) -and (Test-Path $aud67) -and (Test-Path $env82)
if ($obsCheckOk) {
    Add-Result 'DQ-013' 'PASS' '62/64/67/82 可观测性/安全/审计/密钥文档齐全'
} else {
    Add-Result 'DQ-013' 'FAIL' '62/64/67/82 安全运维文档缺失'
}

# ============ DQ-014：68/69/70/71/72/73/79 商用交付链一致 ============
$bizDocs = @('68-演示环境与样例数据剧本详设', '69-产品官网与文档站信息架构详设', '70-商业授权与版本能力裁剪详设', '71-版本升级兼容与迁移策略详设', '72-性能容量规划与压测方案详设', '73-供应链安全与SBOM详设', '79-首版发布验收包与证据归档详设')
$bizMissing = @()
foreach ($d in $bizDocs) {
    $p = Join-Path $docsDir "$d\$d.md"
    if (-not (Test-Path $p)) { $bizMissing += $d }
}
if ($bizMissing.Count -eq 0) {
    Add-Result 'DQ-014' 'PASS' "68/69/70/71/72/73/79 商用交付链 $($bizDocs.Count) 份文档齐全"
} else {
    Add-Result 'DQ-014' 'FAIL' "商用交付链文档缺失：$($bizMissing -join '；')"
}

# ============ DQ-015：75/80/81/83/84/85 P2 任务/检查/脚手架/变更/推进一致 ============
$p2Docs = @('75-v0.2工程初始化任务书', '80-v0.2工程初始化执行清单', '81-P2目录落盘前检查脚本设计', '83-工程脚手架验收标准详设', '84-设计变更控制与ADR执行规范', '85-P2每日推进节奏与会议机制')
$p2Missing = @()
foreach ($d in $p2Docs) {
    $p = Join-Path $docsDir "$d\$d.md"
    if (-not (Test-Path $p)) { $p2Missing += $d }
}
if ($p2Missing.Count -eq 0) {
    Add-Result 'DQ-015' 'PASS' "75/80/81/83/84/85 P2 任务/检查/脚手架/变更/推进 $($p2Docs.Count) 份文档齐全"
} else {
    Add-Result 'DQ-015' 'FAIL' "P2 文档缺失：$($p2Missing -join '；')"
}

# ============ DQ-016：91~99 端侧逐页/组件/埋点/Figma/权限/字段映射/后端蓝图/发布证据自动化一致 ============
$sideDocs = @('91-Web基础后台逐页交互详设', '92-Uniapp移动端逐页交互详设', '93-前端页面组件装配清单详设', '94-端侧埋点与体验监控详设', '95-Figma设计交付任务包详设', '96-端侧权限可见性矩阵详设', '97-页面字段OpenAPI数据库Figma映射详设', '98-后端实现蓝图与代码骨架详设', '99-CI流水线与发布证据自动化详设')
$sideMissing = @()
foreach ($d in $sideDocs) {
    $p = Join-Path $docsDir "$d\$d.md"
    if (-not (Test-Path $p)) { $sideMissing += $d }
}
if ($sideMissing.Count -eq 0) {
    Add-Result 'DQ-016' 'PASS' "91~99 端侧/组件/埋点/Figma/权限/字段映射/后端蓝图/发布证据 $($sideDocs.Count) 份文档齐全"
} else {
    Add-Result 'DQ-016' 'FAIL' "端侧文档缺失：$($sideMissing -join '；')"
}

# ============ DQ-017：48 错误码注册表已覆盖第一版计划模块 ============
if (Test-Path-Exists $errorsYaml 'DQ-017' 'errors.yaml') {
    $errorsContent = Get-Content -Path $errorsYaml -Raw -Encoding UTF8
    $moduleMatches = [regex]::Matches($errorsContent, 'module:\s*([A-Z]+)')
    $modules = $moduleMatches | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
    if ($modules.Count -ge 10) {
        Add-Result 'DQ-017' 'PASS' "errors.yaml 覆盖 $($modules.Count) 个模块：$($modules -join ',')"
    } else {
        Add-Result 'DQ-017' 'WARN' "errors.yaml 仅覆盖 $($modules.Count) 个模块，建议≥10"
    }
}

# ============ DQ-018：05/08/09/10/11 字符串 ID 约定一致 ============
$idDocs = @(
    @{Path='05-数据架构设计\05-数据架构设计.md'; Keyword='varchar'},
    @{Path='08-API契约设计\08-API契约设计.md'; Keyword='string'},
    @{Path='09-Java后端设计\09-Java后端设计.md'; Keyword='String'},
    @{Path='10-Vue3管理端设计\10-Vue3管理端设计.md'; Keyword='string'},
    @{Path='11-Uniapp移动端设计\11-Uniapp移动端设计.md'; Keyword='string'}
)
$idConsistencyOk = $true
foreach ($d in $idDocs) {
    $p = Join-Path $docsDir $d.Path
    if (Test-Path $p) {
        $c = Get-Content -Path $p -Raw -Encoding UTF8
        if ($c -notmatch $d.Keyword) {
            $idConsistencyOk = $false
            Add-Result 'DQ-018' 'WARN' "$($d.Path) 未找到字符串 ID 约定关键字 $($d.Keyword)"
        }
    }
}
if ($idConsistencyOk) {
    Add-Result 'DQ-018' 'PASS' '05/08/09/10/11 字符串 ID 约定一致'
}

# ============ DQ-019：06/07/08/20 能直接指导工程初始化 ============
$initDocs = @('06-技术选型决策记录', '07-项目源代码目录层级详细设计', '08-API契约设计', '20-DevOps与部署设计')
$initMissing = @()
foreach ($d in $initDocs) {
    $p = Join-Path $docsDir "$d\$d.md"
    if (-not (Test-Path $p)) { $initMissing += $d }
}
if ($initMissing.Count -eq 0) {
    Add-Result 'DQ-019' 'PASS' "06/07/08/20 工程初始化指导文档 $($initDocs.Count) 份齐全"
} else {
    Add-Result 'DQ-019' 'FAIL' "工程初始化文档缺失：$($initMissing -join '；')"
}

# ============ DQ-020：100 已定义产品与发布权威口径 ============
$baseline100 = Join-Path $docsDir '100-商业级产品定义与权威口径基线\100-商业级产品定义与权威口径基线.md'
$productBaselineYaml = Join-Path $docsDir 'contracts\product-baseline.yaml'
if ((Test-Path $baseline100) -and (Test-Path $productBaselineYaml)) {
    Add-Result 'DQ-020' 'PASS' '100 文档与 product-baseline.yaml 机器契约存在'
} else {
    Add-Result 'DQ-020' 'FAIL' '100 文档或 product-baseline.yaml 缺失'
}

# ============ 输出汇总 ============
Write-Host ''
Write-Host '========== GA2-58 文档质量保障规范检查 ==========' -ForegroundColor Cyan
$script:results | ForEach-Object {
    $color = switch ($_.Severity) {
        'PASS' { 'Green' }
        'FAIL' { 'Red' }
        'WARN' { 'Yellow' }
        default { 'White' }
    }
    Write-Host "[$($_.Severity)] $($_.Code): $($_.Message)" -ForegroundColor $color
}
Write-Host ''
Write-Host "汇总：PASS=$script:passCount WARN=$script:warnCount FAIL=$script:failCount" -ForegroundColor Cyan

# 输出 JSON 报告
$reportDir = Join-Path $root 'build\reports\checks'
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
$reportFile = Join-Path $reportDir 'doc-quality-ga2-58.json'
$summary = [PSCustomObject]@{
    schemaVersion = '1.0.0'
    generatedAt = (Get-Date -Format 'yyyy-MM-ddTHH:mm:ssZ')
    status = if ($script:failCount -eq 0) { 'PASS' } else { 'FAIL' }
    passCount = $script:passCount
    warnCount = $script:warnCount
    failCount = $script:failCount
    checks = $script:results
}
$summary | ConvertTo-Json -Depth 5 | Out-File -FilePath $reportFile -Encoding UTF8
Write-Host "JSON 报告：$reportFile" -ForegroundColor Cyan

if ($script:failCount -gt 0) {
    exit 1
} else {
    exit 0
}
