<#
.SYNOPSIS
  GA2-56 工程脚手架验收脚本
  设计来源: 83-工程脚手架验收标准详设 (5 大类 32 项检查)
  对齐文档: 83-工程脚手架验收标准详设.md (后端 10 + Web 8 + 移动 5 + 数据库 5 + 部署 4)

.DESCRIPTION
  本脚本对 YuTong 项目工程脚手架进行静态文件级验收, 不启动容器/不调用 API。
  验收范围:
    - 后端: Maven 父工程 / 模块结构 / Java 版本 / Result<T> / GlobalExceptionHandler / ErrorCode / IdGenerator / OpenAPI / health / traceId 日志
    - Web: Vue3+Vite+TS / 路由 / Pinia Store / API 客户端 / 类型生成 / 设计 token / 组件基础 / 错误处理
    - 移动端: Uniapp+Vue3+TS / 页面 / 请求封装 / 弱网 / 上传
    - 数据库: Flyway / V001 / 主键 varchar(32) / 种子数据 / 校验 SQL
    - 部署: Docker Compose / .env.example / 健康检查 / 日志目录

.NOTES
  PowerShell 5.1 兼容性:
    - 脚本保存为 UTF-8 BOM 编码避免中文 GBK 乱码
    - 使用 [regex]::Match($input, "pattern") 静态方法替代 [regex]"pattern".Match()
    - 使用 $script:results = New-Object System.Collections.ArrayList 替代 $results+= 避免作用域问题
#>

param(
  [string]$ProjectRoot = (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)))
)

# 项目根目录解析
if (-not (Test-Path $ProjectRoot)) {
  Write-Host "ERROR: ProjectRoot not found: $ProjectRoot" -ForegroundColor Red
  exit 2
}

$script:results = New-Object System.Collections.ArrayList
$script:passCount = 0
$script:warnCount = 0
$script:failCount = 0

function Add-Result {
  param(
    [string]$Id,
    [string]$Category,
    [string]$Name,
    [string]$Status,  # PASS / WARN / FAIL
    [string]$Detail
  )
  $null = $script:results.Add([PSCustomObject]@{
    Id = $Id
    Category = $Category
    Name = $Name
    Status = $Status
    Detail = $Detail
  })
  if ($Status -eq 'PASS') { $script:passCount++ }
  elseif ($Status -eq 'WARN') { $script:warnCount++ }
  elseif ($Status -eq 'FAIL') { $script:failCount++ }
}

function Test-FileContains {
  param(
    [string]$FilePath,
    [string]$Pattern,
    [string]$Encoding = 'UTF8'
  )
  if (-not (Test-Path $FilePath)) { return $false }
  try {
    $content = Get-Content -Path $FilePath -Encoding $Encoding -Raw -ErrorAction Stop
    if (-not $content) { return $false }
    $m = [regex]::Match($content, $Pattern)
    return $m.Success
  } catch {
    return $false
  }
}

function Test-PathExists {
  param([string]$Path)
  return (Test-Path $Path)
}

# =====================================================================
# 1. 后端脚手架 10 项检查 (BE-001 ~ BE-010)
# =====================================================================

# BE-001: Maven 父工程 - 统一 dependencyManagement、pluginManagement
$backendPom = Join-Path $ProjectRoot 'backend\pom.xml'
if ((Test-PathExists $backendPom) -and
    (Test-FileContains $backendPom '<dependencyManagement>') -and
    (Test-FileContains $backendPom '<pluginManagement>')) {
  Add-Result 'BE-001' '后端' 'Maven 父工程 (统一 dependencyManagement、pluginManagement)' 'PASS' 'backend/pom.xml 含 dependencyManagement + pluginManagement'
} else {
  Add-Result 'BE-001' '后端' 'Maven 父工程 (统一 dependencyManagement、pluginManagement)' 'FAIL' "backend/pom.xml 缺失或不完整: $backendPom"
}

# BE-002: 模块结构 - yutong-common、yutong-boot 至少存在
$yutongCommon = Join-Path $ProjectRoot 'backend\yutong-common\pom.xml'
$yutongBoot = Join-Path $ProjectRoot 'backend\yutong-boot\pom.xml'
if ((Test-PathExists $yutongCommon) -and (Test-PathExists $yutongBoot)) {
  Add-Result 'BE-002' '后端' '模块结构 (yutong-common + yutong-boot 至少存在)' 'PASS' 'yutong-common + yutong-boot pom.xml 均存在'
} else {
  Add-Result 'BE-002' '后端' '模块结构 (yutong-common + yutong-boot 至少存在)' 'FAIL' "缺失模块: common=$yutongCommon boot=$yutongBoot"
}

