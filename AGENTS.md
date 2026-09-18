# AGENTS.md — YuTong 雨桐 · AI 协作总宪章

> 适用范围：本文件是所有 AI Agent（Mavis / Claude Code / Codex / Cursor / 任意兼容 Agent）在本仓库的**最高优先级协作契约**。未读本文件不得开始任何代码改动。

---

## 1. 项目一句话

**YuTong 雨桐**：商业级全栈技术底座 — `Java 25 + Spring Boot 4 + PostgreSQL 18 + Vue 3.5 + UniApp + AI`，以 `yutong-boot 单体启动器`为 GA 基线，预埋网关/低代码/AI/工作流扩展。

- 仓库根：`D:\MyCode\YuTong-Java-Vue`
- Node 24 + pnpm 10 + JDK 25 + Maven 3.9
- 本地依赖一键起：`docker compose -f deploy/docker-compose.boot.yml up -d`（PostgreSQL/Redis/MinIO）
- 后端启动：`cd backend && mvn -pl yutong-boot -am spring-boot:run`
- 前端启动：`pnpm install && pnpm dev:web`（Web 管理端 20050 / 后端 20010）

---

## 2. 目录契约（不可自创目录）

```
backend/               # Java 多模块（yutong-common/infra/api/auth-adapter/system/sample/lowcode/ai/workflow/gateway/boot）
web-admin/             # Vue3 管理端（Element Plus + Pinia + Vite）
mobile-uniapp/         # UniApp 移动端
database/migrations/   # Flyway 迁移
database/seed/         # 种子数据
deploy/                # Docker Compose / Nginx
openapi/               # OpenAPI 契约输出
tools/checks/          # 质量门禁脚本（唯一真相源）
.agents/               # AI 协作：skills / docs / adr / 记忆
design-systems/        # 品牌设计系统（DESIGN.md + tokens.css）
release-evidence/      # 发布证据
```

**红线**：任何新文件必须落在上述目录；新增顶层目录需先写 ADR。

---

## 3. 硬约束（违反则 CI 直接失败）

| 约束 | 规则 | 校验 |
|------|------|------|
| 主键 | 统一 ULID `varchar(32)`，禁止自增 | `tools/checks/check-id-policy` |
| API 前缀 | 统一 `/api/v1`，ID 统一 `string` | `tools/checks/check-openapi-diff.sh` |
| 目录结构 | 必须通过 `check-dir-layout` | `tools/checks/check-dir-layout` |
| 密钥 | 禁止明文密钥/Token 入库 | `tools/checks/check-secrets.sh` + `.gitleaks.toml` |
| 商业授权 | 模块开关只许在 `yutong.modules.*` 与 `License.modules`，禁止在业务核代码散落 `if/else` | `check-commercial-license-trimming` |
| 错误码 | 必须注册 `ErrorCode`，禁止裸抛 | `check-error-registry` |

---

## 4. Agent 工作流（工程纪律 × 设计审美 × 可持续演进）

本项目已接入两大生态并做本地化适配：

### 4.1 工程纪律 — 来自 mattpocock/skills

> 解决四类失控：对不齐、太啰嗦、跑不通、烂成泥。

| 场景 | 调用 Skill | 说明 |
|------|-----------|------|
| 不知道做什么 | `/ask-matt` | 路由器，帮你选对 skill |
| 需求模糊 | `/grill-with-docs` | 边拷问边建 `CONTEXT.md` + ADR |
| 随便聊聊但要对齐 | `/grill-me` | 纯拷问，不写文档 |
| 拆大计划 | `/wayfinder` | 决策票据地图，逐个解决 |
| 转成可执行 spec | `/to-spec` | 把对话压成 spec |
| 拆成票据 | `/to-tickets` | 声明阻塞边的 tracer-bullet 票据 |
| 开干 | `/implement` | 驱动 `/tdd` + 闭环 `/code-review` |
| 深模块设计 | `codebase-design` | 厚功能薄接口，干净切缝 |
| 改架构 | `improve-codebase-architecture` | 扫描 deepening 机会，可视化报告 |
| 写测试 | `tdd` | 红-绿-重构，一次一纵切 |
| 修 Bug | `diagnosing-bugs` | 可复现 → 最小化 → 假设 → 埋点 → 修 → 回归 |
| 查资料 | `research` | 高可信源，引文 Markdown 落库 |
| 合并冲突 | `resolving-merge-conflicts` | 按意图逐 hunk 解决，绝不 --abort |
| 双轴 Code Review | `code-review` | Standards vs Spec 并行子 Agent |

