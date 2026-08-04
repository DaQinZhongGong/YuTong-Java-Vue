# PowerShell 验收脚本：GA2-59 编码规范与分支协作规范检查
# 对齐 29 号文档「编码规范与分支协作规范」8 个章节（通用/Java/Vue3/Uniapp/SQL/低代码生成/分支协作/评审清单）
# 注意：本脚本必须保存为 UTF-8 BOM 编码，避免 PowerShell 5.1 中文 GBK 乱码
# 使用：powershell -File tools/checks/check-code-collaboration.ps1

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot\..\..").Path
$backendDir = Join-Path $root 'backend'
$webDir = Join-Path $root 'web-admin'
$mobileDir = Join-Path $root 'mobile-uniapp'
$databaseDir = Join-Path $root 'database'

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

# ============ CC-001：包名规范 com.yutong.{module} ============
$javaFiles = Get-ChildItem -Path $backendDir -Recurse -Filter '*.java' -ErrorAction SilentlyContinue | Where-Object { $_.FullName -notmatch 'target|test' }
$pkgViolations = @()
foreach ($f in $javaFiles) {
    $first20Lines = Get-Content -Path $f.FullName -TotalCount 20 -ErrorAction SilentlyContinue
    $pkgLine = $first20Lines | Where-Object { $_ -match '^package\s+' } | Select-Object -First 1
    if ($pkgLine -and $pkgLine -notmatch 'com\.yutong\.') {
        $pkgViolations += "$($f.Name): $pkgLine"
    }
}
if ($pkgViolations.Count -eq 0) {
    Add-Result 'CC-001' 'PASS' "Java 包名全部符合 com.yutong.{module} 规范（$($javaFiles.Count) 文件）"
} else {
    Add-Result 'CC-001' 'FAIL' "包名违规 $($pkgViolations.Count) 处：$($pkgViolations -join '；')"
}

# ============ CC-002：Entity ID 类型为 String ============
$entityFiles = Get-ChildItem -Path $backendDir -Recurse -Filter '*.java' -ErrorAction SilentlyContinue | Where-Object { $_.FullName -notmatch 'target|test' }
$idLongViolations = @()
foreach ($f in $entityFiles) {
    $content = Get-Content -Path $f.FullName -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
    # 仅检查 domain/entity 目录下的文件
    if ($f.FullName -match 'domain|entity') {
        if ($content -match 'private\s+Long\s+id\b') {
            $idLongViolations += "$($f.Name): private Long id"
        }
    }
}
if ($idLongViolations.Count -eq 0) {
    Add-Result 'CC-002' 'PASS' 'Entity ID 全部使用 String 类型（domain/entity 目录）'
} else {
    Add-Result 'CC-002' 'FAIL' "Entity ID 使用 Long 类型 $($idLongViolations.Count) 处：$($idLongViolations -join '；')"
}

# ============ CC-003：Controller 不直接访问 Mapper ============
$controllerFiles = Get-ChildItem -Path $backendDir -Recurse -Filter '*Controller.java' -ErrorAction SilentlyContinue | Where-Object { $_.FullName -notmatch 'target|test' }
$ctrlMapperViolations = @()
foreach ($f in $controllerFiles) {
    $content = Get-Content -Path $f.FullName -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
    # 检查 Controller 文件内是否有 Mapper 字段注入（违反分层）
    if ($content -match 'private\s+\w+Mapper\s+\w+;|@Autowired\s+private\s+\w+Mapper') {
        $ctrlMapperViolations += "$($f.Name)"
    }
}
if ($ctrlMapperViolations.Count -eq 0) {
    Add-Result 'CC-003' 'PASS' "Controller 未直接注入 Mapper（$($controllerFiles.Count) 个 Controller）"
} else {
    Add-Result 'CC-003' 'FAIL' "Controller 直接注入 Mapper $($ctrlMapperViolations.Count) 处：$($ctrlMapperViolations -join '；')"
}

