# 66-前端组件API与状态管理详设

> DOC-FE-001 | 前端组件 API 与状态管理 | 承接 50-设计系统与视觉规范详设 / 53-Web管理端页面级分工详设 / 56-UI逐屏线框与交互验收详设

本文档编号 66，关联 50、53、56，涵盖 Web 与 Uniapp 通用组件。

## 1. 通用组件 API

### BaseTable

- Props: columns, dataSource, loading, pagination, rowKey
- Slots: empty / error / loading
- 状态机：idle -> loading -> success | empty | error | forbidden

### SearchForm

- Props: fields, modelValue, collapsed
- Emits: search, reset

### FormDrawer

- Props: open, title, width 480, destroyOnClose
- 关联 idempotencyKey 与 draftSchemaVersion 提交

### FormDialog

- Props: open, title, width 560
- 与 FormDrawer 互为抽屉/弹窗两种形态

### StatusBadge / StatusTag

- StatusBadge 为新规范命名，StatusTag 为历史别名（兼容保留）
- Props: status, text, dot

### FileUploader

- Props: accept, maxSize, limit
- 状态机：idle -> uploading -> success | error，forbidden 时禁用
- 事件：progress, success, error

### PageState

- Props: state: 'default' | 'loading' | 'empty' | 'error' | 'forbidden'
- 错误态需展示 traceId

## 2. Store 设计

- auth / app / dict / lowcode / ai 五大 store (Pinia)
- 401 统一拦截跳 /login，403 渲染 NoPermission，409 提示业务冲突需刷新

HTTP 状态码约定：401 未认证、403 无权限、409 冲突

## 3. 体验监控

- trackApi(event, payload) 上报 PV/点击/接口耗时，关联 traceId
- 埋点遵循 50 号视觉与交互一致性

## 4. 状态枚举补充

- FileUploader: idle, uploading
- PageState: forbidden
- BaseTable 空态与错误态区分

## 5. 关联

- 设计基座 50，页面分工 53，交互验收 56