详细规范见 `.agents/skills/README.md` 与 `.agents/docs/CONTEXT.md`。

### 4.2 设计审美 — 来自 open-design

> 让 AI 协作既有纪律又有审美：`DESIGN.md` 是品牌契约。

| 场景 | 调用 Skill | 说明 |
|------|-----------|------|
| 定品牌方向 | `design-brief` | I-Lang 结构化简报 → 具象设计 spec |
| 建品牌系统 | `reference-design-contract` | 参考图/URL → 可复用 `DESIGN.md` |
| 抽品牌 | `brand-extract` | 从线上站点抽取色/字/Logo 成套件 |
| 出品牌手册 | `brand-guidelines` / `brandkit` | 高端品牌手册/Logo 系统 |
| 做页面 | `frontend-design` | 生产级前端界面（Vue/React） |
| 做海报/静态视觉 | `canvas-design` | 印刷级海报/插画 |
| 主题工厂 | `theme-factory` | 10 套专业配色+字体主题 |
| 升级旧站 | `redesign-skill` | 审计旧站，拔掉 AI 味，高端重塑 |
| 设计评审 | `plan-design-review` / `design-review` | 五维打分，揪 AI Slop |
| 图表可视化 | `d3-visualization` | 复杂交互图表 |

设计系统位置：`design-systems/yutong/`。任何新页面/组件必须先读 `DESIGN.md` 的 tokens，再写代码。

### 4.3 可持续演进循环

```
grill → spec → tickets → tdd → review → DESIGN check → checks 门禁 → release-evidence
  ↑                                                                │
  └──────────── CONTEXT.md / ADR / DESIGN.md / memory 持续沉淀 ──────┘
```

- 每次对话结束：更新 `CONTEXT.md` 术语表、`.agents/adr/*.md` 决策、`.agents/memory/*.md` 复盘
- 每次提交前：跑 `pnpm checks` + `tools/checks/check-frontend-visual-engineering.ps1` + 后端 `mvn verify`
- 每次发布：`tools/release/generate-manifest.sh` 生成 SBOM/证据到 `release-evidence/`

---

## 5. 编码约定

- **先读后改**：改任何文件前先 `read` 周边上下文，沿用命名/类型/框架选择。
- **不擅自扩 scope**：只做用户真正要求的事。
- **查库存在**：引入新依赖前先查 `package.json` / `pom.xml`。
- **安全第一**：不暴露、不打印 secrets。
- **引用代码**：用 `path:line` 格式。
- **Windows**：PowerShell 语法，用 `;` 而非 `&&`，读改文件优先用 Read/Write/Edit 工具（避免 GBK 破坏 UTF-8）。

---

## 6. 记忆与进化

- **Agent Memory**：`C:\Users\Administrator\.minimax\memory\main.md` — 跨项目通用教训
- **User Memory**：`C:\Users\Administrator\.minimax\memory\user.md` — 用户偏好（本项目用户：188）
- **Project Memory**：`.agents/memory/` — 本项目复盘与进化记录
- 每次发现**可复用的硬教训**（会改变下一次默认行为的），必须写入对应记忆层。

---

## 7. 关键连接

- 领域语言：`.agents/docs/CONTEXT.md`
- 品牌契约：`DESIGN.md` + `design-systems/yutong/DESIGN.md`
- 架构决策：`.agents/adr/`
- 技能索引：`.agents/skills/README.md`
- 质量门禁：`tools/checks/README.md`（若无则看 `tools/checks/` 目录）
- 发布证据：`release-evidence/`

> 最后一句话：**先对齐，再动手；先设计，再编码；每次都让项目比接手时更干净。**
