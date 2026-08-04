/** 后端统一响应结构 Result<T> */
export interface Result<T> {
  code: string | number
  message: string
  /** 稳定的 i18n 键，前端优先用于本地化解析 */
  messageKey?: string
  /** 消息插值参数，配合 messageKey 使用 */
  messageArgs?: Record<string, unknown>
  /** 链路追踪 ID */
  traceId?: string
  /** 响应时间戳（ISO-8601） */
  timestamp?: string
  data: T
}

/** 分页查询请求参数 */
export interface PageRequest {
  page: number
  size: number
  keyword?: string
}

/** 分页查询响应结构 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

/** 字典类型 (GA2-25: 字段对齐后端 DictType domain dictType/dictName/status/sortNo) */
export interface DictType {
  id: string
  dictType: string
  dictName: string
  status: string
  systemFlag?: boolean
  sortNo?: number
  createdTime?: string
  updatedTime?: string
}

/** 字典项 (GA2-25: 字段对齐后端 DictItem domain dictType/itemLabel/itemValue/sortNo) */
export interface DictItem {
  id: string
  dictType: string
  itemCode: string
  itemLabel: string
  itemLabelI18n?: string
  itemValue: string
  status: string
  sortNo?: number
  colorToken?: string
  createdTime?: string
  updatedTime?: string
}

/** 登录请求参数 */
export interface LoginRequest {
  username: string
  password: string
}

/** 登录响应数据。GA2-16: 字段类型对齐后端 (userId/tenantId 均为 string ULID)。 */
export interface LoginResponse {
  token: string
  userId: string
  username: string
  tenantId: string
  /** 后端返回的 mock 标识 (local/test profile) */
  mock?: boolean
  /** 21-安全合规: refresh token (14d, 轮换)，登录成功后由后端签发 */
  refreshToken?: string
}

/**
 * 当前登录用户信息。
 * GA2-16: 字段对齐后端 AuthController /auth/me 返回结构
 * (userId/username/tenantId/roles/permissions/dataScopeType)。
 */
export interface UserInfo {
  /** 后端字段名为 userId (字符串 ULID); 兼容旧 id 字段 */
  userId?: string
  /** @deprecated 历史字段, 由 userId 取代 */
  id?: number | string
  username: string
  nickname?: string
  avatar?: string
  email?: string
  phone?: string
  roles?: string[]
  /** 权限码集合; admin 为 ['*'] 通配 */
  permissions?: string[]
  tenantId?: string | number
  /** 数据范围类型 (ALL/TENANT/DEPT_AND_CHILD/DEPT/SELF/CUSTOM/NONE); GA2-16 新增 */
  dataScopeType?: string
}

/**
 * 菜单节点。对齐后端 MenuNodeVO (GET /auth/menus 返回当前用户菜单树)。
 * 设计来源: openapi.yaml MenuNodeVO schema。
 */
export interface Menu {
  id: string
  name: string
  /** i18n 标题键 */
  titleKey: string
  routeId: string
  path: string
  icon?: string | null
  permission?: string | null
  visible: boolean
  children: Menu[]
}

/** 系统健康状态 */
export interface HealthStatus {
  status: string
  components?: Record<string, { status: string; details?: Record<string, unknown> }>
  timestamp?: string
}

/** 客户。对齐后端 Customer domain (设计来源 18-样例业务详细设计) */
export interface Customer {
  id: string
  customerCode: string
  customerName: string
  contactName?: string
  contactPhone?: string
  address?: string
  status: string
  remark?: string
  createdTime?: string
  updatedTime?: string
}

/** 商品。对齐后端 Product domain (设计来源 18-样例业务详细设计) */
export interface Product {
  id: string
  productCode: string
  productName: string
  unit?: string
  price?: number
  status: string
  remark?: string
  createdTime?: string
  updatedTime?: string
}

/**
 * 申请单。GA2-24: 字段对齐后端 BizRequest domain + biz_request 表 (设计来源 18-样例业务详细设计)
 * 旧字段 requestTitle/requestType/customerName/submitTime/approveTime/createdAt/updatedAt 已废弃,
 * 改为后端实际返回字段 title/customerNameSnapshot/submittedTime/approvedTime/createdTime/updatedTime。
 */
export interface BizRequest {
  id: string
  requestNo: string
  /** 申请单标题 (后端字段 title) */
  title: string
  customerId: string
  /** 客户名称快照, 防客户更名后展示错误 */
  customerNameSnapshot: string
  /** 申请原因 */
  applyReason?: string
  /** 状态枚举 DRAFT/SUBMITTED/APPROVED/REJECTED/ARCHIVED */
  requestStatus: string
  /** 合计金额, 由后端按明细重新计算 */
  totalAmount: number
  /** 申请人 ID, 数据权限 SELF 字段 */
  applicantId?: string
  /** 申请人名称快照 */
  applicantNameSnapshot?: string
  /** 数据权限归属用户 */
  ownerUserId?: string
  ownerDeptId?: string
  ownerDeptPath?: string
  submittedTime?: string
  approvedTime?: string
  archivedTime?: string
  /** 乐观锁版本号 (修改时必填) */
  version?: number
  createdTime?: string
  updatedTime?: string
  /** 兼容旧字段, 后端不再返回 */
  remark?: string
}

/**
 * 申请单明细。GA2-24: 对齐后端 BizRequestItem domain (设计来源 18-样例业务详细设计)
 */
export interface BizRequestItem {
  id?: string
  requestId?: string
  productId: string
  productCodeSnapshot?: string
  productNameSnapshot?: string
  unit?: string
  quantity: number
  unitPrice: number
  /** 行金额 = quantity * unitPrice, 由后端计算 */
  lineAmount?: number
  sortNo?: number
}

/**
 * 审批记录。GA2-24: 对齐后端 ApprovalRecord domain (设计来源 18-样例业务详细设计)
 */
export interface ApprovalRecord {
  id?: string
  requestId?: string
  /** 动作 SUBMIT/APPROVE/REJECT/WITHDRAW/ARCHIVE */
  action?: string
  result?: string
  operatorId?: string
  opinion?: string
  operatedTime?: string
}

/**
 * 申请单详情响应 (含明细列表 + 审批记录列表)。对齐后端 BizRequestDetailVO。
 */
export interface BizRequestDetail extends BizRequest {
  items: BizRequestItem[]
  approvals: ApprovalRecord[]
}

/**
 * 保存草稿请求体。对齐后端 SaveBizRequestRequest。
 * id 为空表示新建, 非空表示修改 (修改时 version 必填用于乐观锁)。
 */
export interface SaveBizRequestPayload {
  id?: string
  /** 修改时必填, 乐观锁版本号 */
  version?: number
  title: string
  customerId: string
  customerNameSnapshot?: string
  applyReason?: string
  items: Array<{
    productId: string
    productCodeSnapshot?: string
    productNameSnapshot?: string
    unit?: string
    quantity: number
    unitPrice: number
    sortNo?: number
  }>
}

