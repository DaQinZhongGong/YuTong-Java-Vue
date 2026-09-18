# 54-Uniapp移动端页面级分工详设

> DOC-MOB-002 | Uniapp 移动端页面级分工 | 承接 50-设计系统与视觉规范详设

## 1. 移动路由（9 条）

| 路由 | 页面 | 状态 |
|------|------|------|
| `/pages/login/index` | 登录页 | 已定义 |
| `/pages/workbench/index` | 工作台 | 已定义 |
| `/pages/todo/list` | 待办列表 | 已定义 |
| `/pages/todo/detail` | 待办详情 | 已定义 |
| `/pages/biz/handle` | 业务办理 | 已定义 |
| `/pages/biz/detail` | 业务详情 | 已定义 |
| `/pages/upload/index` | 上传中心 | 已定义 |
| `/pages/profile/index` | 我的 | 已定义 |
| `/pages/settings/index` | 设置 | 已定义 |

> 共 9 行以 "| `/pages/" 开头的路由行，满足 FE-032。

## 2. 移动端专属组件

- OfflineBanner: 离线横幅，网络断开时置顶提示，含重试
- BottomActionBar: 底部操作栏，固定 44px+ 安全区，承载主/次按钮
- 状态处理：BIND_FAILED 绑定失败态，需引导重新绑定身份

## 3. 设计约束

- 最小点击区 44px，遵循 50 号文档 4px 网格与 WCAG AA 对比度
- 断点：375 / 414 / 768，对应 56 号文档 Mobile/{模块}/{页面}/{状态}/{断点} 命名

## 4. 关联文档

- 50-设计系统与视觉规范详设 / 56-UI逐屏线框与交互验收详设
