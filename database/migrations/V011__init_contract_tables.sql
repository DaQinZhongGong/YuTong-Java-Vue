-- V011: 合同档案表 (GA2-35)
-- 设计来源: 35-样例业务矩阵扩展设计 P1 合同档案
-- 验证能力: 文件版本管理 + PostgreSQL 全文检索 + 敏感字段脱敏 + 下载审计 + 归档只读
-- 状态机: DRAFT → SUBMITTED → APPROVED → SIGNED → ARCHIVED
--         含 REJECTED 驳回分支 (SUBMITTED→REJECTED→SUBMITTED 可重新提交)
--         含 CANCELLED 取消终态

-- ===== 合同主表 =====
CREATE TABLE contract (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    contract_no         varchar(32)   NOT NULL,
    title               varchar(256)  NOT NULL,
    contract_type       varchar(32)   NOT NULL DEFAULT 'GENERAL',
    party_a             varchar(128)  NOT NULL,
    party_b             varchar(128)  NOT NULL,
    signed_date         date,
    effective_date      date,
    expire_date         date,
    amount              numeric(18,2),
    currency            varchar(8)    NOT NULL DEFAULT 'CNY',
    content_summary     text,
    status              varchar(16)   NOT NULL DEFAULT 'DRAFT',
    current_version_no  int           NOT NULL DEFAULT 0,
    owner_user_id       varchar(64)   NOT NULL,
    owner_dept_id       varchar(64),
    owner_dept_path     varchar(512),
    submitted_time      timestamptz,
    approved_time       timestamptz,
    signed_time         timestamptz,
    archived_time       timestamptz,
    -- 全文检索向量: 标题+合同号+相对方+内容摘要
    search_vector       tsvector,
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_contract PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_contract_tenant_no ON contract (tenant_id, contract_no) WHERE deleted = false;
CREATE INDEX idx_contract_status ON contract (tenant_id, status) WHERE deleted = false;
CREATE INDEX idx_contract_owner ON contract (tenant_id, owner_user_id) WHERE deleted = false;
CREATE INDEX idx_contract_signed_date ON contract (tenant_id, signed_date) WHERE deleted = false;
-- 全文检索 GIN 索引
CREATE INDEX idx_contract_search ON contract USING gin (search_vector) WHERE deleted = false;
COMMENT ON TABLE contract IS '合同档案';

-- ===== 合同版本表 =====
-- 每次合同内容变更创建一个不可变版本快照，关联 sys_file 文件 ID。
CREATE TABLE contract_version (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    contract_id     varchar(32)   NOT NULL,
    version_no      int           NOT NULL,
    file_id         varchar(32),
    file_name_snapshot varchar(256),
    file_checksum   varchar(128),
    file_size       bigint,
    content_summary text,
    change_log      varchar(512),
    is_current      boolean       NOT NULL DEFAULT false,
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_contract_version PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_contract_version ON contract_version (tenant_id, contract_id, version_no) WHERE deleted = false;
CREATE INDEX idx_contract_version_contract ON contract_version (tenant_id, contract_id, version_no) WHERE deleted = false;
COMMENT ON TABLE contract_version IS '合同版本';

-- ===== 合同附件表 =====
-- 合同附属文件 (非主合同文件)，复用 sys_file 文件元数据。
CREATE TABLE contract_attachment (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    contract_id     varchar(32)   NOT NULL,
    file_id         varchar(32)   NOT NULL,
    file_name_snapshot varchar(256),
    attachment_type varchar(32)   NOT NULL DEFAULT 'GENERAL',
    sort_no         int           NOT NULL DEFAULT 0,
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_contract_attachment PRIMARY KEY (id)
);
CREATE INDEX idx_contract_attachment_contract ON contract_attachment (tenant_id, contract_id, sort_no) WHERE deleted = false;
CREATE UNIQUE INDEX uk_contract_attachment_file ON contract_attachment (tenant_id, contract_id, file_id) WHERE deleted = false;
COMMENT ON TABLE contract_attachment IS '合同附件';

-- ===== 合同审批记录表 =====
-- 每次审批动作写入一条不可变记录，形成审批时间线。
CREATE TABLE contract_approval (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    contract_id     varchar(32)   NOT NULL,
    action          varchar(32)   NOT NULL,
    from_status     varchar(16),
    to_status       varchar(16),
    approver_id     varchar(64)   NOT NULL,
    approver_name   varchar(128),
    opinion         text,
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_contract_approval PRIMARY KEY (id)
);
CREATE INDEX idx_contract_approval_contract ON contract_approval (tenant_id, contract_id, created_time) WHERE deleted = false;
COMMENT ON TABLE contract_approval IS '合同审批记录';

-- ===== 合同标签表 =====
CREATE TABLE contract_tag (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    contract_id     varchar(32)   NOT NULL,
    tag_name        varchar(64)   NOT NULL,
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_contract_tag PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_contract_tag ON contract_tag (tenant_id, contract_id, tag_name) WHERE deleted = false;
CREATE INDEX idx_contract_tag_name ON contract_tag (tenant_id, tag_name) WHERE deleted = false;
COMMENT ON TABLE contract_tag IS '合同标签';

-- ===== 种子数据: 合同 2 条 + 版本 2 条 + 审批 2 条 + 标签 4 条 =====
INSERT INTO contract (id, tenant_id, contract_no, title, contract_type, party_a, party_b, signed_date, effective_date, expire_date, amount, currency, content_summary, status, current_version_no, owner_user_id, submitted_time, approved_time, signed_time, archived_time, search_vector, created_by, created_time, deleted, version, remark) VALUES
('01CONTRACT001000000000001', 'default', 'CT202601010001', '2026 年度技术服务框架合同', 'SERVICE', '上海宇通科技有限公司', '北京云端计算有限公司', '2026-01-15', '2026-02-01', '2026-12-31', 480000.00, 'CNY', '本合同约定 2026 年度技术服务范围、响应时长、SLA 等级及付款方式。', 'ARCHIVED', 2, '01MOCKUSER0000000000000ADMIN', '2026-01-10T10:00:00+08:00', '2026-01-12T15:30:00+08:00', '2026-01-15T09:00:00+08:00', '2026-01-20T14:00:00+08:00', to_tsvector('simple', '2026 年度技术服务框架合同 CT202601010001 上海宇通科技有限公司 北京云端计算有限公司 本合同约定 2026 年度技术服务范围、响应时长、SLA 等级及付款方式。'), 'system', now(), false, 5, '框架合同已归档'),
('01CONTRACT002000000000002', 'default', 'CT202603120002', '办公设备采购合同', 'PURCHASE', '上海宇通科技有限公司', '深圳硬件制造有限公司', '2026-03-20', '2026-04-01', '2026-06-30', 156800.00, 'CNY', '本合同约定 200 台办公电脑及配套外设采购、验收标准与质保期。', 'SIGNED', 1, '01MOCKUSER0000000000000ADMIN', '2026-03-15T11:00:00+08:00', '2026-03-18T16:00:00+08:00', '2026-03-20T10:30:00+08:00', null, to_tsvector('simple', '办公设备采购合同 CT202603120002 上海宇通科技有限公司 深圳硬件制造有限公司 本合同约定 200 台办公电脑及配套外设采购、验收标准与质保期。'), 'system', now(), false, 3, '采购合同已签订待归档');

INSERT INTO contract_version (id, tenant_id, contract_id, version_no, file_id, file_name_snapshot, file_checksum, file_size, content_summary, change_log, is_current, created_by, created_time, deleted, version, remark) VALUES
('01CONTRACTVER000000000001', 'default', '01CONTRACT001000000000001', 1, null, '2026-tech-service-contract-v1.pdf', 'sha256:abc123def456', 245678, '初版: 基础 SLA 标准与单价', '合同初稿', false, 'system', now(), false, 0, null),
('01CONTRACTVER000000000002', 'default', '01CONTRACT001000000000001', 2, null, '2026-tech-service-contract-v2.pdf', 'sha256:abc123def457', 251034, '终版: 增加 7×24 小时响应条款', '增加夜间值班条款', true, 'system', now(), false, 0, null);

INSERT INTO contract_approval (id, tenant_id, contract_id, action, from_status, to_status, approver_id, approver_name, opinion, created_by, created_time, deleted, version, remark) VALUES
('01CONTRACTAPR000000000001', 'default', '01CONTRACT001000000000001', 'SUBMIT', 'DRAFT', 'SUBMITTED', '01MOCKUSER0000000000000ADMIN', 'admin', '提交审批', 'system', now(), false, 0, null),
('01CONTRACTAPR000000000002', 'default', '01CONTRACT001000000000001', 'APPROVE', 'SUBMITTED', 'APPROVED', '01MOCKUSER0000000000000ADMIN', 'admin', '审批通过，可签订', 'system', now(), false, 0, null),
('01CONTRACTAPR000000000003', 'default', '01CONTRACT001000000000001', 'SIGN', 'APPROVED', 'SIGNED', '01MOCKUSER0000000000000ADMIN', 'admin', '完成签订', 'system', now(), false, 0, null),
('01CONTRACTAPR000000000004', 'default', '01CONTRACT001000000000001', 'ARCHIVE', 'SIGNED', 'ARCHIVED', '01MOCKUSER0000000000000ADMIN', 'admin', '归档存证', 'system', now(), false, 0, null);

INSERT INTO contract_tag (id, tenant_id, contract_id, tag_name, created_by, created_time, deleted, version, remark) VALUES
('01CONTRACTTAG000000000001', 'default', '01CONTRACT001000000000001', '框架合同', 'system', now(), false, 0, null),
('01CONTRACTTAG000000000002', 'default', '01CONTRACT001000000000001', 'SLA', 'system', now(), false, 0, null),
('01CONTRACTTAG000000000003', 'default', '01CONTRACT001000000000001', '已归档', 'system', now(), false, 0, null),
('01CONTRACTTAG000000000004', 'default', '01CONTRACT002000000000002', '采购', 'system', now(), false, 0, null);