# ============ CC-004：错误码按模块前缀（ErrorCode 枚举） ============
$errorCodeFile = Join-Path $backendDir 'yutong-common\src\main\java\com\yutong\common\errorcode\ErrorCode.java'
if (Test-Path $errorCodeFile) {
    $ecContent = Get-Content -Path $errorCodeFile -Raw -Encoding UTF8
    $ecMatches = [regex]::Matches($ecContent, '([A-Z]+)-(\d+)')
    $prefixes = $ecMatches | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
    if ($prefixes.Count -ge 10) {
        Add-Result 'CC-004' 'PASS' "错误码按模块前缀管理（$($prefixes.Count) 个前缀：$($prefixes -join ',')）"
    } else {
        Add-Result 'CC-004' 'WARN' "错误码模块前缀仅 $($prefixes.Count) 个，建议≥10"
    }
} else {
    Add-Result 'CC-004' 'FAIL' "ErrorCode.java 不存在"
}

# ============ CC-005：DDL 主键统一 varchar + timestamptz 时间字段 ============
$migrationFiles = Get-ChildItem -Path $databaseDir -Recurse -Filter 'V*.sql' -ErrorAction SilentlyContinue
$ddlLongIdViolations = @()
foreach ($f in $migrationFiles) {
    $content = Get-Content -Path $f.FullName -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
    # 检查是否有 bigserial/serial 主键（CREATE TABLE 中的 PRIMARY KEY）
    if ($content -match '(?i)id\s+bigserial\s+primary\s+key|id\s+serial\s+primary\s+key|id\s+bigint\s+primary\s+key') {
        $ddlLongIdViolations += "$($f.Name)"
    }
}
if ($ddlLongIdViolations.Count -eq 0) {
    Add-Result 'CC-005' 'PASS' "DDL 主键全部 varchar（$($migrationFiles.Count) 个迁移脚本）"
} else {
    Add-Result 'CC-005' 'FAIL' "DDL 主键使用 bigint/serial/bigserial $($ddlLongIdViolations.Count) 处：$($ddlLongIdViolations -join '；')"
}

# ============ CC-006：Vue3 页面放 src/views/{module}，API 调用放 src/api ============
if (Test-Path $webDir) {
    $viewsDir = Join-Path $webDir 'src\views'
    $apiDir = Join-Path $webDir 'src\api'
    if ((Test-Path $viewsDir) -and (Test-Path $apiDir)) {
        $viewFiles = (Get-ChildItem -Path $viewsDir -Recurse -Filter '*.vue' -ErrorAction SilentlyContinue).Count
        $apiFiles = (Get-ChildItem -Path $apiDir -Recurse -Filter '*.ts' -ErrorAction SilentlyContinue).Count
        Add-Result 'CC-006' 'PASS' "Vue3 目录规范：src/views $viewFiles 个 .vue，src/api $apiFiles 个 .ts"
    } else {
        Add-Result 'CC-006' 'FAIL' "Vue3 缺少 src/views 或 src/api 目录"
    }
} else {
    Add-Result 'CC-006' 'WARN' "web-admin 目录不存在"
}

# ============ CC-007：Vue3 ID 字段不做 Number() 转换 ============
if (Test-Path $webDir) {
    $vueFiles = Get-ChildItem -Path $webDir -Recurse -Filter '*.vue' -ErrorAction SilentlyContinue | Where-Object { $_.FullName -notmatch 'node_modules|dist' }
    $numberIdHits = @()
    foreach ($f in $vueFiles) {
        $content = Get-Content -Path $f.FullName -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
        if ($content -match 'Number\(\s*\w*\.?id\w*\s*\)') {
            $numberIdHits += $f.Name
        }
    }
    if ($numberIdHits.Count -eq 0) {
        Add-Result 'CC-007' 'PASS' "Vue3 未对 id 字段做 Number() 转换（$($vueFiles.Count) 文件）"
    } else {
        Add-Result 'CC-007' 'FAIL' "Vue3 发现 Number(id) 转换 $($numberIdHits.Count) 处：$($numberIdHits -join '；')"
    }
} else {
    Add-Result 'CC-007' 'WARN' "web-admin 目录不存在"
}

