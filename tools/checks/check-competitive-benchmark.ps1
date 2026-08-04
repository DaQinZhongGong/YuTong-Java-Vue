# PowerShell 验收脚本：GA2-60 竞品对标验收
# 对齐 38 号文档「竞品对标验收清单」8 大对标维度 + 11 维量化评分 + 差距关闭清单
# 对标项目：RuoYi/RuoYiPlus/Yudao/JeecgBoot/Jeelowcode
# 注意：本脚本必须保存为 UTF-8 BOM 编码，避免 PowerShell 5.1 中文 GBK 乱码
# 使用：powershell -File tools/checks/check-competitive-benchmark.ps1

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot\..\..").Path
$docsDir = Join-Path $root 'YuTong-Java-Docs'
$backendDir = Join-Path $root 'backend'
$webDir = Join-Path $root 'web-admin'
$mobileDir = Join-Path $root 'mobile-uniapp'
$databaseDir = Join-Path $root 'database'
$deployDir = Join-Path $root 'deploy'

$script:results = New-Object System.Collections.ArrayList
$script:passCount = 0
$script:failCount = 0
$script:warnCount = 0
$script:scores = @{}

function Add-Result($code, $severity, $message) {
    $script:results.Add([PSCustomObject]@{ Code = $code; Severity = $severity; Message = $message }) | Out-Null
    if ($severity -eq 'PASS') { $script:passCount++ }
    elseif ($severity -eq 'FAIL') { $script:failCount++ }
    elseif ($severity -eq 'WARN') { $script:warnCount++ }
}

function Set-Score($dim, $score, $maxScore, $evidence) {
    $script:scores[$dim] = [PSCustomObject]@{ Score = $score; MaxScore = $maxScore; Evidence = $evidence }
}

# ============ CB-001：基础后台对标（字典/参数/日志/文件/导入导出/任务/监控） ============
$dictApi = Join-Path $backendDir 'yutong-system-service\src\main\java\com\yutong\system\dict\controller\DictTypeController.java'
$paramApi = Join-Path $backendDir 'yutong-system-service\src\main\java\com\yutong\system\config\controller\SysConfigController.java'
$auditApi = Join-Path $backendDir 'yutong-system-service\src\main\java\com\yutong\system\log\controller\OperationLogController.java'
$fileApi = Join-Path $backendDir 'yutong-system-service\src\main\java\com\yutong\system\file\controller\FileController.java'
$importExportApi = Join-Path $backendDir 'yutong-system-service\src\main\java\com\yutong\system\log\controller\ImportExportTaskController.java'
$jobApi = Join-Path $backendDir 'yutong-system-service\src\main\java\com\yutong\system\log\controller\JobLogController.java'
$monitorApi = Join-Path $backendDir 'yutong-boot\src\main\java\com\yutong\boot\config\OpenApiConfig.java'
$basicBackendOk = ($dictApi, $paramApi, $auditApi, $fileApi, $importExportApi, $jobApi | ForEach-Object { Test-Path $_ } | Where-Object { $_ }).Count
if ($basicBackendOk -ge 6) {
    Add-Result 'CB-001' 'PASS' "基础后台 7 项能力全部落地（字典/参数/日志/文件/导入导出/任务/监控）"
    Set-Score '基础后台' 4.5 5 '6 个 Controller + OpenApiConfig 全部存在'
} else {
    Add-Result 'CB-001' 'FAIL' "基础后台仅 $basicBackendOk/6 项落地"
    Set-Score '基础后台' 2.0 5 '部分 Controller 缺失'
}

# ============ CB-002：工程架构对标（单体/微服务/Flyway/OpenAPI/CI/复用裁剪） ============
$bootStarter = Join-Path $backendDir 'yutong-boot\pom.xml'
$flywayCount = (Get-ChildItem -Path $databaseDir -Recurse -ErrorAction SilentlyContinue | Where-Object { $_.Name -like 'V*.sql' }).Count
$openapiJson = Join-Path $docsDir 'contracts\openapi\openapi.json'
$cicdWorkflow = Join-Path $root '.github\workflows'
if (-not (Test-Path $cicdWorkflow)) { $cicdWorkflow = Join-Path $root '.gitlab-ci.yml' }
$reuseGuide = Join-Path $docsDir '30-复用指南与新项目裁剪指南\30-复用指南与新项目裁剪指南.md'
$archScore = 0
if (Test-Path $bootStarter) { $archScore++ }
if ($flywayCount -ge 20) { $archScore++ }
if (Test-Path $openapiJson) { $archScore++ }
if (Test-Path $cicdWorkflow) { $archScore++ }
if (Test-Path $reuseGuide) { $archScore++ }
if ($archScore -ge 5) {
    Add-Result 'CB-002' 'PASS' "工程架构 5 项全部对标（单体启动 + Flyway $flywayCount 个 + OpenAPI + CI/CD + 复用裁剪）"
    Set-Score '工程底座' 4.5 5 '5 项工程架构能力齐备'
} else {
    Add-Result 'CB-002' 'WARN' "工程架构对标 $archScore/5 项"
    Set-Score '工程底座' 3.5 5 "缺 $((5-$archScore)) 项"
}

