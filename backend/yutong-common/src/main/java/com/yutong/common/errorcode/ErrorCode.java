package com.yutong.common.errorcode;

/**
 * 全量错误码枚举。messageKey 对齐 {@code contracts/registries/errors.yaml} 权威注册表。
 * 设计来源: 48-错误码注册表与API契约详设、98-后端实现蓝图与代码骨架详设
 *
 * <p>规则:
 * <ul>
 *   <li>每个枚举的 code/httpStatus/messageKey 必须与 errors.yaml 完全一致</li>
 *   <li>CI 流水线 {@code tools/checks/check-error-registry} 会校验一致性</li>
 *   <li>新增错误码必须先在 errors.yaml 注册，再在此枚举添加</li>
 *   <li>SYS-403001 为废弃兼容码，新增权限拒绝使用 AUTH-403001 或 AUTH-403002</li>
 * </ul>
 */
public enum ErrorCode {

    // ===== SYS 系统级 4xx =====
    SYS_PARAM_INVALID("SYS-400001", 400, "common.validation.invalid"),
    SYS_PARAM_MISSING("SYS-400002", 400, "common.validation.required"),
    SYS_UNAUTHORIZED("SYS-401001", 401, "auth.error.unauthenticated"),
    SYS_FORBIDDEN("SYS-403001", 403, "auth.error.forbiddenLegacy"),
    SYS_NOT_FOUND("SYS-404001", 404, "common.error.notFound"),
    SYS_OPTIMISTIC_LOCK("SYS-409001", 409, "common.error.optimisticLock"),
    SYS_IDEMPOTENCY_PROCESSING("SYS-409002", 409, "common.error.idempotencyProcessing"),
    SYS_IDEMPOTENCY_CONFLICT("SYS-409003", 409, "common.error.idempotencyMismatch"),
    SYS_BUSINESS_CONFLICT("SYS-409004", 409, "common.error.businessConflict"),
    SYS_RATE_LIMITED("SYS-429001", 429, "common.error.rateLimited"),

    // ===== SYS 系统级 5xx =====
    SYS_INTERNAL_ERROR("SYS-500001", 500, "common.error.internal"),
    SYS_SERVICE_UNAVAILABLE("SYS-500002", 503, "common.error.dependencyUnavailable"),

    // ===== AUTH 认证授权 =====
    AUTH_LOGIN_FAILED("AUTH-401001", 401, "auth.error.loginFailed"),
    AUTH_TOKEN_EXPIRED("AUTH-401002", 401, "auth.error.tokenExpired"),
    AUTH_PERMISSION_DENIED("AUTH-403001", 403, "auth.error.permissionDenied"),
    AUTH_DATA_SCOPE_DENIED("AUTH-403002", 403, "auth.error.dataScopeDenied"),

    // ===== BIZ 样例业务 =====
    BIZ_REQUEST_ITEMS_EMPTY("BIZ-400001", 400, "biz.request.error.itemsRequired"),
    BIZ_REQUEST_CUSTOMER_REQUIRED("BIZ-400002", 400, "biz.request.error.customerRequired"),
    BIZ_REQUEST_REJECT_OPINION_REQUIRED("BIZ-400003", 400, "biz.request.error.rejectOpinionRequired"),
    BIZ_REQUEST_NOT_FOUND("BIZ-404001", 404, "biz.request.error.notFound"),
    BIZ_CUSTOMER_NOT_FOUND("BIZ-404002", 404, "biz.customer.error.notFound"),
    BIZ_PRODUCT_NOT_FOUND("BIZ-404003", 404, "biz.product.error.notFound"),
    BIZ_REQUEST_STATUS_SUBMIT_NOT_ALLOWED("BIZ-409001", 409, "biz.request.error.submitNotAllowed"),
    BIZ_REQUEST_STATUS_APPROVE_NOT_ALLOWED("BIZ-409002", 409, "biz.request.error.approvalNotAllowed"),
    BIZ_REQUEST_NO_DUPLICATE("BIZ-409003", 409, "biz.request.error.requestNoExists"),
    BIZ_REQUEST_STATUS_WITHDRAW_NOT_ALLOWED("BIZ-409004", 409, "biz.request.error.withdrawNotAllowed"),
    BIZ_CUSTOMER_CODE_DUPLICATE("BIZ-409005", 409, "biz.customer.error.codeExists"),
    BIZ_PRODUCT_CODE_DUPLICATE("BIZ-409006", 409, "biz.product.error.codeExists"),
    BIZ_CUSTOMER_REFERENCED("BIZ-409007", 409, "biz.customer.error.referenced"),
    BIZ_PRODUCT_REFERENCED("BIZ-409008", 409, "biz.product.error.referenced"),

