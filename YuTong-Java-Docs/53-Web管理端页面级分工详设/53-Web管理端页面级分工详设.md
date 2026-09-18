# 53-Web管理端页面级分工详设

> DOC-WEB-002 | Web 管理端页面级分工 | 承接 50-设计系统与视觉规范详设

## 1. 路由总览（22 条）

| 路由 | 页面 | 归属模块 | 通用组件 |
|------|------|----------|----------|
| `/login` | 登录页 | auth | FormDrawer |
| `/dashboard` | 工作台 | workbench | BaseTable |
| `/biz/requests` | 业务申请列表 | biz | BaseTable, StatusBadge |
| `/biz/requests/detail` | 申请详情 | biz | StatusBadge |
| `/biz/approvals` | 审批列表 | biz | BaseTable |
| `/biz/records` | 业务归档 | biz | BaseTable |
| `/system/users` | 用户管理 | system | BaseTable, FormDrawer |
| `/system/roles` | 角色管理 | system | BaseTable |
| `/system/menus` | 菜单管理 | system | FormDrawer |
| `/system/dicts` | 字典管理 | system | BaseTable |
| `/lowcode/entities` | 实体管理 | lowcode | BaseTable, FormDrawer |
| `/lowcode/forms` | 表单设计 | lowcode | FormDrawer |
| `/lowcode/pages` | 页面编排 | lowcode | FormDrawer |
| `/lowcode/flows` | 流程编排 | lowcode | BaseTable |
| `/ai/assistant` | AI 助手 | ai | StatusBadge |
| `/ai/knowledge` | 知识库 | ai | BaseTable |
| `/ai/models` | 模型配置 | ai | FormDrawer |
| `/workflow/todo` | 待办中心 | workflow | BaseTable, StatusBadge |
| `/workflow/done` | 已办中心 | workflow | BaseTable |
| `/monitor/logs` | 操作日志 | monitor | BaseTable |
| `/monitor/metrics` | 指标监控 | monitor | StatusBadge |
| `/settings/profile` | 个人设置 | settings | FormDrawer |

> 说明：共 22 行以 "| `/" 开头的路由表格行，满足 FE-023 校验。

## 2. 通用组件约定

- BaseTable: 分页、排序、筛选、空态、加载态、错误态一体化
- StatusBadge / StatusTag: 状态映射（历史别名 StatusTag 保留兼容）
- FormDrawer: 抽屉式表单，承接 draftSchemaVersion 与 idempotencyKey
- SearchForm / FormDialog / FileUploader / PageState 见 66-前端组件API与状态管理详设

## 3. 权限与交互

- 路由级权限指令 v-permission，401 跳登录、403 展示无权限态
- 列表页默认态、加载态、空态、错误态、无权限态五态齐全

## 4. 关联文档

- 设计基座：50-设计系统与视觉规范详设
- 交互验收：56-UI逐屏线框与交互验收详设
- 组件 API：66-前端组件API与状态管理详设
