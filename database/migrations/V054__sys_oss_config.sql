-- ============================================================
-- V054__sys_oss_config.sql
-- P1-D OSS 配置管理: sys_oss_config 多桶配置表建表 + 默认 MinIO 配置种子
-- 设计来源:
--   - 业界同类实现 AI sys_oss_config (endpoint/accessKey/secretKey/bucketName/domain/region)
--   - 后端实体: SysOssConfig / OssConfigService (yutong-system-service)
--   - 约束: 幂等 IF NOT EXISTS / DROP IF EXISTS, 禁止 Flyway 占位 ${}(placeholder-replacement=false)
--   - AGENTS.md 硬约束: ULID varchar(32) PK、tenant_id、deleted/version、
--     created_*/updated_* 规范、CHECK 状态机、中文 COMMENT
-- 注意: sys_oss_config 为平台级配置, 在 application.yml yutong.tenant.ignore-tables
--   中豁免行级过滤 (平台管理员全局可见, 接口由 system:oss-config:* 权限码守卫)
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_oss_config (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    config_key          varchar(64)     NOT NULL,
    config_name         varchar(128)    NOT NULL,
    storage_type        varchar(16)     NOT NULL DEFAULT 'MINIO',
    endpoint            varchar(256)    NOT NULL,
    access_key          varchar(128)    NOT NULL,
    secret_key          varchar(256)    NOT NULL,
    bucket_name         varchar(128)    NOT NULL,
    domain              varchar(256),
    region              varchar(64),
    is_https            boolean         NOT NULL DEFAULT false,
    is_default          boolean         NOT NULL DEFAULT false,
    status              varchar(16)     NOT NULL DEFAULT 'ENABLED',
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_sys_oss_config PRIMARY KEY (id)
);

ALTER TABLE sys_oss_config DROP CONSTRAINT IF EXISTS chk_sys_oss_config_storage_type;
ALTER TABLE sys_oss_config ADD CONSTRAINT chk_sys_oss_config_storage_type
    CHECK (storage_type IN ('MINIO','S3','LOCAL'));

ALTER TABLE sys_oss_config DROP CONSTRAINT IF EXISTS chk_sys_oss_config_status;
ALTER TABLE sys_oss_config ADD CONSTRAINT chk_sys_oss_config_status
    CHECK (status IN ('ENABLED','DISABLED'));

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_oss_config_key ON sys_oss_config (config_key) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_sys_oss_config_status ON sys_oss_config (status) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_sys_oss_config_default ON sys_oss_config (is_default) WHERE deleted = false AND is_default = true;

COMMENT ON TABLE sys_oss_config IS 'OSS 配置表 — V054 P1-D: 多桶对象存储配置 (豁免行级过滤, 权限码守卫), 支持 MinIO/S3/Local';
COMMENT ON COLUMN sys_oss_config.config_key IS '配置键 (唯一, 如 default/backup/cdn), 用于 API 引用';
COMMENT ON COLUMN sys_oss_config.storage_type IS '存储类型: MINIO / S3 / LOCAL';
COMMENT ON COLUMN sys_oss_config.endpoint IS '存储端点 (如 http://minio:9000)';
COMMENT ON COLUMN sys_oss_config.access_key IS '访问密钥 ID';
COMMENT ON COLUMN sys_oss_config.secret_key IS '访问密钥 Secret (敏感, 列表接口脱敏返回)';
COMMENT ON COLUMN sys_oss_config.bucket_name IS '桶名';
COMMENT ON COLUMN sys_oss_config.domain IS '自定义域名/CDN (可空, 空则用 endpoint)';
COMMENT ON COLUMN sys_oss_config.is_default IS '是否默认配置 (同一时刻仅一个 true)';
COMMENT ON COLUMN sys_oss_config.status IS '状态: ENABLED 启用 / DISABLED 停用';

-- 默认 MinIO 配置种子 (与 application-local.yml yutong.storage.minio 对齐, 幂等)
INSERT INTO sys_oss_config (
    id, tenant_id, config_key, config_name, storage_type,
    endpoint, access_key, secret_key, bucket_name,
    is_https, is_default, status, remark
)
SELECT
    '01JDEFAULTMINIOCONFIG0000000001', 'default', 'default', '默认 MinIO', 'MINIO',
    'http://localhost:9000', 'yutong-minio', 'yutong-minio-dev-secret', 'yutong',
    false, true, 'ENABLED', '开发环境默认配置, 生产环境请修改密钥'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_oss_config WHERE config_key = 'default' AND deleted = false
);