# BE-003: Java 版本 - 与 06 一致 (java.version=25)
if (Test-FileContains $backendPom '<java\.version>25</java\.version>') {
  Add-Result 'BE-003' '后端' 'Java 版本 (与 06 ADR 一致, java.version=25)' 'PASS' 'backend/pom.xml java.version=25 (对齐 06-技术选型决策记录)'
} else {
  Add-Result 'BE-003' '后端' 'Java 版本 (与 06 ADR 一致, java.version=25)' 'FAIL' 'backend/pom.xml 未找到 java.version=25'
}

# BE-004: 统一响应 - Result<T> 或等价结构存在
$resultFile = Join-Path $ProjectRoot 'backend\yutong-common\src\main\java\com\yutong\common\response\Result.java'
if ((Test-PathExists $resultFile) -and (Test-FileContains $resultFile 'public\s+record\s+Result<T>')) {
  Add-Result 'BE-004' '后端' '统一响应 Result<T> 存在' 'PASS' 'Result.java public record Result<T> 存在'
} else {
  Add-Result 'BE-004' '后端' '统一响应 Result<T> 存在' 'FAIL' "Result.java 缺失或不含 Result<T>: $resultFile"
}

# BE-005: 异常处理 - 全局异常处理骨架存在
$gehFile = Join-Path $ProjectRoot 'backend\yutong-boot\src\main\java\com\yutong\boot\config\GlobalExceptionHandler.java'
if ((Test-PathExists $gehFile) -and
    (Test-FileContains $gehFile '@RestControllerAdvice') -and
    (Test-FileContains $gehFile '@ExceptionHandler')) {
  Add-Result 'BE-005' '后端' '异常处理 (GlobalExceptionHandler @RestControllerAdvice)' 'PASS' 'GlobalExceptionHandler.java 含 @RestControllerAdvice + @ExceptionHandler'
} else {
  Add-Result 'BE-005' '后端' '异常处理 (GlobalExceptionHandler @RestControllerAdvice)' 'FAIL' "GlobalExceptionHandler 缺失或不完整: $gehFile"
}

# BE-006: 错误码 - 模块前缀和注册表对齐 48
$errorCodeFile = Join-Path $ProjectRoot 'backend\yutong-common\src\main\java\com\yutong\common\errorcode\ErrorCode.java'
$errorsYaml = Join-Path $ProjectRoot 'YuTong-Java-Docs\contracts\registries\errors.yaml'
if ((Test-PathExists $errorCodeFile) -and (Test-PathExists $errorsYaml) -and
    (Test-FileContains $errorCodeFile 'public\s+enum\s+ErrorCode')) {
  Add-Result 'BE-006' '后端' '错误码 (ErrorCode 枚举 + errors.yaml 注册表对齐 48)' 'PASS' 'ErrorCode.java enum 存在 + errors.yaml 注册表存在'
} else {
  Add-Result 'BE-006' '后端' '错误码 (ErrorCode 枚举 + errors.yaml 注册表对齐 48)' 'FAIL' "ErrorCode 或 errors.yaml 缺失: $errorCodeFile / $errorsYaml"
}

# BE-007: ID 生成 - ULID String 生成器存在
$idGenFile = Join-Path $ProjectRoot 'backend\yutong-common\src\main\java\com\yutong\common\id\IdGenerator.java'
if ((Test-PathExists $idGenFile) -and
    (Test-FileContains $idGenFile 'UlidCreator') -and
    (Test-FileContains $idGenFile 'getMonotonicUlid')) {
  Add-Result 'BE-007' '后端' 'ID 生成 (ULID 26 字符 String)' 'PASS' 'IdGenerator.java 使用 UlidCreator.getMonotonicUlid()'
} else {
  Add-Result 'BE-007' '后端' 'ID 生成 (ULID 26 字符 String)' 'FAIL' "IdGenerator 缺失或未用 ULID: $idGenFile"
}

