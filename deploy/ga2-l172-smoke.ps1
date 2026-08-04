# GA2-L172 License 额度扣减业务接入冒烟测试
$token = "mock-token-1784715773875"
$outFile = "d:\MyCode\YuTong-Java-Vue\release-evidence\v1.0.0\ga2-l172-smoke.txt"

# 使用 UTF8 编码写入 (无 BOM)
[System.IO.File]::WriteAllText($outFile, "========== GA2-L172 License 额度扣减业务接入 + License 管理页面 冒烟测试 ==========`r`n", [System.Text.Encoding]::UTF8)

function Append-Line($line) {
    [System.IO.File]::AppendAllText($outFile, "$line`r`n", [System.Text.Encoding]::UTF8)
}

Append-Line "时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') (Asia/Shanghai)"
Append-Line "后端容器: yutong-backend-run (20010->8080)"
Append-Line "镜像: yutong-backend:latest (GA2-L172 重建)"
Append-Line "测试 License: LIC-TEST-2026-0001 Professional (含 ai 模块, ai.monthly.tokens 上限 1000000)"
Append-Line ""

# 1. GET /api/v1/license/current
Append-Line "========== 1. GET /api/v1/license/current (Professional License 已激活) =========="
$resp1 = curl.exe -s http://localhost:20010/api/v1/license/current -H "Authorization: Bearer $token"
Append-Line $resp1
Append-Line ""

# 2. GET /api/v1/license/modules
Append-Line "========== 2. GET /api/v1/license/modules (Professional 全模块授权) =========="
$resp2 = curl.exe -s http://localhost:20010/api/v1/license/modules -H "Authorization: Bearer $token"
Append-Line $resp2
Append-Line ""

# 3. GET /api/v1/license/usage (AI 调用前应为空)
Append-Line "========== 3. GET /api/v1/license/usage (AI 调用前 - 应为空数组) =========="
$resp3 = curl.exe -s http://localhost:20010/api/v1/license/usage -H "Authorization: Bearer $token"
Append-Line $resp3
Append-Line ""

# 4. POST /api/v1/ai/chat (第一次 - 触发 checkQuota + recordQuotaUsage)
Append-Line "========== 4. POST /api/v1/ai/chat (第一次调用 - 触发 checkQuota 预校验 + recordQuotaUsage 扣减) =========="
# 使用临时文件传递 JSON body 避免 PowerShell 转义问题
$body1 = '{"message":"test ga2-l172 quota deduction","scenario":"PLATFORM_QA"}'
$body1 | Out-File -FilePath "$env:TEMP\ga2-l172-body1.json" -Encoding UTF8 -NoNewline
$resp4 = curl.exe -s -X POST http://localhost:20010/api/v1/ai/chat -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "@$env:TEMP\ga2-l172-body1.json"
Append-Line $resp4
Append-Line ""

Start-Sleep -Seconds 2

# 5. GET /api/v1/license/usage (第一次 AI 调用后 - 应有 ai.monthly.tokens 记录)
Append-Line "========== 5. GET /api/v1/license/usage (第一次 AI 调用后 - 应有 ai.monthly.tokens 记录) =========="
$resp5 = curl.exe -s http://localhost:20010/api/v1/license/usage -H "Authorization: Bearer $token"
Append-Line $resp5
Append-Line ""

# 6. POST /api/v1/ai/chat (第二次 - 验证额度累加)
Append-Line "========== 6. POST /api/v1/ai/chat (第二次调用 - 验证额度累加) =========="
$body2 = '{"message":"second call to verify quota accumulation","scenario":"PLATFORM_QA"}'
$body2 | Out-File -FilePath "$env:TEMP\ga2-l172-body2.json" -Encoding UTF8 -NoNewline
$resp6 = curl.exe -s -X POST http://localhost:20010/api/v1/ai/chat -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "@$env:TEMP\ga2-l172-body2.json"
Append-Line $resp6
Append-Line ""

Start-Sleep -Seconds 2

# 7. GET /api/v1/license/usage (第二次 AI 调用后 - used 应累加)
Append-Line "========== 7. GET /api/v1/license/usage (第二次 AI 调用后 - used 应累加) =========="
$resp7 = curl.exe -s http://localhost:20010/api/v1/license/usage -H "Authorization: Bearer $token"
Append-Line $resp7
Append-Line ""

# 8. 验证 sys_license_usage 表数据
Append-Line "========== 8. 数据库直查 sys_license_usage (验证落账) =========="
$dbResp = docker exec yutong-postgres psql -U yutong -d yutong -t -c "SELECT quota_code, usage_period, used_amount, limit_amount, last_used_time FROM sys_license_usage WHERE tenant_id='default' AND quota_code='ai.monthly.tokens';"
Append-Line $dbResp
Append-Line ""

# 9. 验证 sys_license_audit_log 审计记录
Append-Line "========== 9. 数据库直查 sys_license_audit_log (验证额度扣减审计)"
$auditResp = docker exec yutong-postgres psql -U yutong -d yutong -t -c "SELECT action, module_code, quota_code, result FROM sys_license_audit_log WHERE quota_code='ai.monthly.tokens' ORDER BY operated_time DESC LIMIT 5;"
Append-Line $auditResp

Write-Host "Smoke test complete."
