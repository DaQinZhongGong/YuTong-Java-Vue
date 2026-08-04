/**
 * GA2-L183: 客户管理 E2E 页面元素选择器集中管理。
 *
 * 设计来源: web-admin/src/views/customer/CustomerList.vue、web-admin/src/views/login/LoginView.vue。
 * 优先使用 role/text/label 选择器 (Playwright 推荐实践), 必要时辅以稳定的 class 锚点。
 * Element Plus 表单 label 未通过 for/id 关联 input, 故表单字段采用 "form-item 按标签文本过滤 + 内部控件" 定位。
 */

/** 登录页选择器。 */
export const loginSelectors = {
  usernameInput: '#login-username',
  passwordInput: '#login-password',
  /** 登录按钮文本为 "登 录" (含全角空格, 用正则兼容)。 */
  loginButton: /登\s*录/,
  /** Mock 用户类型选择器所在 form-item (sr-only label 文本)。 */
  mockUserFormItemText: 'Mock 用户类型',
  /** 成功登录后跳转的默认首页。 */
  dashboardPath: '/dashboard',
  loginPath: '/login',
}

/** 客户管理页选择器。 */
export const customerSelectors = {
  /** 客户列表路由。 */
  listPath: '/biz/customers',
  /** 搜索框 placeholder。 */
  searchInputPlaceholder: '搜索客户名称',
  searchButton: '搜索',
  addButton: '新增客户',
  editButton: '编辑',
  deleteButton: '删除',
  /** 新增/编辑对话框标题。 */
  addDialogTitle: '新增客户',
  editDialogTitle: '编辑客户',
  /** 对话框内 "确定"/"取消" 按钮 (el-dialog footer, 中文 locale 已配置)。 */
  confirmButton: '确定',
  cancelButton: '取消',
  /**
   * 删除确认弹窗的 "确定" 按钮 (ElMessageBox.confirm)。
   * GA2-L184: ElMessageBox 在未显式注入 zhCn locale 时按钮文本默认为英文 "OK"/"Cancel",
   * 故用正则同时匹配中文 "确定" 与英文 "OK", 兼容两种 locale 配置。
   */
  confirmDeleteButton: /^(确定|OK)$/,
}

/** 客户表单字段标签 (对应 CustomerList.vue el-form-item label)。 */
export const customerFormFieldLabels = {
  customerCode: '客户编码',
  customerName: '客户名称',
  customerType: '客户类型',
  contactName: '联系人',
  contactPhone: '电话',
  contactEmail: '邮箱',
  address: '地址',
  status: '状态',
  remark: '备注',
}

/** 客户类型下拉选项文本。 */
export const customerTypeOptions = {
  ENTERPRISE: '企业',
  INDIVIDUAL: '个人',
} as const

/** 状态下拉选项文本。GA2-L184: 对齐后端 ENABLED/DISABLED 枚举。 */
export const customerStatusOptions = {
  ENABLED: '启用',
  DISABLED: '禁用',
} as const

/** Mock 用户类型 → 登录页下拉选项中可定位的唯一中文片段。 */
export const mockUserOptionText = {
  admin: '平台管理员',
  biz: '业务人员',
  approver: '审核人员',
  viewer: '只读观察者',
} as const

/** 后端直连地址 (绕过前端, 用于权限 API 断言; 后端实际监听 20010, 对齐 vite.config.ts proxy /api target)。 */
export const backendBaseUrl = 'http://localhost:20010'
export const customerApiPath = '/api/v1/customers'