import service from './request'
import type {
  LcComponentRegistry,
  LcComponentRegistryPageQuery,
  LcEntity,
  LcEntityDetailVO,
  LcExpressionValidationResult,
  LcPage,
  LcPageDetailVO,
  LcRule,
  LcRuleEvalResult,
  PageRequest,
  PageResult,
  SaveLcComponentRegistryRequest,
  SaveLcEntityRequest,
  SaveLcPageRequest,
} from './types'

/**
 * GA2-28: 页面状态枚举与选项 (从 types.ts re-export 供视图层统一从 lowcode.ts 引用)
 * 对齐后端 LcPage.STATUS_* 常量
 */
export { LC_PAGE_STATUS, LC_PAGE_STATUS_OPTIONS } from './types'

/**
 * 低代码实体 API。设计来源: 14-低代码平台设计、08-API契约设计
 * GA2-27: 路径对齐后端 LcEntityController @RequestMapping("/api/v1/lowcode/entities"),
 *         baseURL 已含 /api/v1, 此处用相对路径避免双前缀。
 *  - GET    /lowcode/entities          → listLowcodeEntities (分页查询)
 *  - GET    /lowcode/entities/{id}     → getLowcodeEntity (详情, 含 fields + relations)
 *  - POST   /lowcode/entities          → createLowcodeEntityDraft (创建草稿)
 *  - PUT    /lowcode/entities/{id}     → updateLowcodeEntity (更新, 需 version 乐观锁)
 *  - POST   /lowcode/entities/{id}/publish  → publishLowcodeEntity (发布版本)
 *  - POST   /lowcode/entities/{id}/disable  → deleteLowcodeEntity (禁用)
 */

/** 实体状态枚举。对齐后端 LcEntity.STATUS_* 常量 */
export const LC_ENTITY_STATUS = {
  DRAFT: 'DRAFT',
  PUBLISHED: 'PUBLISHED',
  DISABLED: 'DISABLED',
} as const

/** 实体状态选项 (供 el-select/el-tag 渲染) */
export const LC_ENTITY_STATUS_OPTIONS: Array<{
  value: string
  label: string
  tagType: '' | 'primary' | 'success' | 'warning' | 'info' | 'danger'
}> = [
  { value: LC_ENTITY_STATUS.DRAFT, label: '草稿', tagType: 'info' },
  { value: LC_ENTITY_STATUS.PUBLISHED, label: '已发布', tagType: 'success' },
  { value: LC_ENTITY_STATUS.DISABLED, label: '已禁用', tagType: 'warning' },
]

/** 分页查询实体列表: GET /lowcode/entities */
export function getEntities(
  params?: PageRequest & { entityCode?: string; entityName?: string; status?: string }
): Promise<PageResult<LcEntity>> {
  return service.get('/lowcode/entities', { params }) as unknown as Promise<
    PageResult<LcEntity>
  >
}

/** 查询实体详情 (含 fields + relations): GET /lowcode/entities/{id} */
export function getEntity(id: string): Promise<LcEntityDetailVO> {
  return service.get(`/lowcode/entities/${id}`) as unknown as Promise<LcEntityDetailVO>
}

/** 创建实体草稿: POST /lowcode/entities (需 lc:entity:add 权限) */
export function createEntity(data: SaveLcEntityRequest): Promise<LcEntity> {
  return service.post('/lowcode/entities', data) as unknown as Promise<LcEntity>
}

/**
 * 更新实体 (需 lc:entity:edit 权限)
 * 关键约束: 必须传 version 乐观锁, 版本不匹配后端拒绝 (返回 BusinessConflictException)
 */
export function updateEntity(
  id: string,
  data: SaveLcEntityRequest
): Promise<LcEntity> {
  return service.put(`/lowcode/entities/${id}`, data) as unknown as Promise<LcEntity>
}

/** 保存草稿 (新建或更新统一入口, id 为空走新建, 否则更新) */
export function saveEntityDraft(data: SaveLcEntityRequest): Promise<LcEntity> {
  if (data.id) {
    return updateEntity(data.id, data)
  }
  return createEntity(data)
}

/**
 * 发布实体版本: POST /lowcode/entities/{id}/publish?version={version}
 * 设计来源: 14-低代码平台设计 版本、发布与回滚
 *  - 发布时生成不可变版本快照, 记录发布人/发布时间/版本说明/config_hash
 *  - 运行时只读取已发布版本, 设计器可查看草稿和历史版本
 */
export function publishEntity(id: string, version: number): Promise<LcEntity> {
  return service.post(`/lowcode/entities/${id}/publish`, null, {
    params: { version },
  }) as unknown as Promise<LcEntity>
}

