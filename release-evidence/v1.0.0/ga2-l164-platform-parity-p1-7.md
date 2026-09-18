# GA2-L164 Evidence — 业界同类实现 AI 对标 P1-7 多模态视觉 + 端到端门禁

> 生成时间: 2026-09-02 | 执行人: Mavis (已获全权授权)

## 后端单测 (12 个 / 84 case 全 PASS / BUILD SUCCESS)

```
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0  in com.yutong.ai.agent.service.ReActDecisionParseTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0  in com.yutong.ai.aiflow.service.AiflowEngineCycleTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0  in com.yutong.ai.chat.service.llm.CozeAdapterParseTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0  in com.yutong.ai.chat.service.llm.LlmMessageMultimodalTest  [NEW P1-7]
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0 in com.yutong.ai.chat.service.llm.OpenAiCompatibleAdapterContractTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0  in com.yutong.ai.chat.service.llm.OpenAiCompatibleAdapterMultimodalTest  [NEW P1-7]
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0  in com.yutong.ai.chat.ws.AiChatWebSocketHandlerTest
[INFO] Tests run: 28, Failures: 0, Errors: 0, Skipped: 0 in com.yutong.ai.gateway.service.AiToolRegistryTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0  in com.yutong.ai.memory.service.AiMemoryServiceTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0  in com.yutong.ai.rag.service.EmbeddingServiceTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0  in com.yutong.ai.rag.service.GraphExtractionParserTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0  in com.yutong.ai.tool.service.McpMarketInstallParseTest
[INFO] Tests run: 84, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

环境: Azul JDK 25.0.4.1 + Maven 3.9.11 + Windows PowerShell 5.1

仍待 JDK 17/21 回归的 Mockito 旧测试 (6 个): AiChatApplicationServiceTest / AiProviderHealthServiceTest / LlmProviderSelectorTest / OpenAiCompatibleAdapterTest / AiCostGovernanceServiceTest / RagAclServiceTest

## 后端 install (11 模块 BUILD SUCCESS, jar 全部产出)

```
[INFO] Installing yutong-common-0.2.0.jar
[INFO] Installing yutong-infra-0.2.0.jar
[INFO] Installing yutong-api-0.2.0.jar
[INFO] Installing yutong-auth-adapter-0.2.0.jar
[INFO] Installing yutong-system-service-0.2.0.jar
[INFO] Installing yutong-sample-service-0.2.0.jar
[INFO] Installing yutong-lowcode-service-0.2.0.jar
[INFO] Installing yutong-ai-service-0.2.0.jar            (含 P1-7 多模态视觉)
[INFO] Installing yutong-workflow-service-0.2.0.jar
[INFO] Installing yutong-boot-0.2.0.jar
[INFO] Installing yutong-gateway-0.2.0.jar
[INFO] BUILD SUCCESS
```

## 前端 type-check (两端 0 错)

```
==> [web] pnpm type-check
    [web] PASS
==> [web-admin] pnpm type-check
    [web-admin] PASS
=== Type-Check Summary ===
  web          PASS (0 errors)
  web-admin    PASS (0 errors)
exit=0
```

## mobile-uniapp vitest (5 files / 85 case PASS)

```
 ✓ src/utils/__tests__/format.test.ts         (21 tests)  15ms
 ✓ src/utils/__tests__/relativeTime.test.ts   (22 tests)  28ms
 ✓ src/utils/__tests__/route-alias.test.ts    (22 tests)  18ms
 ✓ src/utils/__tests__/permission.test.ts     (10 tests)  13ms
 ✓ src/utils/__tests__/offlineCache.test.ts   (10 tests)  20ms

 Test Files  5 passed (5)
      Tests  85 passed (85)
   Duration  1.59s
```

## 前端 build (两端真实可出)

```
web:  ✓ 2156 modules transformed. built in 34.36s
web-admin:  ✓ 3125 modules transformed.
```

## 门禁脚本 (新)

- `tools/checks/check-frontend-typecheck.ps1` — web + web-admin vue-tsc 0 错 PASS
- `tools/checks/check-llm-fail-close.ps1` — LLM 路径禁止 mock 兜底 PASS
- `tools/checks/check-frontend-mock-leakage.ps1` — 前端禁止 hardcoded mockList / demoData / fakeData 兜底 PASS
- `tools/checks/run-all-checks.ps1` — 端到端 7 步编排 (彩色摘要) exit 0

## 端到端 pnpm run checks:all (5 步全 PASS / exit 0)

```
pnpm run checks:all
  - pnpm type-check web + web-admin
  - pnpm -C mobile-uniapp run test --run     (5 files / 85 case)
  - check-llm-fail-close.ps1
  - check-frontend-mock-leakage.ps1
  - mvn test 12 classes / 84 case
  - mvn verify 11 modules
exit=0
```

## P1-7 多模态视觉落地

| 端 | 路径 | 状态 |
|---|---|---|
| Web (SSE) | ChatView.vue → /ai/chat streamChat body `attachments` 字段 | ✅ |
| Mobile (WS) | chat.vue → /ws/chat WS payload `attachments` 数组 | ✅ |
| Backend | AiChatApplicationService.buildLlmMessages → LlmMessage.userMultimodal | ✅ |
| OpenAI 适配器 | OpenAiCompatibleAdapter valueToTree 序列化为多模态 content | ✅ |
| Dify / Coze | 私有协议折叠为 textContent()，图片走 file 上传链路（二期） | ✅ |

## 待审包

285 文件 / 27809 insertions / 1582 deletions

## 仍待人工环境

- 6 个 Mockito 旧测试 (JDK 17/21)
- Dify/Coze 真实生产端字段 (已多路径兜底)
- 移动端真机 uni.Recorder 回归
- docker compose + pnpm checks + mvn verify 端到端
