$ErrorActionPreference = 'Stop'
Set-Location 'D:\MyCode\YuTong-Java-Vue'

# 拿真路径（不被 PowerShell pipeline 改写）
$paths = (git diff --cached -z --name-only) -split "`0" | Where-Object { $_ -ne '' }
$total = $paths.Count
Write-Host ("total staged = $total")

# 分批规则
$batches = [ordered]@{}
$batches['B1'] = [pscustomobject]@{
    Title = '仓库基线 — agents/design/checks/前端骨架'
    Patterns = @(
        'AGENTS.md','DESIGN.md','STATUS.md','package.json','pnpm-workspace.yaml','pnpm-lock.yaml','.gitignore',
        'YuTong-Java-Docs/','design-systems/','build/',
        '.agents/skills/README.md','.agents/skills/yutong-design-tokens/',
        '.agents/docs/CONTEXT.md','.agents/docs/EVOLUTION.md','.agents/memory/',
        'mobile-uniapp/src/manifest.json','mobile-uniapp/src/pages.json',
        'web-admin/package.json','web-admin/src/layouts/DefaultLayout.vue','web-admin/src/router/index.ts',
        'web-admin/src/components.d.ts',
        'web/index.html','web/package.json','web/src/App.vue','web/src/main.ts','web/src/router/index.ts',
        'web/src/auto-imports.d.ts','web/src/components.d.ts','web/src/vite-env.d.ts',
        'web/src/styles/','web/src/api/client.ts',
        'web/tsconfig.json','web/tsconfig.node.json','web/vite.config.ts'
    )
    Extras = @()
    Msg = 'chore: bootstrap YuTong 仓库基线 — agents/design/checks/前端骨架'
}
$batches['B2'] = [pscustomobject]@{
    Title = 'AI 网关 / Provider'
    Patterns = @(
        'backend/yutong-ai-service/src/main/java/com/yutong/ai/gateway/',
        'backend/yutong-ai-service/src/main/java/com/yutong/ai/chat/service/llm/',
        'database/migrations/V035','database/migrations/V036','database/migrations/V043',
        'web-admin/src/api/aiProviders.ts','web-admin/src/api/types.ts','web-admin/src/views/ai/AiProviders.vue',
        'backend/yutong-common/src/main/java/com/yutong/common/errorcode/',
        'backend/yutong-common/src/main/resources/i18n/',
        'backend/yutong-boot/src/main/resources/application-local.yml',
        'backend/yutong-auth-adapter/src/main/java/com/yutong/auth/',
        'backend/pom.xml','backend/yutong-ai-service/pom.xml'
    )
    Extras = @('backend/yutong-ai-service/src/test/java/com/yutong/ai/gateway/service/AiProviderHealthServiceTest.java')
    Msg = 'feat(ai-gateway): AI Provider 注册 / 路由选择 / 健康检查 + LLM 适配器抽象 + Dify/Coze 接入'
}
$batches['B3'] = [pscustomobject]@{
    Title = '知识库 RAG'
    Patterns = @(
        'backend/yutong-ai-service/src/main/java/com/yutong/ai/rag/',
        'database/migrations/V037','database/migrations/V042',
        'backend/yutong-system-service/src/main/java/com/yutong/system/file/',
        'web-admin/src/api/knowledge.ts','web-admin/src/views/knowledge/',
        'web/src/views/KnowledgeView.vue','mobile-uniapp/src/pages/knowledge/'
    )
    Extras = @('backend/yutong-ai-service/src/test/java/com/yutong/ai/rag/service/GraphExtractionParserTest.java')
    Msg = 'feat(rag): 知识库 RAG — 7 类文档解析 + pgvector + 混合检索 + 重排序 + 知识图谱'
}
$batches['B4'] = [pscustomobject]@{
    Title = 'MCP 工具 + 技能市场'
    Patterns = @(
        'backend/yutong-ai-service/src/main/java/com/yutong/ai/tool/',
        'backend/yutong-ai-service/src/main/java/com/yutong/ai/skill/',
        'backend/yutong-ai-service/src/main/resources/skills/',
        'database/migrations/V038','database/migrations/V044',
        'web-admin/src/api/mcpMarket.ts','web-admin/src/api/skills.ts',
        'web-admin/src/views/mcp/','web-admin/src/views/skill/'
    )
    Extras = @('backend/yutong-ai-service/src/test/java/com/yutong/ai/tool/service/McpMarketInstallParseTest.java')
    Msg = 'feat(ai-mcp-skill): MCP 市场 + 技能注册 + docx/pdf/xlsx 内置技能'
}
$batches['B5'] = [pscustomobject]@{
    Title = '智能体 + 记忆 + 追踪'
    Patterns = @(
        'backend/yutong-ai-service/src/main/java/com/yutong/ai/agent/',
        'backend/yutong-ai-service/src/main/java/com/yutong/ai/memory/',
        'backend/yutong-ai-service/src/main/java/com/yutong/ai/trace/',
        'database/migrations/V039',
        'web-admin/src/api/memory.ts','web-admin/src/api/trace.ts',
        'web-admin/src/views/agent/','web-admin/src/views/memory/','web-admin/src/views/trace/'
    )
    Extras = @(
        'backend/yutong-ai-service/src/test/java/com/yutong/ai/agent/service/ReActDecisionParseTest.java',
        'backend/yutong-ai-service/src/test/java/com/yutong/ai/memory/service/AiMemoryServiceTest.java'
    )
    Msg = 'feat(ai-agent-memory-trace): ReAct 智能体 + Supervisor + 长期记忆 + 全链路追踪'
}
$batches['B6'] = [pscustomobject]@{
    Title = '工作流 + Copilot + 短剧 + 多模态生成'
    Patterns = @(
        'backend/yutong-ai-service/src/main/java/com/yutong/ai/aiflow/',
        'backend/yutong-ai-service/src/main/java/com/yutong/ai/copilot/',
        'backend/yutong-ai-service/src/main/java/com/yutong/ai/drama/',
        'backend/yutong-ai-service/src/main/java/com/yutong/ai/media/',
        'backend/yutong-lowcode-service/src/main/java/com/yutong/lowcode/copilot/',
        'backend/yutong-sample-service/src/main/java/com/yutong/sample/drama/',
        'database/migrations/V040','database/migrations/V041','database/migrations/V045','database/migrations/V046',
        'web-admin/src/api/aiflow.ts','web-admin/src/api/copilot.ts','web-admin/src/api/media.ts',
        'web-admin/src/views/aiflow/','web-admin/src/views/copilot/',
        'web-admin/src/views/drama/','web-admin/src/views/media/',
        'web/src/views/DramaView.vue','web/src/views/StoreView.vue'
    )
    Extras = @('backend/yutong-ai-service/src/test/java/com/yutong/ai/aiflow/service/AiflowEngineCycleTest.java')
    Msg = 'feat(aiflow-drama-media-copilot): 可视化工作流 + 短剧 7 表 + 多模态生成 + Copilot Harness'
}
$batches['B7'] = [pscustomobject]@{
    Title = 'P1-7 多模态视觉'
    Patterns = @(
        'backend/yutong-ai-service/src/main/java/com/yutong/ai/chat/ws/',
        'backend/yutong-ai-service/src/main/java/com/yutong/ai/chat/dto/ChatAttachment.java',
        'backend/yutong-ai-service/src/main/java/com/yutong/ai/chat/dto/AiChatRequest.java',
        'backend/yutong-ai-service/src/main/java/com/yutong/ai/chat/service/AiChatApplicationService.java',
        'backend/yutong-ai-service/src/main/java/com/yutong/ai/chat/service/ChatStreamListener.java',
        'web/src/views/ChatView.vue','mobile-uniapp/src/pages/ai/chat.vue',
        'web-admin/src/views/ai/AiChat.vue',
        'web-admin/src/components/StreamingMessage.vue',
        'web/src/components/StreamingMessage.vue'
    )
    Extras = @(
        'backend/yutong-ai-service/src/test/java/com/yutong/ai/chat/service/AiChatApplicationServiceTest.java',
        'backend/yutong-ai-service/src/test/java/com/yutong/ai/chat/ws/AiChatWebSocketHandlerTest.java',
        'backend/yutong-ai-service/src/test/java/com/yutong/ai/chat/service/llm/LlmMessageMultimodalTest.java',
        'backend/yutong-ai-service/src/test/java/com/yutong/ai/chat/service/llm/OpenAiCompatibleAdapterMultimodalTest.java',
        'backend/yutong-ai-service/src/test/java/com/yutong/ai/chat/service/llm/CozeAdapterParseTest.java'
    )
    Msg = 'feat(multimodal-vision): P1-7 图片对话全栈真落地 — LlmMessage 多模态 content + OpenAI 兼容真序列化 + 三端 attachments 透传'
}
$batches['B8'] = [pscustomobject]@{
    Title = '4 门禁 + 端到端编排'
    Patterns = @('tools/checks/')
    Extras = @()
    Msg = 'chore(checks): 4 个 PowerShell 门禁 + 7 步端到端编排 (frontend-typecheck/llm-fail-close/frontend-mock-leakage/run-all-checks)'
}
$batches['B9'] = [pscustomobject]@{
    Title = '发布证据 / 状态板 / 决策记录 / Commit 配方'
    Patterns = @(
        'release-evidence/','.agents/adr/',
        '.agents/docs/FINAL_AUDIT_2026-09-01.md','.agents/docs/HANDOFF_2026-09-01.md',
        '.agents/docs/COMMIT_RECIPE_2026-09-02.md',
        '.agents/skills/yutong-checks/SKILL.md'
    )
    Extras = @()
    Msg = 'chore(release): P0+P1 全 7 项证据 + 3 个 ADR + 4 文档 + COMMIT 配方'
}