/** 待办任务 */
export interface TodoTask {
  id: string
  title: string
  businessType: string
  businessId: string
  assigneeId: string
  todoStatus: string
  priority: string
  dueDate: string
  createdAt: string
}

/** 站内消息 */
export interface Message {
  id: string
  messageTitle: string
  content: string
  messageType: string
  readStatus: string
  readTime: string
  createdAt: string
}

/** 文件 */
/** 文件信息 (GA2-25: 字段对齐后端 SysFile domain fileName/fileKey/contentType/uploadStatus) */
export interface FileInfo {
  id: string
  fileName: string
  fileKey: string
  fileSize: number
  contentType: string
  fileExt?: string
  storageType: string
  checksum?: string
  uploadStatus: string
  createdTime?: string
  updatedTime?: string
}

/** AI 对话。GA2-26: 字段对齐后端 AiConversation domain (conversationTitle/scenario/lastMessageTime/status) */
export interface AiConversation {
  id: string
  /** 会话编号 (后端字段 conversationNo) */
  conversationNo?: string
  conversationTitle: string
  scenario: string
  /** 模型编码 */
  modelCode?: string
  /** 会话状态 ACTIVE/ARCHIVED */
  status?: string
  /** locale */
  locale?: string
  lastMessageTime: string
  createdAt: string
}

/** AI 消息。GA2-26: 字段对齐后端 AiMessage domain (role/contentSummary/citationJson/tokenInput/tokenOutput/latencyMs) */
export interface AiMessage {
  id: string
  conversationId: string
  /** 角色: 后端常量为小写 "system" / "user" / "assistant" (AiMessage.ROLE_*) */
  role: string
  /** 内容摘要 (脱敏, 列表展示用, 后端字段 contentSummary) */
  contentSummary?: string
  /** 加密后的完整内容 (后端字段 contentEncrypted, 第一版未加密) */
  contentEncrypted?: string
  /** 引用列表 JSON (后端字段 citationJson, 字符串形式, 由前端解析) */
  citationJson?: string
  tokenInput?: number
  tokenOutput?: number
  latencyMs?: number
  /** 兼容字段: 前端组件统一用 content 展示消息, 取 contentSummary 或 contentEncrypted */
  content?: string
  createdAt: string
}

/**
 * AI 对话请求。GA2-26: 对齐后端 AiChatRequest DTO
 * 设计来源: 13-AI能力设计 chatWithAssistant
 * 第一版不支持 SSE, 通过 Accept: application/json 返回完整 AiChatVO (SSE 留 v0.5+)。
 */
export interface AiChatRequest {
  /** 会话 ID, 为空表示新会话 */
  conversationId?: string
  /** 用户问题 */
  message: string
  /** 场景: PLATFORM_QA / FIELD_SUGGEST / PAGE_GENERATE / SQL_EXPLAIN / OPS_DIAGNOSE */
  scenario?: string
  /** 关联知识库 ID, 用于 RAG 检索 */
  kbId?: string
  /** 幂等键 */
  idempotencyKey: string
  /** 供应商编码 (用户指定模型来源) */
  providerCode?: string
  /** 模型编码 (用户指定具体模型) */
  modelCode?: string
}

/** 可用模型选项。GET /ai/providers/models 返回, 用于 AI 聊天页模型选择器。 */
export interface AiModelOption {
  /** 供应商编码 */
  providerCode: string
  /** 模型编码 */
  modelCode: string
  /** 模型展示名称 */
  modelName: string
}

/** AI 引用来源 (RAG 检索结果)。GA2-26: 对齐后端 AiChatVO.Citation */
export interface AiCitation {
  documentId: string
  chunkId: string
  docTitle: string
  sectionPath: string
  /** 来源类型: DOC / KB / WEB 等 */
  sourceType: string
  /** 相关度得分 0-1 */
  score: number
}

/**
 * AI 对话响应。GA2-26: 对齐后端 AiChatVO。
 * 第一版通过 POST /ai/chat Accept: application/json 获取完整响应;
 * SSE 流式 (meta/delta/citation/tool/done/error 事件) 留待 v0.5+ 后端支持后实现。
 * 设计来源: 13-AI能力设计 chatWithAssistant / 16-原型与交互体验设计 AI 助手原型
 */
export interface AiChatVO {
  conversationId: string
  messageId: string
  /** AI 回复内容 */
  content: string
  /** 引用来源列表 (RAG 检索结果) */
  citations: AiCitation[]
  scenario: string
  modelCode: string
  tokenInput: number
  tokenOutput: number
  /** 响应耗时 ms */
  latencyMs: number
  createdTime: string
}

/**
 * 应用 AI 建议请求。GA2-26: 对齐后端 ApplySuggestionRequest DTO
 * 设计来源: 13-AI能力设计 applyAiSuggestion 第 244-250 行
 * 关键约束:
 *  - AI 生成结果只能进入草稿区, 不直接修改生产数据
 *  - 必须回传 schemaVersion / configHash / expectedVersion / idempotencyKey
 *  - 任一不匹配后端拒绝写入 (返回 BusinessConflictException)
 */
export interface ApplySuggestionRequest {
  /** 草稿 ID (低代码草稿区中的草稿) */
  draftId: string
  /** 草稿类型: PAGE / ENTITY / SQL */
  draftType: 'PAGE' | 'ENTITY' | 'SQL'
  /** 草稿内容 JSON 字符串 */
  draftContent: string
  /** schema 版本 */
  schemaVersion: string
  /** 配置哈希 */
  configHash: string
  /** 期望版本号 (乐观锁) */
  expectedVersion: number
  /** 幂等键 */
  idempotencyKey: string
}

// ============================================================================
// GA2-31: SSE 流式事件类型。对齐 contracts/openapi/openapi.yaml AiStreamEvent schema (line 2183-2224)
// 设计来源: 13-AI能力设计 line 137-143/252
// 事件序列: meta → (citation)* → (delta)+ → done | error
// ============================================================================

/** SSE 事件类型枚举 */
export const AI_STREAM_EVENT_TYPE = {
  META: 'meta',
  DELTA: 'delta',
  CITATION: 'citation',
  TOOL: 'tool',
  DONE: 'done',
  ERROR: 'error',
} as const

export type AiStreamEventType = (typeof AI_STREAM_EVENT_TYPE)[keyof typeof AI_STREAM_EVENT_TYPE]

/** meta 事件数据: 流开始时推送一次，告知客户端使用的模型和场景 */
export interface AiStreamMetaData {
  modelCode: string
  scenario: string
}

/** delta 事件数据: 文本增量，客户端按 sequence 顺序拼接得到完整 AI 回复 */
export interface AiStreamDeltaData {
  text: string
}

