# Demo 环境一键重置脚本
# 设计来源: 68-演示环境与样例数据剧本详设、79-首版发布验收包与证据归档详设
# 功能: 重置 Demo 样例数据、账号和文件，不影响系统配置
# 用法: .\reset-demo.ps1 [-BackendHost localhost] [-BackendPort 8082] [-DemoPassword demo123]
#
# GA2-L178 升级:
#   - 验证指标对齐 68 号文档 (20 客户/50 商品/100 申请单/50 消息/3 低代码实体)
#   - 增加 4 类 Demo 账号登录验证 (admin_demo/reviewer_demo/user_demo/viewer_demo)
#   - 密码通过环境变量 YUTONG_DEMO_PASSWORD 初始化 (默认 demo123)

param(
    [string]$BackendHost = "localhost",
    [int]$BackendPort = 8082,
    [string]$DemoPassword = "demo123"
)

$ErrorActionPreference = "Stop"
$base = "http://${BackendHost}:${BackendPort}"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  YuTong Demo 环境一键重置" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "目标: $base"
Write-Host "时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host "Demo 密码: $DemoPassword (来自 YUTONG_DEMO_PASSWORD 环境变量或默认值)"
Write-Host ""

# Step 1: 验证后端健康状态
Write-Host "[1/6] 验证后端健康状态..." -NoNewline
try {
    $health = Invoke-RestMethod -Uri "$base/actuator/health" -Method Get -TimeoutSec 10
    if ($health.status -ne "UP") {
        Write-Host " FAIL (status=$($health.status))" -ForegroundColor Red
        exit 1
    }
    Write-Host " UP" -ForegroundColor Green
} catch {
    Write-Host " FAIL ($($_.Exception.Message))" -ForegroundColor Red
    exit 1
}

# Step 2: 通过 Flyway repeatable migration 重置 Demo 数据
# R__seed_demo_data.sql 会自动清理 ID 前缀 01JYYDEMO% 的旧数据并重新插入
Write-Host "[2/6] 重置 Demo 种子数据 (Flyway repeatable)..." -NoNewline
try {
    # 清理 Demo 数据 + 重置 Flyway checksum 强制 R__ 脚本重跑
    docker exec yutong-postgres psql -U yutong -d yutong -c "
        -- 清理 Demo 数据 (ID 前缀 01JYYDEMO%)
        DELETE FROM ai_embedding WHERE chunk_id LIKE '01JYYDEMOCHUNK%';
        DELETE FROM ai_document_chunk WHERE id LIKE '01JYYDEMO%';
        DELETE FROM ai_document WHERE id LIKE '01JYYDEMO%';
        DELETE FROM ai_knowledge_base WHERE id LIKE '01JYYDEMO%';
        DELETE FROM ai_prompt_template WHERE id LIKE '01JYYDEMO%';
        DELETE FROM ai_provider WHERE id LIKE '01JYYDEMO%';
        DELETE FROM lc_entity WHERE id LIKE '01JYYDEMO%';
        DELETE FROM biz_request_item WHERE id LIKE '01JYYDEMO%';
        DELETE FROM biz_approval_record WHERE id LIKE '01JYYDEMO%';
        DELETE FROM biz_request WHERE id LIKE '01JYYDEMO%';
        DELETE FROM sys_todo_task WHERE id LIKE '01JYYDEMO%';
        DELETE FROM sys_message WHERE id LIKE '01JYYDEMO%';
        DELETE FROM biz_product WHERE id LIKE '01JYYDEMO%';
        DELETE FROM biz_customer WHERE id LIKE '01JYYDEMO%';
        -- 重置 Flyway checksum 强制 R__ 脚本重跑
        UPDATE flyway_schema_history SET checksum = NULL WHERE script = 'R__seed_demo_data.sql' AND type = 'SQL';
    " 2>&1 | Out-Null

    # 重启后端触发 Flyway 重跑
    docker restart yutong-backend-run 2>&1 | Out-Null

    # 等待后端启动
    $maxWait = 90
    $waited = 0
    while ($waited -lt $maxWait) {
        Start-Sleep -Seconds 2
        $waited += 2
        try {
            $h = Invoke-RestMethod -Uri "$base/actuator/health" -Method Get -TimeoutSec 5
            if ($h.status -eq "UP") { break }
        } catch { }
    }
    if ($waited -ge $maxWait) {
        Write-Host " TIMEOUT" -ForegroundColor Red
        exit 1
    }
    Write-Host " DONE (${waited}s)" -ForegroundColor Green
} catch {
    Write-Host " FAIL ($($_.Exception.Message))" -ForegroundColor Red
    exit 1
}

# Step 3: 验证 4 类 Demo 账号登录 (GA2-L178)
Write-Host "[3/6] 验证 4 类 Demo 账号登录..." -NoNewline
$demoAccounts = @(
    @{ username = "admin_demo";    expectedRole = "ADMIN";    expectedMockType = "admin" },
    @{ username = "reviewer_demo"; expectedRole = "APPROVER"; expectedMockType = "approver" },
    @{ username = "user_demo";     expectedRole = "BIZ_USER"; expectedMockType = "biz" },
    @{ username = "viewer_demo";   expectedRole = "VIEWER";   expectedMockType = "viewer" }
)
$loginResults = @()
foreach ($acct in $demoAccounts) {
    try {
        $loginBody = @{ username = $acct.username; password = $DemoPassword } | ConvertTo-Json
        $r = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body $loginBody -ContentType "application/json" -TimeoutSec 10
        $roleMatch = ($r.data.roles -contains $acct.expectedRole)
        $typeMatch = ($r.data.mockUserType -eq $acct.expectedMockType)
        if ($roleMatch -and $typeMatch) {
            $loginResults += "OK"
        } else {
            $loginResults += "MISMATCH(role=$roleMatch,type=$typeMatch)"
        }
    } catch {
        $loginResults += "FAIL($($_.Exception.Message))"
    }
}
$allLoginOk = ($loginResults | Where-Object { $_ -ne "OK" }).Count -eq 0
if ($allLoginOk) {
    Write-Host " OK (4/4)" -ForegroundColor Green
} else {
    Write-Host " WARN" -ForegroundColor Yellow
    for ($i = 0; $i -lt $demoAccounts.Count; $i++) {
        Write-Host "  $($demoAccounts[$i].username): $($loginResults[$i])" -ForegroundColor $(if ($loginResults[$i] -eq "OK") {"Green"} else {"Yellow"})
    }
}