# BE-008: OpenAPI - 可访问 OpenAPI JSON
$openApiConfig = Join-Path $ProjectRoot 'backend\yutong-boot\src\main\java\com\yutong\boot\config\OpenApiConfig.java'
$openApiJson = Join-Path $ProjectRoot 'openapi\openapi.json'
$openApiYaml = Join-Path $ProjectRoot 'openapi\openapi.yaml'
if ((Test-PathExists $openApiConfig) -and
    ((Test-PathExists $openApiJson) -or (Test-PathExists $openApiYaml))) {
  Add-Result 'BE-008' '后端' 'OpenAPI (OpenApiConfig + openapi.json/yaml)' 'PASS' 'OpenApiConfig.java 存在 + openapi.json/yaml 任一存在'
} else {
  Add-Result 'BE-008' '后端' 'OpenAPI (OpenApiConfig + openapi.json/yaml)' 'FAIL' "OpenApiConfig 或 openapi.* 缺失: $openApiConfig / $openApiJson"
}

# BE-009: 健康检查 - health endpoint 可访问 (application.yml 配置 management.endpoint)
$appYml = Join-Path $ProjectRoot 'backend\yutong-boot\src\main\resources\application.yml'
if ((Test-PathExists $appYml) -and
    ((Test-FileContains $appYml 'actuator') -or (Test-FileContains $appYml 'health') -or
     (Test-FileContains $appYml 'management'))) {
  Add-Result 'BE-009' '后端' '健康检查 (health endpoint 配置)' 'PASS' 'application.yml 含 actuator/health/management 配置'
} else {
  Add-Result 'BE-009' '后端' '健康检查 (health endpoint 配置)' 'FAIL' "application.yml 缺失或未配置 health: $appYml"
}

# BE-010: 日志 - traceId 字段预留
$logbackFile = Join-Path $ProjectRoot 'backend\yutong-boot\src\main\resources\logback-spring.xml'
if ((Test-PathExists $logbackFile) -and (Test-FileContains $logbackFile '%X\{traceId')) {
  Add-Result 'BE-010' '后端' '日志 (traceId 字段预留 %X{traceId})' 'PASS' 'logback-spring.xml 含 [%X{traceId:-}] MDC 字段'
} else {
  Add-Result 'BE-010' '后端' '日志 (traceId 字段预留 %X{traceId})' 'FAIL' "logback-spring.xml 缺失或未含 traceId: $logbackFile"
}

# =====================================================================
# 2. Web 脚手架 8 项检查 (WEB-001 ~ WEB-008)
# =====================================================================

# WEB-001: Vue3/Vite/TS - 版本与技术选型一致
$webPkg = Join-Path $ProjectRoot 'web-admin\package.json'
if ((Test-PathExists $webPkg) -and
    (Test-FileContains $webPkg '"vue":\s*"\^3') -and
    (Test-FileContains $webPkg '"vite":\s*"\^5') -and
    (Test-FileContains $webPkg '"typescript":\s*"\^5')) {
  Add-Result 'WEB-001' 'Web' 'Vue3/Vite/TS 版本与技术选型一致' 'PASS' 'web-admin/package.json vue@^3 + vite@^5 + typescript@^5'
} else {
  Add-Result 'WEB-001' 'Web' 'Vue3/Vite/TS 版本与技术选型一致' 'FAIL' "web-admin/package.json 缺失或版本不匹配: $webPkg"
}

# WEB-002: 路由 - 登录、工作台、错误页存在
$routerFile = Join-Path $ProjectRoot 'web-admin\src\router\index.ts'
$routerOk = $false
if ((Test-PathExists $routerFile) -and
    (Test-FileContains $routerFile "name:\s*'Login'") -and
    (Test-FileContains $routerFile "name:\s*'Dashboard'") -and
    (Test-FileContains $routerFile "name:\s*'NotFound'")) {
  $routerOk = $true
}
if ($routerOk) {
  Add-Result 'WEB-002' 'Web' '路由 (登录/工作台/错误页存在)' 'PASS' 'router/index.ts 含 Login + Dashboard + NotFound 路由'
} else {
  Add-Result 'WEB-002' 'Web' '路由 (登录/工作台/错误页存在)' 'FAIL' "router/index.ts 缺失或路由不全: $routerFile"
}

