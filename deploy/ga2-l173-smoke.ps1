# GA2-L173: 低代码生成额度扣减 + 报表导出额度扣减 冒烟测试
# 设计来源: 70-商业授权与版本能力裁剪详设「额度扣减规则」第 1 条
#   "AI token、低代码生成、报表导出等额度使用必须先检查再扣减；扣减失败不得执行业务动作"
# 验证: lowcode.generate.count + report.export.count 两个额度编码的 checkQuota + recordQuotaUsage 完整流程

$ErrorActionPreference = 'Continue'
$baseUrl = 'http://localhost:20010'
$outFile = "$PSScriptRoot\..\release-evidence\v1.0.0\ga2-l173-smoke.txt"
$ts = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'

# UTF-8 BOM 写入头部（PowerShell 5.1 兼容）
$header = "========== GA2-L173 低代码生成额度扣减 + 报表导出额度扣减 冒烟测试 ==========`r`n时间: $ts (Asia/Shanghai)`r`n后端容器: yutong-backend-run (20010->8080)`r`n测试 License: LIC-TEST-2026-0001 Professional (lowcode.generate.count=100, report.export.count=50)`r`n"
[System.IO.File]::WriteAllText($outFile, $header, [System.Text.UTF8Encoding]::new($true))

function Write-Section($title) {
    $line = "`r`n========== $title ==========`r`n"
    [System.IO.File]::AppendAllText($outFile, $line, [System.Text.UTF8Encoding]::new($true))
    Write-Host $line
}

function Write-Result($resp) {
    [System.IO.File]::AppendAllText($outFile, $resp + "`r`n", [System.Text.UTF8Encoding]::new($true))
    Write-Host $resp
}

# ========== 0. 插入 Professional 测试 License ==========
Write-Section '0. 插入 Professional 测试 License (含 lowcode.generate.count=100 + report.export.count=50)'
$sqlContent = Get-Content -Path "$PSScriptRoot\ga2-l173-seed-license.sql" -Raw
$sqlContent | docker exec -i yutong-postgres psql -U yutong -d yutong 2>&1 | ForEach-Object { Write-Result $_ }

# ========== 1. Mock 登录 ==========
Write-Section '1. Mock 登录获取 token'
$loginBody = '{"username":"admin","password":"admin"}'
[System.IO.File]::WriteAllText("$env:TEMP\ga2-l173-login.json", $loginBody, [System.Text.UTF8Encoding]::new($false))
$loginResp = curl.exe -s -X POST "$baseUrl/api/v1/auth/login" -H "Content-Type: application/json" -d "@$env:TEMP\ga2-l173-login.json"
Write-Result $loginResp
$token = ($loginResp | ConvertFrom-Json).data.token
$authHeader = "Authorization: Bearer $token"
Write-Host "Token: $token"

# ========== 2. GET /license/usage (调用前 - 应为空或无 lowcode/report 记录) ==========
Write-Section '2. GET /api/v1/license/usage (低代码/报表调用前 - 应无 lowcode/report 记录)'
$resp2 = curl.exe -s -X GET "$baseUrl/api/v1/license/usage" -H $authHeader
Write-Result $resp2

# ========== 3. 低代码: 创建生成任务 (DDL scope, 用种子实体 project) ==========
Write-Section '3. 低代码: 创建生成任务 (DDL scope, 种子实体 project id=01JYYDEMOLCENT000001)'
$createTaskBody = '{"entityId":"01JYYDEMOLCENT000001","targetScope":"DDL","templateVersion":"1.0"}'
[System.IO.File]::WriteAllText("$env:TEMP\ga2-l173-task.json", $createTaskBody, [System.Text.UTF8Encoding]::new($false))
$resp3 = curl.exe -s -X POST "$baseUrl/api/v1/lowcode/generator-tasks" -H $authHeader -H "Content-Type: application/json" -d "@$env:TEMP\ga2-l173-task.json"
Write-Result $resp3
$taskId = ($resp3 | ConvertFrom-Json).data.id
Write-Host "生成任务 ID: $taskId"

# ========== 4. 低代码: 执行生成任务 (触发 checkQuota + recordQuotaUsage) ==========
Write-Section '4. 低代码: 执行生成任务 (POST /generator-tasks/{id}/run - 触发 checkQuota + recordQuotaUsage)'
$resp4 = curl.exe -s -X POST "$baseUrl/api/v1/lowcode/generator-tasks/$taskId/run" -H $authHeader
Write-Result $resp4