/** done 事件数据: 流正常结束，携带 token 用量和是否需要人工确认 */
export interface AiStreamDoneData {
  usage: {
    inputTokens: number
    outputTokens: number
    latencyMs: number
    estimatedCost?: string | null
    currency?: string | null
  }
  requiresHumanConfirmation: boolean
}

/** error 事件数据: 流异常终止，客户端展示错误提示 */
export interface AiStreamErrorData {
  code: string
  messageKey: string
  traceId: string
  retryable: boolean
}

/**
 * SSE 流式事件信封。对齐后端 AiStreamEvent DTO / openapi.yaml AiStreamEvent schema。
 * 每个事件携带 eventId / sequence / conversationId / messageId，sequence 从 0 递增。
 */
export interface AiStreamEvent {
  eventId: string
  eventType: AiStreamEventType
  sequence: number
  conversationId: string
  messageId: string
  /** 按 eventType 不同为不同 DTO（AiStreamMetaData / AiStreamDeltaData / AiCitation / AiStreamDoneData / AiStreamErrorData） */
  data: AiStreamMetaData | AiStreamDeltaData | AiCitation | AiStreamDoneData | AiStreamErrorData | unknown
}

/**
 * 低代码实体。GA2-27: 字段对齐后端 LcEntity domain
 * 状态: DRAFT → PUBLISHED → DISABLED；发布时生成不可变版本快照 + config_hash
 * 设计来源: 14-低代码平台设计 lc_entity
 */
export interface LcEntity {
  id: string
  entityCode: string
  entityName: string
  /** 目标表名, 生成 DDL 时使用 */
  tableName: string
  moduleCode: string
  /** 元模型版本号, 每次发布自增 */
  versionNo?: number
  /** 元模型 schema 版本, 平台升级时迁移依据 */
  schemaVersion?: string
  /** 配置摘要 (字段+关系 hash), 发布时计算, 用于 diff */
  configHash?: string
  /** DRAFT / PUBLISHED / DISABLED */
  status: string
  ownerUserId?: string
  /** 乐观锁版本号 (更新时传入) */
  version?: number
  createdTime?: string
  updatedTime?: string
  /** 前端兼容字段 (历史) */
  createdAt?: string
}

/** 低代码字段数据类型枚举。对齐后端 LcField.TYPE_* 常量 */
export const LC_FIELD_TYPE = {
  STRING: 'STRING',
  DECIMAL: 'DECIMAL',
  DATE: 'DATE',
  DICT: 'DICT',
  FILE: 'FILE',
  JSON: 'JSON',
} as const

/** 低代码字段类型选项 (供 el-select 渲染) */
export const LC_FIELD_TYPE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: LC_FIELD_TYPE.STRING, label: '字符串 STRING' },
  { value: LC_FIELD_TYPE.DECIMAL, label: '数字 DECIMAL' },
  { value: LC_FIELD_TYPE.DATE, label: '日期 DATE' },
  { value: LC_FIELD_TYPE.DICT, label: '字典 DICT' },
  { value: LC_FIELD_TYPE.FILE, label: '文件 FILE' },
  { value: LC_FIELD_TYPE.JSON, label: 'JSON' },
]

/** 低代码字段定义。GA2-27: 对齐后端 LcField domain
 * 约束: primary_flag=true 时只允许 field_code=id, data_type=STRING, db_column=varchar(32) */
export interface LcField {
  id?: string
  entityId?: string
  /** 字段编码, JSON 使用 camelCase */
  fieldCode: string
  fieldName: string
  /** 多语言字段名 jsonb */
  fieldNameI18n?: string
  /** 数据库列名 snake_case */
  dbColumn: string
  /** STRING/DECIMAL/DATE/DICT/FILE/JSON */
  dataType: string
  lengthValue?: number
  precisionValue?: number
  scaleValue?: number
  nullable?: boolean
  defaultValue?: string
  /** 仅 DICT 类型使用 */
  dictType?: string
  primaryFlag?: boolean
  uniqueFlag?: boolean
  indexFlag?: boolean
  sortNo?: number
  /** 元模型迁移兼容旧字段名 */
  oldFieldCode?: string
}

/** 低代码关系类型枚举。对齐后端 LcRelation.TYPE_* 常量 */
export const LC_RELATION_TYPE = {
  ONE_TO_ONE: 'ONE_TO_ONE',
  ONE_TO_MANY: 'ONE_TO_MANY',
  MANY_TO_ONE: 'MANY_TO_ONE',
} as const

/** 低代码级联策略枚举。对齐后端 LcRelation 注释 */
export const LC_CASCADE_POLICY = {
  CASCADE: 'CASCADE',
  SET_NULL: 'SET_NULL',
  RESTRICT: 'RESTRICT',
} as const

/** 低代码实体关系。GA2-27: 对齐后端 LcRelation domain */
export interface LcRelation {
  id?: string
  sourceEntityId?: string
  targetEntityId: string
  /** ONE_TO_ONE / ONE_TO_MANY / MANY_TO_ONE */
  relationType: string
  sourceFieldCode?: string
  targetFieldCode?: string
  /** CASCADE / SET_NULL / RESTRICT */
  cascadePolicy?: string
  required?: boolean
}

/** 实体详情 VO (含字段和关系列表)。GA2-27: 对齐后端 LcEntityDetailVO */
export interface LcEntityDetailVO extends LcEntity {
  publishedTime?: string
  fields: LcField[]
  relations: LcRelation[]
}

/** 保存实体草稿请求。GA2-27: 对齐后端 SaveLcEntityRequest DTO
 * id 为空表示新建, 非空表示修改 (修改时 version 必填用于乐观锁) */
export interface SaveLcEntityRequest {
  id?: string
  /** 乐观锁版本号 (更新时必填) */
  version?: number
  entityCode: string
  entityName: string
  tableName: string
  moduleCode: string
  ownerUserId?: string
  fields: Array<Omit<LcField, 'id' | 'entityId'>>
  relations: Array<Omit<LcRelation, 'id' | 'sourceEntityId'>>
}

/**
 * 低代码页面。GA2-28: 字段对齐后端 LcPage domain
 * 状态: DRAFT → PUBLISHED; 回滚 = 历史版本复制为草稿后重新发布
 * 设计来源: 14-低代码平台设计 lc_page
 */
export interface LcPage {
  id: string
  pageCode: string
  pageName: string
  /** 关联实体 ID */
  entityId?: string
  /** LIST / FORM / DETAIL */
  pageType: string
  /** 布局配置 JSON */
  layoutJson?: string
  /** 布局 schema 版本 */
  layoutSchemaVersion?: string
  /** 元模型版本号, 每次发布自增 */
  versionNo?: number
  /** DRAFT / PUBLISHED */
  status: string
  /** 乐观锁版本号 (更新时传入) */
  version?: number
  publishedTime?: string
  createdTime?: string
  updatedTime?: string
}