# 分类
$covered = @()
$batchMatches = [ordered]@{}
foreach ($k in $batches.Keys) {
    $b = $batches[$k]
    $m = @()
    foreach ($p in $b.Patterns) {
        $m += $paths | Where-Object { $_ -like "$p*" }
    }
    foreach ($e in $b.Extras) {
        if ($paths -contains $e) { $m += $e }
    }
    $m = $m | Select-Object -Unique | Sort-Object
    $batchMatches[$k] = $m
    $covered += $m
}
$covered = $covered | Select-Object -Unique
$left = $paths | Where-Object { $_ -notin $covered } | Sort-Object

# 输出 recipe
$sb = [System.Text.StringBuilder]::new()
[void]$sb.AppendLine('# YuTong × 业界同类实现 AI 对标 — 分批 Commit 配方')
[void]$sb.AppendLine('')
[void]$sb.AppendLine("> 生成于 $(Get-Date -Format 'yyyy-MM-dd HH:mm') · 共 $total 文件 · 9 批")
[void]$sb.AppendLine('> 用法：每批执行 `git add <file>` × N + `git commit -m "..."` 后再下一批')
[void]$sb.AppendLine('> 依赖顺序：B1 → B2-B7（按域）→ B8 → B9')
[void]$sb.AppendLine('> 命令兼容 bash / PowerShell / Git Bash（每行一个 git add，无多行续行）')
[void]$sb.AppendLine('')