# ========== 5. GET /license/usage (低代码生成后 - 应有 lowcode.generate.count=1) ==========
Write-Section '5. GET /api/v1/license/usage (低代码生成后 - 应有 lowcode.generate.count used=1)'
Start-Sleep -Seconds 2
$resp5 = curl.exe -s -X GET "$baseUrl/api/v1/license/usage" -H $authHeader
Write-Result $resp5

# ========== 6. 低代码: 第二次执行生成 (验证额度累加) ==========
Write-Section '6. 低代码: 创建并执行第二个生成任务 (验证 lowcode.generate.count 累加到 2)'
$createTaskBody2 = '{"entityId":"01JYYDEMOLCENT000001","targetScope":"OPENAPI","templateVersion":"1.0"}'
[System.IO.File]::WriteAllText("$env:TEMP\ga2-l173-task2.json", $createTaskBody2, [System.Text.UTF8Encoding]::new($false))
$resp6a = curl.exe -s -X POST "$baseUrl/api/v1/lowcode/generator-tasks" -H $authHeader -H "Content-Type: application/json" -d "@$env:TEMP\ga2-l173-task2.json"
Write-Result $resp6a
$taskId2 = ($resp6a | ConvertFrom-Json).data.id
$resp6b = curl.exe -s -X POST "$baseUrl/api/v1/lowcode/generator-tasks/$taskId2/run" -H $authHeader
Write-Result $resp6b

# ========== 7. GET /license/usage (第二次低代码生成后 - lowcode.generate.count used=2) ==========
Write-Section '7. GET /api/v1/license/usage (第二次低代码生成后 - lowcode.generate.count used=2)'
Start-Sleep -Seconds 2
$resp7 = curl.exe -s -X GET "$baseUrl/api/v1/license/usage" -H $authHeader
Write-Result $resp7

# ========== 8. 报表: 导出报表 (POST /report/reports/biz_request_overview/export) ==========
Write-Section '8. 报表: 导出报表 biz_request_overview (触发 checkQuota + 异步 recordQuotaUsage)'
$resp8 = curl.exe -s -X POST "$baseUrl/api/v1/report/reports/biz_request_overview/export" -H $authHeader -H "Content-Type: application/json" -d "{}"
Write-Result $resp8
$exportTaskId = ($resp8 | ConvertFrom-Json).data.id
Write-Host "报表导出任务 ID: $exportTaskId"

# ========== 9. 等待异步导出完成并轮询任务状态 ==========
Write-Section '9. 轮询报表导出任务状态 (等待异步完成)'
Start-Sleep -Seconds 5
$resp9 = curl.exe -s -X GET "$baseUrl/api/v1/import-export-tasks/$exportTaskId" -H $authHeader
Write-Result $resp9

# ========== 10. GET /license/usage (报表导出后 - 应有 report.export.count=1) ==========
Write-Section '10. GET /api/v1/license/usage (报表导出后 - 应有 report.export.count used=1)'
Start-Sleep -Seconds 2
$resp10 = curl.exe -s -X GET "$baseUrl/api/v1/license/usage" -H $authHeader
Write-Result $resp10

# ========== 11. 数据库直查 sys_license_usage (验证落账) ==========
Write-Section '11. 数据库直查 sys_license_usage (验证 lowcode + report 额度落账)'
$dbResult = docker exec yutong-postgres psql -U yutong -d yutong -c "SELECT quota_code, usage_period, used_amount, limit_amount, last_used_time FROM sys_license_usage WHERE tenant_id='default' AND quota_code IN ('lowcode.generate.count','report.export.count') ORDER BY quota_code;" 2>&1
Write-Result $dbResult

# ========== 12. 完成提示 ==========
Write-Section '12. GA2-L173 冒烟测试完成'
$footer = "`r`n预期结果: lowcode.generate.count used=2 (两次生成), report.export.count used=1 (一次导出)`r`n"
[System.IO.File]::AppendAllText($outFile, $footer, [System.Text.UTF8Encoding]::new($true))
Write-Host $footer
Write-Host "结果已写入: $outFile"
