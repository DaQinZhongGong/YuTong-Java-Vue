import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { getToken } from '@/utils/auth'
import { useAuthStore } from '@/stores/auth'
import { useLicenseStore } from '@/stores/license'
import { hasAnyPermission } from '@/utils/permission'
import { trackPageView } from '@/utils/tracker'

/**
 * GA2-16: 路由 meta.permissions 字段。
 * 设计来源: 96-端侧权限可见性矩阵详设 (菜单权限矩阵章节)
 *
 * 列表页使用 *:list 权限码; 详情页使用 *:detail; 历史命名 dashboard:view 例外保留。
 * 路由守卫会校验 hasAnyPermission(meta.permissions), 不通过时跳转 /403。
 * 公共路由 (meta.public=true) 不校验权限。
 *
 * GA2-L171: 新增 meta.licenseRequired 字段 (商业模块授权校验)。
 * 设计来源: 70-商业授权与版本能力裁剪详设「模块授权行为矩阵」。
 * 路由守卫会校验 licenseStore.isModuleActive(meta.licenseRequired)，不通过时跳转 /403。
 * 后端 LicenseInterceptor 是强制边界，前端路由守卫只是体验优化。
 */
declare module 'vue-router' {
  interface RouteMeta {
    /** 是否公共路由 (无需登录) */
    public?: boolean
    /** 页面标题 */
    title?: string
    /** 进入路由所需权限码 (满足任意一个即可); 未设置则只校验登录 */
    permissions?: string[]
    /** GA2-L171: 商业模块编码 (如 'lowcode'/'ai'/'report'/'workflow'/'datasource'/'plugin')。
     * 路由守卫校验 licenseStore.isModuleActive()，不通过时跳转 /403。 */
    licenseRequired?: string
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/ForbiddenView.vue'),
    meta: { public: true, title: '无权限' },
  },
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        // 96 号文档: dashboard:view 是看板页面权限例外, 不属于 *:list 规则
        meta: { title: '工作台', permissions: ['dashboard:view'] },
      },
      {
        path: 'biz/customers',
        name: 'Customers',
        component: () => import('@/views/customer/CustomerList.vue'),
        // GA2-16: 设计 96 号文档菜单矩阵使用 biz:customer:list (与 masterdata:customer:add/edit/delete 后端权限码区分)
        meta: { title: '客户管理', permissions: ['biz:customer:list'] },
      },
      {
        path: 'biz/products',
        name: 'Products',
        component: () => import('@/views/product/ProductList.vue'),
        // GA2-16: 设计 96 号文档菜单矩阵使用 biz:product:list
        meta: { title: '商品管理', permissions: ['biz:product:list'] },
      },
      {
        path: 'biz/requests',
        name: 'Requests',
        component: () => import('@/views/request/RequestList.vue'),
        meta: { title: '申请单管理', permissions: ['biz:request:list'] },
      },
      {
        // GA2-24: 新建申请单。注意放在 :id 之前, 保证 /biz/requests/create 不被 :id 匹配。
        path: 'biz/requests/create',
        name: 'RequestCreate',
        component: () => import('@/views/request/RequestForm.vue'),
        meta: { title: '新建申请单', permissions: ['biz:request:add'] },
      },
      {
        // GA2-24: 编辑申请单 (仅 DRAFT/REJECTED 状态可进入)
        path: 'biz/requests/:id/edit',
        name: 'RequestEdit',
        component: () => import('@/views/request/RequestForm.vue'),
        meta: { title: '编辑申请单', permissions: ['biz:request:edit'] },
      },
      {
        path: 'biz/requests/:id',
        name: 'RequestDetail',
        component: () => import('@/views/request/RequestDetail.vue'),
        meta: { title: '申请单详情', permissions: ['biz:request:detail'] },
      },
      {
        path: 'todos',
        name: 'Todos',
        component: () => import('@/views/todo/TodoList.vue'),
        meta: { title: '待办任务', permissions: ['system:todo:list'] },
      },
      {
        path: 'system/messages',
        name: 'Messages',
        component: () => import('@/views/message/MessageList.vue'),
        meta: { title: '站内消息', permissions: ['system:message:list'] },
      },
      {
        path: 'system/files',
        name: 'Files',
        component: () => import('@/views/file/FileList.vue'),
        meta: { title: '文件管理', permissions: ['system:file:list'] },
      },
      {
        // GA2-34: 工单中心。设计来源: 35-样例业务矩阵扩展设计 P1 工单中心
        path: 'tickets',
        name: 'Tickets',
        component: () => import('@/views/ticket/TicketList.vue'),
        meta: { title: '工单中心', permissions: ['system:todo:list'] },
      },
      {
        path: 'tickets/:id',
        name: 'TicketDetail',
        component: () => import('@/views/ticket/TicketDetail.vue'),
        meta: { title: '工单详情', permissions: ['system:todo:list'] },
      },
      {
        // GA2-35: 合同档案。设计来源: 35-样例业务矩阵扩展设计 P1 合同档案
        // 核心能力: 文件版本管理 + PG 全文检索 + 敏感字段脱敏 + 操作审计 + 归档只读
        path: 'contracts',
        name: 'Contracts',
        component: () => import('@/views/contract/ContractList.vue'),
        meta: { title: '合同档案', permissions: ['system:todo:list'] },
      },
      {
        path: 'contracts/:id',
        name: 'ContractDetail',
        component: () => import('@/views/contract/ContractDetail.vue'),
        meta: { title: '合同详情', permissions: ['system:todo:list'] },
      },
      {
        // GA2-36: 报表分析。设计来源: 35-样例业务矩阵扩展设计 P1 报表分析、42-报表与大屏可视化设计 R1
        // 验证 5 项能力: 物化视图刷新 + ECharts 图表 + 大数据量导出异步化 + AI 指标解释 + 数据权限下的报表过滤
        path: 'reports',
        name: 'Reports',
        component: () => import('@/views/report/ReportList.vue'),
        meta: { title: '报表分析', permissions: ['report:view'], licenseRequired: 'report' },
      },
      {
        path: 'reports/:code',
        name: 'ReportDetail',
        component: () => import('@/views/report/ReportDetail.vue'),
        meta: { title: '报表详情', permissions: ['report:view'], licenseRequired: 'report' },
      },
      {
        // GA2-R2: 报表设计器。设计来源: 42-报表与大屏可视化设计 R2 拖拽式报表设计器
        // 布局: 左侧组件库 + 中间画布(栅格 24 列) + 右侧属性面板 + 底部数据预览
        path: 'report-designer/:id',
        name: 'ReportDesigner',
        component: () => import('@/views/dashboard/ReportDesigner.vue'),
        meta: { title: '报表设计器', permissions: ['report:edit'], licenseRequired: 'report' },
      },
      {
        // GA2-R3: 大屏列表。设计来源: 42-报表与大屏可视化设计 R3 数据大屏
        path: 'dashboards',
        name: 'Dashboards',
        component: () => import('@/views/dashboard/DashboardList.vue'),
        meta: { title: '数据大屏', permissions: ['dashboard:view'], licenseRequired: 'report' },
      },
      {
        // GA2-R3: 大屏设计器。设计来源: 42-报表与大屏可视化设计 R3 数据大屏
        // 布局: 1920x1080 画布 + 拖拽组件 + 属性配置 + 全屏预览
        path: 'dashboard-designer/:id',
        name: 'DashboardDesigner',
        component: () => import('@/views/dashboard/DashboardDesigner.vue'),
        meta: { title: '大屏设计器', permissions: ['dashboard:edit'], licenseRequired: 'report' },
      },
      {
        // GA2-37: 库存出入库。设计来源: 35-样例业务矩阵扩展设计 P1 库存出入库
        // 验证 7 项能力: 并发扣减乐观锁 + 幂等键防重复 + 库存不足拦截 + 导入物料 + 导出库存 + 异步任务 + 异常补偿
        path: 'inventory',
        name: 'InventoryList',
        component: () => import('@/views/inventory/InventoryList.vue'),
        meta: { title: '库存余额', permissions: ['system:todo:list'] },
      },
      {
        // GA2-37: 出入库操作 (入库单/出库单/库存流水)
        path: 'inventory/in-out',
        name: 'StockInOut',
        component: () => import('@/views/inventory/StockInOut.vue'),
        meta: { title: '出入库操作', permissions: ['system:todo:list'] },
      },
      {
        // GA2-38: 外部接口同步。设计来源: 35-样例业务矩阵扩展设计 P2 外部接口同步
        // 验证 6 项能力: HTTP Client 适配 + HMAC-SHA256 签名 + 失败重试 + 幂等写入 + 死信队列 + 同步监控
        path: 'ext-sync',
        name: 'ExtSync',
        component: () => import('@/views/extsync/ExtSyncView.vue'),
        meta: { title: '外部接口同步', permissions: ['system:todo:list'] },
      },
      {
        // GA2-39: 知识库运营。设计来源: 35-样例业务矩阵扩展设计 P2 知识库运营
        // 验证 6 项能力: 文档导入 + 分块向量化 + 权限过滤 + 问答引用 + 命中率统计 + 低置信度拒答
        path: 'kb-ops',
        name: 'KnowledgeOps',
        component: () => import('@/views/knowledge/KnowledgeOps.vue'),
        meta: { title: '知识库运营', permissions: ['system:todo:list'] },
      },
      {
        // GA2-40: 实时通知。设计来源: 35-样例业务矩阵扩展设计 P2 实时通知、44-实时通信与消息推送设计
        // 验证 6 项能力: 站内信 + 未读数 + 实时推送 + 移动端订阅消息 + 消息模板 + 消息重试
        path: 'notification-ops',
        name: 'NotificationOps',
        component: () => import('@/views/notification/NotificationOps.vue'),
        meta: { title: '通知运营', permissions: ['system:todo:list'] },
      },
      {
        // GA2-41: 支付订单。设计来源: 35-样例业务矩阵扩展设计 P2 支付订单
        // 验证 6 项能力: 支付单状态机 + 第三方回调验签 + 回调幂等 + 对账文件导入 + 金额精度 + 安全审计
        path: 'payment-ops',
        name: 'PaymentOps',
        component: () => import('@/views/payment/PaymentOps.vue'),
        meta: { title: '支付订单', permissions: ['system:todo:list'] },
      },
      {
        // GA2-42: 问卷表单。设计来源: 35-样例业务矩阵扩展设计 P2 问卷表单
        // 验证 6 项能力: 动态表单渲染 + 条件显隐 + 字段校验 + 移动端填写 + 统计报表 + AI 生成题目草稿
        path: 'survey-ops',
        name: 'SurveyOps',
        component: () => import('@/views/survey/SurveyOps.vue'),
        meta: { title: '问卷表单', permissions: ['system:todo:list'] },
      },
      {
        // GA2-44: BPMN 工作流引擎。设计来源: 41-工作流与BPMN引擎设计 L1+L2 轻量自研
        // 验证 7 项能力: 流程定义 CRUD + BPMN 解析 + 流程实例启动 + 任务办理通过 + 任务驳回 + 委派转办 + 运营监控
        path: 'workflow/definitions',
        name: 'WorkflowDefinitions',
        component: () => import('@/views/workflow/WorkflowDefinitionList.vue'),
        meta: { title: '流程定义', permissions: ['system:todo:list'], licenseRequired: 'workflow' },
      },
      {
        path: 'workflow/instances',
        name: 'WorkflowInstances',
        component: () => import('@/views/workflow/WorkflowInstanceList.vue'),
        meta: { title: '流程实例', permissions: ['system:todo:list'], licenseRequired: 'workflow' },
      },
      {
        path: 'workflow/todo',
        name: 'WorkflowTodo',
        component: () => import('@/views/workflow/WorkflowTodoList.vue'),
        meta: { title: '任务待办', permissions: ['system:todo:list'], licenseRequired: 'workflow' },
      },
      {
        path: 'ai/assistant',
        name: 'AiChat',
        component: () => import('@/views/ai/AiChat.vue'),
        meta: { title: 'AI 助手', permissions: ['ai:assistant:use'], licenseRequired: 'ai' },
      },
      {
        // AI 供应商管理: 展示供应商列表与健康状态, 触发健康检查。
        // 设计来源: 13-AI能力设计 provider registry / 37-AI治理与评测设计 供应商健康检查
        // 复用 ai:assistant:use 权限码 (与 AI 助手同级, AI 模块可见即可), 商业模块 ai
        path: 'ai/providers',
        name: 'AiProviders',
        component: () => import('@/views/ai/AiProviders.vue'),
        meta: { title: '供应商管理', permissions: ['ai:assistant:use'], licenseRequired: 'ai' },
      },
      {
        // GA2-45: AI 治理与评测。设计来源: 37-AI治理与评测设计
        // 5 大能力域: Prompt 治理 / AI 工具注册 / 成本治理 / 反馈闭环 / RAG 评测
        // 复用 system:todo:list 权限码 (与 GA2-40~44 一致, 全角色可见)
        path: 'ai-governance/prompts',
        name: 'AiGovernancePrompts',
        component: () => import('@/views/ai-governance/AiGovernancePromptList.vue'),
        meta: { title: 'Prompt 治理', permissions: ['system:todo:list'], licenseRequired: 'ai' },
      },
      {
        path: 'ai-governance/tools',
        name: 'AiGovernanceTools',
        component: () => import('@/views/ai-governance/AiGovernanceToolList.vue'),
        meta: { title: 'AI 工具注册', permissions: ['system:todo:list'], licenseRequired: 'ai' },
      },
      {
        path: 'ai-governance/monitor',
        name: 'AiGovernanceMonitor',
        component: () => import('@/views/ai-governance/AiGovernanceMonitorList.vue'),
        meta: { title: 'AI 治理监控', permissions: ['system:todo:list'], licenseRequired: 'ai' },
      },
      {
        // GA2-46: 多数据源与数据集。设计来源: 46-多数据源与数据集设计
        // 复用 system:todo:list 权限码 (与 GA2-45 一致, 全角色可见)
        path: 'datasources',
        name: 'DataSourceList',
        component: () => import('@/views/datasource/DataSourceList.vue'),
        meta: { title: '数据源管理', permissions: ['system:todo:list'], licenseRequired: 'datasource' },
      },
      {
        path: 'datasources/:code/metadata',
        name: 'DataSourceMetadata',
        component: () => import('@/views/datasource/DataSourceMetadataBrowser.vue'),
        meta: { title: '元数据浏览', permissions: ['system:todo:list'], licenseRequired: 'datasource' },
      },
      {
        path: 'lowcode/entities',
        name: 'LcEntities',
        component: () => import('@/views/lowcode/LcEntityList.vue'),
        meta: { title: '低代码实体', permissions: ['lc:entity:list'], licenseRequired: 'lowcode' },
      },
      {
        // GA2-28: 低代码页面设计器。设计来源: 14-低代码平台设计、16-原型与交互体验设计
        path: 'lowcode/pages',
        name: 'LcPages',
        component: () => import('@/views/lowcode/LcPageList.vue'),
        meta: { title: '低代码页面', permissions: ['lc:page:list'], licenseRequired: 'lowcode' },
      },
      {
        // GA2-47: 低代码生成任务中心。设计来源: 47-低代码设计器交互详设 生成任务中心章节
        // 复用 system:todo:list 权限码 (与 GA2-40~46 一致, 全角色可见)
        path: 'lowcode/generator-tasks',
        name: 'LcGeneratorTasks',
        component: () => import('@/views/lowcode/LcGeneratorTaskList.vue'),
        meta: { title: '生成任务中心', permissions: ['system:todo:list'], licenseRequired: 'lowcode' },
      },
      {
        // GA2-L191: 低代码规则与表达式引擎。设计来源: 36-低代码高级能力设计
        path: 'lc-rules',
        name: 'LcRules',
        component: () => import('@/views/lowcode/LcRuleEditor.vue'),
        meta: { title: '规则引擎', permissions: ['lc:rule:eval'], licenseRequired: 'lowcode' },
      },
      {
        // GA2-L191: 低代码组件协议注册表。设计来源: 36-低代码高级能力设计
        path: 'lc-components',
        name: 'LcComponents',
        component: () => import('@/views/lowcode/LcComponentRegistryList.vue'),
        meta: { title: '组件协议', permissions: ['lc:component:view'], licenseRequired: 'lowcode' },
      },
      {
        // 45 号文档: 插件与模板生态设计 - 插件管理
        path: 'plugins',
        name: 'Plugins',
        component: () => import('@/views/plugin/PluginList.vue'),
        meta: { title: '插件管理', permissions: ['plugin:view'], licenseRequired: 'plugin' },
      },
      {
        // 45 号文档: 插件与模板生态设计 - 模板市场
        path: 'templates',
        name: 'Templates',
        component: () => import('@/views/plugin/TemplateList.vue'),
        meta: { title: '模板市场', permissions: ['template:view'], licenseRequired: 'plugin' },
      },
      {
        // 45 号文档: 插件市场
        path: 'plugin-market',
        name: 'PluginMarket',
        component: () => import('@/views/plugin/PluginMarket.vue'),
        meta: { title: '插件市场', permissions: ['plugin:view'], licenseRequired: 'plugin' },
      },
      {
        // 45 号文档 E0: 插件注册表管理
        path: 'plugin-registries',
        name: 'PluginRegistries',
        component: () => import('@/views/plugin/PluginRegistryList.vue'),
        meta: { title: '插件注册表', permissions: ['plugin:view'], licenseRequired: 'plugin' },
      },
      {
        // 45 号文档 E0: 代码生成模板管理
        path: 'code-templates',
        name: 'CodeTemplates',
        component: () => import('@/views/plugin/CodeTemplateList.vue'),
        meta: { title: '代码模板管理', permissions: ['template:view'], licenseRequired: 'plugin' },
      },
      {
        // 45 号文档 E0: 代码生成模板编辑
        path: 'code-templates/edit',
        name: 'CodeTemplateEdit',
        component: () => import('@/views/plugin/CodeTemplateEdit.vue'),
        meta: { title: '编辑代码模板', permissions: ['template:edit'], licenseRequired: 'plugin' },
      },
      {
        path: 'system/dict-types',
        name: 'DictTypes',
        component: () => import('@/views/dict/DictTypeView.vue'),
        meta: { title: '字典类型', permissions: ['system:dict:list'] },
      },
      {
        path: 'system/dict-items',
        name: 'DictItems',
        component: () => import('@/views/dict/DictItemView.vue'),
        meta: { title: '字典项', permissions: ['system:dict-item:list'] },
      },
      {
        // ADR-002: 对齐 routes.yaml 契约 web.system.dict-items
        // 契约要求 /system/dict-types/:dictType/items（带 path 参数 dictType）
        // 保留旧 /system/dict-items 路由以向后兼容（ADR-001）
        path: 'system/dict-types/:dictType/items',
        name: 'DictItemsByType',
        component: () => import('@/views/dict/DictItemView.vue'),
        meta: { title: '字典项', permissions: ['system:dict-item:list'] },
      },
      // GA2-15: 平台基础能力补齐 — 参数配置/操作日志/任务日志/导入导出任务
      // GA2-16: 路由 meta.permissions 对齐 96 号文档权限矩阵
      {
        path: 'system/configs',
        name: 'SystemConfigs',
        component: () => import('@/views/system/ConfigList.vue'),
        meta: { title: '参数配置', permissions: ['system:config:list'] },
      },
      {
        path: 'system/operation-logs',
        name: 'SystemOperationLogs',
        component: () => import('@/views/system/OperationLogList.vue'),
        meta: { title: '操作日志', permissions: ['system:operation-log:list'] },
      },
      {
        path: 'system/job-logs',
        name: 'SystemJobLogs',
        component: () => import('@/views/system/JobLogList.vue'),
        meta: { title: '任务日志', permissions: ['system:job-log:list'] },
      },
      {
        path: 'system/import-export-tasks',
        name: 'SystemImportExportTasks',
        component: () => import('@/views/system/ImportExportTaskList.vue'),
        meta: { title: '导入导出任务', permissions: ['system:import-export-task:list'] },
      },
      {
        // GA2-L172: 商业授权管理。设计来源: 70-商业授权与版本能力裁剪详设「授权校验流程」
        // 复用 system:config:list 权限码 (系统管理员可见，与参数配置同级)
        path: 'system/license',
        name: 'SystemLicense',
        component: () => import('@/views/system/LicenseManage.vue'),
        meta: { title: '商业授权管理', permissions: ['system:config:list'] },
      },
      // GA2-L177: 平台监控。设计来源: 91-Web基础后台逐页交互详设「服务健康页/缓存概览页」
      // 权限码对齐 contracts/registries/permissions.yaml line 35 (monitor 域)
      {
        path: 'monitor/health',
        name: 'MonitorHealth',
        component: () => import('@/views/system/MonitorHealth.vue'),
        meta: { title: '服务健康', permissions: ['monitor:health:view'] },
      },
      {
        path: 'monitor/cache',
        name: 'MonitorCache',
        component: () => import('@/views/system/MonitorCache.vue'),
        meta: { title: '缓存监控', permissions: ['monitor:cache:view'] },
      },
      {
        path: 'monitor/metrics',
        name: 'MonitorMetrics',
        component: () => import('@/views/system/MonitorMetrics.vue'),
        meta: { title: '性能指标', permissions: ['monitor:metrics:view'] },
      },
    ],
  },
  {
    // GA2-R3: 大屏全屏展示。设计来源: 42-报表与大屏可视化设计 R3 数据大屏
    // F11 全屏 + 自动轮播 + 刷新间隔，独立路由不走 DefaultLayout
    path: '/dashboard-fullscreen/:id',
    name: 'DashboardFullscreen',
    component: () => import('@/views/dashboard/DashboardFullscreen.vue'),
    meta: { title: '数据大屏', permissions: ['dashboard:view'], licenseRequired: 'report' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: { public: true, title: '404' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

/**
 * GA2-16: 路由守卫。
 * 设计来源: 96-端侧权限可见性矩阵详设
 *
 * 三层校验:
 *  1. token 存在性 (未登录跳 /login)
 *  2. userInfo 已拉取 (首次进入调用 fetchUserInfo 获取 permissions)
 *  3. meta.permissions 权限码校验 (不通过跳 /403)
 *
 * GA2-L171: 新增第四层校验 — meta.licenseRequired 商业模块授权校验。
 * 首次进入时调用 licenseStore.fetchModuleStatuses() 加载模块状态矩阵，
 * 校验 licenseStore.isModuleActive(meta.licenseRequired)，不通过时跳转 /403。
 * 后端 LicenseInterceptor 是强制边界，前端路由守卫只是体验优化。
 *
 * 公共路由 (meta.public=true) 跳过校验。
 */
router.beforeEach(async (to, _from, next) => {
  const token = getToken()
  if (to.meta.title) {
    document.title = `${to.meta.title} - YuTong 管理后台`
  }
  if (to.meta.public) {
    next()
    return
  }
  if (!token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }
  // GA2-16: 首次进入或刷新页面时拉取 userInfo (含 permissions/roles/dataScopeType)
  const authStore = useAuthStore()
  if (!authStore.userInfoLoaded || !authStore.userInfo) {
    try {
      await authStore.fetchUserInfo()
    } catch {
      // 拉取失败 (token 过期等) 跳登录
      next({ name: 'Login', query: { redirect: to.fullPath } })
      return
    }
  }
  // GA2-L171: 首次进入时拉取 License 模块状态矩阵
  const licenseStore = useLicenseStore()
  if (!licenseStore.loaded) {
    try {
      await licenseStore.fetchModuleStatuses()
    } catch {
      // License 拉取失败不阻断导航 (后端 LicenseInterceptor 是最终防线)
    }
  }
  // GA2-16: meta.permissions 校验
  const required = to.meta.permissions
  if (required && required.length > 0) {
    if (!hasAnyPermission(required)) {
      next({ name: 'Forbidden' })
      return
    }
  }
  // GA2-L171: meta.licenseRequired 商业模块授权校验
  const licenseRequired = to.meta.licenseRequired
  if (licenseRequired && !licenseStore.isModuleActive(licenseRequired)) {
    next({ name: 'Forbidden' })
    return
  }
  next()
})

// GA2-18: 路由切换后自动上报页面访问事件 (设计来源 94-端侧埋点与体验监控详设)
router.afterEach((to) => {
  trackPageView(to.fullPath, to.meta.title as string | undefined)
})

export default router



