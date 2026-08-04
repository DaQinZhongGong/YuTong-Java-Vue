import pathlib

p = pathlib.Path(r'd:\\MyCode\\YuTong-Java-Vue\\YuTong-Java-Docs\\23-设计到落地追踪记录\\23-设计到落地追踪记录.md')
rows = """| 2026-07-25 | v1.0 GA2 Web Playwright E2E 环境修复与复验 | 修复 E2E 运行环境并复验前端质量基线，19/19 PASS（耗时 2.4 分钟） | （1）问题诊断：原 `web-admin` 使用 npm 安装产生独立的 `playwright` 实例，与 `@playwright/test` 依赖的 .pnpm 实例冲突，导致 `test.describe() called here` 错误；配置文件 `playwright.config.ts` 被误移除为 `.bak`；（2）环境修复：安装 `pnpm@11.10.0`，删除 `web-admin/node_modules` 与 `web-admin/package-lock.json`，根目录运行 `pnpm install --no-frozen-lockfile` 统一依赖树；恢复 `playwright.config.ts`；删除临时调试文件 `smoke-js.spec.js`、`smoke-esm.spec.js`、`ts-import-test.ts`、`ts-test.ts`、`.bak` 配置；恢复被篡改的 `playwright/lib/globals.js`；（3）稳定性加固：新增 `e2e/global-setup.ts` 在测试前完成真实登录并进入 dashboard，触发 Vite 完成 Element Plus 按需依赖预构建；`playwright.config.ts` webServer 命令追加 `--force` 避免过期优化缓存；（4）更新 `web-admin/package.json` `@playwright/test` 版本为 `^1.61.1` 与 lock 文件对齐；（5）复验结果：`npx playwright test --reporter=list --retries=0` 19/19 PASS，覆盖认证流程 4 用例、客户 CRUD 5 用例、权限控制 4 用例、表单校验 5 用例、smoke 1 用例。已知偏差：DEV-L191-001 首轮运行因 Vite 按需优化触发页面 reload 导致 auth 首用例超时，已通过 global-setup 预热解决；DEV-L191-002 当前 Node 版本 v22.23.1 低于 engines 声明的 >=24，pnpm 仅提示 WARN 不影响运行；DEV-L191-003 命令退出码受 TRAE 沙箱访问 NVIDIA 目录限制显示非零，但 Playwright 报告 19 passed。验收结论：**PASS**。 | `59-测试矩阵与验收用例详设`、`91-Web基础后台逐页交互详设`、`82-环境变量与密钥管理规范详设`、`23` |
"""
with p.open('a', encoding='utf-8') as f:
    f.write(rows)
print('Appended', len(rows), 'chars')