/** 页面类型枚举。对齐后端 LcPage.PAGE_TYPE_* 常量 */
export const LC_PAGE_TYPE = {
  LIST: 'LIST',
  FORM: 'FORM',
  DETAIL: 'DETAIL',
} as const

/** 页面类型选项 (供 el-select 渲染) */
export const LC_PAGE_TYPE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: LC_PAGE_TYPE.LIST, label: '列表页 LIST' },
  { value: LC_PAGE_TYPE.FORM, label: '表单页 FORM' },
  { value: LC_PAGE_TYPE.DETAIL, label: '详情页 DETAIL' },
]

/** 页面状态枚举。对齐后端 LcPage.STATUS_* 常量 */
export const LC_PAGE_STATUS = {
  DRAFT: 'DRAFT',
  PUBLISHED: 'PUBLISHED',
} as const

/** 页面状态选项 (供 el-select/el-tag 渲染) */
export const LC_PAGE_STATUS_OPTIONS: Array<{
  value: string
  label: string
  tagType: '' | 'primary' | 'success' | 'warning' | 'info' | 'danger'
}> = [
  { value: LC_PAGE_STATUS.DRAFT, label: '草稿', tagType: 'info' },
  { value: LC_PAGE_STATUS.PUBLISHED, label: '已发布', tagType: 'success' },
]

/** 组件类型枚举。对齐后端 LcComponent 注释 */
export const LC_COMPONENT_TYPE = {
  INPUT: 'INPUT',
  SELECT: 'SELECT',
  TABLE: 'TABLE',
  FORM: 'FORM',
  BUTTON: 'BUTTON',
  CUSTOM: 'CUSTOM',
} as const

/** 组件类型选项 (供 el-select 渲染) */
export const LC_COMPONENT_TYPE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: LC_COMPONENT_TYPE.INPUT, label: '输入框 INPUT' },
  { value: LC_COMPONENT_TYPE.SELECT, label: '下拉选择 SELECT' },
  { value: LC_COMPONENT_TYPE.TABLE, label: '表格 TABLE' },
  { value: LC_COMPONENT_TYPE.FORM, label: '表单 FORM' },
  { value: LC_COMPONENT_TYPE.BUTTON, label: '按钮 BUTTON' },
  { value: LC_COMPONENT_TYPE.CUSTOM, label: '自定义 CUSTOM' },
]

/** 低代码页面组件。GA2-28: 对齐后端 LcComponent domain
 * 自定义组件必须声明标准 Props: value/modelValue, field, disabled, formMode */
export interface LcComponent {
  id?: string
  pageId?: string
  componentCode: string
  /** INPUT / SELECT / TABLE / FORM / BUTTON / CUSTOM */
  componentType: string
  /** 组件属性 JSON */
  propsJson?: string
  /** 校验/联动规则 JSON */
  rulesJson?: string
  /** 事件绑定 JSON, 命名: field:{code}:change, action:{code}:trigger */
  eventsJson?: string
  propsSchemaVersion?: string
  parentComponentId?: string
  sortNo?: number
}

/** 动作类型枚举。对齐后端 LcAction.TYPE_* 常量 */
export const LC_ACTION_TYPE = {
  SUBMIT: 'SUBMIT',
  RESET: 'RESET',
  API_CALL: 'API_CALL',
  NAVIGATE: 'NAVIGATE',
  EXPORT: 'EXPORT',
} as const

/** 动作类型选项 (供 el-select 渲染) */
export const LC_ACTION_TYPE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: LC_ACTION_TYPE.SUBMIT, label: '提交 SUBMIT' },
  { value: LC_ACTION_TYPE.RESET, label: '重置 RESET' },
  { value: LC_ACTION_TYPE.API_CALL, label: 'API 调用 API_CALL' },
  { value: LC_ACTION_TYPE.NAVIGATE, label: '导航 NAVIGATE' },
  { value: LC_ACTION_TYPE.EXPORT, label: '导出 EXPORT' },
]

/** 低代码页面动作。GA2-28: 对齐后端 LcAction domain */
export interface LcAction {
  id?: string
  pageId?: string
  actionCode: string
  actionName: string
  /** SUBMIT / RESET / API_CALL / NAVIGATE / EXPORT */
  actionType: string
  permissionCode?: string
  confirmRequired?: boolean
  /** HTTP 方法 GET/POST/PUT/DELETE (仅 API_CALL 使用) */
  apiMethod?: string
  /** API 路径 (仅 API_CALL 使用) */
  apiPath?: string
  /** 请求参数映射 JSON */
  payloadMapping?: string
}

/** 页面详情 VO (含组件和动作列表)。GA2-28: 对齐后端 LcPageDetailVO */
export interface LcPageDetailVO extends LcPage {
  components: LcComponent[]
  actions: LcAction[]
}

/** 保存页面草稿请求。GA2-28: 对齐后端 SaveLcPageRequest DTO
 * id 为空表示新建, 非空表示修改 (修改时 version 必填用于乐观锁) */
export interface SaveLcPageRequest {
  id?: string
  version?: number
  pageCode: string
  pageName: string
  entityId?: string
  pageType: string
  layoutJson?: string
  layoutSchemaVersion?: string
  components: Array<Omit<LcComponent, 'id' | 'pageId'>>
  actions: Array<Omit<LcAction, 'id' | 'pageId'>>
}

/** 工作台统计 */
export interface WorkbenchStats {
  totalCustomers: number
  totalProducts: number
  totalRequests: number
  draftRequests: number
  submittedRequests: number
  approvedRequests: number
  rejectedRequests: number
  archivedRequests: number
  pendingTodos: number
  unreadMessages: number
}

/** GA2-33: 工作台趋势数据点。对齐 42 号文档 biz_request_trend_7d 数据集。 */
export interface WorkbenchTrendPoint {
  date: string
  count: number
  amount: number
}

/** GA2-33: 工作台趋势数据。前端 ECharts 折线图数据源。 */
export interface WorkbenchTrend {
  points: WorkbenchTrendPoint[]
  totalCount: number
  totalAmount: number
}

/** GA2-33: 金额 Top N 项。对齐 42 号文档 biz_request_amount_top10 数据集。 */
export interface WorkbenchTopItem {
  requestNo: string
  title: string
  totalAmount: number
  customerNameSnapshot: string
  requestStatus: string
}