    // ===== WORK 工单中心 (35 号文档 P1) =====
    WORK_TICKET_NOT_FOUND("WORK-404001", 404, "work.ticket.error.notFound"),
    WORK_CATEGORY_NOT_FOUND("WORK-404002", 404, "work.ticket.error.categoryNotFound"),
    WORK_TICKET_STATUS_NOT_ALLOWED("WORK-409001", 409, "work.ticket.error.statusNotAllowed"),
    WORK_TICKET_HANDLER_REQUIRED("WORK-400001", 400, "work.ticket.error.handlerRequired"),
    WORK_TICKET_EVALUATION_SCORE_INVALID("WORK-400002", 400, "work.ticket.error.scoreInvalid"),

    // ===== CTR 合同档案 (35 号文档 P1) =====
    CTR_CONTRACT_NOT_FOUND("CTR-404001", 404, "biz.contract.error.notFound"),
    CTR_CONTRACT_VERSION_NOT_FOUND("CTR-404002", 404, "biz.contract.error.versionNotFound"),
    CTR_CONTRACT_STATUS_NOT_ALLOWED("CTR-409001", 409, "biz.contract.error.statusNotAllowed"),
    CTR_CONTRACT_TITLE_REQUIRED("CTR-400001", 400, "biz.contract.error.titleRequired"),
    CTR_CONTRACT_APPROVAL_OPINION_REQUIRED("CTR-400002", 400, "biz.contract.error.approvalOpinionRequired"),
    CTR_CONTRACT_ARCHIVED_READ_ONLY("CTR-409002", 409, "biz.contract.error.archivedReadOnly"),
    CTR_CONTRACT_NO_DUPLICATE("CTR-409003", 409, "biz.contract.error.contractNoExists"),

    // ===== FILE 文件 =====
    FILE_TYPE_NOT_ALLOWED("FILE-400001", 400, "system.file.error.type"),
    FILE_SIZE_EXCEEDED("FILE-400002", 400, "system.file.error.size"),
    FILE_NOT_FOUND("FILE-404001", 404, "system.file.error.notFound"),
    FILE_BIND_INVALID("FILE-409001", 409, "system.file.error.bindInvalid"),
    FILE_MEDIA_TYPE_UNSUPPORTED("FILE-415001", 415, "system.file.error.mediaTypeUnsupported"),

    // ===== MSG 消息 =====
    MSG_NOT_FOUND("MSG-404001", 404, "system.message.error.notFound"),

    // ===== JOB 任务/导入导出 =====
    JOB_IMPORT_FORMAT_INVALID("JOB-400001", 400, "system.job.error.importFormat"),
    JOB_ALREADY_RUNNING("JOB-409001", 409, "system.job.error.alreadyRunning"),
    JOB_IDEMPOTENCY_DUPLICATE("JOB-409002", 409, "system.job.error.idempotencyDuplicate"),
    JOB_IMPORT_EXPORT_FAILED("JOB-500001", 500, "system.job.error.executionFailed"),

    // ===== LC 低代码 =====
    LC_META_VALIDATION_FAILED("LC-400001", 400, "lowcode.entity.error.validation"),
    LC_PUBLISH_FORBIDDEN("LC-403001", 403, "lowcode.page.error.publishDenied"),
    LC_ENTITY_CODE_DUPLICATE("LC-409001", 409, "lowcode.entity.error.codeExists"),
    LC_PAGE_CODE_DUPLICATE("LC-409002", 409, "lowcode.page.error.codeExists"),
    LC_GENERATE_CONFLICT("LC-409003", 409, "lowcode.generator.error.conflict"),

    // ===== AI =====
    AI_PROMPT_INVALID("AI-400001", 400, "ai.error.inputInvalid"),
    AI_TOOL_DENIED("AI-403001", 403, "ai.error.toolDenied"),
    AI_QUOTA_EXCEEDED("AI-429001", 429, "ai.error.budgetExceeded"),
    AI_PROVIDER_ERROR("AI-500001", 500, "ai.error.providerFailure"),
    AI_SERVICE_DEGRADED("AI-503001", 503, "ai.error.serviceDegraded"),

