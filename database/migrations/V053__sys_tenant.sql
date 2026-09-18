-- ============================================================
-- V053__sys_tenant.sql
-- P2-F 租户列表管理: sys_tenant 注册表建表 + default 内置租户种子
-- 设计来源:
--   - 业界同类实现 AI sys_tenant (tenantId/contact/company/package/expire/accountCount/status)
--   - 后端实体: SysTenant / SysTenantService (yutong-system-service)
--   - 约束: 幂等 IF NOT EXISTS / DROP IF EXISTS, 禁止 Flyway 占位 ${}(placeholder-replacement=false)
--   - AGENTS.md 硬约束: ULID varchar(32) PK、tenant_id、deleted/version、
--     created_*/updated_* 规范、CHECK 状态机、中文 COMMENT
-- 注意: sys_tenant 为平台级主数据, 在 application.yml yutong.tenant.ignore-tables
--   中豁免行级过滤 (平台管理员全局可见, 接口由 tenant:tenant:* 权限码守卫);
--   行 tenant_id 自描述为自身编码, 满足全表 tenant_id 列约定
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_tenant (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    tenant_code         varchar(32)     NOT NULL,
    company_name        varchar(128)    NOT NULL,
    contact_user_name   varchar(64),
    contact_phone       varchar(32),
    license_number      varchar(64),
    address             varchar(256),
    domain              varchar(128),
    intro               text,
    package_id          varchar(32),
    expire_time         timestamptz,
    account_count       bigint,
    status              varchar(16)     NOT NULL DEFAULT 'NORMAL',
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_sys_tenant PRIMARY KEY (id)
);

ALTER TABLE sys_tenant DROP CONSTRAINT IF EXISTS chk_sys_tenant_status;
ALTER TABLE sys_tenant ADD CONSTRAINT chk_sys_tenant_status
    CHECK (status IN ('NORMAL','DISABLED'));

ALTER TABLE sys_tenant DROP CONSTRAINT IF EXISTS chk_sys_tenant_account;
ALTER TABLE sys_tenant ADD CONSTRAINT chk_sys_tenant_account
    CHECK (account_count IS NULL OR account_count >= -1);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_tenant_code ON sys_tenant (tenant_code) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_sys_tenant_status ON sys_tenant (status) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_sys_tenant_package ON sys_tenant (package_id) WHERE deleted = false;

COMMENT ON TABLE sys_tenant IS '租户注册表 — V053 P2-F 租户列表管理: 平台级主数据 (豁免行级过滤, 权限码守卫), tenantCode 创建后不可改';
COMMENT ON COLUMN sys_tenant.tenant_code IS '租户编码 (如 default/acme), 全局唯一, 各业务表 tenant_id 取值, 创建后不可改';
COMMENT ON COLUMN sys_tenant.tenant_id IS '自描述为自身编码 (表豁免行级过滤, 列仅满足全表约定)';
COMMENT ON COLUMN sys_tenant.package_id IS '绑定套餐 sys_tenant_package.id, 可空 = 未订阅';
COMMENT ON COLUMN sys_tenant.expire_time IS '套餐过期时间, null = 不过期';
COMMENT ON COLUMN sys_tenant.account_count IS '账号上限, -1 = 不限制';
COMMENT ON COLUMN sys_tenant.status IS '状态机 NORMAL 正常 / DISABLED 停用';

-- default 内置租户种子 (与 V051 tenant_id 回填默认值对齐, 幂等)
INSERT INTO sys_tenant (id, tenant_id, tenant_code, company_name, contact_user_name,
    account_count, status, created_time, deleted, version, remark)
SELECT '01JYYYYYYYYYYTENANT000001', 'default', 'default', 'YuTong 平台默认租户', 'platform-admin',
    -1, 'NORMAL', now(), false, 0, 'V053 内置种子, 禁止停用/删除'
WHERE NOT EXISTS (SELECT 1 FROM sys_tenant WHERE tenant_code = 'default' AND deleted = false);