/** 系统参数配置 (GA2-15: 对齐后端 SysConfig + sys_config 表) */
export interface SysConfig {
  id: string
  configKey: string
  /** 列表和详情接口对敏感配置返回 ****** 脱敏值 */
  configValue: string
  valueType?: string
  configGroup?: string
  editable?: boolean
  sensitive?: boolean
  status?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

/** 操作日志 (GA2-15: 对齐后端 SysOperationLog + sys_operation_log 表) */
export interface OperationLog {
  id: string
  operationType: string
  module?: string
  bizType?: string
  bizId?: string
  content?: string
  beforeJson?: string
  afterJson?: string
  result: string
  errorCode?: string
  traceId?: string
  operatorId?: string
  operatorName?: string
  ip?: string
  userAgent?: string
  operatedTime?: string
  createdAt?: string
}

/** 任务日志 (GA2-15: 对齐后端 SysJobLog + sys_job_log 表) */
export interface JobLog {
  id: string
  jobCode?: string
  jobName?: string
  bizType?: string
  bizId?: string
  triggerType?: string
  status: string
  startTime?: string
  endTime?: string
  durationMs?: number
  errorMessage?: string
  traceId?: string
  createdAt?: string
}

/** 导入导出任务 (GA2-15: 对齐后端 SysImportExportTask + sys_import_export_task 表) */
export interface ImportExportTask {
  id: string
  taskType: string
  bizType?: string
  fileId?: string
  status: string
  totalRows?: number
  successRows?: number
  failRows?: number
  errorFileId?: string
  errorMessage?: string
  startedTime?: string
  finishedTime?: string
  createdAt?: string
}

// ==================== GA2-34: 工单中心 (35 号文档 P1) ====================

/** 工单分类 */
export interface WorkTicketCategory {
  id: string
  categoryCode: string
  categoryName: string
  slaHours: number
  status: string
}

/** 工单 */
export interface WorkTicket {
  id: string
  ticketNo: string
  title: string
  description?: string
  categoryId: string
  categoryNameSnapshot: string
  priority: string
  status: string
  reporterId?: string
  reporterNameSnapshot?: string
  handlerId?: string
  handlerNameSnapshot?: string
  slaDeadline?: string
  assignedTime?: string
  resolvedTime?: string
  closedTime?: string
  satisfactionScore?: number
  satisfactionComment?: string
  createdTime?: string
  createdBy?: string
}

/** 工单处理记录 */
export interface WorkTicketLog {
  id: string
  ticketId: string
  action: string
  fromStatus?: string
  toStatus?: string
  operatorId: string
  operatorName?: string
  comment?: string
  createdTime?: string
}

/** 工单详情（含处理记录时间线） */
export interface TicketDetail extends WorkTicket {
  logs: WorkTicketLog[]
}

/** 新建工单请求 */
export interface SaveTicketRequest {
  title: string
  description?: string
  categoryId: string
  priority?: string
  reporterId?: string
}

/** 工单状态流转操作请求 */
export interface TicketActionRequest {
  action: string
  handlerId?: string
  handlerName?: string
  comment?: string
  satisfactionScore?: number
  satisfactionComment?: string
}

// ==================== GA2-35: 合同档案 (35 号文档 P1) ====================

/** 合同（主表） */
export interface Contract {
  id: string
  contractNo: string
  title: string
  contractType?: string
  partyA: string
  partyB: string
  signedDate?: string
  effectiveDate?: string
  expireDate?: string
  /** 合同金额（viewer 角色脱敏为 null） */
  amount?: number | null
  currency?: string
  /** 内容摘要（viewer 角色 + 非 APPROVED 以上状态脱敏为 ***） */
  contentSummary?: string
  status: string
  currentVersionNo: number
  ownerUserId?: string
  submittedTime?: string
  approvedTime?: string
  signedTime?: string
  archivedTime?: string
  createdTime?: string
  createdBy?: string
}

/** 合同版本快照 */
export interface ContractVersion {
  id: string
  contractId: string
  versionNo: number
  fileId?: string
  fileNameSnapshot?: string
  fileSize?: number
  fileChecksum?: string
  contentSummary?: string
  changeLog?: string
  isCurrent: boolean
  createdTime?: string
  createdBy?: string
}

/** 合同审批记录 */
export interface ContractApproval {
  id: string
  contractId: string
  action: string
  fromStatus?: string
  toStatus?: string
  approverId?: string
  approverName?: string
  opinion?: string
  createdTime?: string
}

/** 合同标签 */
export interface ContractTag {
  id: string
  contractId: string
  tagName: string
  createdTime?: string
}

/** 合同详情（聚合版本/审批/标签） */
export interface ContractDetail extends Contract {
  versions: ContractVersion[]
  approvals: ContractApproval[]
  tags: ContractTag[]
  editable: boolean
}

/** 新建/更新合同请求 */
export interface SaveContractRequest {
  title: string
  contractType?: string
  partyA: string
  partyB: string
  signedDate?: string
  effectiveDate?: string
  expireDate?: string
  amount?: number
  currency?: string
  contentSummary?: string
}

/** 合同状态流转操作请求 */
export interface ContractActionRequest {
  action: string
  /** 审批意见（APPROVE/REJECT 必填） */
  opinion?: string
}

// ==================== GA2-36: 报表分析 (35 号文档 P1 / 42 号文档 R1) ====================

/** 报表数据集定义 (对应后端 RptDataset domain) */
export interface RptDataset {
  id: string
  datasetCode: string
  datasetName: string
  sourceType: string
  queryText: string
  paramsSchema?: string
  cacheSeconds: number
  riskLevel: string
  ownerUserId?: string
  permissionCode?: string
  sensitiveColumns?: string
  maxRows: number
  timeoutMs: number
  reviewStatus: string
  status: string
  description?: string
  tenantId?: string
  createdBy?: string
  createdTime?: string
  updatedBy?: string
  updatedTime?: string
  version?: number
}

/** 报表定义 (对应后端 RptReport domain) */
export interface RptReport {
  id: string
  reportCode: string
  reportName: string
  reportType: string
  /** 布局 JSON: {canvas, components:[{id,type,datasetCode,props,layout}]} */
  layoutJson: string
  versionNo: number
  permissionCode?: string
  status: string
  description?: string
  tenantId?: string
  createdBy?: string
  createdTime?: string
  updatedBy?: string
  updatedTime?: string
  version?: number
}

/** 数据集执行结果 (对应后端 DatasetResultVO) */
export interface DatasetResultVO {
  columns: string[]
  rows: Record<string, unknown>[]
  rowCount: number
  fromCache: boolean
  generatedTime: string
  datasetVersion: number
  dataScopeApplied: boolean
  maskedColumns: string[]
  traceId?: string
}

/** 报表渲染结果 (对应后端 ReportRenderVO) */
export interface ReportRenderVO {
  reportCode: string
  reportName: string
  reportType: string
  layoutJson: string
  componentData: Record<string, DatasetResultVO>
  componentIds: string[]
  traceId?: string
}

/** AI 指标解释结果 (对应后端 ReportExplainVO) */
export interface ReportExplainVO {
  reportCode: string
  explanation: string
  generatedTime: string
  traceId?: string
  /** 降级模式标记 (v1.0 无 AI 提供商依赖) */
  degraded: boolean
}

/** 报表布局组件定义 (layoutJson.components[] 解析后) */
export interface ReportComponent {
  id: string
  type: 'pie-chart' | 'bar-chart' | 'line-chart' | 'table' | 'metric-card'
  datasetCode: string
  props: {
    title?: string
    nameField?: string
    valueField?: string
    xField?: string
    yField?: string
  }
  layout: { x: number; y: number; w: number; h: number }
}

/** 数据集保存请求 */
export interface SaveDatasetRequest {
  datasetCode: string
  datasetName: string
  sourceType?: string
  queryText: string
  paramsSchema?: string
  cacheSeconds?: number
  riskLevel?: string
  permissionCode?: string
  sensitiveColumns?: string
  maxRows?: number
  timeoutMs?: number
  description?: string
}

/** 报表保存请求 */
export interface SaveReportRequest {
  reportCode: string
  reportName: string
  reportType?: string
  layoutJson: string
  permissionCode?: string
  description?: string
}

// ==================== GA2-37: 库存出入库 (35 号文档 P1) ====================

/**
 * MyBatis-Plus Page<T> 序列化结构 (后端 InventoryController 直接返回 Page<T>)。
 * 字段: records / total / size / current / pages, 与 PageResult<T> 不完全一致。
 */
export interface InvPage<T> {
  records: T[]
  total: number
  /** 当前页 (MyBatis-Plus Page.current) */
  current: number
  /** 每页大小 */
  size: number
  /** 总页数 */
  pages: number
}

/** 物料主表 (对应后端 InvMaterial domain) */
export interface InvMaterial {
  id: string
  materialCode: string
  materialName: string
  /** ELECTRONIC/MECHANICAL/ACCESSORY/GENERAL */
  materialType?: string
  spec?: string
  /** PCS/BOX/KG/M */
  unit?: string
  category?: string
  barcode?: string
  referencePrice?: number
  /** ACTIVE/INACTIVE */
  status?: string
  remark?: string
  createdBy?: string
  createdTime?: string
  updatedBy?: string
  updatedTime?: string
  version?: number
}

/** 仓库主表 (对应后端 InvWarehouse domain) */
export interface InvWarehouse {
  id: string
  warehouseCode: string
  warehouseName: string
  /** CENTRAL/BRANCH */
  warehouseType?: string
  address?: string
  managerUserId?: string
  /** ACTIVE/INACTIVE */
  status?: string
  remark?: string
  createdBy?: string
  createdTime?: string
  updatedBy?: string
  updatedTime?: string
  version?: number
}

/** 库存余额 (对应后端 InvStockBalance domain) */
export interface InvStockBalance {
  id: string
  materialId: string
  warehouseId: string
  quantity: number
  lockedQuantity: number
  lastInTime?: string
  lastOutTime?: string
  version?: number
}

/** 库存余额 VO (含物料/仓库冗余字段, 对应后端 StockBalanceVO) */
export interface StockBalanceVO {
  id: string
  materialId: string
  materialCode: string
  materialName: string
  warehouseId: string
  warehouseCode: string
  warehouseName: string
  quantity: number
  lockedQuantity: number
  /** 可用数量 = quantity - lockedQuantity (后端计算) */
  availableQuantity: number
  lastInTime?: string
  lastOutTime?: string
}

/** 入库单 (对应后端 InvInboundOrder domain) */
export interface InvInboundOrder {
  id: string
  /** 单号 INyyyyMMddNNNN */
  orderNo: string
  /** 幂等键 (租户内唯一) */
  idempotencyKey: string
  warehouseId: string
  materialId: string
  quantity: number
  unitCost?: number
  totalAmount?: number
  /** DRAFT/CONFIRMED */
  status: string
  /** PURCHASE/RETURN/TRANSFER_IN/INITIAL */
  inboundType?: string
  batchNo?: string
  supplier?: string
  confirmedTime?: string
  confirmedBy?: string
  /** CONFIRMED 后回填 */
  balanceId?: string
  remark?: string
  createdBy?: string
  createdTime?: string
  updatedBy?: string
  updatedTime?: string
  version?: number
}

/** 出库单 (对应后端 InvOutboundOrder domain) */
export interface InvOutboundOrder {
  id: string
  /** 单号 OUTyyyyMMddNNNN */
  orderNo: string
  /** 幂等键 (租户内唯一) */
  idempotencyKey: string
  warehouseId: string
  materialId: string
  quantity: number
  unitCost?: number
  totalAmount?: number
  /** DRAFT/CONFIRMED/COMPENSATED */
  status: string
  /** SALE/SCRAP/TRANSFER_OUT */
  outboundType?: string
  batchNo?: string
  customer?: string
  confirmedTime?: string
  confirmedBy?: string
  compensatedTime?: string
  compensatedBy?: string
  /** CONFIRMED 后回填 */
  balanceId?: string
  remark?: string
  createdBy?: string
  createdTime?: string
  updatedBy?: string
  updatedTime?: string
  version?: number
}

/* ==========================================================================
 * GA2-47: 低代码生成任务。设计来源: 14-低代码平台设计、47-低代码设计器交互详设
 * 后端 LcGeneratorTask domain + GeneratorTaskController 5 端点
 * 状态机: PENDING → RUNNING → SUCCESS / FAILED / CONFLICT / CANCELLED
 * target_scope: DDL / JAVA / VUE / UNIAPP / OPENAPI
 *  - GET    /lowcode/generator-tasks          → 分页查询
 *  - GET    /lowcode/generator-tasks/{id}     → 详情
 *  - POST   /lowcode/generator-tasks          → 创建 (PENDING)
 *  - POST   /lowcode/generator-tasks/{id}/run → 执行 (PENDING→RUNNING→SUCCESS/CONFLICT/FAILED)
 *  - POST   /lowcode/generator-tasks/{id}/cancel → 取消 (PENDING/RUNNING→CANCELLED)
 * ========================================================================== */

/** 生成任务状态枚举。对齐后端 LcGeneratorTask.STATUS_* 常量 */
export const LC_GENERATOR_TASK_STATUS = {
  PENDING: 'PENDING',
  RUNNING: 'RUNNING',
  SUCCESS: 'SUCCESS',
  FAILED: 'FAILED',
  CONFLICT: 'CONFLICT',
  CANCELLED: 'CANCELLED',
} as const

/** 生成任务状态选项 (供 el-select/el-tag 渲染) */
export const LC_GENERATOR_TASK_STATUS_OPTIONS: Array<{
  value: string
  label: string
  tagType: '' | 'primary' | 'success' | 'warning' | 'info' | 'danger'
}> = [
  { value: LC_GENERATOR_TASK_STATUS.PENDING, label: '待执行', tagType: 'info' },
  { value: LC_GENERATOR_TASK_STATUS.RUNNING, label: '执行中', tagType: 'primary' },
  { value: LC_GENERATOR_TASK_STATUS.SUCCESS, label: '成功', tagType: 'success' },
  { value: LC_GENERATOR_TASK_STATUS.FAILED, label: '失败', tagType: 'danger' },
  { value: LC_GENERATOR_TASK_STATUS.CONFLICT, label: '有冲突', tagType: 'warning' },
  { value: LC_GENERATOR_TASK_STATUS.CANCELLED, label: '已取消', tagType: 'info' },
]

/** 生成任务目标范围枚举。对齐后端 LcGeneratorTask.SCOPE_* 常量 */
export const LC_GENERATOR_SCOPE = {
  DDL: 'DDL',
  JAVA: 'JAVA',
  VUE: 'VUE',
  UNIAPP: 'UNIAPP',
  OPENAPI: 'OPENAPI',
} as const

/** 生成任务目标范围选项 */
export const LC_GENERATOR_SCOPE_OPTIONS: Array<{ value: string; label: string; desc: string }> = [
  { value: LC_GENERATOR_SCOPE.DDL, label: 'DDL 迁移脚本', desc: 'PostgreSQL Flyway 迁移文件 (.sql)' },
  { value: LC_GENERATOR_SCOPE.JAVA, label: 'Java 实体', desc: '后端 Entity domain 骨架 (.java)' },
  { value: LC_GENERATOR_SCOPE.VUE, label: 'Vue3 列表页', desc: 'Web Admin 列表页骨架 (.vue)' },
  { value: LC_GENERATOR_SCOPE.UNIAPP, label: 'Uniapp 页面', desc: '移动端页面骨架 (.vue)' },
  { value: LC_GENERATOR_SCOPE.OPENAPI, label: 'OpenAPI 片段', desc: 'OpenAPI 3.1 路径片段 (.yaml)' },
]

/** 生成任务 diff JSON 解析结构 (对齐后端 GeneratorTaskApplicationService.buildDiffJson) */
export interface LcGeneratorDiff {
  added: string[]
  modified: string[]
  deleted: string[]
  conflict: string[]
}

/** 低代码生成任务。GA2-47: 对齐后端 LcGeneratorTask domain */
export interface LcGeneratorTask {
  id: string
  tenantId?: string
  taskNo: string
  entityId?: string
  pageId?: string
  templateVersion?: string
  /** DDL / JAVA / VUE / UNIAPP / OPENAPI */
  targetScope: string
  /** 差异预览 JSON: {added:[],modified:[],deleted:[],conflict:[]} */
  diffJson?: string
  conflictCount?: number
  status: string
  resultFileId?: string
  errorMessage?: string
  startedTime?: string
  finishedTime?: string
  createdBy?: string
  createdTime?: string
  updatedBy?: string
  updatedTime?: string
  version?: number
}

/** 创建生成任务请求。对齐后端 CreateGeneratorTaskRequest DTO */
export interface CreateGeneratorTaskRequest {
  entityId?: string
  pageId?: string
  templateVersion?: string
  /** DDL / JAVA / VUE / UNIAPP / OPENAPI */
  targetScope: string
  /** 幂等键，同 tenantId+lowcode+task+idempotencyKey 已存在直接返回 */
  idempotencyKey?: string
}

/** 库存流水 (不可变, 对应后端 InvStockTransaction domain) */
export interface InvStockTransaction {
  id: string
  /** 流水号 TXyyyyMMddNNNNNN */
  transactionNo: string
  balanceId: string
  materialId: string
  warehouseId: string
  /** IN 入库 / OUT 出库 / COMPENSATE 补偿 */
  transactionType: string
  /** 变动数量 (IN 正 / OUT 负 / COMPENSATE 正) */
  quantity: number
  /** 变动前数量 */
  quantityBefore: number
  /** 变动后数量 */
  quantityAfter: number
  /** 关联业务单据类型 INBOUND/OUTBOUND */
  bizOrderType: string
  bizOrderId: string
  bizOrderNo: string
  remark?: string
  createdBy?: string
  createdTime?: string
}

/** 新建/更新物料请求 (对应后端 SaveMaterialRequest DTO) */
export interface SaveMaterialRequest {
  materialCode: string
  materialName: string
  materialType?: string
  spec?: string
  unit?: string
  category?: string
  barcode?: string
  referencePrice?: number
}

/** 入库请求 (对应后端 InboundRequest DTO, 幂等键必填) */
export interface InboundRequest {
  /** 幂等键 (防重复提交) */
  idempotencyKey: string
  warehouseId: string
  materialId: string
  quantity: number
  unitCost?: number
  /** PURCHASE/RETURN/TRANSFER_IN/INITIAL */
  inboundType?: string
  batchNo?: string
  supplier?: string
}

/** 出库请求 (对应后端 OutboundRequest DTO, 幂等键必填) */
export interface OutboundRequest {
  /** 幂等键 (防重复提交) */
  idempotencyKey: string
  warehouseId: string
  materialId: string
  quantity: number
  unitCost?: number
  /** SALE/SCRAP/TRANSFER_OUT */
  outboundType?: string
  batchNo?: string
  customer?: string
}

/* ==========================================================================
 * GA2-L191: 低代码规则与表达式 (36-低代码高级能力设计)
 * ========================================================================== */

/** 规则类型枚举 */
export const LC_RULE_TYPE = {
  VALIDATION: 'VALIDATION',
  VISIBILITY: 'VISIBILITY',
  LINKAGE: 'LINKAGE',
  COMPUTATION: 'COMPUTATION',
  DEFAULT_VALUE: 'DEFAULT_VALUE',
  READONLY: 'READONLY',
} as const

export const LC_RULE_TYPE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: LC_RULE_TYPE.VALIDATION, label: '校验' },
  { value: LC_RULE_TYPE.VISIBILITY, label: '显隐' },
  { value: LC_RULE_TYPE.LINKAGE, label: '联动' },
  { value: LC_RULE_TYPE.COMPUTATION, label: '计算' },
  { value: LC_RULE_TYPE.DEFAULT_VALUE, label: '默认值' },
  { value: LC_RULE_TYPE.READONLY, label: '只读' },
]