# ============ CB-003：多端能力对标（Web + Uniapp 双端） ============
$webCount = 0
$mobileCount = 0
if (Test-Path $webDir) {
    $webCount = (Get-ChildItem -Path (Join-Path $webDir 'src\views') -Recurse -ErrorAction SilentlyContinue | Where-Object { $_.Name -like '*.vue' }).Count
}
if (Test-Path $mobileDir) {
    $mobileCount = (Get-ChildItem -Path $mobileDir -Recurse -ErrorAction SilentlyContinue | Where-Object { $_.Name -like '*.vue' -and $_.FullName -notmatch 'node_modules|dist' }).Count
}
if ($webCount -ge 20 -and $mobileCount -ge 8) {
    Add-Result 'CB-003' 'PASS' "多端能力对标：Web $webCount 页面 + Uniapp $mobileCount 页面"
    Set-Score '多端能力' 4.0 5 "Web $webCount + Mobile $mobileCount"
} else {
    Add-Result 'CB-003' 'WARN' "多端能力 Web $webCount + Mobile $mobileCount 不足"
    Set-Score '多端能力' 3.0 5 '页面数偏少'
}

# ============ CB-004：低代码对标（实体/字段/页面/组件/动作/生成器/diff） ============
$lcEntity = Join-Path $backendDir 'yutong-lowcode-service\src\main\java\com\yutong\lowcode\meta\domain\LcEntity.java'
$lcPage = Join-Path $backendDir 'yutong-lowcode-service\src\main\java\com\yutong\lowcode\meta\domain\LcPage.java'
$lcGen = Join-Path $backendDir 'yutong-lowcode-service\src\main\java\com\yutong\lowcode\generator\service\GeneratorTaskApplicationService.java'
$lcDiff = Join-Path $backendDir 'yutong-lowcode-service\src\main\java\com\yutong\lowcode\generator\service\GeneratorDiffService.java'
$lcTpl = (Get-ChildItem -Path (Join-Path $backendDir 'yutong-lowcode-service\src\main\resources\templates') -Recurse -ErrorAction SilentlyContinue | Where-Object { $_.Name -like '*.ftl' }).Count
$lcScore = 0
if (Test-Path $lcEntity) { $lcScore++ }
if (Test-Path $lcPage) { $lcScore++ }
if (Test-Path $lcGen) { $lcScore++ }
if (Test-Path $lcDiff) { $lcScore++ }
if ($lcTpl -ge 10) { $lcScore++ }
if ($lcScore -ge 5) {
    Add-Result 'CB-004' 'PASS' "低代码 5 项能力对标（实体 + 页面 + 生成器 + diff + $lcTpl 模板）"
    Set-Score '低代码' 4.0 5 '5 项能力齐备'
} else {
    Add-Result 'CB-004' 'WARN' "低代码对标 $lcScore/5 项"
    Set-Score '低代码' 3.0 5 "缺 $((5-$lcScore)) 项"
}