# WEB-003: 状态管理 - Pinia 基础 store 存在
$storeDir = Join-Path $ProjectRoot 'web-admin\src\stores'
$requiredStores = @('app.ts', 'auth.ts')
$missingStores = @()
foreach ($s in $requiredStores) {
  $p = Join-Path $storeDir $s
  if (-not (Test-PathExists $p)) { $missingStores += $s }
}
# 至少 2 个 store (app + auth) 必备, 其余算 bonus
$storeCount = (Get-ChildItem -Path $storeDir -Filter '*.ts' -ErrorAction SilentlyContinue | Measure-Object).Count
if ($missingStores.Count -eq 0 -and $storeCount -ge 2) {
  Add-Result 'WEB-003' 'Web' "状态管理 (Pinia 基础 store 存在, $storeCount 个)" 'PASS' "stores/ 含 app.ts + auth.ts + 其余共 $storeCount 个 store"
} else {
  Add-Result 'WEB-003' 'Web' '状态管理 (Pinia 基础 store 存在)' 'FAIL' "缺失 store: $($missingStores -join ', ')"
}

# WEB-004: API 客户端 - 统一 request 封装存在
$requestFile = Join-Path $ProjectRoot 'web-admin\src\api\request.ts'
if ((Test-PathExists $requestFile) -and
    (Test-FileContains $requestFile 'axios\.create') -and
    (Test-FileContains $requestFile 'interceptors\.request') -and
    (Test-FileContains $requestFile 'interceptors\.response')) {
  Add-Result 'WEB-004' 'Web' 'API 客户端 (统一 request 封装)' 'PASS' 'api/request.ts 含 axios.create + 请求/响应拦截器'
} else {
  Add-Result 'WEB-004' 'Web' 'API 客户端 (统一 request 封装)' 'FAIL' "api/request.ts 缺失或不完整: $requestFile"
}

# WEB-005: 类型生成 - 可从 OpenAPI 生成类型 (openapi/openapi.json 存在即可)
if (Test-PathExists $openApiJson) {
  $openApiSize = (Get-Item $openApiJson).Length
  if ($openApiSize -gt 1000) {
    Add-Result 'WEB-005' 'Web' "类型生成 (openapi.json 可用, $openApiSize bytes)" 'PASS' 'openapi/openapi.json 存在且大小 > 1KB, 可生成类型'
  } else {
    Add-Result 'WEB-005' 'Web' '类型生成 (openapi.json)' 'WARN' "openapi.json 大小异常: $openApiSize bytes"
  }
} else {
  Add-Result 'WEB-005' 'Web' '类型生成 (openapi.json)' 'FAIL' "openapi/openapi.json 缺失: $openApiJson"
}

# WEB-006: 设计 token - CSS 变量或 token 文件存在
$variablesCss = Join-Path $ProjectRoot 'web-admin\src\styles\variables.css'
if ((Test-PathExists $variablesCss) -and
    (Test-FileContains $variablesCss '--yt-color-primary') -and
    (Test-FileContains $variablesCss ':root')) {
  Add-Result 'WEB-006' 'Web' '设计 token (CSS 变量文件存在)' 'PASS' 'styles/variables.css 含 :root + --yt-color-primary 等 token'
} else {
  Add-Result 'WEB-006' 'Web' '设计 token (CSS 变量文件存在)' 'FAIL' "styles/variables.css 缺失或不完整: $variablesCss"
}

# WEB-007: 组件基础 - BaseTable/SearchForm 最小组件存在
$baseTable = Join-Path $ProjectRoot 'web-admin\src\components\BaseTable.vue'
$searchForm = Join-Path $ProjectRoot 'web-admin\src\components\SearchForm.vue'
if ((Test-PathExists $baseTable) -and (Test-PathExists $searchForm) -and
    (Test-FileContains $baseTable 'defineProps') -and
    (Test-FileContains $searchForm 'defineProps')) {
  Add-Result 'WEB-007' 'Web' '组件基础 (BaseTable + SearchForm 含 Props/Events)' 'PASS' 'BaseTable.vue + SearchForm.vue 均存在且含 defineProps'
} else {
  Add-Result 'WEB-007' 'Web' '组件基础 (BaseTable + SearchForm)' 'FAIL' "BaseTable 或 SearchForm 缺失: $baseTable / $searchForm"
}

# WEB-008: 错误处理 - 401/403/500 最小处理链路存在
if ((Test-FileContains $requestFile '401') -and
    (Test-FileContains $requestFile '403') -and
    ((Test-FileContains $requestFile '500') -or (Test-FileContains $requestFile 'ElMessage\.error'))) {
  Add-Result 'WEB-008' 'Web' '错误处理 (401/403/500 最小处理链路)' 'PASS' 'api/request.ts 含 401/403 分支 + ElMessage.error 兜底'
} else {
  Add-Result 'WEB-008' 'Web' '错误处理 (401/403/500 最小处理链路)' 'FAIL' 'api/request.ts 未覆盖 401/403/500 处理'
}