    // ===== RPT 报表 (35 号文档 P1 报表分析, 42 号文档 R1) =====
    RPT_DATASET_SQL_INVALID("RPT-400001", 400, "report.dataset.error.sqlInvalid"),
    RPT_PERMISSION_DENIED("RPT-403001", 403, "report.error.permissionDenied"),
    RPT_DATASOURCE_NOT_READONLY("RPT-403002", 403, "report.datasource.error.readOnlyRequired"),
    RPT_NOT_FOUND("RPT-404001", 404, "report.error.notFound"),
    RPT_DATASET_REFERENCED("RPT-409001", 409, "report.dataset.error.referenced"),
    RPT_DATASET_EXECUTION_FAILED("RPT-500001", 500, "report.dataset.error.executionFailed"),

    // ===== DS 数据源 (46 号文档 v1.5 多数据源与数据集) =====
    /** GA2-46: 数据源参数无效 */
    DS_PARAMETER_INVALID("DS-400001", 400, "common.datasource.error.parameterInvalid"),
    /** GA2-46: 仅允许只读 SELECT 或已批准 VIEW */
    DS_READ_ONLY_REQUIRED("DS-400002", 400, "common.datasource.error.readOnlyRequired"),
    /** GA2-46: 数据源访问未授权 */
    DS_PERMISSION_DENIED("DS-403001", 403, "common.datasource.error.permissionDenied"),
    /** GA2-46: 数据源不存在 */
    DS_NOT_FOUND("DS-404001", 404, "common.datasource.error.notFound"),
    /** GA2-46: 数据源编码已存在 */
    DS_CODE_DUPLICATE("DS-409001", 409, "common.datasource.error.codeExists"),
    /** GA2-46: 查询超出速率/行数/资源限制 */
    DS_QUERY_LIMITED("DS-429001", 429, "common.datasource.error.queryLimited"),
    /** GA2-46: 数据源连接或查询失败 */
    DS_CONNECTION_FAILED("DS-500001", 500, "common.datasource.error.connectionFailed"),
    /** GA2-46: 数据源不可用或熔断中 */
    DS_UNAVAILABLE("DS-503001", 503, "common.datasource.error.unavailable"),

    // ===== IVT 库存出入库 (35 号文档 P1 库存出入库) =====
    IVT_MATERIAL_NOT_FOUND("IVT-404001", 404, "inventory.material.error.notFound"),
    IVT_WAREHOUSE_NOT_FOUND("IVT-404002", 404, "inventory.warehouse.error.notFound"),
    IVT_INBOUND_NOT_FOUND("IVT-404003", 404, "inventory.inbound.error.notFound"),
    IVT_OUTBOUND_NOT_FOUND("IVT-404004", 404, "inventory.outbound.error.notFound"),
    IVT_STOCK_INSUFFICIENT("IVT-409001", 409, "inventory.stock.error.insufficient"),
    IVT_STATUS_NOT_ALLOWED("IVT-409002", 409, "inventory.error.statusNotAllowed"),
    IVT_IDEMPOTENT_DUPLICATE("IVT-409003", 409, "inventory.error.idempotentDuplicate"),
    IVT_QUANTITY_INVALID("IVT-400001", 400, "inventory.error.quantityInvalid"),

    // ===== EXT 外部接口同步 (35 号文档 P2 外部接口同步) =====
    EXT_SYSTEM_NOT_FOUND("EXT-404001", 404, "extsync.system.error.notFound"),
    EXT_TASK_NOT_FOUND("EXT-404002", 404, "extsync.task.error.notFound"),
    EXT_RECORD_NOT_FOUND("EXT-404003", 404, "extsync.record.error.notFound"),
    EXT_SYSTEM_CODE_DUPLICATE("EXT-409001", 409, "extsync.system.error.codeExists"),
    EXT_TASK_CODE_DUPLICATE("EXT-409002", 409, "extsync.task.error.codeExists"),
    EXT_TASK_DISABLED("EXT-409003", 409, "extsync.task.error.disabled"),
    EXT_REQUEST_INVALID("EXT-400001", 400, "extsync.error.requestInvalid"),
    EXT_SYNC_EXECUTION_FAILED("EXT-500001", 500, "extsync.error.executionFailed"),