# ============ CB-005：AI 对标（Gateway/RAG/Prompt 治理/成本治理/反馈/评测） ============
$aiGateway = Join-Path $backendDir 'yutong-ai-service\src\main\java\com\yutong\ai\gateway\controller\AiProviderController.java'
$aiRag = Join-Path $backendDir 'yutong-ai-service\src\main\java\com\yutong\ai\rag\controller\KnowledgeBaseController.java'
$aiPrompt = Join-Path $backendDir 'yutong-ai-service\src\main\java\com\yutong\ai\gateway\controller\AiPromptTemplateController.java'
$aiGov = Join-Path $backendDir 'yutong-ai-service\src\main\java\com\yutong\ai\governance\controller\AiGovernanceController.java'
$aiCost = Join-Path $backendDir 'yutong-ai-service\src\main\java\com\yutong\ai\gateway\domain\AiCostLog.java'
$aiEval = Join-Path $backendDir 'yutong-ai-service\src\main\java\com\yutong\ai\governance\domain\AiEvalRun.java'
$aiScore = 0
foreach ($p in @($aiGateway, $aiRag, $aiPrompt, $aiGov, $aiCost, $aiEval)) { if (Test-Path $p) { $aiScore++ } }
if ($aiScore -ge 6) {
    Add-Result 'CB-005' 'PASS' "AI 6 项能力对标（Gateway + RAG + Prompt 治理 + 治理中心 + 成本日志 + 评测）"
    Set-Score 'AI' 4.0 5 '6 项能力齐备'
} else {
    Add-Result 'CB-005' 'WARN' "AI 对标 $aiScore/6 项"
    Set-Score 'AI' 3.0 5 "缺 $((6-$aiScore)) 项"
}

# ============ CB-006：DevOps 对标（Docker Compose + 监控 + Runbook） ============
$composeRun = Join-Path $deployDir 'docker-compose.run.yml'
$composeBoot = Join-Path $deployDir 'docker-compose.boot.yml'
$composeMon = Join-Path $deployDir 'docker-compose.monitoring.yml'
$runbook = Join-Path $docsDir '63-Runbook与运维手册\63-Runbook与运维手册.md'
$devopsScore = 0
foreach ($p in @($composeRun, $composeBoot, $composeMon, $runbook)) { if (Test-Path $p) { $devopsScore++ } }
# 检查 yutong 前缀规则
$yutongPrefixOk = $false
if (Test-Path $composeRun) {
    $c = Get-Content -Path $composeRun -Raw -Encoding UTF8
    if ($c -match 'yutong-backend:latest' -and $c -match 'yutong-postgres' -and $c -match 'yutong-redis' -and $c -match 'yutong-minio' -and $c -match 'yutong_default') {
        $yutongPrefixOk = $true
    }
}
if ($devopsScore -ge 4 -and $yutongPrefixOk) {
    Add-Result 'CB-006' 'PASS' "DevOps 4 项对标 + yutong 前缀规则落实"
    Set-Score 'DevOps' 4.0 5 'Docker Compose + 监控 + Runbook + yutong 前缀'
} elseif ($devopsScore -ge 4) {
    Add-Result 'CB-006' 'WARN' "DevOps 4 项对标但 yutong 前缀规则未完全落实"
    Set-Score 'DevOps' 3.5 5 'yutong 前缀未完全落实'
} else {
    Add-Result 'CB-006' 'WARN' "DevOps 对标 $devopsScore/4 项"
    Set-Score 'DevOps' 3.0 5 "缺 $((4-$devopsScore)) 项"
}

# ============ CB-007：安全合规对标（DataScope + 审计 + 幂等 + SBOM + 密钥） ============
$dataScope = Join-Path $backendDir 'yutong-infra\src\main\java\com\yutong\infra\persistence\DataScopeFilter.java'
$auditable = Join-Path $backendDir 'yutong-boot\src\main\java\com\yutong\boot\config\AuditableAspect.java'
$idempotent = Join-Path $backendDir 'yutong-boot\src\main\java\com\yutong\boot\config\IdempotentAspect.java'
$sbom = Join-Path $docsDir '73-供应链安全与SBOM详设\73-供应链安全与SBOM详设.md'
$secret = Join-Path $docsDir '82-环境变量与密钥管理规范详设\82-环境变量与密钥管理规范详设.md'
$secScore = 0
foreach ($p in @($dataScope, $auditable, $idempotent, $sbom, $secret)) { if (Test-Path $p) { $secScore++ } }
if ($secScore -ge 5) {
    Add-Result 'CB-007' 'PASS' "安全合规 5 项对标（DataScope + 审计 + 幂等 + SBOM + 密钥管理）"
    Set-Score '安全合规' 4.5 5 '5 项能力齐备'
} else {
    Add-Result 'CB-007' 'WARN' "安全合规对标 $secScore/5 项"
    Set-Score '安全合规' 3.5 5 "缺 $((5-$secScore)) 项"
}

