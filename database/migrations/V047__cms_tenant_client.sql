-- ============================================================
-- V047__cms_tenant_client.sql
-- P2-F 批次 6 (CMS / 租户套餐 / OAuth 客户端) 建表补齐
-- 设计来源:
--   - 业界同类实现 AI sys_client + CmsContent + sys_tenant_package
--   - 后端实体: CmsContent / SysTenantPackage / SysClient (yutong-system-service)
--   - 约束: 幂等 IF NOT EXISTS / DROP IF EXISTS, 禁止 Flyway 占位 ${}(placeholder-replacement=false)
--   - 所有表遵循 AGENTS.md 硬约束: ULID varchar(32) PK、tenant_id、deleted/version、
--     created_*/updated_* 规范、GIN for jsonb、CHECK 状态机、中文 COMMENT
-- 注意: 6-A/6-B/6-C service 先行落地, 本迁移补足生产建表 (此前依赖 MyBatis-Plus 建表不可投产)
-- ============================================================

-- ------------------------------------------------------------
-- 1. cms_content — CMS 内容 (公告/帮助/条款/博客)
-- 来源: CmsContent 实体。状态机 DRAFT → PUBLISHED → ARCHIVED, 租户内 slug 唯一
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cms_content (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    title               varchar(256)    NOT NULL,
    slug                varchar(128)    NOT NULL,
    content_md          text,
    content_html        text,
    summary             varchar(512),
    category            varchar(64),
    tags                varchar(256),
    status              varchar(16)     NOT NULL DEFAULT 'DRAFT',
    author_id           varchar(32),
    view_count          bigint          NOT NULL DEFAULT 0,
    published_at        timestamptz,
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_cms_content PRIMARY KEY (id)
);

ALTER TABLE cms_content DROP CONSTRAINT IF EXISTS chk_cms_content_status;
ALTER TABLE cms_content ADD CONSTRAINT chk_cms_content_status
    CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED'));

ALTER TABLE cms_content DROP CONSTRAINT IF EXISTS chk_cms_content_view;
ALTER TABLE cms_content ADD CONSTRAINT chk_cms_content_view
    CHECK (view_count >= 0);

