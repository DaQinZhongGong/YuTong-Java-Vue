-- ============================================================
-- V056__sys_url_whitelist.sql
-- P3 URL 白名单管理: sys_url 表建表 + 默认公开端点种子
-- 设计来源: 业界同类实现 SysUrlController + ADR 0005 P3
-- 说明: DB 驱动的 URL 白名单, 与 @PublicEndpoint 注解互补
-- 约束: 幂等 IF NOT EXISTS, ULID varchar(32) PK
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_url (
    id              varchar(32)     NOT NULL,
    tenant_id       varchar(32)     NOT NULL,
    url_pattern     varchar(256)    NOT NULL,
    url_type        varchar(16)     NOT NULL DEFAULT 'PUBLIC',
    description     varchar(256),
    status          varchar(16)     NOT NULL DEFAULT 'ENABLED',
    created_by      varchar(64),
    created_time    timestamptz     NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean         NOT NULL DEFAULT false,
    version         int             NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_sys_url PRIMARY KEY (id)
);

ALTER TABLE sys_url DROP CONSTRAINT IF EXISTS chk_sys_url_type;
ALTER TABLE sys_url ADD CONSTRAINT chk_sys_url_type
    CHECK (url_type IN ('PUBLIC','ANONYMOUS','PERMIT_ALL'));

ALTER TABLE sys_url DROP CONSTRAINT IF EXISTS chk_sys_url_status;
ALTER TABLE sys_url ADD CONSTRAINT chk_sys_url_status
    CHECK (status IN ('ENABLED','DISABLED'));

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_url_pattern ON sys_url (url_pattern) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_sys_url_status ON sys_url (status) WHERE deleted = false;

COMMENT ON TABLE sys_url IS 'URL 白名单表 — V056 P3: DB 驱动的公开端点配置, 与 @PublicEndpoint 注解互补';
COMMENT ON COLUMN sys_url.url_pattern IS 'URL 模式 (Ant 风格, 如 /api/v1/auth/**)';
COMMENT ON COLUMN sys_url.url_type IS '类型: PUBLIC 公开 / ANONYMOUS 匿名 / PERMIT_ALL 全放行';
COMMENT ON COLUMN sys_url.status IS '状态: ENABLED 启用 / DISABLED 停用';

-- 默认公开端点种子 (与 @PublicEndpoint 注解对齐, 幂等)
INSERT INTO sys_url (id, tenant_id, url_pattern, url_type, description, status)
SELECT '01JURLWHITELISTLOGIN00000001', 'default', '/api/v1/auth/login', 'PUBLIC', '登录端点', 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM sys_url WHERE url_pattern = '/api/v1/auth/login' AND deleted = false);

INSERT INTO sys_url (id, tenant_id, url_pattern, url_type, description, status)
SELECT '01JURLWHITELISTREFRESH000001', 'default', '/api/v1/auth/refresh-token', 'PUBLIC', 'Token 刷新', 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM sys_url WHERE url_pattern = '/api/v1/auth/refresh-token' AND deleted = false);

INSERT INTO sys_url (id, tenant_id, url_pattern, url_type, description, status)
SELECT '01JURLWHITELISTREGISTER00001', 'default', '/api/v1/auth/register', 'PUBLIC', '用户注册', 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM sys_url WHERE url_pattern = '/api/v1/auth/register' AND deleted = false);

INSERT INTO sys_url (id, tenant_id, url_pattern, url_type, description, status)
SELECT '01JURLWHITELISTHEALTH0000001', 'default', '/api/v1/monitor/health', 'PUBLIC', '健康检查', 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM sys_url WHERE url_pattern = '/api/v1/monitor/health' AND deleted = false);

INSERT INTO sys_url (id, tenant_id, url_pattern, url_type, description, status)
SELECT '01JURLWHITELISTEVENTS0000001', 'default', '/api/v1/client-events/**', 'PUBLIC', '端侧事件上报', 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM sys_url WHERE url_pattern = '/api/v1/client-events/**' AND deleted = false);

INSERT INTO sys_url (id, tenant_id, url_pattern, url_type, description, status)
SELECT '01JURLWHITELISTCMS0000000001', 'default', '/api/v1/cms/published/**', 'PUBLIC', 'CMS 公开内容', 'ENABLED'
WHERE NOT EXISTS (SELECT 1 FROM sys_url WHERE url_pattern = '/api/v1/cms/published/**' AND deleted = false);