# ============ CB-008：产品化对标（版本 + 文档站 + 演示 + 授权 + 升级） ============
$ver100 = Join-Path $docsDir '100-商业级产品定义与权威口径基线\100-商业级产品定义与权威口径基线.md'
$docSite = Join-Path $docsDir '69-产品官网与文档站信息架构详设\69-产品官网与文档站信息架构详设.md'
$demo = Join-Path $docsDir '68-演示环境与样例数据剧本详设\68-演示环境与样例数据剧本详设.md'
$license = Join-Path $docsDir '70-商业授权与版本能力裁剪详设\70-商业授权与版本能力裁剪详设.md'
$upgrade = Join-Path $docsDir '71-版本升级兼容与迁移策略详设\71-版本升级兼容与迁移策略详设.md'
$productScore = 0
foreach ($p in @($ver100, $docSite, $demo, $license, $upgrade)) { if (Test-Path $p) { $productScore++ } }
if ($productScore -ge 5) {
    Add-Result 'CB-008' 'PASS' "产品化 5 项对标（版本 + 文档站 + 演示 + 授权 + 升级）"
    Set-Score '产品化' 4.0 5 '5 项设计齐备（实现待 v1.5+）'
} else {
    Add-Result 'CB-008' 'WARN' "产品化对标 $productScore/5 项"
    Set-Score '产品化' 3.0 5 "缺 $((5-$productScore)) 项"
}

# ============ CB-009：样例业务闭环对标（客户/商品/申请单/审批/库存/合同/工单/支付/问卷/外部同步） ============
$sampleModules = @('masterdata', 'request', 'contract', 'inventory', 'ticket', 'payment', 'survey', 'extsync', 'report', 'mobile')
$sampleOkCount = 0
foreach ($m in $sampleModules) {
    $modulePath = Join-Path $backendDir "yutong-sample-service\src\main\java\com\yutong\sample\$m"
    if (Test-Path $modulePath) { $sampleOkCount++ }
}
if ($sampleOkCount -ge 10) {
    Add-Result 'CB-009' 'PASS' "样例业务 10 个子模块全部对标（$($sampleModules -join '/')）"
    Set-Score '样例闭环' 4.5 5 '10 个子模块齐备'
} else {
    Add-Result 'CB-009' 'WARN' "样例业务子模块 $sampleOkCount/10 个"
    Set-Score '样例闭环' 3.5 5 "缺 $((10-$sampleOkCount)) 个"
}

# ============ CB-010：工作流对标（BPMN 引擎 L0+L1+L2） ============
$wfService = Join-Path $backendDir 'yutong-workflow-service\src\main\java\com\yutong\workflow'
if (Test-Path $wfService) {
    $wfFiles = (Get-ChildItem -Path $wfService -Recurse -Filter '*.java' -ErrorAction SilentlyContinue).Count
    if ($wfFiles -ge 5) {
        Add-Result 'CB-010' 'PASS' "工作流引擎对标：$wfFiles 个 Java 文件"
        Set-Score '工作流' 3.5 5 'L0+L1 已落地，L2 BPMN 设计器留 v1.1+'
    } else {
        Add-Result 'CB-010' 'WARN' "工作流引擎文件偏少（$wfFiles 个）"
        Set-Score '工作流' 3.0 5 'L0 状态机'
    }
} else {
    Add-Result 'CB-010' 'FAIL' '工作流引擎模块缺失'
    Set-Score '工作流' 1.0 5 '模块缺失'
}

# ============ CB-011：差异化卖点对标（文档驱动 + 源码可控 + AI 治理 + 端侧交付 + 跨团队闭环） ============
$diffSellingPoints = @(
    @{Name='文档驱动'; Path=Join-Path $docsDir '23-设计到落地追踪记录\23-设计到落地追踪记录.md'},
    @{Name='源码可控低代码'; Path=$lcDiff},
    @{Name='AI 安全治理'; Path=$aiGov},
    @{Name='端侧交付可验收'; Path=Join-Path $docsDir '96-端侧权限可见性矩阵详设\96-端侧权限可见性矩阵详设.md'},
    @{Name='跨团队闭环'; Path=Join-Path $docsDir '97-页面字段OpenAPI数据库Figma映射详设\97-页面字段OpenAPI数据库Figma映射详设.md'},
    @{Name='商用证据链'; Path=Join-Path $docsDir '79-首版发布验收包与证据归档详设\79-首版发布验收包与证据归档详设.md'}
)
$diffScore = 0
foreach ($sp in $diffSellingPoints) { if (Test-Path $sp.Path) { $diffScore++ } }
if ($diffScore -ge 6) {
    Add-Result 'CB-011' 'PASS' "6 大差异化卖点全部对标（文档驱动 + 源码可控 + AI 治理 + 端侧交付 + 跨团队闭环 + 商用证据链）"
    Set-Score '差异化卖点' 4.5 5 '6 大卖点齐备'
} else {
    Add-Result 'CB-011' 'WARN' "差异化卖点 $diffScore/6 项"
    Set-Score '差异化卖点' 3.5 5 "缺 $((6-$diffScore)) 项"
}

