-- ============================================================
-- V007: 端侧埋点事件表 sys_client_event
-- 设计来源: 94-端侧埋点与体验监控详设
-- 范围: 第一版必须落地 Web/Uniapp 基础 RUM、接口错误、JS 错误、
--       页面访问、关键业务操作事件的统一存储。
-- 约束: 主键 varchar ULID；继承 BaseEntity 范式（tenant_id/created_by/...）。
--       写入后一般不修改，保留 deleted/version 字段仅为与 BaseEntity 对齐。
-- 隐私: 不得采集明文密码/token/完整手机号/完整身份证/文件原文/AI Prompt 敏感字段。
--       userIdHash/tenantIdHash/bizIdHash 由端侧 SDK 脱敏后上报。
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_client_event (
    id              varchar(26)   NOT NULL,
    tenant_id       varchar(26)   NOT NULL,
    event_id        varchar(26)   NOT NULL,
    event_name      varchar(128)  NOT NULL,
    occurred_time   timestamptz   NOT NULL,
    trace_id        varchar(64),
    session_id      varchar(64)   NOT NULL,
    user_id_hash    varchar(64),
    tenant_id_hash  varchar(64),
    route           varchar(256)  NOT NULL,
    page_title      varchar(256),
    platform        varchar(16)   NOT NULL,
    app_version     varchar(32)   NOT NULL,
    biz_type        varchar(64),
    biz_id_hash     varchar(64),
    result          varchar(16),
    error_code      varchar(32),
    duration_ms     integer,
    payload         jsonb,
    created_by      varchar(26),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(26),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         integer       NOT NULL DEFAULT 0,
    remark          varchar(256),
    CONSTRAINT pk_sys_client_event PRIMARY KEY (id)
);

-- 按租户 + 发生时间范围查询（看板默认查询）
CREATE INDEX IF NOT EXISTS idx_sys_client_event_tenant_time
    ON sys_client_event (tenant_id, occurred_time DESC);

-- 按事件名查询（聚合统计）
CREATE INDEX IF NOT EXISTS idx_sys_client_event_name
    ON sys_client_event (tenant_id, event_name, occurred_time DESC);

-- 按 traceId 关联后端链路
CREATE INDEX IF NOT EXISTS idx_sys_client_event_trace
    ON sys_client_event (trace_id)
    WHERE trace_id IS NOT NULL;

-- 按会话查询（用户行为路径回放）
CREATE INDEX IF NOT EXISTS idx_sys_client_event_session
    ON sys_client_event (tenant_id, session_id, occurred_time DESC);

COMMENT ON TABLE sys_client_event IS '端侧埋点事件表。设计来源: 94-端侧埋点与体验监控详设';
COMMENT ON COLUMN sys_client_event.event_id IS '端侧生成 ULID，唯一标识一次事件';
COMMENT ON COLUMN sys_client_event.event_name IS '事件名，小写点分格式 端.模块.对象.动作';
COMMENT ON COLUMN sys_client_event.occurred_time IS '事件发生时间（端侧时间）';
COMMENT ON COLUMN sys_client_event.session_id IS '会话 ID，运行期生成';
COMMENT ON COLUMN sys_client_event.user_id_hash IS '用户标识 hash，隐私脱敏';
COMMENT ON COLUMN sys_client_event.tenant_id_hash IS '租户标识 hash，隐私脱敏';
COMMENT ON COLUMN sys_client_event.platform IS '平台: web/h5/mp/app';
COMMENT ON COLUMN sys_client_event.payload IS '附加业务字段 JSON（已脱敏），如 itemCount/fileSize/filterCount';