/** 规则模型 (对齐后端 Rule record) */
export interface LcRule {
  ruleId: string
  ruleName: string
  ruleType: string
  expression: string
  message?: string
  targetField?: string
  priority?: number
}

/** 规则求值结果 (对齐后端 RuleEvalResult record) */
export interface LcRuleEvalResult {
  validations: string[]
  visibility: Record<string, boolean>
  computations: Record<string, unknown>
  defaults: Record<string, unknown>
  readonly: Record<string, boolean>
  linkage: Record<string, unknown>
}

/** 表达式校验结果 */
export interface LcExpressionValidationResult {
  valid: boolean
  error: string | null
}

/* ==========================================================================
 * GA2-L191: 低代码组件协议注册表 (36-低代码高级能力设计)
 * ========================================================================== */

export const LC_COMPONENT_PLATFORM = {
  WEB: 'WEB',
  MOBILE: 'MOBILE',
  BOTH: 'BOTH',
} as const

export const LC_COMPONENT_PLATFORM_OPTIONS: Array<{ value: string; label: string }> = [
  { value: LC_COMPONENT_PLATFORM.WEB, label: 'Web' },
  { value: LC_COMPONENT_PLATFORM.MOBILE, label: 'Mobile' },
  { value: LC_COMPONENT_PLATFORM.BOTH, label: '全平台' },
]