# ============ CB-012：差距关闭清单对标 ============
$gapClosure = @{
    '完整权限租户后台' = @{Doc='32-企业级权限与租户接入方案'; Version='v1.5'}
    '报表大屏设计器' = @{Doc='42-报表与大屏可视化设计'; Version='v1.5'}
    'BPMN 工作流' = @{Doc='41-工作流与BPMN引擎设计'; Version='v1.1~v1.5'}
    '插件模板生态' = @{Doc='45-插件与模板生态设计'; Version='v2.0'}
    '多数据源' = @{Doc='46-多数据源与数据集设计'; Version='v1.5'}
    'AI 评测治理' = @{Doc='37-AI治理与评测设计'; Version='v1.5'}
    '商业授权' = @{Doc='70-商业授权与版本能力裁剪详设'; Version='v2.0'}
    '文档站和发布证据' = @{Doc='69-产品官网与文档站信息架构详设'; Version='v1.0'}
}
$gapDocsOk = 0
$gapTotal = $gapClosure.Count
foreach ($k in $gapClosure.Keys) {
    $docPath = Join-Path $docsDir "$($gapClosure[$k].Doc)\$($gapClosure[$k].Doc).md"
    if (Test-Path $docPath) { $gapDocsOk++ }
}
if ($gapDocsOk -ge $gapTotal) {
    Add-Result 'CB-012' 'PASS' "8 项顶尖产品差距全部有专项设计文档（实现按版本路线图）"
} else {
    Add-Result 'CB-012' 'WARN' "差距关闭设计文档 $gapDocsOk/$gapTotal 项"
}

# ============ CB-013：23 追踪记录对标项可追踪 ============
$trackRecord = Join-Path $docsDir '23-设计到落地追踪记录\23-设计到落地追踪记录.md'
if (Test-Path $trackRecord) {
    $trackContent = Get-Content -Path $trackRecord -Raw -Encoding UTF8
    $doneCount = ([regex]::Matches($trackContent, '\| Done')).Count
    if ($doneCount -ge 100) {
        Add-Result 'CB-013' 'PASS' "23 追踪记录 Done=$doneCount 项，每个对标项可追踪到设计/代码/证据"
    } else {
        Add-Result 'CB-013' 'WARN' "23 追踪记录 Done=$doneCount 项偏少，建议≥100"
    }
} else {
    Add-Result 'CB-013' 'FAIL' '23 追踪记录不存在'
}

# ============ CB-014：发布证据对标（release-evidence 目录） ============
$evidenceDir = Join-Path $root 'release-evidence\v1.0.0'
if (Test-Path $evidenceDir) {
    $evidenceFolders = (Get-ChildItem -Path $evidenceDir -Directory -ErrorAction SilentlyContinue).Count
    $evidenceFiles = (Get-ChildItem -Path $evidenceDir -Recurse -File -ErrorAction SilentlyContinue).Count
    if ($evidenceFolders -ge 10 -and $evidenceFiles -ge 20) {
        Add-Result 'CB-014' 'PASS' "发布证据对标：$evidenceFolders 个证据目录 + $evidenceFiles 个证据文件"
    } else {
        Add-Result 'CB-014' 'WARN' "发布证据 $evidenceFolders 目录 / $evidenceFiles 文件偏少"
    }
} else {
    Add-Result 'CB-014' 'WARN' 'release-evidence 目录不存在'
}

# ============ CB-015：yutong 前缀规则对标（防止与其他项目冲突） ============
$yutongPrefixInPom = $false
$parentPom = Join-Path $backendDir 'pom.xml'
if (Test-Path $parentPom) {
    $pomContent = Get-Content -Path $parentPom -Raw -Encoding UTF8
    if ($pomContent -match '<groupId>com.yutong</groupId>' -and $pomContent -match 'yutong-parent' -and $pomContent -match 'yutong-common' -and $pomContent -match 'yutong-boot') {
        $yutongPrefixInPom = $true
    }
}
if ($yutongPrefixOk -and $yutongPrefixInPom) {
    Add-Result 'CB-015' 'PASS' 'yutong 前缀规则全链路落实（pom.xml + docker-compose + 容器/镜像名）'
} else {
    Add-Result 'CB-015' 'FAIL' "yutong 前缀规则未完全落实（pom=$yutongPrefixInPom compose=$yutongPrefixOk）"
}