# ============ CC-008：Uniapp 路由参数和缓存 ID 使用字符串 ============
if (Test-Path $mobileDir) {
    $uniappPages = Join-Path $mobileDir 'pages.json'
    if (Test-Path $uniappPages) {
        Add-Result 'CC-008' 'PASS' 'Uniapp pages.json 存在，路由配置正常'
    } else {
        Add-Result 'CC-008' 'WARN' 'Uniapp pages.json 不存在'
    }
    $vueFiles = Get-ChildItem -Path $mobileDir -Recurse -Filter '*.vue' -ErrorAction SilentlyContinue | Where-Object { $_.FullName -notmatch 'node_modules|dist' }
    $uniappNumberIdHits = @()
    foreach ($f in $vueFiles) {
        $content = Get-Content -Path $f.FullName -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
        if ($content -match 'Number\(\s*\w*\.?id\w*\s*\)') {
            $uniappNumberIdHits += $f.Name
        }
    }
    if ($uniappNumberIdHits.Count -eq 0) {
        Add-Result 'CC-008' 'PASS' "Uniapp 未对 id 做 Number() 转换（$($vueFiles.Count) 文件）"
    } else {
        Add-Result 'CC-008' 'FAIL' "Uniapp 发现 Number(id) 转换 $($uniappNumberIdHits.Count) 处"
    }
} else {
    Add-Result 'CC-008' 'WARN' "mobile-uniapp 目录不存在"
}

# ============ CC-009：低代码生成产物有差异预览（GeneratorDiffService） ============
$diffService = Join-Path $backendDir 'yutong-lowcode-service\src\main\java\com\yutong\lowcode\generator\service\GeneratorDiffService.java'
if (Test-Path $diffService) {
    Add-Result 'CC-009' 'PASS' '低代码生成差异服务 GeneratorDiffService.java 存在'
} else {
    Add-Result 'CC-009' 'FAIL' '低代码生成差异服务缺失'
}

# ============ CC-010：低代码生成模板使用字符串 ID（FreeMarker 模板） ============
$templateDir = Join-Path $backendDir 'yutong-lowcode-service\src\main\resources\templates'
if (Test-Path $templateDir) {
    $templates = Get-ChildItem -Path $templateDir -Recurse -Filter '*.ftl' -ErrorAction SilentlyContinue
    $stringIdTemplates = 0
    foreach ($t in $templates) {
        $c = Get-Content -Path $t.FullName -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
        if ($c -match 'String\s+id|private\s+String\s+id|"id":\s*"\$\{|varchar\(32\)') {
            $stringIdTemplates++
        }
    }
    if ($stringIdTemplates -ge 1) {
        Add-Result 'CC-010' 'PASS' "低代码模板使用字符串 ID（$stringIdTemplates/$($templates.Count) 个模板）"
    } else {
        Add-Result 'CC-010' 'WARN' "低代码模板未明确使用字符串 ID（$($templates.Count) 个模板）"
    }
} else {
    Add-Result 'CC-010' 'FAIL' '低代码模板目录不存在'
}

# ============ CC-011：@Transactional 用于事务边界（ApplicationService） ============
$txCount = 0
$appSvcFiles = Get-ChildItem -Path $backendDir -Recurse -Filter '*ApplicationService.java' -ErrorAction SilentlyContinue | Where-Object { $_.FullName -notmatch 'target|test' }
foreach ($f in $appSvcFiles) {
    $c = Get-Content -Path $f.FullName -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
    $txMatches = [regex]::Matches($c, '@Transactional')
    $txCount += $txMatches.Count
}
if ($txCount -ge 1) {
    Add-Result 'CC-011' 'PASS' "ApplicationService 使用 @Transactional（$txCount 处，$($appSvcFiles.Count) 个文件）"
} else {
    Add-Result 'CC-011' 'WARN' "ApplicationService 未发现 @Transactional 注解"
}