export const LC_COMPONENT_CATEGORY = {
  INPUT: 'INPUT',
  DISPLAY: 'DISPLAY',
  CONTAINER: 'CONTAINER',
  TABLE: 'TABLE',
  BUSINESS: 'BUSINESS',
  MOBILE: 'MOBILE',
  CHART: 'CHART',
} as const

export const LC_COMPONENT_CATEGORY_OPTIONS: Array<{ value: string; label: string }> = [
  { value: LC_COMPONENT_CATEGORY.INPUT, label: '输入' },
  { value: LC_COMPONENT_CATEGORY.DISPLAY, label: '展示' },
  { value: LC_COMPONENT_CATEGORY.CONTAINER, label: '容器' },
  { value: LC_COMPONENT_CATEGORY.TABLE, label: '表格' },
  { value: LC_COMPONENT_CATEGORY.BUSINESS, label: '业务' },
  { value: LC_COMPONENT_CATEGORY.MOBILE, label: '移动端' },
  { value: LC_COMPONENT_CATEGORY.CHART, label: '图表' },
]

export const LC_COMPONENT_GRADE = {
  STABLE: 'STABLE',
  EXPERIMENTAL: 'EXPERIMENTAL',
  INTERNAL: 'INTERNAL',
} as const

export const LC_COMPONENT_GRADE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: LC_COMPONENT_GRADE.STABLE, label: '稳定' },
  { value: LC_COMPONENT_GRADE.EXPERIMENTAL, label: '实验性' },
  { value: LC_COMPONENT_GRADE.INTERNAL, label: '内部' },
]

