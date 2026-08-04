/**
 * GA2-L183: AI 助手 (AiChat) 核心 GA 路由 E2E 测试。
 *
 * 设计来源:
 *  - 59-测试矩阵与验收用例详设 (Web E2E 章节: /ai/assistant 路由覆盖)
 *  - web-admin/src/views/ai/AiChat.vue (AI 助手页面: 会话列表 + 消息区 + 输入区 + SSE 流式)
 *
 * 覆盖场景:
 *  - 访问 /ai/assistant: AI 助手页面可见、会话列表可见、输入框可见
 *  - 发送消息 "你好": 用户消息气泡出现, 等待 AI 回复气泡 (正常回复或错误气泡均算 PASS, 证明交互链路通)
 *
 * 前置条件:
 *  - 路由 meta.licenseRequired='ai', 需 licenseStore.isModuleActive('ai') 通过
 *  - 路由 meta.permissions: 需 'ai:assistant:use'
 *  - admin_mock 用户具备全部权限, dev/local profile 下 License 模块默认全部激活
 *
 * SSE 交互链路 (AiChat.vue handleSend):
 *  1. 填写 inputMessage → 点击 "发送" (el-button aria-label="发送消息")
 *  2. 乐观追加 USER 消息 (role='user', class msg-user, aria-label="我的消息")
 *  3. 创建 ASSISTANT 占位消息 (role='assistant', class msg-ai, streaming=true)
 *  4. streamChat SSE 流: meta → citation* → delta+ → done | error
 *  5. 正常回复: delta 逐字追加 content → msg-content 非空
 *     错误回复: catch 块追加 "[错误] ..." → msg-content 非空
 *  两种情况均证明交互链路通 → PASS
 */
import { test, expect } from '@playwright/test'
import { loginAs } from './helpers/auth'

test.describe('GA2-L183 AI 助手核心路由', () => {
  test.describe.configure({ mode: 'serial' })

  test('访问 /ai/assistant 页面/会话列表/输入框可见', async ({ page }) => {
    await loginAs(page, 'admin_demo', 'demo123', 'admin')
    await page.goto('/ai/assistant')
    await page.waitForLoadState('networkidle')

    // 验证 AI 会话列表可见 (AiChat.vue el-card aria-label="AI 会话列表", header 含 "会话列表" 文本)
    await expect(page.getByText('会话列表', { exact: true })).toBeVisible({ timeout: 10_000 })

    // 验证消息输入框可见 (el-input type=textarea placeholder="输入消息... (Ctrl+Enter 发送)")
    await expect(page.getByPlaceholder(/输入消息/)).toBeVisible({ timeout: 10_000 })

    // 验证发送按钮可见 (el-button aria-label="发送消息", 文本 "发送")
    await expect(page.getByRole('button', { name: '发送消息' })).toBeVisible()
  })

  test('发送消息 "你好" 验证交互链路 (用户气泡 + AI 回复气泡)', async ({ page }) => {
    await loginAs(page, 'admin_demo', 'demo123', 'admin')
    await page.goto('/ai/assistant')
    await page.waitForLoadState('networkidle')

    // 等待输入框就绪
    const input = page.getByPlaceholder(/输入消息/)
    await expect(input).toBeVisible({ timeout: 10_000 })

    // 填写消息并发送
    await input.fill('你好')
    // 发送按钮 (aria-label="发送消息"), 填写后 :disabled="!inputMessage.trim()" 解除
    await page.getByRole('button', { name: '发送消息' }).click()

    // 验证用户消息气泡出现 (msg-user 区域含 "你好" 文本)
    // AiChat.vue handleSend 乐观追加 USER 消息, content=msg
    await expect(page.locator('.msg-user')).toContainText('你好', { timeout: 10_000 })

    // 等待 AI 回复气泡出现内容 (正常回复: delta 追加文本; 错误回复: catch 追加 "[错误]..." — 均算 PASS)
    // AiChat.vue 先创建 ASSISTANT 占位 (content=''), 流式/错误后 content 变为非空
    const aiReplyContent = page.locator('.msg-ai .msg-content').last()
    await expect(aiReplyContent).not.toBeEmpty({ timeout: 30_000 })
  })
})
