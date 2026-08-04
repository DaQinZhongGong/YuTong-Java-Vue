-- V002: 系统基础表
-- 设计来源: 57-完整DDL清单与数据字典详设、51-数据库物理模型与DDL详设
-- 约束: 主键统一 varchar(32) ULID，禁止自增；所有业务表含 tenant_id/deleted/version

-- ===== 字典类型 =====
CREATE TABLE sys_dict_type (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    dict_type       varchar(64)   NOT NULL,
    dict_name       varchar(128)  NOT NULL,
    status          varchar(16)   NOT NULL,
    system_flag     boolean       NOT NULL DEFAULT false,
    sort_no         int           NOT NULL DEFAULT 0,
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_sys_dict_type PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_sys_dict_type ON sys_dict_type (tenant_id, dict_type) WHERE deleted = false;
COMMENT ON TABLE sys_dict_type IS '字典类型';

-- ===== 字典项 =====
CREATE TABLE sys_dict_item (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    dict_type       varchar(64)   NOT NULL,
    item_code       varchar(64)   NOT NULL,
    item_label      varchar(128)  NOT NULL,
    item_label_i18n jsonb,
    item_value      varchar(128),
    status          varchar(16)   NOT NULL,
    sort_no         int           NOT NULL DEFAULT 0,
    color_token     varchar(64),
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_sys_dict_item PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_sys_dict_item ON sys_dict_item (tenant_id, dict_type, item_code) WHERE deleted = false;
CREATE INDEX idx_sys_dict_item_type ON sys_dict_item (tenant_id, dict_type, sort_no);
COMMENT ON TABLE sys_dict_item IS '字典项';

-- ===== 参数配置 =====
CREATE TABLE sys_config (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    config_key      varchar(128)  NOT NULL,
    config_value    text,
    value_type      varchar(32)   NOT NULL DEFAULT 'STRING',
    config_group    varchar(64)   NOT NULL DEFAULT 'system',
    editable        boolean       NOT NULL DEFAULT true,
    sensitive       boolean       NOT NULL DEFAULT false,
    status          varchar(16)   NOT NULL,
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_sys_config PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_sys_config_key ON sys_config (tenant_id, config_key) WHERE deleted = false;
COMMENT ON TABLE sys_config IS '参数配置';

-- ===== 文件元数据 =====
CREATE TABLE sys_file (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    file_name       varchar(256)  NOT NULL,
    file_key        varchar(512)  NOT NULL,
    file_size       bigint        NOT NULL,
    content_type    varchar(128)  NOT NULL,
    file_ext        varchar(32),
    storage_type    varchar(32)   NOT NULL,
    checksum        varchar(128),
    upload_status   varchar(16)   NOT NULL,
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_sys_file PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_sys_file_key ON sys_file (tenant_id, file_key) WHERE deleted = false;
CREATE INDEX idx_sys_file_created ON sys_file (tenant_id, created_time);
COMMENT ON TABLE sys_file IS '文件元数据';

-- ===== 文件业务绑定 =====
CREATE TABLE biz_file_rel (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    biz_type        varchar(64)   NOT NULL,
    biz_id          varchar(32)   NOT NULL,
    file_id         varchar(32)   NOT NULL,
    rel_type        varchar(32),
    sort_no         int           NOT NULL DEFAULT 0,
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_biz_file_rel PRIMARY KEY (id)
);
CREATE INDEX idx_biz_file_rel_biz ON biz_file_rel (tenant_id, biz_type, biz_id);
CREATE INDEX idx_biz_file_rel_file ON biz_file_rel (tenant_id, file_id);
CREATE UNIQUE INDEX uk_biz_file_rel ON biz_file_rel (tenant_id, biz_type, biz_id, file_id) WHERE deleted = false;
COMMENT ON TABLE biz_file_rel IS '文件业务绑定';

-- ===== 站内信 =====
CREATE TABLE sys_message (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    receiver_id     varchar(64)   NOT NULL,
    msg_type        varchar(32)   NOT NULL,
    title           varchar(256)  NOT NULL,
    content         text,
    read_status     varchar(16)   NOT NULL DEFAULT 'UNREAD',
    read_time       timestamptz,
    biz_type        varchar(64),
    biz_id          varchar(32),
    target_route_id varchar(128)  NOT NULL,
    target_params   jsonb         NOT NULL DEFAULT '{}',
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_sys_message PRIMARY KEY (id)
);
CREATE INDEX idx_sys_message_receiver ON sys_message (tenant_id, receiver_id, read_status, created_time);
CREATE INDEX idx_sys_message_biz ON sys_message (tenant_id, biz_type, biz_id);
COMMENT ON TABLE sys_message IS '站内信';

-- ===== 消息模板 =====
CREATE TABLE sys_message_template (
    id                varchar(32)   NOT NULL,
    tenant_id         varchar(32)   NOT NULL,
    template_code     varchar(64)   NOT NULL,
    template_name     varchar(128)  NOT NULL,
    channel           varchar(32)   NOT NULL,
    title_template    varchar(256)  NOT NULL,
    content_template  text          NOT NULL,
    params_schema     jsonb,
    status            varchar(16)   NOT NULL,
    created_by        varchar(64),
    created_time      timestamptz   NOT NULL DEFAULT now(),
    updated_by        varchar(64),
    updated_time      timestamptz,
    deleted           boolean       NOT NULL DEFAULT false,
    version           int           NOT NULL DEFAULT 0,
    remark            varchar(512),
    CONSTRAINT pk_sys_message_template PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_msg_template ON sys_message_template (tenant_id, template_code, channel) WHERE deleted = false;
COMMENT ON TABLE sys_message_template IS '消息模板';

-- ===== 轻量待办任务 =====
CREATE TABLE sys_todo_task (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    todo_type       varchar(32)   NOT NULL,
    biz_type        varchar(64)   NOT NULL,
    biz_id          varchar(32)   NOT NULL,
    title           varchar(256)  NOT NULL,
    assignee_id     varchar(64)   NOT NULL,
    todo_status     varchar(16)   NOT NULL,
    priority        varchar(16)   NOT NULL DEFAULT 'NORMAL',
    due_time        timestamptz,
    completed_time  timestamptz,
    source_event_id varchar(64),
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_sys_todo_task PRIMARY KEY (id)
);
CREATE INDEX idx_sys_todo_assignee_status ON sys_todo_task (tenant_id, assignee_id, todo_status, created_time);
CREATE INDEX idx_sys_todo_biz ON sys_todo_task (tenant_id, biz_type, biz_id);
CREATE UNIQUE INDEX uk_sys_todo_pending ON sys_todo_task (tenant_id, biz_type, biz_id, assignee_id) WHERE deleted = false AND todo_status = 'PENDING';
COMMENT ON TABLE sys_todo_task IS '轻量待办任务';

-- ===== 操作日志 =====
CREATE TABLE sys_operation_log (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    operation_type  varchar(32)   NOT NULL,
    module          varchar(64)   NOT NULL,
    biz_type        varchar(64),
    biz_id          varchar(32),
    content         varchar(512),
    before_json     jsonb,
    after_json      jsonb,
    result          varchar(16)   NOT NULL,
    error_code      varchar(32),
    trace_id        varchar(64)   NOT NULL,
    operator_id     varchar(64)   NOT NULL,
    operator_name   varchar(128),
    ip              varchar(64),
    user_agent      varchar(512),
    operated_time   timestamptz   NOT NULL,
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_sys_operation_log PRIMARY KEY (id)
);
CREATE INDEX idx_oplog_biz ON sys_operation_log (tenant_id, biz_type, biz_id, operated_time);
CREATE INDEX idx_oplog_operator ON sys_operation_log (tenant_id, operator_id, operated_time);
CREATE INDEX idx_oplog_trace ON sys_operation_log (tenant_id, trace_id);
COMMENT ON TABLE sys_operation_log IS '操作日志';

-- ===== 登录审计 =====
CREATE TABLE sys_login_log (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    login_type      varchar(32)   NOT NULL,
    login_result    varchar(16)   NOT NULL,
    fail_reason     varchar(256),
    user_id         varchar(64),
    username        varchar(128),
    token_id        varchar(128),
    device_type     varchar(32),
    ip              varchar(64),
    user_agent      varchar(512),
    location        varchar(128),
    trace_id        varchar(64)   NOT NULL,
    login_time      timestamptz   NOT NULL,
    logout_time     timestamptz,
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_sys_login_log PRIMARY KEY (id)
);
CREATE INDEX idx_login_log_user ON sys_login_log (tenant_id, user_id, login_time);
CREATE INDEX idx_login_log_result ON sys_login_log (tenant_id, login_result, login_time);
CREATE INDEX idx_login_log_trace ON sys_login_log (tenant_id, trace_id);
COMMENT ON TABLE sys_login_log IS '登录审计';

-- ===== 幂等记录 =====
CREATE TABLE sys_idempotency_record (
    id                varchar(32)   NOT NULL,
    tenant_id         varchar(32)   NOT NULL,
    resource_type     varchar(64)   NOT NULL,
    resource_id       varchar(32),
    action            varchar(64)   NOT NULL,
    idempotency_key   varchar(128)  NOT NULL,
    request_hash      varchar(128)  NOT NULL,
    response_code     varchar(32),
    response_snapshot jsonb,
    status            varchar(16)   NOT NULL,
    locked_until      timestamptz,
    expire_time       timestamptz   NOT NULL,
    trace_id          varchar(64)   NOT NULL,
    created_by        varchar(64),
    created_time      timestamptz   NOT NULL DEFAULT now(),
    updated_by        varchar(64),
    updated_time      timestamptz,
    deleted           boolean       NOT NULL DEFAULT false,
    version           int           NOT NULL DEFAULT 0,
    remark            varchar(512),
    CONSTRAINT pk_sys_idempotency_record PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_idempotency_scope ON sys_idempotency_record (tenant_id, resource_type, COALESCE(resource_id, ''), action, idempotency_key) WHERE deleted = false;
CREATE INDEX idx_idempotency_expire ON sys_idempotency_record (tenant_id, expire_time);
COMMENT ON TABLE sys_idempotency_record IS '幂等记录';

-- ===== 事务事件 Outbox =====
CREATE TABLE sys_outbox_event (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    event_type      varchar(128)  NOT NULL,
    event_class     varchar(128)  NOT NULL,
    event_version   int           NOT NULL,
    aggregate_type  varchar(64)   NOT NULL,
    aggregate_id    varchar(32)   NOT NULL,
    payload         jsonb         NOT NULL,
    trace_id        varchar(64)   NOT NULL,
    producer        varchar(64)   NOT NULL,
    correlation_id  varchar(64),
    causation_id    varchar(32),
    actor_id        varchar(64),
    occurred_time   timestamptz   NOT NULL,
    publish_status  varchar(16)   NOT NULL,
    retry_count     int           NOT NULL DEFAULT 0,
    next_retry_time timestamptz,
    published_time  timestamptz,
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_sys_outbox_event PRIMARY KEY (id)
);
CREATE INDEX idx_outbox_publish ON sys_outbox_event (tenant_id, publish_status, next_retry_time, created_time);
CREATE INDEX idx_outbox_aggregate ON sys_outbox_event (tenant_id, aggregate_type, aggregate_id);
COMMENT ON TABLE sys_outbox_event IS '事务事件 Outbox';

-- ===== 任务日志 =====
CREATE TABLE sys_job_log (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    job_code        varchar(64)   NOT NULL,
    job_name        varchar(128),
    biz_type        varchar(64),
    biz_id          varchar(32),
    trigger_type    varchar(32)   NOT NULL,
    status          varchar(16)   NOT NULL,
    start_time      timestamptz   NOT NULL,
    end_time        timestamptz,
    duration_ms     bigint,
    error_message   varchar(1024),
    trace_id        varchar(64)   NOT NULL,
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_sys_job_log PRIMARY KEY (id)
);
CREATE INDEX idx_job_log_code ON sys_job_log (tenant_id, job_code, start_time);
CREATE INDEX idx_job_log_status ON sys_job_log (tenant_id, status, start_time);
COMMENT ON TABLE sys_job_log IS '任务日志';

-- ===== 导入导出任务 =====
CREATE TABLE sys_import_export_task (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    task_type       varchar(16)   NOT NULL,
    biz_type        varchar(64)   NOT NULL,
    file_id         varchar(32),
    status          varchar(16)   NOT NULL,
    total_rows      int           NOT NULL DEFAULT 0,
    success_rows    int           NOT NULL DEFAULT 0,
    fail_rows       int           NOT NULL DEFAULT 0,
    error_file_id   varchar(32),
    started_time    timestamptz,
    finished_time   timestamptz,
    error_message   varchar(1024),
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_sys_import_export_task PRIMARY KEY (id)
);
CREATE INDEX idx_import_export_status ON sys_import_export_task (tenant_id, status, created_time);
CREATE INDEX idx_import_export_biz ON sys_import_export_task (tenant_id, biz_type, created_time);
COMMENT ON TABLE sys_import_export_task IS '导入导出任务';

-- ===== 业务编码序列 =====
CREATE TABLE sys_sequence (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    sequence_code   varchar(64)   NOT NULL,
    biz_date        varchar(8)    NOT NULL,
    current_value   int           NOT NULL DEFAULT 0,
    step            int           NOT NULL DEFAULT 1,
    reset_policy    varchar(16)   NOT NULL DEFAULT 'DAILY',
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_sys_sequence PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_sys_sequence_tenant_code_date ON sys_sequence (tenant_id, sequence_code, biz_date) WHERE deleted = false;
COMMENT ON TABLE sys_sequence IS '业务编码序列';
