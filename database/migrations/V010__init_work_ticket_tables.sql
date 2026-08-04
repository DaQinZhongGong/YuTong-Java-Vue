-- V010: 工单中心表
-- 设计来源: 35-样例业务矩阵扩展设计 P1 工单中心
-- 状态机: NEW→ASSIGNED→PROCESSING→COMPLETED→CLOSED，含 SUSPENDED 挂起分支
-- 验证能力: SLA 定时扫描、派单/转单、评价、消息通知

-- ===== 工单分类 =====
CREATE TABLE work_ticket_category (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    category_code   varchar(32)   NOT NULL,
    category_name   varchar(64)   NOT NULL,
    sla_hours       int           NOT NULL DEFAULT 24,
    status          varchar(16)   NOT NULL DEFAULT 'ACTIVE',
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_work_ticket_category PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_work_ticket_category_tenant_code ON work_ticket_category (tenant_id, category_code) WHERE deleted = false;
COMMENT ON TABLE work_ticket_category IS '工单分类';

-- ===== 工单主表 =====
CREATE TABLE work_ticket (
    id                    varchar(32)   NOT NULL,
    tenant_id             varchar(32)   NOT NULL,
    ticket_no             varchar(32)   NOT NULL,
    title                 varchar(128)  NOT NULL,
    description           text,
    category_id           varchar(32)   NOT NULL,
    category_name_snapshot varchar(64)  NOT NULL,
    priority              varchar(16)   NOT NULL DEFAULT 'MEDIUM',
    status                varchar(16)   NOT NULL DEFAULT 'NEW',
    reporter_id           varchar(64),
    reporter_name_snapshot varchar(128),
    handler_id            varchar(64),
    handler_name_snapshot varchar(128),
    owner_user_id         varchar(64)   NOT NULL,
    owner_dept_id         varchar(64),
    owner_dept_path       varchar(512),
    sla_deadline          timestamptz,
    assigned_time         timestamptz,
    resolved_time         timestamptz,
    closed_time           timestamptz,
    satisfaction_score    int,
    satisfaction_comment  varchar(512),
    created_by            varchar(64),
    created_time          timestamptz   NOT NULL DEFAULT now(),
    updated_by            varchar(64),
    updated_time          timestamptz,
    deleted               boolean       NOT NULL DEFAULT false,
    version               int           NOT NULL DEFAULT 0,
    remark                varchar(512),
    CONSTRAINT pk_work_ticket PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_work_ticket_tenant_no ON work_ticket (tenant_id, ticket_no) WHERE deleted = false;
CREATE INDEX idx_work_ticket_status ON work_ticket (tenant_id, status) WHERE deleted = false;
CREATE INDEX idx_work_ticket_handler ON work_ticket (tenant_id, handler_id) WHERE deleted = false;
CREATE INDEX idx_work_ticket_sla ON work_ticket (tenant_id, sla_deadline, status) WHERE deleted = false AND status IN ('NEW','ASSIGNED','PROCESSING','SUSPENDED');
COMMENT ON TABLE work_ticket IS '工单';

-- ===== 工单处理记录 =====
CREATE TABLE work_ticket_log (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    ticket_id       varchar(32)   NOT NULL,
    action          varchar(32)   NOT NULL,
    from_status     varchar(16),
    to_status       varchar(16),
    operator_id     varchar(64)   NOT NULL,
    operator_name   varchar(128),
    comment         text,
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_work_ticket_log PRIMARY KEY (id)
);
CREATE INDEX idx_work_ticket_log_ticket ON work_ticket_log (tenant_id, ticket_id, created_time) WHERE deleted = false;
COMMENT ON TABLE work_ticket_log IS '工单处理记录';

-- ===== 种子数据: 工单分类 =====
INSERT INTO work_ticket_category (id, tenant_id, category_code, category_name, sla_hours, status, created_by, created_time, deleted, version) VALUES
('01WORKCATFAULT00000000001', 'default', 'FAULT', '故障报告', 4, 'ACTIVE', 'system', now(), false, 0),
('01WORKCATREQ00000000000002', 'default', 'REQUEST', '需求提报', 48, 'ACTIVE', 'system', now(), false, 0),
('01WORKCATCONSULT0000000003', 'default', 'CONSULT', '咨询问答', 8, 'ACTIVE', 'system', now(), false, 0),
('01WORKCATCOMPLAINT00000004', 'default', 'COMPLAINT', '投诉建议', 24, 'ACTIVE', 'system', now(), false, 0);