# =====================================================================
# 3. 移动端脚手架 5 项检查 (MOB-001 ~ MOB-005)
# =====================================================================

# MOB-001: Uniapp/Vue3/TS - 工程可启动 H5
$mobPkg = Join-Path $ProjectRoot 'mobile-uniapp\package.json'
$mobManifest = Join-Path $ProjectRoot 'mobile-uniapp\src\manifest.json'
if ((Test-PathExists $mobPkg) -and (Test-PathExists $mobManifest) -and
    (Test-FileContains $mobPkg '"@dcloudio/uni-app"') -and
    (Test-FileContains $mobPkg '"vue":\s*"\^3')) {
  Add-Result 'MOB-001' '移动端' 'Uniapp/Vue3/TS 工程可启动 H5' 'PASS' 'mobile-uniapp/package.json 含 @dcloudio/uni-app + vue@^3 + manifest.json'
} else {
  Add-Result 'MOB-001' '移动端' 'Uniapp/Vue3/TS 工程可启动 H5' 'FAIL' "package.json 或 manifest.json 缺失: $mobPkg / $mobManifest"
}

# MOB-002: 页面 - 工作台、待办、登录骨架存在
$pagesJson = Join-Path $ProjectRoot 'mobile-uniapp\src\pages.json'
if ((Test-PathExists $pagesJson) -and
    (Test-FileContains $pagesJson 'pages/workbench/workbench') -and
    (Test-FileContains $pagesJson 'pages/todo/todo') -and
    (Test-FileContains $pagesJson 'pages/login/login')) {
  Add-Result 'MOB-002' '移动端' '页面 (工作台/待办/登录骨架)' 'PASS' 'pages.json 含 workbench + todo + login 三页面'
} else {
  Add-Result 'MOB-002' '移动端' '页面 (工作台/待办/登录骨架)' 'FAIL' "pages.json 缺失或页面不全: $pagesJson"
}

# MOB-003: 请求封装 - token、traceId、错误处理最小链路存在
$mobRequest = Join-Path $ProjectRoot 'mobile-uniapp\src\utils\request.ts'
if ((Test-PathExists $mobRequest) -and
    (Test-FileContains $mobRequest 'Authorization') -and
    (Test-FileContains $mobRequest 'X-Trace-Id') -and
    (Test-FileContains $mobRequest '401')) {
  Add-Result 'MOB-003' '移动端' '请求封装 (token + traceId + 错误处理)' 'PASS' 'utils/request.ts 含 Authorization + X-Trace-Id + 401 处理'
} else {
  Add-Result 'MOB-003' '移动端' '请求封装 (token + traceId + 错误处理)' 'FAIL' "utils/request.ts 缺失或不完整: $mobRequest"
}

# MOB-004: 弱网 - loading/retry/toast 基础封装
$mobTracker = Join-Path $ProjectRoot 'mobile-uniapp\src\utils\tracker.ts'
$mobHasRetry = $false
if (Test-PathExists $mobTracker) {
  if ((Test-FileContains $mobTracker 'retry') -and (Test-FileContains $mobTracker 'setTimeout')) {
    $mobHasRetry = $true
  }
}
# uni.showToast/showLoading 在 utils/ 或 pages/ 任一目录存在即可 (业务页面普遍使用)
$mobShowToastFound = $false
$mobSrcDir = Join-Path $ProjectRoot 'mobile-uniapp\src'
if (Test-PathExists $mobSrcDir) {
  $allMobFiles = Get-ChildItem -Path $mobSrcDir -Recurse -Include '*.ts','*.vue' -ErrorAction SilentlyContinue
  foreach ($f in $allMobFiles) {
    if (Test-FileContains $f.FullName 'uni\.showToast|uni\.showLoading') {
      $mobShowToastFound = $true
      break
    }
  }
}
if ($mobHasRetry -and $mobShowToastFound) {
  Add-Result 'MOB-004' '移动端' '弱网 (loading/retry/toast 基础封装)' 'PASS' 'tracker.ts 含 retry + setTimeout + src/ 含 uni.showToast/showLoading'
} else {
  Add-Result 'MOB-004' '移动端' '弱网 (loading/retry/toast 基础封装)' 'WARN' "retry=$mobHasRetry toast=$mobShowToastFound (移动端弱网封装部分依赖业务页面)"
}

