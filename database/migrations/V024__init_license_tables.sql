-- ============================================================
-- V024__init_license_tables.sql
-- 商业授权与版本能力裁剪详设（DOC-PRD-010, 70号文档）
-- 第一版可不实现 License Server，但架构必须预留以下三张表：
--   sys_license           - 授权主表
--   sys_license_usage     - 额度使用统计
--   sys_license_audit_log - 授权审计日志
-- 字段对齐 70 号文档「授权运行数据模型」章节
-- 主键采用 varchar(32) 字符串主键（ULID），与平台约定一致
-- ============================================================

-- 1. 授权主表 sys_license
CREATE TABLE IF NOT EXISTS sys_license (
    id                   varchar(32)   NOT NULL,
    tenant_id            varchar(32)   NOT NULL DEFAULT 'default',
    license_id           varchar(64)   NOT NULL,
    edition              varchar(32)   NOT NULL,
    subject              varchar(256)  NOT NULL,
    deployment_id        varchar(64)   NOT NULL,
    license_schema_version varchar(16) NOT NULL DEFAULT '1.0.0',
    limits_json          jsonb         NOT NULL DEFAULT '{}'::jsonb,
    modules_json         jsonb         NOT NULL DEFAULT '[]'::jsonb,
    expire_time          timestamptz   NOT NULL,
    signature            text          NOT NULL,
    status               varchar(16)   NOT NULL DEFAULT 'ACTIVE',
    last_verified_time   timestamptz,
    created_by           varchar(64)   NOT NULL DEFAULT 'system',
    created_time         timestamptz   NOT NULL DEFAULT now(),
    updated_by           varchar(64),
    updated_time         timestamptz   NOT NULL DEFAULT now(),
    deleted              boolean       NOT NULL DEFAULT false,
    version              int           NOT NULL DEFAULT 0,
    remark               varchar(512),
    CONSTRAINT pk_sys_license PRIMARY KEY (id),
    CONSTRAINT uk_sys_license_license_id UNIQUE (license_id),
    CONSTRAINT ck_sys_license_edition CHECK (edition IN ('Community', 'Professional', 'Enterprise', 'Industry')),
    CONSTRAINT ck_sys_license_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'INVALID', 'REVOKED'))
);