export const LC_COMPONENT_STATUS = {
  DRAFT: 'DRAFT',
  PUBLISHED: 'PUBLISHED',
  DISABLED: 'DISABLED',
} as const

export const LC_COMPONENT_STATUS_OPTIONS: Array<{ value: string; label: string; tagType: '' | 'primary' | 'success' | 'warning' | 'info' | 'danger' }> = [
  { value: LC_COMPONENT_STATUS.DRAFT, label: '草稿', tagType: 'info' },
  { value: LC_COMPONENT_STATUS.PUBLISHED, label: '已发布', tagType: 'success' },
  { value: LC_COMPONENT_STATUS.DISABLED, label: '已禁用', tagType: 'warning' },
]

/** 组件协议元数据 (对齐后端 LcComponentRegistry) */
export interface LcComponentRegistry {
  id: string
  tenantId: string
  componentCode: string
  componentName: string
  componentType: string
  displayName: string
  platform: string
  category: string
  compatibilityGrade: string
  propsSchema: string | null
  eventSchema: string | null
  dataBinding: string | null
  permissionSupport: boolean
  validationSupport: boolean
  permissionCode: string | null
  componentVersion: string
  minPlatformVersion: string | null
  maxPlatformVersion: string | null
  description: string | null
  icon: string | null
  status: string
  deprecated: boolean
  deprecatedMessage: string | null
  sortNo: number
  createdBy: string | null
  createdTime: string
  updatedBy: string | null
  updatedTime: string
  version: number
  remark: string | null
}

/** 保存组件协议请求 */
export interface SaveLcComponentRegistryRequest {
  id?: string
  version?: number
  componentCode: string
  componentName: string
  componentType: string
  displayName: string
  platform?: string
  category: string
  compatibilityGrade?: string
  propsSchema?: string
  eventSchema?: string
  dataBinding?: string
  permissionSupport?: boolean
  validationSupport?: boolean
  permissionCode?: string
  componentVersion?: string
  minPlatformVersion?: string
  maxPlatformVersion?: string
  description?: string
  icon?: string
  status?: string
  deprecated?: boolean
  deprecatedMessage?: string
  sortNo?: number
  remark?: string
}

/** 组件协议分页查询参数 */
export interface LcComponentRegistryPageQuery extends PageRequest {
  category?: string
  platform?: string
  status?: string
  keyword?: string
}

// ============================================================================
// AI 供应商管理类型。对齐后端 AiProvider 域 / AiProviderHealth 健康状态契约。
// 设计来源: 13-AI能力设计 provider registry / 37-AI治理与评测设计 供应商健康检查
// 端点:
//   - GET  /api/v1/ai/providers            → listProviders (供应商列表)
//   - POST /api/v1/ai/providers/health-check → checkProvidersHealth (触发健康检查)
//   - GET  /api/v1/ai/providers/health      → getProvidersHealth (查询最近健康状态)
// ============================================================================

/** AI 供应商配置。GET /ai/providers 返回。 */
export interface AiProvider {
  /** 主键 ID (启用/禁用/编辑时需要) */
  id?: string
  /** 乐观锁版本号 (启用/禁用/编辑时需要) */
  version?: number
  /** 供应商编码 (如 OPENAI / AZURE / DOUBAO / QWEN) */
  providerCode: string
  /** 供应商展示名称 */
  providerName: string
  /** API 调用端点 */
  endpoint?: string
  /** 密钥引用 (JSON {"apiKey":"sk-xxx"} 或明文, 编辑时透传) */
  apiKeyRef?: string
  /** 可用模型列表 JSON (后端 model_list_json) */
  modelListJson?: string
  /** 协议: OPENAI_COMPATIBLE / CUSTOM */
  protocol?: string
  /** 是否启用 */
  enabled: boolean
  /** 优先级 (数字越小优先级越高) */
  priority?: number
  /** 该供应商下可用模型数量 (后端不直接返回时可由前端从 modelListJson 计算) */
  modelCount?: number
  /** 创建时间 (ISO-8601) */
  createdTime?: string
  /** 更新时间 (ISO-8601) */
  updatedTime?: string
}

/** 单个供应商健康状态。GET /ai/providers/health 返回数组元素。 */
export interface AiProviderHealth {
  /** 供应商编码 (与 AiProvider.providerCode 对应) */
  providerCode: string
  /** 供应商展示名称 (冗余字段, 便于页面直接渲染) */
  providerName?: string
  /** 端点是否可达 */
  reachable: boolean
  /** 探测响应耗时 (ms) */
  latencyMs?: number
  /** 默认模型是否可用 */
  defaultModelOk?: boolean
  /** 失败时的错误信息 (reachable=false 时填充) */
  errorMessage?: string
  /** 最近一次检查时间 (ISO-8601) */
  checkedAt?: string
}