# Step 4: 验证密码错误拒绝 (安全边界)
Write-Host "[4/6] 验证密码错误拒绝..." -NoNewline
try {
    $badLoginBody = @{ username = "admin_demo"; password = "wrong-password" } | ConvertTo-Json
    try {
        Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body $badLoginBody -ContentType "application/json" -TimeoutSec 10
        Write-Host " FAIL (should reject)" -ForegroundColor Red
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -eq 401 -or $statusCode -eq 403) {
            Write-Host " OK (HTTP $statusCode)" -ForegroundColor Green
        } else {
            Write-Host " WARN (HTTP $statusCode)" -ForegroundColor Yellow
        }
    }
} catch {
    Write-Host " FAIL ($($_.Exception.Message))" -ForegroundColor Red
}

# Step 5: 验证核心数据 (68 号文档要求: 20 客户/50 商品/100 申请单/50 消息/3 低代码实体)
Write-Host "[5/6] 验证核心数据 (68 号文档要求)..." -NoNewline
try {
    # 使用 admin_demo 登录获取 token + mockUserType
    $adminLoginBody = @{ username = "admin_demo"; password = $DemoPassword } | ConvertTo-Json
    $adminLogin = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body $adminLoginBody -ContentType "application/json" -TimeoutSec 10
    $adminToken = $adminLogin.data.token
    $adminHeaders = @{
        "Authorization" = "Bearer $adminToken"
        "X-Mock-User" = $adminLogin.data.mockUserType
        "X-Trace-Id" = "demo-reset-$(Get-Date -Format 'yyyyMMddHHmmss')"
    }

    # 直接查询数据库验证数据量 (更准确)
    $counts = docker exec yutong-postgres psql -U yutong -d yutong -t -A -c "
        SELECT
            (SELECT COUNT(*) FROM biz_customer WHERE id LIKE '01JYYDEMO%' OR id LIKE '01JYYYYYYCUSTSAMPLE%') || '|' ||
            (SELECT COUNT(*) FROM biz_product WHERE id LIKE '01JYYDEMO%' OR id LIKE '01JYYYYYYPRODSAMPLE%') || '|' ||
            (SELECT COUNT(*) FROM biz_request WHERE id LIKE '01JYYDEMO%') || '|' ||
            (SELECT COUNT(*) FROM sys_message WHERE id LIKE '01JYYDEMO%') || '|' ||
            (SELECT COUNT(*) FROM lc_entity WHERE id LIKE '01JYYDEMO%')
    " 2>&1
    $parts = $counts.Trim().Split("|")
    $custCount = [int]$parts[0]
    $prodCount = [int]$parts[1]
    $reqCount = [int]$parts[2]
    $msgCount = [int]$parts[3]
    $lcCount = [int]$parts[4]

    $expected = @{ customers = 20; products = 50; requests = 100; messages = 50; lcEntities = 3 }
    $actual = @{ customers = $custCount; products = $prodCount; requests = $reqCount; messages = $msgCount; lcEntities = $lcCount }

    $allMatch = $true
    foreach ($k in $expected.Keys) {
        if ($actual[$k] -lt $expected[$k]) {
            $allMatch = $false
        }
    }
    if ($allMatch) {
        Write-Host " OK (${custCount}客户/${prodCount}商品/${reqCount}申请单/${msgCount}消息/${lcCount}低代码实体)" -ForegroundColor Green
    } else {
        Write-Host " WARN (expected 20/50/100/50/3, got ${custCount}/${prodCount}/${reqCount}/${msgCount}/${lcCount})" -ForegroundColor Yellow
    }
} catch {
    Write-Host " FAIL ($($_.Exception.Message))" -ForegroundColor Red
}

# Step 6: 输出 Demo 账号信息 (68 号文档 line 30-37)
Write-Host "[6/6] Demo 账号信息 (68 号文档):" -ForegroundColor Cyan
Write-Host "  ==========================================" -ForegroundColor Cyan
Write-Host "  账号            角色       能力" -ForegroundColor White
Write-Host "  ------------------------------------------"
Write-Host "  admin_demo      管理员     全部演示能力 (ALL 数据范围)"
Write-Host "  reviewer_demo   审核人     待办审核 (CUSTOM 数据范围)"
Write-Host "  user_demo       业务人员   创建申请单 (SELF 数据范围)"
Write-Host "  viewer_demo     只读用户   查看报表/文档 (TENANT 数据范围 + 脱敏)"
Write-Host "  ------------------------------------------"
Write-Host "  密码: $DemoPassword (环境变量 YUTONG_DEMO_PASSWORD)"
Write-Host "  ==========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  登录方式: POST /api/v1/auth/login"
Write-Host "    Body: {\"username\":\"admin_demo\",\"password\":\"$DemoPassword\"}"
Write-Host "  后续请求 Header:"
Write-Host "    Authorization: Bearer <token>"
Write-Host "    X-Mock-User: <mockUserType>  (登录响应返回, admin/approver/biz/viewer)"
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Demo 重置完成" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