    // ===== KB 知识库运营 (35 号文档 P2 知识库运营) =====
    KB_REQUEST_INVALID("KB-400001", 400, "kb.error.requestInvalid"),
    KB_NOT_FOUND("KB-404001", 404, "kb.error.kbNotFound"),
    KB_DOCUMENT_NOT_FOUND("KB-404002", 404, "kb.error.documentNotFound"),
    KB_CONVERSATION_NOT_FOUND("KB-404003", 404, "kb.error.conversationNotFound"),
    KB_DISABLED("KB-409001", 409, "kb.error.kbDisabled"),
    KB_OPS_INTERNAL_ERROR("KB-500001", 500, "kb.error.internal"),

    // ===== NOT 实时通知 (35 号文档 P2 实时通知 + 44-实时通信与消息推送设计) =====
    NOT_REQUEST_INVALID("NOT-400001", 400, "notification.error.requestInvalid"),
    NOT_TEMPLATE_NOT_FOUND("NOT-404001", 404, "notification.error.templateNotFound"),
    NOT_DISPATCH_NOT_FOUND("NOT-404002", 404, "notification.error.dispatchNotFound"),
    NOT_SUBSCRIPTION_NOT_FOUND("NOT-404003", 404, "notification.error.subscriptionNotFound"),
    NOT_TEMPLATE_DISABLED("NOT-409001", 409, "notification.error.templateDisabled"),
    NOT_DISPATCH_INTERNAL_ERROR("NOT-500001", 500, "notification.error.internal"),

    // ===== PAY 支付订单 (35 号文档 P2 支付订单) =====
    PAY_REQUEST_INVALID("PAY-400001", 400, "payment.error.requestInvalid"),
    PAY_AMOUNT_INVALID("PAY-400002", 400, "payment.error.amountInvalid"),
    PAY_REFUND_AMOUNT_EXCEEDED("PAY-400003", 400, "payment.error.refundAmountExceeded"),
    PAY_ORDER_NOT_FOUND("PAY-404001", 404, "payment.error.orderNotFound"),
    PAY_REFUND_NOT_FOUND("PAY-404002", 404, "payment.error.refundNotFound"),
    PAY_RECON_NOT_FOUND("PAY-404003", 404, "payment.error.reconNotFound"),
    PAY_ORDER_STATUS_NOT_ALLOWED("PAY-409001", 409, "payment.error.orderStatusNotAllowed"),
    PAY_ORDER_NO_DUPLICATE("PAY-409002", 409, "payment.error.orderNoExists"),
    PAY_IDEMPOTENT_DUPLICATE("PAY-409003", 409, "payment.error.idempotentDuplicate"),
    PAY_CALLBACK_IDEMPOTENT("PAY-409004", 409, "payment.error.callbackIdempotent"),
    PAY_RECON_DATE_CHANNEL_DUPLICATE("PAY-409005", 409, "payment.error.reconDateChannelExists"),
    PAY_CALLBACK_VERIFY_FAILED("PAY-409006", 409, "payment.error.callbackVerifyFailed"),
    PAY_INTERNAL_ERROR("PAY-500001", 500, "payment.error.internal"),

    // ===== SUR 问卷表单 (35 号文档 P2 问卷表单, GA2-42) =====
    SUR_REQUEST_INVALID("SUR-400001", 400, "survey.error.requestInvalid"),
    SUR_QUESTION_OPTION_INVALID("SUR-400002", 400, "survey.error.questionOptionInvalid"),
    SUR_ANSWER_VALIDATION_FAILED("SUR-400003", 400, "survey.error.answerValidationFailed"),
    SUR_LOGIC_RULE_INVALID("SUR-400004", 400, "survey.error.logicRuleInvalid"),
    SUR_SURVEY_NOT_FOUND("SUR-404001", 404, "survey.error.surveyNotFound"),
    SUR_QUESTION_NOT_FOUND("SUR-404002", 404, "survey.error.questionNotFound"),
    SUR_RESPONSE_NOT_FOUND("SUR-404003", 404, "survey.error.responseNotFound"),
    SUR_SURVEY_STATUS_NOT_ALLOWED("SUR-409001", 409, "survey.error.surveyStatusNotAllowed"),
    SUR_SURVEY_NO_DUPLICATE("SUR-409002", 409, "survey.error.surveyNoExists"),
    SUR_QUESTION_CODE_DUPLICATE("SUR-409003", 409, "survey.error.questionCodeExists"),
    SUR_RESPONSE_ALREADY_SUBMITTED("SUR-409004", 409, "survey.error.responseAlreadySubmitted"),
    SUR_RESPONSE_LIMIT_EXCEEDED("SUR-409005", 409, "survey.error.responseLimitExceeded"),
    SUR_AI_DRAFT_FAILED("SUR-500001", 500, "survey.error.aiDraftFailed"),

