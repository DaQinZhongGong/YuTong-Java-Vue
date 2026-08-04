-- V003: 样例业务表
-- 设计来源: 18-样例业务详细设计、57-完整DDL清单与数据字典详设

-- ===== 客户 =====
CREATE TABLE biz_customer (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    customer_code   varchar(32)   NOT NULL,
    customer_name   varchar(128)  NOT NULL,
    contact_name    varchar(64),
    contact_phone   varchar(20),
    address         varchar(256),
    status          varchar(16)   NOT NULL,
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_biz_customer PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_biz_customer_tenant_code ON biz_customer (tenant_id, customer_code) WHERE deleted = false;
COMMENT ON TABLE biz_customer IS '客户';

-- ===== 商品 =====
CREATE TABLE biz_product (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    product_code    varchar(32)   NOT NULL,
    product_name    varchar(128)  NOT NULL,
    unit            varchar(16)   NOT NULL,
    price           numeric(18,2) NOT NULL,
    status          varchar(16)   NOT NULL,
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_biz_product PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_biz_product_tenant_code ON biz_product (tenant_id, product_code) WHERE deleted = false;
COMMENT ON TABLE biz_product IS '商品';

-- ===== 申请单 =====
CREATE TABLE biz_request (
    id                    varchar(32)   NOT NULL,
    tenant_id             varchar(32)   NOT NULL,
    request_no            varchar(32)   NOT NULL,
    title                 varchar(128)  NOT NULL,
    customer_id           varchar(32)   NOT NULL,
    customer_name_snapshot varchar(128) NOT NULL,
    apply_reason          text,
    request_status        varchar(16)   NOT NULL,
    total_amount          numeric(18,2) NOT NULL DEFAULT 0,
    applicant_id          varchar(64)   NOT NULL,
    applicant_name_snapshot varchar(128),
    owner_user_id         varchar(64)   NOT NULL,
    owner_dept_id         varchar(64),
    owner_dept_path       varchar(512),
    submitted_time        timestamptz,
    approved_time         timestamptz,
    archived_time         timestamptz,
    created_by            varchar(64),
    created_time          timestamptz   NOT NULL DEFAULT now(),
    updated_by            varchar(64),
    updated_time          timestamptz,
    deleted               boolean       NOT NULL DEFAULT false,
    version               int           NOT NULL DEFAULT 0,
    remark                varchar(512),
    CONSTRAINT pk_biz_request PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_biz_request_tenant_no ON biz_request (tenant_id, request_no) WHERE deleted = false;
CREATE INDEX idx_biz_request_status ON biz_request (tenant_id, request_status, submitted_time);
CREATE INDEX idx_biz_request_owner ON biz_request (tenant_id, owner_user_id, created_time);
CREATE INDEX idx_biz_request_dept ON biz_request (tenant_id, owner_dept_id, created_time);
COMMENT ON TABLE biz_request IS '申请单';

-- ===== 申请单明细 =====
CREATE TABLE biz_request_item (
    id                      varchar(32)   NOT NULL,
    tenant_id               varchar(32)   NOT NULL,
    request_id              varchar(32)   NOT NULL,
    product_id              varchar(32)   NOT NULL,
    product_code_snapshot   varchar(32)   NOT NULL,
    product_name_snapshot   varchar(128)  NOT NULL,
    unit                    varchar(16)   NOT NULL,
    quantity                numeric(18,4) NOT NULL,
    unit_price              numeric(18,2) NOT NULL,
    line_amount             numeric(18,2) NOT NULL,
    sort_no                 int           NOT NULL DEFAULT 0,
    created_by              varchar(64),
    created_time            timestamptz   NOT NULL DEFAULT now(),
    updated_by              varchar(64),
    updated_time            timestamptz,
    deleted                 boolean       NOT NULL DEFAULT false,
    version                 int           NOT NULL DEFAULT 0,
    remark                  varchar(512),
    CONSTRAINT pk_biz_request_item PRIMARY KEY (id)
);
CREATE INDEX idx_biz_request_item_request ON biz_request_item (tenant_id, request_id, sort_no);
CREATE INDEX idx_biz_request_item_product ON biz_request_item (tenant_id, product_id);
COMMENT ON TABLE biz_request_item IS '申请单明细';

-- ===== 审批记录 =====
CREATE TABLE biz_approval_record (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    request_id      varchar(32)   NOT NULL,
    action          varchar(16)   NOT NULL,
    result          varchar(16),
    opinion         text,
    operator_id     varchar(64)   NOT NULL,
    operated_time   timestamptz   NOT NULL,
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_biz_approval_record PRIMARY KEY (id)
);
CREATE INDEX idx_biz_approval_record_request ON biz_approval_record (tenant_id, request_id, operated_time);
COMMENT ON TABLE biz_approval_record IS '审批记录';