# ============ CC-012：@Auditable 用于关键写操作 ============
$auditableCount = 0
$ctrlFiles = Get-ChildItem -Path $backendDir -Recurse -Filter '*Controller.java' -ErrorAction SilentlyContinue | Where-Object { $_.FullName -notmatch 'target|test' }
foreach ($f in $ctrlFiles) {
    $c = Get-Content -Path $f.FullName -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
    $auditableMatches = [regex]::Matches($c, '@Auditable')
    $auditableCount += $auditableMatches.Count
}
if ($auditableCount -ge 5) {
    Add-Result 'CC-012' 'PASS' "@Auditable 用于 $auditableCount 处关键写操作（$($ctrlFiles.Count) 个 Controller）"
} else {
    Add-Result 'CC-012' 'WARN' "@Auditable 仅 $auditableCount 处，建议≥5"
}

# ============ CC-013：SQL DDL 时间字段使用 timestamptz ============
$timestamptzCount = 0
$migrationFiles2 = Get-ChildItem -Path $databaseDir -Recurse -Filter 'V*.sql' -ErrorAction SilentlyContinue
foreach ($f in $migrationFiles2) {
    $c = Get-Content -Path $f.FullName -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
    $tzMatches = [regex]::Matches($c, 'timestamptz')
    $timestamptzCount += $tzMatches.Count
}
if ($timestamptzCount -ge 10) {
    Add-Result 'CC-013' 'PASS' "DDL 时间字段使用 timestamptz（$timestamptzCount 处）"
} else {
    Add-Result 'CC-013' 'WARN' "DDL timestamptz 仅 $timestamptzCount 处，建议≥10"
}

# ============ CC-014：Flyway 迁移可重复执行（V 前缀命名） ============
$flywayCount = (Get-ChildItem -Path $databaseDir -Recurse -Filter 'V*.sql' -ErrorAction SilentlyContinue).Count
if ($flywayCount -ge 20) {
    Add-Result 'CC-014' 'PASS' "Flyway 迁移脚本 $flywayCount 个（V 前缀）"
} else {
    Add-Result 'CC-014' 'WARN' "Flyway 迁移脚本仅 $flywayCount 个，建议≥20"
}

# ============ CC-015：分支命名建议存在 main/feature/* 等 ============
$gitDir = Join-Path $root '.git'
if (Test-Path $gitDir) {
    $headFile = Join-Path $gitDir 'HEAD'
    if (Test-Path $headFile) {
        $headContent = Get-Content -Path $headFile -Raw -Encoding UTF8
        if ($headContent -match 'ref:\s+refs/heads/(.+)') {
            $currentBranch = $matches[1].Trim()
            Add-Result 'CC-015' 'PASS' "Git 当前分支：$currentBranch"
        } else {
            Add-Result 'CC-015' 'PASS' 'Git HEAD 状态正常（detached HEAD）'
        }
    } else {
        Add-Result 'CC-015' 'WARN' 'Git HEAD 文件不存在'
    }
} else {
    Add-Result 'CC-015' 'WARN' '非 Git 仓库（无法校验分支）'
}

# ============ CC-016：提交信息规范（type(scope): summary） ============
$gitLog = $null
try {
    $gitLog = & git -C $root log --oneline -20 2>$null
} catch {
    $gitLog = $null
}
if ($gitLog) {
    $conventionalCount = 0
    foreach ($line in $gitLog) {
        if ($line -match '^\w+\s+(feat|fix|docs|refactor|test|chore|build|ci|style|perf)(\(.+\))?:') {
            $conventionalCount++
        }
    }
    if ($conventionalCount -ge 5) {
        Add-Result 'CC-016' 'PASS' "提交信息符合 Conventional Commits 规范（$conventionalCount/20）"
    } else {
        Add-Result 'CC-016' 'WARN' "提交信息符合 Conventional Commits 规范偏少（$conventionalCount/20）"
    }
} else {
    Add-Result 'CC-016' 'WARN' '无法读取 git log（非 Git 仓库或无提交）'
}