    // ===== WF 工作流 (41 号文档 L1+L2 轻量自研引擎, GA2-44) =====
    WF_REQUEST_INVALID("WF-400001", 400, "workflow.error.requestInvalid"),
    WF_BPMN_XML_INVALID("WF-400002", 400, "workflow.error.bpmnXmlInvalid"),
    WF_PERMISSION_DENIED("WF-403001", 403, "workflow.error.permissionDenied"),
    WF_DEFINITION_NOT_FOUND("WF-404001", 404, "workflow.error.definitionNotFound"),
    WF_INSTANCE_NOT_FOUND("WF-404002", 404, "workflow.error.instanceNotFound"),
    WF_TASK_NOT_FOUND("WF-404003", 404, "workflow.error.taskNotFound"),
    WF_TASK_NOT_ASSIGNEE("WF-409001", 409, "workflow.error.taskNotAssignee"),
    WF_INSTANCE_COMPLETED("WF-409002", 409, "workflow.error.instanceCompleted"),
    WF_DEFINITION_KEY_DUPLICATE("WF-409003", 409, "workflow.error.definitionKeyDuplicate"),
    WF_DEFINITION_NOT_PUBLISHED("WF-409004", 409, "workflow.error.definitionNotPublished"),
    WF_TASK_ALREADY_COMPLETED("WF-409005", 409, "workflow.error.taskAlreadyCompleted"),
    WF_SERVICE_TASK_NOT_WHITELISTED("WF-409006", 409, "workflow.error.serviceTaskNotWhitelisted"),
    WF_INTERNAL_ERROR("WF-500001", 500, "workflow.error.internal"),

    // ===== AIG AI 治理与评测 (37 号文档, GA2-45) =====
    AIG_REQUEST_INVALID("AIG-400001", 400, "ai.governance.error.requestInvalid"),
    AIG_PROMPT_NOT_DRAFT("AIG-400002", 400, "ai.governance.error.promptNotDraft"),
    AIG_TOOL_FORBIDDEN("AIG-403001", 403, "ai.governance.error.toolForbidden"),
    AIG_QUOTA_EXCEEDED("AIG-429001", 429, "ai.governance.error.quotaExceeded"),
    AIG_PROMPT_NOT_FOUND("AIG-404001", 404, "ai.governance.error.promptNotFound"),
    AIG_TOOL_NOT_FOUND("AIG-404002", 404, "ai.governance.error.toolNotFound"),
    AIG_FEEDBACK_NOT_FOUND("AIG-404003", 404, "ai.governance.error.feedbackNotFound"),
    AIG_EVAL_RUN_NOT_FOUND("AIG-404004", 404, "ai.governance.error.evalRunNotFound"),
    AIG_EVAL_DATASET_NOT_FOUND("AIG-404005", 404, "ai.governance.error.evalDatasetNotFound"),
    AIG_PROMPT_CODE_DUPLICATE("AIG-409001", 409, "ai.governance.error.promptCodeDuplicate"),
    AIG_TOOL_NAME_DUPLICATE("AIG-409002", 409, "ai.governance.error.toolNameDuplicate"),
    AIG_PROMPT_ALREADY_PUBLISHED("AIG-409003", 409, "ai.governance.error.promptAlreadyPublished"),
    AIG_EVAL_GATE_FAILED("AIG-409004", 409, "ai.governance.error.evalGateFailed"),
    /** GA2-45: 成本额度 scope 重复 (tenant_id + quota_scope + scope_key + model_code 唯一) */
    AIG_QUOTA_SCOPE_DUPLICATE("AIG-409005", 409, "ai.governance.error.quotaScopeDuplicate"),
    AIG_INTERNAL_ERROR("AIG-500001", 500, "ai.governance.error.internal"),