/**
 * 禁用实体: POST /lowcode/entities/{id}/disable?version={version}
 * 设计来源: 14-低代码平台设计, 禁用后运行时不再读取该实体元模型
 */
export function disableEntity(id: string, version: number): Promise<LcEntity> {
  return service.post(`/lowcode/entities/${id}/disable`, null, {
    params: { version },
  }) as unknown as Promise<LcEntity>
}

/* ==========================================================================
 * 低代码页面 API (GA2-28)
 * 设计来源: 14-低代码平台设计 lc_page / lc_component / lc_action
 * 16-原型与交互体验设计 低代码设计器 (左侧组件区 + 中间画布 + 右侧属性面板)
 * GA2-28: 路径对齐后端 LcPageController @RequestMapping("/api/v1/lowcode/pages"),
 *         baseURL 已含 /api/v1, 此处用相对路径避免双前缀。
 *  - GET    /lowcode/pages                → listLowcodePages (分页查询)
 *  - GET    /lowcode/pages/{id}           → getLowcodePage (详情, 含 components + actions)
 *  - POST   /lowcode/pages                → createLowcodePageDraft (创建草稿)
 *  - PUT    /lowcode/pages/{id}           → updateLowcodePage (更新, 需 version 乐观锁)
 *  - POST   /lowcode/pages/{id}/preview   → previewLowcodePage (预览, 返回 LcPageDetailVO)
 *  - POST   /lowcode/pages/{id}/publish   → publishLowcodePage (发布版本)
 *  - POST   /lowcode/pages/{id}/rollback  → rollbackLowcodePage (回滚到历史版本)
 * ========================================================================== */

/** 分页查询页面列表: GET /lowcode/pages */
export function getPages(
  params?: PageRequest & {
    pageCode?: string
    pageName?: string
    status?: string
    pageType?: string
    entityId?: string
  }
): Promise<PageResult<LcPage>> {
  return service.get('/lowcode/pages', { params }) as unknown as Promise<
    PageResult<LcPage>
  >
}

/** 查询页面详情 (含 components + actions): GET /lowcode/pages/{id} */
export function getPage(id: string): Promise<LcPageDetailVO> {
  return service.get(`/lowcode/pages/${id}`) as unknown as Promise<LcPageDetailVO>
}

/** 创建页面草稿: POST /lowcode/pages (需 lc:page:add 权限) */
export function createPage(data: SaveLcPageRequest): Promise<LcPage> {
  return service.post('/lowcode/pages', data) as unknown as Promise<LcPage>
}

/**
 * 更新页面 (需 lc:page:edit 权限)
 * 关键约束: 必须传 version 乐观锁, 版本不匹配后端拒绝
 */
export function updatePage(
  id: string,
  data: SaveLcPageRequest
): Promise<LcPage> {
  return service.put(`/lowcode/pages/${id}`, data) as unknown as Promise<LcPage>
}

/** 保存草稿 (新建或更新统一入口, id 为空走新建, 否则更新) */
export function savePageDraft(data: SaveLcPageRequest): Promise<LcPage> {
  if (data.id) {
    return updatePage(data.id, data)
  }
  return createPage(data)
}

/**
 * 预览页面: POST /lowcode/pages/{id}/preview
 * 设计来源: 14-低代码平台设计, 预览使用草稿最新配置 (不发布即可预览)
 * 返回 LcPageDetailVO (含 components + actions)
 */
export function previewPage(id: string): Promise<LcPageDetailVO> {
  return service.post(`/lowcode/pages/${id}/preview`) as unknown as Promise<LcPageDetailVO>
}

/**
 * 发布页面版本: POST /lowcode/pages/{id}/publish?version={version}
 * 设计来源: 14-低代码平台设计 版本、发布与回滚
 *  - 发布时生成不可变版本快照, 记录发布人/发布时间/版本说明/config_hash
 *  - 运行时只读取已发布版本, 设计器可查看草稿和历史版本
 */
export function publishPage(id: string, version: number): Promise<LcPage> {
  return service.post(`/lowcode/pages/${id}/publish`, null, {
    params: { version },
  }) as unknown as Promise<LcPage>
}

/**
 * 回滚页面版本: POST /lowcode/pages/{id}/rollback?version={version}
 * 设计来源: 14-低代码平台设计, 回滚 = 历史版本复制为草稿后重新发布
 * 回滚后 status 变为 PUBLISHED, version_no 增加
 */