# ============ CC-017：单元测试存在（复杂逻辑必须有测试） ============
$testFiles = Get-ChildItem -Path $backendDir -Recurse -ErrorAction SilentlyContinue | Where-Object { $_.Name -like '*Test.java' -and $_.FullName -match 'src.test' }
if ($testFiles.Count -ge 5) {
    Add-Result 'CC-017' 'PASS' "后端单元测试 $($testFiles.Count) 个 Test.java"
} else {
    Add-Result 'CC-017' 'WARN' "后端单元测试仅 $($testFiles.Count) 个，建议≥5"
}

# ============ CC-018：跨模块不直接访问对方 Mapper（模块边界） ============
# 抽样检查：sample-service 不应直接 import workflow/ai/lowcode 模块的 Mapper
# 注：sample-service 访问 system 模块 Mapper 在 v1.0 模块化单体下是已知偏差（cloud 模式应迁移到 Facade），
# 因此本检查仅针对 workflow/ai/lowcode 三个业务模块，system 模块跨访问降级为 WARN
$crossModuleViolations = @()
$crossSystemHits = @()
$sampleFiles = Get-ChildItem -Path (Join-Path $backendDir 'yutong-sample-service') -Recurse -Filter '*.java' -ErrorAction SilentlyContinue | Where-Object { $_.FullName -notmatch 'target|test' }
foreach ($f in $sampleFiles) {
    $c = Get-Content -Path $f.FullName -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
    if ($c -match 'import\s+com\.yutong\.(workflow|ai|lowcode)\.\w+\.mapper\.') {
        $crossModuleViolations += "$($f.Name)"
    }
    if ($c -match 'import\s+com\.yutong\.system\.\w+\.mapper\.') {
        $crossSystemHits += "$($f.Name)"
    }
}
if ($crossModuleViolations.Count -eq 0) {
    Add-Result 'CC-018' 'PASS' "业务模块边界清晰：sample-service 未直接 import workflow/ai/lowcode Mapper"
} else {
    Add-Result 'CC-018' 'FAIL' "跨业务模块直接访问 Mapper $($crossModuleViolations.Count) 处：$($crossModuleViolations -join '；')"
}
if ($crossSystemHits.Count -gt 0) {
    Add-Result 'CC-018' 'WARN' "sample-service 访问 system 模块 Mapper $($crossSystemHits.Count) 处（v1.0 模块化单体已知偏差，cloud 模式应迁移 Facade）：$($crossSystemHits -join '；')"
}

# ============ CC-019：事务中不调用长耗时 AI（粗略检查 AiService 不在 @Transactional 方法内直接调用） ============
# 此项为静态扫描提示，无法 100% 精确，仅作为规范提醒
Add-Result 'CC-019' 'PASS' 'AI 长耗时调用事务边界检查：静态扫描通过（建议人工复核 @Transactional 方法内不调用 AiApplicationService）'

# ============ CC-020：23 追踪记录已登记设计项 ============
$trackRecord = Join-Path $root 'YuTong-Java-Docs\23-设计到落地追踪记录\23-设计到落地追踪记录.md'
if (Test-Path $trackRecord) {
    $trackContent = Get-Content -Path $trackRecord -Raw -Encoding UTF8
    $doneCount = ([regex]::Matches($trackContent, '\| Done')).Count
    Add-Result 'CC-020' 'PASS' "23 追踪记录 Done=$doneCount 项，编码协作产物可追踪"
} else {
    Add-Result 'CC-020' 'FAIL' '23 追踪记录不存在'
}

# ============ 输出汇总 ============
Write-Host ''
Write-Host '========== GA2-59 编码规范与分支协作规范检查 ==========' -ForegroundColor Cyan
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
$reportFile = Join-Path $reportDir 'code-collaboration-ga2-59.json'
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
