# YuTong Demo 环境操作手册

> 设计来源: 68-演示环境与样例数据剧本详设
> 落地批次: GA2-L178
> 维护者: 平台团队

## 1. Demo 账号体系

| 账号 | 密码 | 角色 | Mock 用户类型 | 数据范围 | 能力 |
| --- | --- | --- | --- | --- | --- |
| `admin_demo` | 环境变量 `YUTONG_DEMO_PASSWORD` (默认 `demo123`) | ADMIN | admin | ALL | 全部演示能力 |
| `reviewer_demo` | 同上 | APPROVER | approver | CUSTOM (白名单) | 待办审核 |
| `user_demo` | 同上 | BIZ_USER | biz | SELF (仅本人) | 创建申请单 |
| `viewer_demo` | 同上 | VIEWER | viewer | TENANT (租户内 + 脱敏) | 查看报表/文档 |

**登录方式**:
```bash
POST /api/v1/auth/login
Content-Type: application/json

{"username":"admin_demo","password":"demo123"}
```

**登录响应**:
```json
{
  "code": 0,
  "data": {
    "token": "mock-token-...",
    "userId": "01MOCKUSER0000000000000ADMIN",
    "username": "admin",
    "tenantId": "default",
    "mock": true,
    "mockUserType": "admin"
  }
}
```

**后续请求**:
- 携带 `Authorization: Bearer <token>` 头
- 携带 `X-Mock-User: <mockUserType>` 头（值为登录响应的 `mockUserType`，admin/approver/biz/viewer）

## 2. 样例数据包

| 数据 | 数量 | 说明 |
| --- | --- | --- |
| 客户 | 20 | 覆盖启用/禁用 (3 sample + 17 demo) |
| 商品 | 50 | 不同单位和价格 (4 sample + 46 demo) |
| 申请单 | 100 | 分布在 DRAFT/SUBMITTED/APPROVED/REJECTED/ARCHIVED |
| 申请单明细 | 100 | 每个申请单 1 条明细 |
| 站内消息 | 50 | 未读/已读混合 |
| 低代码实体 | 3 | 项目(PUBLISHED) + 合同(DRAFT) + 工单(DRAFT) |
| AI 供应商 | 2 | 本地 Mock + OpenAI 兼容 |
| AI Prompt 模板 | 2 | 客户问答 + 商品推荐 |
| AI 知识库 | 1 | 平台常见问题 |
| AI 文档 | 2 | 快速入门 + FAQ |
| AI 文档分块 | 4 | 供 RAG 向量检索测试 |

**ID 约定**: 所有 Demo 数据 ID 以 `01JYYDEMO` 前缀标识，便于演示识别和重置清理。

## 3. 数据重置

### 一键重置

```powershell
.\tools\demo\reset-demo.ps1
```

**重置流程** (6 步):
1. 验证后端健康状态
2. Flyway repeatable migration 重置 Demo 种子数据 (清理 `01JYYDEMO%` 旧数据 + 重跑 `R__seed_demo_data.sql`)
3. 验证 4 类 Demo 账号登录
4. 验证密码错误拒绝 (安全边界)
5. 验证核心数据量 (20 客户/50 商品/100 申请单/50 消息/3 低代码实体)
6. 输出 Demo 账号信息

**自定义参数**:
```powershell
.\tools\demo\reset-demo.ps1 -BackendHost localhost -BackendPort 8082 -DemoPassword "custom-password"
```

### 手动重置

如需手动重置，直接在 PostgreSQL 执行:
```sql
-- 清理 Demo 数据
DELETE FROM biz_customer WHERE id LIKE '01JYYDEMO%';
DELETE FROM biz_product WHERE id LIKE '01JYYDEMO%';
-- ... 其他表同理

-- 重置 Flyway checksum 强制 R__ 脚本重跑
UPDATE flyway_schema_history SET checksum = NULL WHERE script = 'R__seed_demo_data.sql';
```
然后重启后端触发 Flyway 重跑。

### 每日自动重置

68 号文档要求"每日凌晨重置数据库到种子快照"。生产 Demo 环境应配置定时任务:
```bash
# crontab (每日 03:00 重置)
0 3 * * * /path/to/reset-demo.ps1 >> /var/log/yutong-demo-reset.log 2>&1
```

## 4. 演示剧本

### 剧本 1: 技术底座总览 (5 分钟)

**目标**: 让观众快速理解平台核心价值。

| 步骤 | 操作 | 预期展示 |
| --- | --- | --- |
| 1 | 使用 `admin_demo` 登录 Web 管理端 | 工作台展示指标卡片 |
| 2 | 查看工作台指标与待办 | 客户数 20、申请单数 100、待办数、未读消息 |
| 3 | 打开申请单列表，展示状态筛选 | 5 种状态分布 (DRAFT/SUBMITTED/APPROVED/REJECTED/ARCHIVED) |
| 4 | 切换 `reviewer_demo` 账号，打开待办 | 审批待办列表 |
| 5 | 完成一次审批操作 | 状态流转 + 操作日志 |

### 剧本 2: 低代码生成 (8 分钟)

**目标**: 展示低代码平台快速建模能力。

| 步骤 | 操作 | 预期展示 |
| --- | --- | --- |
| 1 | 使用 `admin_demo` 打开实体设计器 | 3 个低代码实体 (项目/合同/工单) |
| 2 | 使用 AI 建议生成"问卷表单"字段草稿 | AI 生成字段建议 |
| 3 | 进入页面设计器拖拽列表/表单 | 可视化设计器 |
| 4 | 预览页面 | 生成的页面预览 |
| 5 | 创建代码生成任务，查看 diff | 代码生成 + diff 对比 |

### 剧本 3: AI 助手 (5 分钟)

**目标**: 展示 AI 助手 RAG 问答能力。

| 步骤 | 操作 | 预期展示 |
| --- | --- | --- |
| 1 | 使用 `user_demo` 提问"申请单状态机有哪些状态" | AI 回答带引用来源 |
| 2 | 查看引用来源 | 引用知识库文档分块 |
| 3 | 让 AI 生成页面草稿 | AI 生成草稿进入草稿区 |
| 4 | 查看人工确认和审计日志 | 操作审计记录 |

### 剧本 4: 运维与安全 (5 分钟)

**目标**: 展示平台可观测性与安全边界。

| 步骤 | 操作 | 预期展示 |
| --- | --- | --- |
| 1 | 使用 `admin_demo` 查看服务健康页 | 8 组件健康状态 |
| 2 | 查看缓存监控页 | 4 缓存概览 + 白名单清理 |
| 3 | 切换 `viewer_demo` 尝试清理缓存 | 403 权限拒绝 |
| 4 | 触发一次导出任务 | 任务指标和日志 traceId |
| 5 | 展示操作审计与 AI 工具日志 | 审计日志列表 |

## 5. 安全边界

- Demo 账号权限固定，禁止修改系统密钥
- 文件上传限制 5MB
- AI 工具只允许查询和生成草稿
- Grafana 只读
- OpenAPI 仅展示 Demo 环境
- Demo 环境禁止连接真实短信/邮件/支付

## 6. 验收标准

- [x] 任一新用户按剧本可在 30 分钟内看完整个平台价值
- [x] Demo 数据可重置且不污染真实环境
- [x] Web、低代码、AI、运维至少各有一个可演示闭环
- [x] 4 类 Demo 账号 username/password 登录可用
- [x] 样例数据量达到 68 号文档要求 (20/50/100/50/3)
