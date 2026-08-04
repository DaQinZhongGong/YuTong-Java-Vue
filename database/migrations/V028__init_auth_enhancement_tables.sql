-- ============================================================
-- V028__init_auth_enhancement_tables.sql
-- 企业级权限与租户接入方案（32 号文档）
-- 创建 4 张表：身份提供商、Refresh Token 家族、应急管理员、登录日志
-- 主键采用 varchar(32) 字符串主键（ULID），与平台约定一致
-- ============================================================

-- ============================================================
-- 1. auth_identity_provider - 身份提供商配置表
-- ============================================================
CREATE TABLE IF NOT EXISTS auth_identity_provider (
    id                      varchar(32)   NOT NULL,
    tenant_id               varchar(32)   NOT NULL DEFAULT 'default',
    provider_code           varchar(64)   NOT NULL,
    provider_type           varchar(32)   NOT NULL,
    issuer                  varchar(512),
    client_id               varchar(256),
    client_secret_encrypted varchar(1024),
    redirect_uri            varchar(512),
    scope                   varchar(512),
    jwks_uri                varchar(512),
    userinfo_endpoint       varchar(512),
    auth_endpoint           varchar(512),
    token_endpoint          varchar(512),
    logout_endpoint         varchar(512),
    clock_skew_seconds      int           NOT NULL DEFAULT 60,
    group_mapping_json      jsonb,
    role_mapping_json       jsonb,
    status                  varchar(16)   NOT NULL DEFAULT 'ACTIVE',
    -- 通用字段（BaseEntity 约束）
    created_by              varchar(32),
    created_time            timestamptz   NOT NULL DEFAULT now(),
    updated_by              varchar(32),
    updated_time            timestamptz   NOT NULL DEFAULT now(),
    deleted                 boolean       NOT NULL DEFAULT false,
    version                 int           NOT NULL DEFAULT 0,
    remark                  varchar(256),
    CONSTRAINT pk_auth_identity_provider PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_auth_identity_provider_code ON auth_identity_provider (tenant_id, provider_code) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_auth_identity_provider_type ON auth_identity_provider (tenant_id, provider_type) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_auth_identity_provider_status ON auth_identity_provider (tenant_id, status) WHERE deleted = false;

COMMENT ON TABLE auth_identity_provider IS '身份提供商配置表（32 号文档）保存 OIDC/LDAP_AD/CAS/WECHAT/DINGTALK/FEISHU 等身份提供商配置';
COMMENT ON COLUMN auth_identity_provider.provider_code IS '提供商编码，租户内唯一';
COMMENT ON COLUMN auth_identity_provider.provider_type IS '提供商类型：OIDC/LDAP_AD/CAS/WECHAT/DINGTALK/FEISHU';
COMMENT ON COLUMN auth_identity_provider.issuer IS 'OIDC 发行者 URL';
COMMENT ON COLUMN auth_identity_provider.client_id IS 'OAuth2 客户端 ID';
COMMENT ON COLUMN auth_identity_provider.client_secret_encrypted IS '加密后的客户端密钥';
COMMENT ON COLUMN auth_identity_provider.redirect_uri IS '重定向 URI';
COMMENT ON COLUMN auth_identity_provider.scope IS '请求的权限范围，空格分隔';
COMMENT ON COLUMN auth_identity_provider.jwks_uri IS 'JWKS 公钥端点';
COMMENT ON COLUMN auth_identity_provider.userinfo_endpoint IS '用户信息端点';
COMMENT ON COLUMN auth_identity_provider.auth_endpoint IS '授权端点';
COMMENT ON COLUMN auth_identity_provider.token_endpoint IS '令牌端点';
COMMENT ON COLUMN auth_identity_provider.logout_endpoint IS '登出端点';
COMMENT ON COLUMN auth_identity_provider.clock_skew_seconds IS '时钟偏差容忍秒数，默认 60';
COMMENT ON COLUMN auth_identity_provider.group_mapping_json IS '组映射 JSON，外部组到平台角色/权限的映射';
COMMENT ON COLUMN auth_identity_provider.role_mapping_json IS '角色映射 JSON';
COMMENT ON COLUMN auth_identity_provider.status IS '状态：ACTIVE/INACTIVE';

-- ============================================================
-- 2. auth_refresh_token_family - Refresh Token 家族表
-- ============================================================
CREATE TABLE IF NOT EXISTS auth_refresh_token_family (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL DEFAULT 'default',
    family_id       varchar(64)   NOT NULL,
    session_id      varchar(64)   NOT NULL,
    subject_id      varchar(64)   NOT NULL,
    current_jti     varchar(128)  NOT NULL,
    device_hash     varchar(128),
    issued_at       timestamptz   NOT NULL,
    expires_at      timestamptz   NOT NULL,
    rotated_at      timestamptz,
    revoked         boolean       NOT NULL DEFAULT false,
    revoke_reason   varchar(256),
    -- 通用字段（BaseEntity 约束）
    created_by      varchar(32),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(32),
    updated_time    timestamptz   NOT NULL DEFAULT now(),
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(256),
    CONSTRAINT pk_auth_refresh_token_family PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_family_family ON auth_refresh_token_family (family_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_family_session ON auth_refresh_token_family (session_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_family_subject ON auth_refresh_token_family (subject_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_family_jti ON auth_refresh_token_family (current_jti) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_family_expires ON auth_refresh_token_family (expires_at) WHERE deleted = false;

COMMENT ON TABLE auth_refresh_token_family IS 'Refresh Token 家族表（32 号文档）跟踪 refresh token 轮换和重放检测';
COMMENT ON COLUMN auth_refresh_token_family.family_id IS 'token 家族 ID，同一会话的所有 refresh token 共享';
COMMENT ON COLUMN auth_refresh_token_family.session_id IS '会话 ID';
COMMENT ON COLUMN auth_refresh_token_family.subject_id IS '主体 ID（用户 ID）';
COMMENT ON COLUMN auth_refresh_token_family.current_jti IS '当前 JWT ID';
COMMENT ON COLUMN auth_refresh_token_family.device_hash IS '设备指纹哈希';
COMMENT ON COLUMN auth_refresh_token_family.issued_at IS '签发时间';
COMMENT ON COLUMN auth_refresh_token_family.expires_at IS '过期时间';
COMMENT ON COLUMN auth_refresh_token_family.rotated_at IS '最近轮换时间';
COMMENT ON COLUMN auth_refresh_token_family.revoked IS '是否已撤销';
COMMENT ON COLUMN auth_refresh_token_family.revoke_reason IS '撤销原因';

-- ============================================================
-- 3. auth_emergency_admin - 应急管理员表
-- ============================================================
CREATE TABLE IF NOT EXISTS auth_emergency_admin (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL DEFAULT 'default',
    admin_code          varchar(64)   NOT NULL,
    admin_name          varchar(128)  NOT NULL,
    secret_hash         varchar(512)  NOT NULL,
    status              varchar(16)   NOT NULL DEFAULT 'DISABLED',
    last_used_at        timestamptz,
    usage_count         int           NOT NULL DEFAULT 0,
    max_usage_count     int           NOT NULL DEFAULT 5,
    approved_by         varchar(64),
    approved_reason     varchar(512),
    approved_at         timestamptz,
    -- 通用字段（BaseEntity 约束）
    created_by          varchar(32),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(32),
    updated_time        timestamptz   NOT NULL DEFAULT now(),
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(256),
    CONSTRAINT pk_auth_emergency_admin PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_auth_emergency_admin_code ON auth_emergency_admin (tenant_id, admin_code) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_auth_emergency_admin_status ON auth_emergency_admin (tenant_id, status) WHERE deleted = false;

COMMENT ON TABLE auth_emergency_admin IS '应急管理员表（32 号文档）用于 IdP 故障时的紧急访问';
COMMENT ON COLUMN auth_emergency_admin.admin_code IS '管理员编码，租户内唯一';
COMMENT ON COLUMN auth_emergency_admin.admin_name IS '管理员名称';
COMMENT ON COLUMN auth_emergency_admin.secret_hash IS '加密后的密钥哈希';
COMMENT ON COLUMN auth_emergency_admin.status IS '状态：DISABLED/ENABLED/IN_USE';
COMMENT ON COLUMN auth_emergency_admin.last_used_at IS '最近使用时间';
COMMENT ON COLUMN auth_emergency_admin.usage_count IS '已使用次数';
COMMENT ON COLUMN auth_emergency_admin.max_usage_count IS '最大使用次数，默认 5';
COMMENT ON COLUMN auth_emergency_admin.approved_by IS '批准人';
COMMENT ON COLUMN auth_emergency_admin.approved_reason IS '批准原因';
COMMENT ON COLUMN auth_emergency_admin.approved_at IS '批准时间';

-- ============================================================
-- 4. auth_login_log - 登录日志表
-- ============================================================
CREATE TABLE IF NOT EXISTS auth_login_log (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL DEFAULT 'default',
    user_id         varchar(64),
    username        varchar(128),
    auth_method     varchar(32),
    login_result    varchar(16)   NOT NULL,
    ip_address      varchar(64),
    user_agent      varchar(512),
    session_id      varchar(64),
    trace_id        varchar(64),
    failure_reason  varchar(512),
    -- 通用字段（BaseEntity 约束）
    created_by      varchar(32),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(32),
    updated_time    timestamptz   NOT NULL DEFAULT now(),
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(256),
    CONSTRAINT pk_auth_login_log PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_auth_login_log_user ON auth_login_log (user_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_auth_login_log_session ON auth_login_log (session_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_auth_login_log_result ON auth_login_log (login_result) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_auth_login_log_time ON auth_login_log (created_time DESC) WHERE deleted = false;

COMMENT ON TABLE auth_login_log IS '登录日志表（32 号文档）记录所有登录、登出、失败事件';
COMMENT ON COLUMN auth_login_log.user_id IS '用户 ID';
COMMENT ON COLUMN auth_login_log.username IS '用户名';
COMMENT ON COLUMN auth_login_log.auth_method IS '认证方式：OIDC/LDAP_AD/CAS/MOCK/EMERGENCY_ADMIN';
COMMENT ON COLUMN auth_login_log.login_result IS '登录结果：SUCCESS/FAILED/LOCKED';
COMMENT ON COLUMN auth_login_log.ip_address IS '客户端 IP 地址';
COMMENT ON COLUMN auth_login_log.user_agent IS 'User-Agent';
COMMENT ON COLUMN auth_login_log.session_id IS '会话 ID';
COMMENT ON COLUMN auth_login_log.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN auth_login_log.failure_reason IS '失败原因';

-- ============================================================
-- 种子数据
-- ============================================================

-- 1 个 OIDC 提供商（mock-oidc-provider，用于测试）
INSERT INTO auth_identity_provider (id, tenant_id, provider_code, provider_type, issuer, client_id, redirect_uri, scope, status, created_by)
VALUES ('01AUTHIDP000000000000000001', 'default', 'mock-oidc-provider', 'OIDC', 
        'http://localhost:8080/mock-oidc', 'mock-client-id', 
        'http://localhost:3000/callback', 'openid profile email',
        'ACTIVE', 'system')
ON CONFLICT (tenant_id, provider_code) WHERE deleted = false DO NOTHING;

-- 2 个应急管理员（emergency-admin-001, emergency-admin-002，状态 DISABLED）
INSERT INTO auth_emergency_admin (id, tenant_id, admin_code, admin_name, secret_hash, status, max_usage_count, created_by)
VALUES ('01AUTHEMA000000000000000001', 'default', 'emergency-admin-001', '应急管理员 001',
        '$2a$10$placeholder_hash_001', 'DISABLED', 5, 'system')
ON CONFLICT (tenant_id, admin_code) WHERE deleted = false DO NOTHING;

INSERT INTO auth_emergency_admin (id, tenant_id, admin_code, admin_name, secret_hash, status, max_usage_count, created_by)
VALUES ('01AUTHEMA000000000000000002', 'default', 'emergency-admin-002', '应急管理员 002',
        '$2a$10$placeholder_hash_002', 'DISABLED', 5, 'system')
ON CONFLICT (tenant_id, admin_code) WHERE deleted = false DO NOTHING;