CREATE INDEX IF NOT EXISTS idx_sys_license_tenant   ON sys_license (tenant_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_sys_license_deploy   ON sys_license (deployment_id);
CREATE INDEX IF NOT EXISTS idx_sys_license_status   ON sys_license (status) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_sys_license_expire   ON sys_license (expire_time);

COMMENT ON TABLE  sys_license IS '授权主表（70号文档）保存 License 文件解析后的关键字段，支持离线授权和审计';
COMMENT ON COLUMN sys_license.license_id              IS '授权 ID（如 LIC-2026-0001），业务唯一键';
COMMENT ON COLUMN sys_license.edition                 IS '版本枚举 Community/Professional/Enterprise/Industry';
COMMENT ON COLUMN sys_license.subject                 IS '授权主体（客户/公司名）';
COMMENT ON COLUMN sys_license.deployment_id           IS '部署实例 ID，绑定机器或集群';
COMMENT ON COLUMN sys_license.license_schema_version  IS 'License schema 版本，支持大版本升级兼容';
COMMENT ON COLUMN sys_license.limits_json             IS '租户数、用户数、AI 月额度等限制（JSON）';
COMMENT ON COLUMN sys_license.modules_json            IS '授权模块列表（JSON 数组）';
COMMENT ON COLUMN sys_license.expire_time             IS '到期时间（含时区）';
COMMENT ON COLUMN sys_license.signature               IS '服务端私钥签名，运行时只用公钥验证';
COMMENT ON COLUMN sys_license.status                  IS 'ACTIVE/EXPIRED/INVALID/REVOKED';
COMMENT ON COLUMN sys_license.last_verified_time      IS '最近校验时间，用于篡改检测和定时校验';

-- 2. 额度使用统计表 sys_license_usage
CREATE TABLE IF NOT EXISTS sys_license_usage (
    id                   varchar(32)   NOT NULL,
    tenant_id            varchar(32)   NOT NULL,
    quota_code           varchar(64)   NOT NULL,
    usage_period         varchar(16)   NOT NULL,
    used_amount          numeric(18,4) NOT NULL DEFAULT 0,
    limit_amount         numeric(18,4) NOT NULL DEFAULT 0,
    last_used_time       timestamptz,
    created_by           varchar(64)   NOT NULL DEFAULT 'system',
    created_time         timestamptz   NOT NULL DEFAULT now(),
    updated_by           varchar(64),
    updated_time         timestamptz   NOT NULL DEFAULT now(),
    deleted              boolean       NOT NULL DEFAULT false,
    version              int           NOT NULL DEFAULT 0,
    remark               varchar(512),
    CONSTRAINT pk_sys_license_usage PRIMARY KEY (id),
    CONSTRAINT uk_sys_license_usage_quota UNIQUE (tenant_id, quota_code, usage_period)
);

CREATE INDEX IF NOT EXISTS idx_sys_license_usage_tenant ON sys_license_usage (tenant_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_sys_license_usage_period ON sys_license_usage (usage_period);
CREATE INDEX IF NOT EXISTS idx_sys_license_usage_quota  ON sys_license_usage (quota_code);

COMMENT ON TABLE  sys_license_usage IS '额度使用统计表（70号文档）记录 AI token、低代码生成、报表导出等额度按租户/周期统计';
COMMENT ON COLUMN sys_license_usage.quota_code    IS '额度编码，如 ai.monthly.tokens / lowcode.generate.count';
COMMENT ON COLUMN sys_license_usage.usage_period  IS '统计周期，例如 2026-07（按租户时区，默认 Asia/Shanghai）';
COMMENT ON COLUMN sys_license_usage.used_amount   IS '已使用量（高并发场景由 Redis 原子计数，定期落账）';
COMMENT ON COLUMN sys_license_usage.limit_amount  IS '授权上限（来自 sys_license.limits_json）';

-- 3. 授权审计日志表 sys_license_audit_log
CREATE TABLE IF NOT EXISTS sys_license_audit_log (
    id                   varchar(32)   NOT NULL,
    tenant_id            varchar(32)   NOT NULL DEFAULT 'default',
    action               varchar(32)   NOT NULL,
    license_id           varchar(64),
    module_code          varchar(64),
    quota_code           varchar(64),
    result               varchar(16)   NOT NULL,
    error_code           varchar(64),
    user_id_hash         varchar(128),
    tenant_id_hash       varchar(128),
    trace_id             varchar(64),
    detail_json          jsonb,
    operated_time        timestamptz   NOT NULL DEFAULT now(),
    created_time         timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_sys_license_audit_log PRIMARY KEY (id),
    CONSTRAINT ck_sys_license_audit_action CHECK (action IN ('LOAD', 'VERIFY', 'REFRESH', 'DENY', 'EXPIRE', 'QUOTA_EXCEEDED')),
    CONSTRAINT ck_sys_license_audit_result CHECK (result IN ('SUCCESS', 'FAILURE', 'DENIED', 'WARN'))
);

CREATE INDEX IF NOT EXISTS idx_sys_license_audit_license ON sys_license_audit_log (license_id);
CREATE INDEX IF NOT EXISTS idx_sys_license_audit_action  ON sys_license_audit_log (action);
CREATE INDEX IF NOT EXISTS idx_sys_license_audit_time    ON sys_license_audit_log (operated_time);
CREATE INDEX IF NOT EXISTS idx_sys_license_audit_trace   ON sys_license_audit_log (trace_id);
CREATE INDEX IF NOT EXISTS idx_sys_license_audit_tenant  ON sys_license_audit_log (tenant_id_hash);

COMMENT ON TABLE  sys_license_audit_log IS '授权审计日志表（70号文档）记录所有直连 API 拒绝、授权校验、额度超限等事件';
COMMENT ON COLUMN sys_license_audit_log.action         IS 'LOAD/VERIFY/REFRESH/DENY/EXPIRE/QUOTA_EXCEEDED';
COMMENT ON COLUMN sys_license_audit_log.module_code    IS '模块编码（可空），如 lowcode/ai/report/workflow/datasource/plugin';
COMMENT ON COLUMN sys_license_audit_log.quota_code     IS '额度编码（可空）';
COMMENT ON COLUMN sys_license_audit_log.user_id_hash   IS '哈希化用户标识，避免审计泄露客户信息';
COMMENT ON COLUMN sys_license_audit_log.tenant_id_hash IS '哈希化租户标识';
COMMENT ON COLUMN sys_license_audit_log.trace_id       IS '链路 ID，关联 sys_operation_log';