CREATE UNIQUE INDEX IF NOT EXISTS uk_cms_content_slug ON cms_content (tenant_id, slug) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_cms_content_tenant ON cms_content (tenant_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_cms_content_tenant_status ON cms_content (tenant_id, status) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_cms_content_tenant_category ON cms_content (tenant_id, category) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_cms_content_published ON cms_content (tenant_id, published_at DESC) WHERE deleted = false AND status = 'PUBLISHED';

COMMENT ON TABLE cms_content IS 'CMS 内容 — V047 P2-F 批次 6-A: 公告/帮助/条款/博客, Markdown 原文 + 渲染缓存, 租户内 slug 唯一';
COMMENT ON COLUMN cms_content.slug IS 'URL slug (用于 /cms/{slug}), 租户内唯一 (软删过滤)';
COMMENT ON COLUMN cms_content.content_md IS 'Markdown 原文';
COMMENT ON COLUMN cms_content.content_html IS '渲染后 HTML 缓存 (当前实现直接存原文, 前端渲染)';
COMMENT ON COLUMN cms_content.status IS '状态机 DRAFT → PUBLISHED → ARCHIVED';
COMMENT ON COLUMN cms_content.view_count IS '浏览次数, 公开接口命中 +1';

-- ------------------------------------------------------------
-- 2. sys_tenant_package — 租户套餐 (SaaS 订阅)
-- 来源: SysTenantPackage 实体。状态机 DRAFT → ACTIVE → ARCHIVED, package_code 全局唯一
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_tenant_package (
    id                      varchar(32)     NOT NULL,
    tenant_id               varchar(32)     NOT NULL,
    package_code            varchar(64)     NOT NULL,
    package_name            varchar(128),
    description             text,
    price_cny_per_period    numeric(12,2),
    period_months           int,
    status                  varchar(16)     NOT NULL DEFAULT 'DRAFT',
    menu_ids_json           jsonb,
    quota_json              jsonb,
    sort_no                 int             NOT NULL DEFAULT 0,
    published_at            timestamptz,
    created_by              varchar(64),
    created_time            timestamptz     NOT NULL DEFAULT now(),
    updated_by              varchar(64),
    updated_time            timestamptz,
    deleted                 boolean         NOT NULL DEFAULT false,
    version                 int             NOT NULL DEFAULT 0,
    remark                  varchar(512),
    CONSTRAINT pk_sys_tenant_package PRIMARY KEY (id)
);

ALTER TABLE sys_tenant_package DROP CONSTRAINT IF EXISTS chk_sys_tenant_package_status;
ALTER TABLE sys_tenant_package ADD CONSTRAINT chk_sys_tenant_package_status
    CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED'));

ALTER TABLE sys_tenant_package DROP CONSTRAINT IF EXISTS chk_sys_tenant_package_price;
ALTER TABLE sys_tenant_package ADD CONSTRAINT chk_sys_tenant_package_price
    CHECK (price_cny_per_period IS NULL OR price_cny_per_period >= 0);

ALTER TABLE sys_tenant_package DROP CONSTRAINT IF EXISTS chk_sys_tenant_package_period;
ALTER TABLE sys_tenant_package ADD CONSTRAINT chk_sys_tenant_package_period
    CHECK (period_months IS NULL OR period_months >= 1);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_tenant_package_code ON sys_tenant_package (package_code) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_sys_tenant_package_tenant ON sys_tenant_package (tenant_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_sys_tenant_package_tenant_status ON sys_tenant_package (tenant_id, status) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_sys_tenant_package_quota_gin ON sys_tenant_package USING GIN (quota_json);

COMMENT ON TABLE sys_tenant_package IS '租户套餐 — V047 P2-F 批次 6-B: SaaS 订阅套餐, menu_ids_json 绑定菜单, quota_json 资源配额';
COMMENT ON COLUMN sys_tenant_package.package_code IS '套餐编码 (如 BASIC/PRO/ENTERPRISE), 全局唯一 (软删过滤)';
COMMENT ON COLUMN sys_tenant_package.quota_json IS '资源配额 JSON {aiMonthlyTokens, storageGb, ...}';
COMMENT ON COLUMN sys_tenant_package.status IS '状态机 DRAFT → ACTIVE → ARCHIVED';

-- ------------------------------------------------------------
-- 3. sys_client — OAuth2/SaaS 客户端
-- 来源: SysClient 实体。clientId 全局唯一 (cli_ + 16 位), 状态 ENABLE/DISABLE
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_client (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    client_id           varchar(64)     NOT NULL,
    client_secret       varchar(256)    NOT NULL,
    client_name         varchar(128),
    grant_types         varchar(256),
    device_type         varchar(32),
    access_token_ttl    int,
    refresh_token_ttl   int,
    redirect_uris       text,
    status              varchar(16)     NOT NULL DEFAULT 'ENABLE',
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_sys_client PRIMARY KEY (id)
);

ALTER TABLE sys_client DROP CONSTRAINT IF EXISTS chk_sys_client_status;
ALTER TABLE sys_client ADD CONSTRAINT chk_sys_client_status
    CHECK (status IN ('ENABLE','DISABLE'));

ALTER TABLE sys_client DROP CONSTRAINT IF EXISTS chk_sys_client_ttl;
ALTER TABLE sys_client ADD CONSTRAINT chk_sys_client_ttl
    CHECK (
        (access_token_ttl IS NULL OR access_token_ttl > 0)
        AND (refresh_token_ttl IS NULL OR refresh_token_ttl > 0)
    );

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_client_id ON sys_client (client_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_sys_client_tenant ON sys_client (tenant_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_sys_client_tenant_status ON sys_client (tenant_id, status) WHERE deleted = false;

COMMENT ON TABLE sys_client IS 'OAuth2/SaaS 客户端 — V047 P2-F 批次 6-C: 第三方应用接入, client_credentials 颁发 token';
COMMENT ON COLUMN sys_client.client_id IS '客户端 ID (公开, cli_ + 16 位随机), 全局唯一 (软删过滤)';
COMMENT ON COLUMN sys_client.client_secret IS '客户端密钥 (当前实现明文随机串, 生产建议 BCrypt 存储)';
COMMENT ON COLUMN sys_client.status IS '状态 ENABLE/DISABLE';