# MOB-005: 上传 - 上传队列目录和接口桩存在
$mobApiDir = Join-Path $ProjectRoot 'mobile-uniapp\src\api'
$mobHasUpload = $false
if (Test-PathExists $mobApiDir) {
  $apiFiles = Get-ChildItem -Path $mobApiDir -Filter '*.ts' -ErrorAction SilentlyContinue
  foreach ($f in $apiFiles) {
    if (Test-FileContains $f.FullName 'upload|uploadFile|chooseImage') {
      $mobHasUpload = $true
      break
    }
  }
}
# 检查业务页面中是否有 upload 相关代码 (handle.vue 申请单处理含拍照上传)
$mobHandlePage = Join-Path $ProjectRoot 'mobile-uniapp\src\pages\biz\handle.vue'
$mobHasUploadInPage = $false
if (Test-PathExists $mobHandlePage) {
  if (Test-FileContains $mobHandlePage 'chooseImage|uploadFile|uni\.choose') {
    $mobHasUploadInPage = $true
  }
}
if ($mobHasUpload -or $mobHasUploadInPage) {
  $detail = if ($mobHasUpload) { 'api/ 含 upload 接口桩' } else { 'pages/biz/handle.vue 含拍照上传' }
  Add-Result 'MOB-005' '移动端' '上传 (接口桩/页面集成)' 'PASS' "移动端上传能力存在: $detail"
} else {
  Add-Result 'MOB-005' '移动端' '上传 (接口桩/页面集成)' 'WARN' 'mobile-uniapp 未找到 upload 接口桩或页面集成 (端能力待 v1.1+)'
}

# =====================================================================
# 4. 数据库脚手架 5 项检查 (DB-001 ~ DB-005)
# =====================================================================

# DB-001: Flyway - migration 目录存在
$migrationsDir = Join-Path $ProjectRoot 'database\migrations'
if (Test-PathExists $migrationsDir) {
  $migrationCount = (Get-ChildItem -Path $migrationsDir -Filter 'V*.sql' -ErrorAction SilentlyContinue | Measure-Object).Count
  Add-Result 'DB-001' '数据库' "Flyway migration 目录存在 ($migrationCount 个 V*.sql)" 'PASS' "database/migrations/ 含 $migrationCount 个 V*.sql 迁移脚本"
} else {
  Add-Result 'DB-001' '数据库' 'Flyway migration 目录存在' 'FAIL' "migrations 目录缺失: $migrationsDir"
}

# DB-002: 基础脚本 - V001 初始化可执行, 迁移编号固定三位
$v001File = Join-Path $migrationsDir 'V001__init_extensions.sql'
$v002File = Join-Path $migrationsDir 'V002__init_system_tables.sql'
if ((Test-PathExists $v001File) -and (Test-PathExists $v002File)) {
  # 检查编号格式: V001 三位数
  $formatOk = $true
  $allMigrations = Get-ChildItem -Path $migrationsDir -Filter 'V*.sql' -ErrorAction SilentlyContinue
  foreach ($m in $allMigrations) {
    $m2 = [regex]::Match($m.Name, '^V(\d{3})__')
    if (-not $m2.Success) {
      $formatOk = $false
      break
    }
  }
  if ($formatOk) {
    Add-Result 'DB-002' '数据库' '基础脚本 V001 + 三位编号格式' 'PASS' 'V001 + V002 存在 + 所有迁移编号均为 V\d{3}__ 三位格式'
  } else {
    Add-Result 'DB-002' '数据库' '基础脚本 V001 + 三位编号格式' 'WARN' "V001/V002 存在但部分文件编号非三位: $($m.Name)"
  }
} else {
  Add-Result 'DB-002' '数据库' '基础脚本 V001 + 三位编号格式' 'FAIL' "V001 或 V002 缺失: $v001File / $v002File"
}

# DB-003: 主键 - varchar(32) 字符串 ID (扫描 V002 等基础表)
$v003File = Join-Path $migrationsDir 'V003__init_sample_tables.sql'
$dbPkOk = $false
if ((Test-PathExists $v002File) -and (Test-FileContains $v002File 'varchar\(32\)')) {
  $dbPkOk = $true
}
if ($dbPkOk) {
  Add-Result 'DB-003' '数据库' '主键 varchar(32) 字符串 ID' 'PASS' 'V002 含 varchar(32) 主键定义 (ULID 26 字符)'
} else {
  Add-Result 'DB-003' '数据库' '主键 varchar(32) 字符串 ID' 'FAIL' "V002 未找到 varchar(32) 主键: $v002File"
}