foreach ($k in $batches.Keys) {
    $b = $batches[$k]
    $m = $batchMatches[$k]
    [void]$sb.AppendLine("## $k — $($b.Title)")
    [void]$sb.AppendLine('')
    [void]$sb.AppendLine("**$($m.Count) 文件** · commit msg: ``$($b.Msg)``")
    [void]$sb.AppendLine('')
    [void]$sb.AppendLine('```bash')
    if ($k -eq 'B1') {
        [void]$sb.AppendLine("git reset HEAD -- .  # 仅 B1 需要：清掉当前 291 个 staged")
    }
    # 每行一个 git add（bash / PowerShell / Git Bash 全兼容）
    $fileList = ($m | ForEach-Object { "git add $_" }) -join "`n"
    [void]$sb.AppendLine($fileList)
    [void]$sb.AppendLine("git commit -m `"$($b.Msg)`"")
    [void]$sb.AppendLine('```')
    [void]$sb.AppendLine('')
}

[void]$sb.AppendLine('---')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('## 合计')
[void]$sb.AppendLine('')
[void]$sb.AppendLine(("- B1~B9 已分批: **$($covered.Count) 文件**"))
[void]$sb.AppendLine(("- 未归类: **$($left.Count) 文件**"))
[void]$sb.AppendLine('')

if ($left.Count -gt 0) {
    [void]$sb.AppendLine('### 未归类文件清单（人工核对后再定）')
    [void]$sb.AppendLine('')
    foreach ($f in $left) {
        [void]$sb.AppendLine("- ``$f``")
    }
    [void]$sb.AppendLine('')
}

[void]$sb.AppendLine('## 注意事项')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('1. **B1 必须先 commit**（其他批可能 import / 引用 AGENTS.md / DESIGN.md / 设计 token）')
[void]$sb.AppendLine('2. **B1 里那行 `git reset HEAD -- .` 仅 B1 一次需要**（清掉当前 291 个 staged）')
[void]$sb.AppendLine('3. 每次 commit 后跑 `git diff --cached --stat` 应为 0 改动；如非 0，说明前面 `git reset` 没生效')
[void]$sb.AppendLine('4. 每批 commit 前可先跑 `pnpm run checks:all` 确保不破门禁')
[void]$sb.AppendLine('5. 推荐推送顺序：B1 → B2-B7 顺序合并 → B8 → B9；或每个 feat/ 一个 PR')
[void]$sb.AppendLine('6. 推送前确认 docker compose / docker 镜像 / CI 绿')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('## 风险点（不在本批范围）')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('- yutong-boot ContractTest 25 个 pre-existing Auth 403 Failure — 需后续 PR 修')
[void]$sb.AppendLine('- 6 个 Mockito 旧测试在 Azul JDK 25 偶发 ByteBuddyAgent 失败 — 需 JDK 17/21 回归')
[void]$sb.AppendLine('- Dify/Coze 真实生产端字段细节 — 已多路径兜底，需真实环境联调')
[void]$sb.AppendLine('- 移动端真机 uni.Recorder 回归 — H5 MediaRecorder 已通过，真机未测')
[void]$sb.AppendLine('')

$content = $sb.ToString()
$content | Out-File -FilePath '.agents/docs/COMMIT_RECIPE_2026-09-02.md' -Encoding utf8 -NoNewline

Write-Host ("recipe written: $($content.Length) bytes")
Write-Host ("B1~B9 covered = $($covered.Count)")
Write-Host ("unclassified = $($left.Count)")
