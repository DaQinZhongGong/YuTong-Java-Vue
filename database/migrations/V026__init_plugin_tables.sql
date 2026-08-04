-- ============================================================
-- V026__init_plugin_tables.sql
-- 插件与模板生态设计（45 号文档「插件与模板生态设计」）
-- 创建 4 张表：plugin_package（插件包）、plugin_installation（插件安装记录）、
-- mkt_template（模板市场）、plugin_audit_log（插件审计日志）
-- 主键采用 varchar(32) 字符串主键（ULID），与平台约定一致
-- ============================================================

-- ===== 1. plugin_package（插件包）=====
CREATE TABLE IF NOT EXISTS plugin_package (
    id                      varchar(32)   NOT NULL,
    tenant_id               varchar(32)   NOT NULL DEFAULT 'default',
    plugin_code             varchar(64)   NOT NULL,
    plugin_name             varchar(128)  NOT NULL,
    plugin_version          varchar(32)   NOT NULL DEFAULT '1.0.0',
    package_hash            varchar(128),
    signature_status        varchar(16)   NOT NULL DEFAULT 'UNSIGNED',
    license_type            varchar(32),
    risk_level              varchar(16)   NOT NULL DEFAULT 'LOW',
    min_platform_version    varchar(16),
    max_platform_version    varchar(16),
    status                  varchar(16)   NOT NULL DEFAULT 'UPLOADED',
    install_count           int           NOT NULL DEFAULT 0,
    -- 通用字段（BaseEntity 约束）
    created_by              varchar(32),
    created_time            timestamptz   NOT NULL DEFAULT now(),
    updated_by              varchar(32),
    updated_time            timestamptz   NOT NULL DEFAULT now(),
    deleted                 boolean       NOT NULL DEFAULT false,
    version                 int           NOT NULL DEFAULT 0,
    remark                  varchar(256),
    CONSTRAINT pk_plugin_package PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_plugin_package_code ON plugin_package (tenant_id, plugin_code) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_plugin_package_status ON plugin_package (tenant_id, status) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_plugin_package_risk_level ON plugin_package (tenant_id, risk_level) WHERE deleted = false;

COMMENT ON TABLE plugin_package IS '插件包表（45 号文档插件生态）保存插件元数据、签名状态、风险等级、版本兼容性等';
COMMENT ON COLUMN plugin_package.plugin_code IS '插件编码，租户内唯一';
COMMENT ON COLUMN plugin_package.plugin_version IS '插件版本，semver';
COMMENT ON COLUMN plugin_package.package_hash IS '插件包哈希值（SHA-256）';
COMMENT ON COLUMN plugin_package.signature_status IS '签名状态 UNSIGNED/VALID/INVALID';
COMMENT ON COLUMN plugin_package.license_type IS '授权类型（开源/商业/试用）';
COMMENT ON COLUMN plugin_package.risk_level IS '风险等级 LOW/MEDIUM/HIGH';
COMMENT ON COLUMN plugin_package.min_platform_version IS '最低平台版本兼容约束';
COMMENT ON COLUMN plugin_package.max_platform_version IS '最高平台版本兼容约束';
COMMENT ON COLUMN plugin_package.status IS '状态 UPLOADED/VERIFIED/REJECTED/DEPRECATED';
COMMENT ON COLUMN plugin_package.install_count IS '安装次数统计';

-- ===== 2. plugin_installation（插件安装记录）=====
CREATE TABLE IF NOT EXISTS plugin_installation (
    id                      varchar(32)   NOT NULL,
    tenant_id               varchar(32)   NOT NULL DEFAULT 'default',
    plugin_id               varchar(32)   NOT NULL,
    installed_by            varchar(32)   NOT NULL,
    installed_time          timestamptz   NOT NULL DEFAULT now(),
    uninstalled_time        timestamptz,
    status                  varchar(16)   NOT NULL DEFAULT 'ACTIVE',
    config_json             jsonb,
    -- 通用字段（BaseEntity 约束）
    created_by              varchar(32),
    created_time            timestamptz   NOT NULL DEFAULT now(),
    updated_by              varchar(32),
    updated_time            timestamptz   NOT NULL DEFAULT now(),
    deleted                 boolean       NOT NULL DEFAULT false,
    version                 int           NOT NULL DEFAULT 0,
    remark                  varchar(256),
    CONSTRAINT pk_plugin_installation PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_plugin_installation_plugin_id ON plugin_installation (tenant_id, plugin_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_plugin_installation_status ON plugin_installation (tenant_id, status) WHERE deleted = false;

COMMENT ON TABLE plugin_installation IS '插件安装记录表（45 号文档插件生态）保存租户插件安装状态、配置、安装/卸载时间';
COMMENT ON COLUMN plugin_installation.plugin_id IS '关联的插件包 ID';
COMMENT ON COLUMN plugin_installation.installed_by IS '安装人用户 ID';
COMMENT ON COLUMN plugin_installation.installed_time IS '安装时间';
COMMENT ON COLUMN plugin_installation.uninstalled_time IS '卸载时间';
COMMENT ON COLUMN plugin_installation.status IS '状态 ACTIVE/INACTIVE';
COMMENT ON COLUMN plugin_installation.config_json IS '插件配置 JSON（运行时参数）';

-- ===== 3. mkt_template（模板市场）=====
CREATE TABLE IF NOT EXISTS mkt_template (
    id                      varchar(32)   NOT NULL,
    tenant_id               varchar(32)   NOT NULL DEFAULT 'default',
    template_code           varchar(64)   NOT NULL,
    template_name           varchar(128)  NOT NULL,
    category                varchar(32)   NOT NULL,
    preview_images          text,
    package_url             varchar(512),
    min_version             varchar(16),
    install_count           int           NOT NULL DEFAULT 0,
    status                  varchar(16)   NOT NULL DEFAULT 'DRAFT',
    -- 通用字段（BaseEntity 约束）
    created_by              varchar(32),
    created_time            timestamptz   NOT NULL DEFAULT now(),
    updated_by              varchar(32),
    updated_time            timestamptz   NOT NULL DEFAULT now(),
    deleted                 boolean       NOT NULL DEFAULT false,
    version                 int           NOT NULL DEFAULT 0,
    remark                  varchar(256),
    CONSTRAINT pk_mkt_template PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mkt_template_code ON mkt_template (tenant_id, template_code) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_mkt_template_category ON mkt_template (tenant_id, category) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_mkt_template_status ON mkt_template (tenant_id, status) WHERE deleted = false;

COMMENT ON TABLE mkt_template IS '模板市场表（45 号文档模板生态）保存模板元数据、分类、预览图、安装包地址';
COMMENT ON COLUMN mkt_template.template_code IS '模板编码，租户内唯一';
COMMENT ON COLUMN mkt_template.category IS '模板分类 BUSINESS/PAGE/INDUSTRY/THEME';
COMMENT ON COLUMN mkt_template.preview_images IS '预览图 URL 列表（JSON 数组）';
COMMENT ON COLUMN mkt_template.package_url IS '模板安装包下载地址';
COMMENT ON COLUMN mkt_template.min_version IS '最低平台版本要求';
COMMENT ON COLUMN mkt_template.install_count IS '安装次数统计';
COMMENT ON COLUMN mkt_template.status IS '状态 DRAFT/PUBLISHED';

-- ===== 4. plugin_audit_log（插件审计日志）=====
CREATE TABLE IF NOT EXISTS plugin_audit_log (
    id                      varchar(32)   NOT NULL,
    tenant_id               varchar(32)   NOT NULL DEFAULT 'default',
    plugin_code             varchar(64),
    plugin_version          varchar(32),
    operator_id             varchar(32),
    permission_code         varchar(128),
    biz_type                varchar(64),
    biz_id                  varchar(64),
    trace_id                varchar(64),
    result                  varchar(16)   NOT NULL DEFAULT 'SUCCESS',
    error_code              varchar(32),
    -- 通用字段（BaseEntity 约束）
    created_by              varchar(32),
    created_time            timestamptz   NOT NULL DEFAULT now(),
    updated_by              varchar(32),
    updated_time            timestamptz   NOT NULL DEFAULT now(),
    deleted                 boolean       NOT NULL DEFAULT false,
    version                 int           NOT NULL DEFAULT 0,
    remark                  varchar(256),
    CONSTRAINT pk_plugin_audit_log PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_plugin_audit_log_plugin_code ON plugin_audit_log (tenant_id, plugin_code) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_plugin_audit_log_operator_id ON plugin_audit_log (tenant_id, operator_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_plugin_audit_log_biz_type ON plugin_audit_log (tenant_id, biz_type) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_plugin_audit_log_created_time ON plugin_audit_log (tenant_id, created_time DESC) WHERE deleted = false;

COMMENT ON TABLE plugin_audit_log IS '插件审计日志表（45 号文档插件生态）记录插件操作审计轨迹';
COMMENT ON COLUMN plugin_audit_log.plugin_code IS '操作的插件编码';
COMMENT ON COLUMN plugin_audit_log.plugin_version IS '操作的插件版本';
COMMENT ON COLUMN plugin_audit_log.operator_id IS '操作人用户 ID';
COMMENT ON COLUMN plugin_audit_log.permission_code IS '使用的权限码';
COMMENT ON COLUMN plugin_audit_log.biz_type IS '业务类型（安装/卸载/配置等）';
COMMENT ON COLUMN plugin_audit_log.biz_id IS '业务 ID（安装记录 ID 等）';
COMMENT ON COLUMN plugin_audit_log.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN plugin_audit_log.result IS '操作结果 SUCCESS/FAILED';
COMMENT ON COLUMN plugin_audit_log.error_code IS '错误码（失败时记录）';

-- ============================================================
-- 种子数据: 3 个官方插件 + 2 个模板（45 号文档 line 132-150）
-- status: VERIFIED（已验证）/ PUBLISHED（已发布）
-- ON CONFLICT 保证可重复执行
-- ============================================================

-- ===== 官方插件 =====
INSERT INTO plugin_package (id, tenant_id, plugin_code, plugin_name, plugin_version, package_hash, signature_status, license_type, risk_level, min_platform_version, max_platform_version, status, install_count, created_by)
VALUES ('01PLG000000000000000000001', 'default', 'demo-chart-widget', '图表组件插件', '1.0.0', 'sha256:abc123def456', 'VALID', '开源', 'LOW', '1.0.0', '2.0.0', 'VERIFIED', 128, 'system')
ON CONFLICT (tenant_id, plugin_code) WHERE deleted = false DO NOTHING;

INSERT INTO plugin_package (id, tenant_id, plugin_code, plugin_name, plugin_version, package_hash, signature_status, license_type, risk_level, min_platform_version, max_platform_version, status, install_count, created_by)
VALUES ('01PLG000000000000000000002', 'default', 'demo-form-template', '表单模板插件', '1.0.0', 'sha256:xyz789uvw012', 'VALID', '开源', 'LOW', '1.0.0', '2.0.0', 'VERIFIED', 256, 'system')
ON CONFLICT (tenant_id, plugin_code) WHERE deleted = false DO NOTHING;

INSERT INTO plugin_package (id, tenant_id, plugin_code, plugin_name, plugin_version, package_hash, signature_status, license_type, risk_level, min_platform_version, max_platform_version, status, install_count, created_by)
VALUES ('01PLG000000000000000000003', 'default', 'demo-report-component', '报表组件插件', '1.0.0', 'sha256:mno345pqr678', 'VALID', '商业', 'MEDIUM', '1.0.0', '2.0.0', 'VERIFIED', 64, 'system')
ON CONFLICT (tenant_id, plugin_code) WHERE deleted = false DO NOTHING;

-- ===== 模板 =====
INSERT INTO mkt_template (id, tenant_id, template_code, template_name, category, preview_images, package_url, min_version, install_count, status, created_by)
VALUES ('01TPL000000000000000000001', 'default', 'leave-request-template', '请假申请模板', 'BUSINESS', '["https://example.com/preview1.png","https://example.com/preview2.png"]', 'https://example.com/templates/leave-request.zip', '1.0.0', 512, 'PUBLISHED', 'system')
ON CONFLICT (tenant_id, template_code) WHERE deleted = false DO NOTHING;

INSERT INTO mkt_template (id, tenant_id, template_code, template_name, category, preview_images, package_url, min_version, install_count, status, created_by)
VALUES ('01TPL000000000000000000002', 'default', 'purchase-order-template', '采购订单模板', 'BUSINESS', '["https://example.com/preview3.png","https://example.com/preview4.png"]', 'https://example.com/templates/purchase-order.zip', '1.0.0', 256, 'PUBLISHED', 'system')
ON CONFLICT (tenant_id, template_code) WHERE deleted = false DO NOTHING;