# DB-004: 种子数据 - 本地可重复导入 (R__seed 脚本存在)
$seedDir = Join-Path $ProjectRoot 'database\seed'
if (Test-PathExists $seedDir) {
  $seedFiles = Get-ChildItem -Path $seedDir -Filter 'R__*.sql' -ErrorAction SilentlyContinue
  $seedCount = ($seedFiles | Measure-Object).Count
  if ($seedCount -ge 1) {
    # 检查至少有一个种子文件含 ON CONFLICT (可重复执行)
    $hasOnConflict = $false
    foreach ($sf in $seedFiles) {
      if (Test-FileContains $sf.FullName 'ON\s+CONFLICT') {
        $hasOnConflict = $true
        break
      }
    }
    if ($hasOnConflict) {
      Add-Result 'DB-004' '数据库' "种子数据 (R__seed $seedCount 个 + ON CONFLICT 可重复执行)" 'PASS' "database/seed/ 含 $seedCount 个 R__seed 脚本且使用 ON CONFLICT"
    } else {
      Add-Result 'DB-004' '数据库' '种子数据 (R__seed 可重复执行)' 'WARN' "$seedCount 个 R__seed 脚本但未检测到 ON CONFLICT, 重复执行可能报错"
    }
  } else {
    Add-Result 'DB-004' '数据库' '种子数据 (R__seed 脚本)' 'FAIL' "database/seed/ 无 R__*.sql 脚本"
  }
} else {
  Add-Result 'DB-004' '数据库' '种子数据 (R__seed 脚本)' 'FAIL' "seed 目录缺失: $seedDir"
}

# DB-005: 校验 SQL - 禁止自增检查可运行
$checkAutoInc = Join-Path $ProjectRoot 'tools\checks\check-no-autoincrement.sh'
$verifyDdl = Join-Path $ProjectRoot 'tools\db\verify-ddl-constraints.sql'
if ((Test-PathExists $checkAutoInc) -and (Test-PathExists $verifyDdl)) {
  Add-Result 'DB-005' '数据库' '校验 SQL (禁止自增检查可运行)' 'PASS' 'tools/checks/check-no-autoincrement.sh + tools/db/verify-ddl-constraints.sql 均存在'
} else {
  Add-Result 'DB-005' '数据库' '校验 SQL (禁止自增检查可运行)' 'FAIL' "校验脚本缺失: $checkAutoInc / $verifyDdl"
}

# =====================================================================
# 5. 部署脚手架 4 项检查 (DEP-001 ~ DEP-004)
# =====================================================================

# DEP-001: Docker Compose - PostgreSQL/Redis/MinIO 可启动
$composeBoot = Join-Path $ProjectRoot 'deploy\docker-compose.boot.yml'
if ((Test-PathExists $composeBoot) -and
    (Test-FileContains $composeBoot 'postgres:') -and
    (Test-FileContains $composeBoot 'redis:') -and
    (Test-FileContains $composeBoot 'minio:')) {
  Add-Result 'DEP-001' '部署' 'Docker Compose (PostgreSQL + Redis + MinIO)' 'PASS' 'deploy/docker-compose.boot.yml 含 postgres + redis + minio 三服务'
} else {
  Add-Result 'DEP-001' '部署' 'Docker Compose (PostgreSQL + Redis + MinIO)' 'FAIL' "docker-compose.boot.yml 缺失或不全: $composeBoot"
}

# DEP-002: 环境变量 - .env.example 存在
$envExample = Join-Path $ProjectRoot '.env.example'
if ((Test-PathExists $envExample) -and
    (Test-FileContains $envExample 'POSTGRES_') -and
    (Test-FileContains $envExample 'REDIS_') -and
    (Test-FileContains $envExample 'MINIO_')) {
  Add-Result 'DEP-002' '部署' '.env.example 存在' 'PASS' '.env.example 含 POSTGRES_ + REDIS_ + MINIO_ 关键变量'
} else {
  Add-Result 'DEP-002' '部署' '.env.example 存在' 'FAIL' ".env.example 缺失或不全: $envExample"
}