    // ===== LIC 商业授权与模块开关 (70 号文档, GA2-L170) =====
    /** GA2-L170: 模块未授权 / License 已过期、吊销或无效 */
    LIC_MODULE_DENIED("LIC-403001", 403, "license.error.moduleDenied"),
    /** GA2-L170: 当前周期授权额度已用尽 (checkQuota 抛出) */
    LIC_QUOTA_EXCEEDED("LIC-429001", 429, "license.error.quotaExceeded"),
    /** GA2-L170: License 加载/校验/刷新内部错误 */
    LIC_INTERNAL_ERROR("LIC-500001", 500, "license.error.internal"),
    /** GA2-L174: 上传的 License 文件不是合法 JSON 或格式不支持 */
    LIC_FILE_INVALID("LIC-400001", 400, "license.error.fileInvalid"),
    /** GA2-L174: 上传的 License 文件缺少必填字段 (licenseId/edition/subject/expireTime) */
    LIC_FIELDS_MISSING("LIC-400002", 400, "license.error.fieldsMissing"),
    /** GA2-L174: 上传的 License 文件 schema 版本与运行平台不兼容 */
    LIC_INCOMPATIBLE("LIC-409001", 409, "license.error.incompatible"),

    // ===== PLG 插件与模板生态 (45 号文档) =====
    PLG_REQUEST_INVALID("PLG-400001", 400, "plugin.error.requestInvalid"),
    PLG_PACKAGE_NOT_FOUND("PLG-404001", 404, "plugin.error.packageNotFound"),
    PLG_TEMPLATE_NOT_FOUND("PLG-404002", 404, "plugin.error.templateNotFound"),
    PLG_CODE_DUPLICATE("PLG-409001", 409, "plugin.error.codeDuplicate"),
    PLG_ALREADY_INSTALLED("PLG-409002", 409, "plugin.error.alreadyInstalled"),
    PLG_NOT_INSTALLED("PLG-409003", 409, "plugin.error.notInstalled"),
    PLG_INTERNAL_ERROR("PLG-500001", 500, "plugin.error.internal"),

    // ===== DB 报表大屏 (42 号文档 R1-R3) =====
    DB_REPORT_NOT_FOUND("DB-404001", 404, "dashboard.report.error.notFound"),
    DB_DASHBOARD_NOT_FOUND("DB-404002", 404, "dashboard.error.notFound"),
    DB_WIDGET_NOT_FOUND("DB-404003", 404, "dashboard.widget.error.notFound"),
    DB_CODE_DUPLICATE("DB-409001", 409, "dashboard.error.codeDuplicate"),
    DB_STATUS_NOT_ALLOWED("DB-409002", 409, "dashboard.error.statusNotAllowed"),
    DB_INTERNAL_ERROR("DB-500001", 500, "dashboard.error.internal"),

    // ===== AUTH-ENH 身份增强 (32 号文档) =====
    AUTH_ENH_PROVIDER_NOT_FOUND("AUTH-ENH-404001", 404, "auth.enh.error.providerNotFound"),
    AUTH_ENH_ADMIN_NOT_FOUND("AUTH-ENH-404002", 404, "auth.enh.error.adminNotFound"),
    AUTH_ENH_PROVIDER_DISABLED("AUTH-ENH-409001", 409, "auth.enh.error.providerDisabled"),
    AUTH_ENH_ADMIN_DISABLED("AUTH-ENH-409002", 409, "auth.enh.error.adminDisabled"),
    AUTH_ENH_ADMIN_USAGE_EXCEEDED("AUTH-ENH-409003", 409, "auth.enh.error.adminUsageExceeded"),
    AUTH_ENH_REFRESH_REVOKED("AUTH-ENH-409004", 409, "auth.enh.error.refreshRevoked"),
    AUTH_ENH_INTERNAL_ERROR("AUTH-ENH-500001", 500, "auth.enh.error.internal"),

    // ===== MKT 应用市场 (marketplace) =====
    MKT_400001("MKT-400001", 400, "common.marketplace.error.packageInvalid"),
    MKT_403001("MKT-403001", 403, "common.marketplace.error.permissionDenied"),
    MKT_404001("MKT-404001", 404, "common.marketplace.error.notFound"),
    MKT_409001("MKT-409001", 409, "common.marketplace.error.versionConflict"),
    MKT_500001("MKT-500001", 500, "common.marketplace.error.installFailed");

    private final String code;
    private final int httpStatus;
    private final String messageKey;

    ErrorCode(String code, int httpStatus, String messageKey) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }

    public String code() { return code; }
    public int httpStatus() { return httpStatus; }
    public String messageKey() { return messageKey; }
}