export function rollbackPage(id: string, version: number): Promise<LcPage> {
  return service.post(`/lowcode/pages/${id}/rollback`, null, {
    params: { version },
  }) as unknown as Promise<LcPage>
}

/* ==========================================================================
 * GA2-L191: 低代码规则与表达式 API (36-低代码高级能力设计)
 *  - POST /lowcode/rules/evaluate           → evaluateLowcodeRules
 *  - POST /lowcode/rules/validate-expression → validateLowcodeExpression
 * ========================================================================== */

/** 求值规则集合: POST /lowcode/rules/evaluate */
export function evaluateRules(
  rules: LcRule[],
  context: Record<string, unknown>
): Promise<LcRuleEvalResult> {
  return service.post('/lowcode/rules/evaluate', { rules, context }) as unknown as Promise<
    LcRuleEvalResult
  >
}

/** 校验表达式语法: POST /lowcode/rules/validate-expression */
export function validateExpression(
  expression: string,
  context?: Record<string, unknown>
): Promise<LcExpressionValidationResult> {
  return service.post('/lowcode/rules/validate-expression', {
    expression,
    context,
  }) as unknown as Promise<LcExpressionValidationResult>
}

/* ==========================================================================
 * GA2-L191: 低代码组件协议注册表 API (36-低代码高级能力设计)
 *  - GET    /lowcode/components/registry              → listLowcodeComponentRegistry
 *  - GET    /lowcode/components/registry/published     → listPublishedLowcodeComponentRegistry
 *  - GET    /lowcode/components/registry/categories/{category}
 *  - GET    /lowcode/components/registry/{id}          → getLowcodeComponentRegistry
 *  - POST   /lowcode/components/registry               → createLowcodeComponentRegistry
 *  - PUT    /lowcode/components/registry/{id}          → updateLowcodeComponentRegistry
 *  - DELETE /lowcode/components/registry/{id}          → deleteLowcodeComponentRegistry
 *  - POST   /lowcode/components/registry/{id}/publish  → publishLowcodeComponentRegistry
 *  - POST   /lowcode/components/registry/{id}/disable  → disableLowcodeComponentRegistry
 * ========================================================================== */

/** 分页查询组件协议: GET /lowcode/components/registry */
export function getComponentRegistries(
  params?: LcComponentRegistryPageQuery
): Promise<PageResult<LcComponentRegistry>> {
  return service.get('/lowcode/components/registry', { params }) as unknown as Promise<
    PageResult<LcComponentRegistry>
  >
}

/** 已发布组件列表: GET /lowcode/components/registry/published */
export function getPublishedComponents(): Promise<LcComponentRegistry[]> {
  return service.get('/lowcode/components/registry/published') as unknown as Promise<
    LcComponentRegistry[]
  >
}

/** 按分类查询组件: GET /lowcode/components/registry/categories/{category} */
export function getComponentsByCategory(category: string): Promise<LcComponentRegistry[]> {
  return service.get(`/lowcode/components/registry/categories/${category}`) as unknown as Promise<
    LcComponentRegistry[]
  >
}

/** 组件协议详情: GET /lowcode/components/registry/{id} */
export function getComponentRegistry(id: string): Promise<LcComponentRegistry> {
  return service.get(`/lowcode/components/registry/${id}`) as unknown as Promise<
    LcComponentRegistry
  >
}

/** 新建组件协议: POST /lowcode/components/registry */
export function createComponentRegistry(
  data: SaveLcComponentRegistryRequest
): Promise<LcComponentRegistry> {
  return service.post('/lowcode/components/registry', data) as unknown as Promise<
    LcComponentRegistry
  >
}

/** 更新组件协议: PUT /lowcode/components/registry/{id} */
export function updateComponentRegistry(
  id: string,
  data: SaveLcComponentRegistryRequest
): Promise<LcComponentRegistry> {
  return service.put(`/lowcode/components/registry/${id}`, data) as unknown as Promise<
    LcComponentRegistry
  >
}

/** 删除组件协议: DELETE /lowcode/components/registry/{id} */
export function deleteComponentRegistry(id: string): Promise<void> {
  return service.delete(`/lowcode/components/registry/${id}`) as unknown as Promise<void>
}

/** 发布组件协议: POST /lowcode/components/registry/{id}/publish */
export function publishComponentRegistry(id: string): Promise<void> {
  return service.post(`/lowcode/components/registry/${id}/publish`) as unknown as Promise<void>
}

/** 禁用组件协议: POST /lowcode/components/registry/{id}/disable */
export function disableComponentRegistry(id: string): Promise<void> {
  return service.post(`/lowcode/components/registry/${id}/disable`) as unknown as Promise<void>
}