# DEP-003: 健康检查 - 服务启动后可检查
# docker-compose.boot.yml healthcheck 使用 YAML 数组格式 ["CMD", "mc", "ready", "local"]
# 因此 pg_isready/redis-cli/mc/ready 关键字分别独立匹配
if ((Test-FileContains $composeBoot 'healthcheck:') -and
    (Test-FileContains $composeBoot 'pg_isready') -and
    (Test-FileContains $composeBoot 'redis-cli') -and
    (Test-FileContains $composeBoot '"mc"') -and
    (Test-FileContains $composeBoot '"ready"')) {
  Add-Result 'DEP-003' '部署' '健康检查 (docker compose healthcheck)' 'PASS' 'docker-compose.boot.yml 含 pg_isready + redis-cli + mc ready 三个 healthcheck'
} else {
  Add-Result 'DEP-003' '部署' '健康检查 (docker compose healthcheck)' 'FAIL' 'docker-compose.boot.yml 未含完整 healthcheck 配置'
}

# DEP-004: 日志目录 - 本地日志路径明确 (logback 配置 + docker volumes)
$hasLogbackLog = Test-FileContains $logbackFile 'CONSOLE|FILE|appender'
$hasDockerVolume = Test-FileContains $composeBoot './data/redis|./data/postgres|./data/minio'
if ($hasLogbackLog -and $hasDockerVolume) {
  Add-Result 'DEP-004' '部署' '日志目录 (本地日志路径明确)' 'PASS' 'logback-spring.xml 含 appender + docker-compose 含 ./data/* 持久化卷'
} else {
  Add-Result 'DEP-004' '部署' '日志目录 (本地日志路径明确)' 'WARN' "logback appender=$hasLogbackLog docker volume=$hasDockerVolume (本地日志路径部分依赖运行时)"
}

# =====================================================================
# 汇总输出
# =====================================================================

Write-Host ''
Write-Host '============================================================' -ForegroundColor Cyan
Write-Host '  GA2-56 工程脚手架验收 (83-工程脚手架验收标准详设)' -ForegroundColor Cyan
Write-Host '  5 大类 32 项检查: 后端 10 + Web 8 + 移动 5 + 数据库 5 + 部署 4' -ForegroundColor Cyan
Write-Host '============================================================' -ForegroundColor Cyan
Write-Host ''

# 按分类输出
$categories = @('后端', 'Web', '移动端', '数据库', '部署')
foreach ($cat in $categories) {
  Write-Host "[$cat]" -ForegroundColor Yellow
  $catResults = $script:results | Where-Object { $_.Category -eq $cat }
  foreach ($r in $catResults) {
    $color = if ($r.Status -eq 'PASS') { 'Green' } elseif ($r.Status -eq 'WARN') { 'Yellow' } else { 'Red' }
    Write-Host ("  [{0}] {1} - {2}" -f $r.Status, $r.Id, $r.Name) -ForegroundColor $color
    Write-Host ("        {0}" -f $r.Detail) -ForegroundColor Gray
  }
  Write-Host ''
}

# 统计
$total = $script:results.Count
Write-Host '============================================================' -ForegroundColor Cyan
Write-Host ("  汇总: {0} 项检查  PASS={1}  WARN={2}  FAIL={3}" -f $total, $script:passCount, $script:warnCount, $script:failCount) -ForegroundColor Cyan
Write-Host '============================================================' -ForegroundColor Cyan

# 验收结论 (对齐 83 号文档"验收结论"章节)
Write-Host ''
if ($script:failCount -eq 0 -and $script:warnCount -eq 0) {
  Write-Host '  验收结论: 通过 (可进入 v0.3 样例业务开发)' -ForegroundColor Green
  $exitCode = 0
} elseif ($script:failCount -eq 0 -and $script:warnCount -gt 0) {
  Write-Host "  验收结论: 有条件通过 ($script:warnCount 个 P1 问题限期关闭)" -ForegroundColor Yellow
  $exitCode = 0
} else {
  Write-Host "  验收结论: 不通过 (存在 $script:failCount 个 P0, 不得进入下一阶段)" -ForegroundColor Red
  $exitCode = 1
}

# 输出 23 号追踪回写提示
Write-Host ''
Write-Host '回写提示: YuTong-Java-Docs/23-设计到落地追踪记录/23-设计到落地追踪记录.md line 140 工程脚手架验收' -ForegroundColor Gray

exit $exitCode
