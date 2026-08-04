# RB-06 AI 调用失败 Runbook

> 适用: AI Provider 不可用、超时、token 异常、限流
> 优先级: P2 | 影响: AI 对话、生成建议、知识库问答 | 设计来源: 33/63

## 1. 现象

- `/api/v1/ai/chat` 返回 500 / 502 / 504。
- 应用日志出现 `AiProviderException` / `OpenAiApiException` / `SocketTimeoutException`。
- Grafana `HighAiFailureRate` 告警触发（AI 失败率 > 10% for 10m）。
- 指标 `ai_request_total{status="FAILED"}` 激增。
- AI 对话回复内容为空或包含错误提示。
- mock 模式正常，真实 provider 调用失败。

## 2. 影响范围

- AI 对话不可用，但其他业务功能（登录、字典、申请单）正常。
- AI 生成建议无法应用，但已生成的草稿仍可访问。
- 知识库问答不可用，但文档入库和检索元数据正常。
- 工具调用审计会记录失败，但不会影响业务数据。

## 3. 快速判断命令

```bash
# 1) 应用容器状态
docker ps --filter "name=yutong-boot" --format "{{.Names}}\t{{.Status}}"

# 2) AI 接口健康（mock 模式）
curl.exe -s -X POST http://localhost:20010/api/v1/ai/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -d '{"scenario":"PLATFORM_QA","message":"ping"}' \
  -w "\nHTTP: %{http_code}\n"

# 3) AI 指标
curl.exe -s "http://localhost:20010/actuator/prometheus" | findstr "ai_request_total ai_request_duration_seconds"

# 4) Provider 可达性（如配置真实 provider）
curl.exe -s -o NUL -w "%{http_code}\n" -H "Authorization: Bearer {api_key}" \
  https://api.openai.com/v1/models

# 5) 最近失败 AI 请求（数据库）
docker exec yutong-postgres psql -U yutong -d yutong -c "
select id, scenario, status, error_code, left(error_message, 200) as err,
       latency_ms, created_time
from ai_conversation
where status = 'ERROR'
  and created_time > now() - interval '1 hour'
order by created_time desc
limit 20;"

# 6) AI 审计日志（工具调用失败）
docker exec yutong-postgres psql -U yutong -d yutong -c "
select tool_name, risk_level, status, left(input_summary, 100) as input,
       left(error_message, 200) as err, created_time
from ai_tool_call_audit
where status != 'SUCCESS'
  and created_time > now() - interval '1 hour'
order by created_time desc
limit 20;"

# 7) Token 用量统计
docker exec yutong-postgres psql -U yutong -d yutong -c "
select scenario, sum(token_input) as input_tokens, sum(token_output) as output_tokens,
       count(*) as request_count
from ai_message
where created_time > now() - interval '24 hours'
group by scenario
order by request_count desc;"

# 8) Provider 限流头（如调用真实 API）
curl.exe -s -I -H "Authorization: Bearer {api_key}" \
  https://api.openai.com/v1/models 2>&1 | findstr "X-RateLimit\|Retry-After"
```

## 4. 关键日志位置

| 位置 | 内容 |
| --- | --- |
| `docker logs yutong-boot` | AI provider 异常栈、超时、限流响应 |
| `ai_conversation` / `ai_message` 表 | 对话历史与失败状态 |
| `ai_tool_call_audit` 表 | 工具调用审计 |
| `ai_cost_log` 表 | token 成本记录 |
| Prometheus `ai_request_total` 指标 | 实时失败率 |

## 5. 回滚或临时降级方案

### 5.1 切换到 mock 模式（首选降级）

```yaml
# application.yml 紧急配置
yutong:
  ai:
    provider: mock              # 从 openai 切到 mock
    degraded: true
    degraded-message: "AI 服务暂时不可用，已切换到模拟模式"
    disabledTools: [generate_page_draft, generate_sql_draft]
    allowRagOnly: true          # 仅保留 RAG 问答，关闭生成类
```

```bash
# 重启应用生效
docker restart yutong-boot
sleep 30
curl.exe -s http://localhost:20010/actuator/health
```

### 5.2 切换备用 Provider

```yaml
yutong:
  ai:
    provider: openai-compatible
    providerFallbackOrder:
      - primary-model          # 主 provider
      - backup-model           # 备用 provider
    providers:
      openai-compatible:
        base-url: https://backup-provider.com/v1
        api-key: ${BACKUP_AI_API_KEY}
        model: gpt-4o-mini
        timeout-ms: 30000
```

### 5.3 关闭生成类工具（保留问答）

```yaml
yutong:
  ai:
    disabledTools:
      - generate_page_draft    # 关闭页面生成
      - generate_sql_draft     # 关闭 SQL 生成
    allowRagOnly: true         # 仅允许 RAG 问答
```

> 验证: `/api/v1/ai/chat` 对生成类场景返回明确降级提示，历史会话和引用查看仍可用。

### 5.4 限流自保（防止 provider 限流连锁）

```yaml
yutong:
  ai:
    rate-limit:
      enabled: true
      requests-per-minute: 30    # 限流到 30 RPM
      tokens-per-minute: 10000
    circuit-breaker:
      enabled: true
      failure-threshold: 5       # 5 次失败后熔断
      recovery-timeout-ms: 60000
```

## 6. 根因分析模板

```markdown
## RCA - {incidentId}

- 触发原因:
  - [ ] AI Provider 不可用（API 宕机/DNS 解析失败）
  - [ ] API Key 失效或额度耗尽
  - [ ] 限流（429 Too Many Requests）
  - [ ] 超时（网络延迟/大上下文）
  - [ ] Token 超限（context length exceeded）
  - [ ] 模型不支持的工具调用
  - [ ] 内容安全策略拒绝
  - [ ] 其他: ___
- 根因: {详细描述}
- 影响范围: {失败请求数/受影响用户数/场景}
- token 损失: {失败的 input/output token 总量}
- 实际 RTO: {minutes} 分钟
- 降级方案: {mock/备用 provider/关闭生成类}
- 防复发任务:
  1. {任务1}
  2. {任务2}
```

## 7. 后续修复任务

1. AI Provider 健康检查接入 `/actuator/health` 自定义 HealthIndicator。
2. 增加多 provider fallback 链路和熔断器。
3. token 用量接入 Grafana `ai-overview.json` 看板，按场景/租户维度告警。