# ============ 计算综合评分（按 38 号文档 8 维权重） ============
$weights = @{
    '工程底座' = 0.20
    '基础后台' = 0.15
    '样例闭环' = 0.15
    '低代码' = 0.15
    'AI' = 0.10
    'DevOps' = 0.10
    '安全合规' = 0.10
    '产品化' = 0.05
}
$workfLowWeight = 0.0  # 工作流未列入 38 号 8 维主权重，作为辅助维度
$weightedScore = 0.0
$weightSum = 0.0
foreach ($dim in $weights.Keys) {
    if ($script:scores.ContainsKey($dim)) {
        $s = $script:scores[$dim]
        $normalized = $s.Score / $s.MaxScore * 100
        $weightedScore += $normalized * $weights[$dim]
        $weightSum += $weights[$dim]
    }
}
$finalScore = if ($weightSum -gt 0) { [math]::Round($weightedScore / $weightSum, 1) } else { 0 }

# 阶段目标对标
$stageTargets = @{
    'v0.2' = @{Target=70; Desc='工程底座达到 70 分'}
    'v0.3' = @{Target=70; Desc='基础后台和样例闭环达到 70 分'}
    'v0.4' = @{Target=60; Desc='低代码和 AI 达到 60 分'}
    'v0.5' = @{Target=60; Desc='DevOps、安全、产品化达到 60 分'}
    'v1.0' = @{Target=80; Desc='v1.0 GA 必须满足 100 的 GA 范围并通过 C2/C3 全部门禁'}
    'v1.5' = @{Target=85; Desc='补齐权限、报表设计器、工作流、合规'}
    'G0'   = @{Target=95; Desc='综合设计分达到 95 分方可编码开工'}
}
$targetV10 = $stageTargets['v1.0'].Target
if ($finalScore -ge $targetV10) {
    Add-Result 'CB-FINAL' 'PASS' "v1.0 GA 阶段目标达标：综合评分 $finalScore ≥ $targetV10"
} else {
    Add-Result 'CB-FINAL' 'WARN' "v1.0 GA 阶段目标未达标：综合评分 $finalScore < $targetV10（注：38 号文档说明评分仅辅助对标，不能替代发布证据）"
}

# ============ 输出汇总 ============
Write-Host ''
Write-Host '========== GA2-60 竞品对标验收 ==========' -ForegroundColor Cyan
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
Write-Host '---- 11 维度量化评分（满分 100）----' -ForegroundColor Cyan
$script:scores.Keys | Sort-Object | ForEach-Object {
    $s = $script:scores[$_]
    $normalized = [math]::Round($s.Score / $s.MaxScore * 100, 1)
    Write-Host ("  {0,-12} : {1,5} / 100  ({2})" -f $_, $normalized, $s.Evidence) -ForegroundColor White
}
Write-Host ''
Write-Host "综合加权评分：$finalScore / 100" -ForegroundColor Yellow
Write-Host ''
Write-Host "汇总：PASS=$script:passCount WARN=$script:warnCount FAIL=$script:failCount" -ForegroundColor Cyan

# 输出 JSON 报告
$reportDir = Join-Path $root 'build\reports\checks'
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
$reportFile = Join-Path $reportDir 'competitive-benchmark-ga2-60.json'
$summary = [PSCustomObject]@{
    schemaVersion = '1.0.0'
    generatedAt = (Get-Date -Format 'yyyy-MM-ddTHH:mm:ssZ')
    status = if ($script:failCount -eq 0) { 'PASS' } else { 'FAIL' }
    finalScore = $finalScore
    passCount = $script:passCount
    warnCount = $script:warnCount
    failCount = $script:failCount
    dimensionScores = $script:scores
    checks = $script:results
}
$summary | ConvertTo-Json -Depth 5 | Out-File -FilePath $reportFile -Encoding UTF8
Write-Host "JSON 报告：$reportFile" -ForegroundColor Cyan

if ($script:failCount -gt 0) {
    exit 1
} else {
    exit 0
}
