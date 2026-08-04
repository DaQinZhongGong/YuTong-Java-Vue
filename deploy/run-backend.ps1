<#
.SYNOPSIS
    YuTong 后端构建与运行脚本
.DESCRIPTION
    GA2-30: 统一使用 yutong- 前缀的镜像名和容器名, 防止与其他项目冲突。
    设计来源: 20-DevOps与部署设计、25-本地开发与工程初始化手册

    用法:
      .\deploy\run-backend.ps1              # 构建并启动 (默认)
      .\deploy\run-backend.ps1 -BuildOnly   # 仅构建镜像
      .\deploy\run-backend.ps1 -Up          # 启动 (使用已有镜像)
      .\deploy\run-backend.ps1 -Down        # 停止并移除容器
      .\deploy\run-backend.ps1 -Logs        # 查看日志
      .\deploy\run-backend.ps1 -Rebuild     # 强制重新构建并启动
#>

param(
    [switch]$BuildOnly,
    [switch]$Up,
    [switch]$Down,
    [switch]$Logs,
    [switch]$Rebuild
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$ComposeFile = Join-Path $ScriptDir "docker-compose.run.yml"

# yutong 前缀镜像名 (GA2-30 硬约束)
$ImageName = "yutong-backend"
$ImageTag = "latest"
$FullImageName = "${ImageName}:${ImageTag}"

# DEP-PORT-001: 后端宿主机端口可通过环境变量覆盖，默认 20010（与 deploy/.env BACKEND_PORT=20010、deploy/port-mapping.md 一致）
$BackendPort = if ($env:BACKEND_PORT) { $env:BACKEND_PORT } else { '20010' }

Write-Host "=== YuTong Backend Build & Run ===" -ForegroundColor Cyan
Write-Host "Image:  $FullImageName"
Write-Host "Network: yutong_default"
Write-Host "Prefix: yutong- (防止与其他项目冲突)"
Write-Host ""

function Invoke-Compose {
    param([string[]]$Args)
    $cmd = "docker compose -f `"$ComposeFile`" $($Args -join ' ')"
    Write-Host "> $cmd" -ForegroundColor DarkGray
    Invoke-Expression $cmd
    if ($LASTEXITCODE -ne 0) {
        Write-Error "命令失败 (exit code: $LASTEXITCODE)"
        exit 1
    }
}

# --- 停止 ---
if ($Down) {
    Write-Host "[1/1] 停止并移除容器..." -ForegroundColor Yellow
    Invoke-Compose -Args @("down")
    Write-Host "Done. 容器已停止。" -ForegroundColor Green
    return
}

# --- 查看日志 ---
if ($Logs) {
    Write-Host "[1/1] 查看日志..." -ForegroundColor Yellow
    Invoke-Compose -Args @("logs", "-f", "--tail=100")
    return
}

# --- 仅构建 ---
if ($BuildOnly) {
    Write-Host "[1/2] 构建 Docker 镜像 $FullImageName ..." -ForegroundColor Yellow
    # 先构建镜像
    $buildCmd = "docker build -t $FullImageName -f `"$ProjectRoot\backend\Dockerfile`" `"$ProjectRoot`""
    Write-Host "> $buildCmd" -ForegroundColor DarkGray
    Invoke-Expression $buildCmd
    if ($LASTEXITCODE -ne 0) {
        Write-Error "镜像构建失败 (exit code: $LASTEXITCODE)"
        exit 1
    }
    Write-Host "[2/2] 镜像构建成功:" -ForegroundColor Green
    docker images $ImageName --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}\t{{.CreatedAt}}"
    return
}

# --- 启动 (不构建) ---
if ($Up -and -not $Rebuild) {
    Write-Host "[1/2] 检查镜像是否存在..." -ForegroundColor Yellow
    $imgExists = docker images -q $FullImageName 2>$null
    if (-not $imgExists) {
        Write-Host "镜像 $FullImageName 不存在, 请先运行: .\deploy\run-backend.ps1 -BuildOnly" -ForegroundColor Red
        exit 1
    }
    Write-Host "[2/2] 启动容器..." -ForegroundColor Yellow
    Invoke-Compose -Args @("up", "-d")
    Write-Host "Done. 后端已启动:" -ForegroundColor Green
    docker ps --filter "name=yutong-backend-run" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
    Write-Host ""
    Write-Host "API: http://localhost:${BackendPort}/api/v1" -ForegroundColor Cyan
    Write-Host "Swagger: http://localhost:${BackendPort}/swagger-ui.html" -ForegroundColor Cyan
    Write-Host "Health: http://localhost:${BackendPort}/actuator/health" -ForegroundColor Cyan
    return
}

# --- 默认: 构建并启动 ---
if ($Rebuild) {
    Write-Host "[1/3] 强制重新构建镜像 (--no-cache)..." -ForegroundColor Yellow
    $buildCmd = "docker build --no-cache -t $FullImageName -f `"$ProjectRoot\backend\Dockerfile`" `"$ProjectRoot`""
    Write-Host "> $buildCmd" -ForegroundColor DarkGray
    Invoke-Expression $buildCmd
    if ($LASTEXITCODE -ne 0) {
        Write-Error "镜像构建失败 (exit code: $LASTEXITCODE)"
        exit 1
    }
    Write-Host "[2/3] 停止旧容器..." -ForegroundColor Yellow
    Invoke-Compose -Args @("down")
    Write-Host "[3/3] 启动新容器..." -ForegroundColor Yellow
    Invoke-Compose -Args @("up", "-d")
} else {
    Write-Host "[1/2] 构建并启动..." -ForegroundColor Yellow
    Invoke-Compose -Args @("up", "-d", "--build")
}

Write-Host ""
Write-Host "Done. 后端已启动:" -ForegroundColor Green
docker ps --filter "name=yutong-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
Write-Host ""
Write-Host "API: http://localhost:${BackendPort}/api/v1" -ForegroundColor Cyan
Write-Host "Swagger: http://localhost:${BackendPort}/swagger-ui.html" -ForegroundColor Cyan
Write-Host "Health: http://localhost:${BackendPort}/actuator/health" -ForegroundColor Cyan
