-- ============================================================
-- V044__skill_media_memory_production.sql
-- 生产级补齐：四层记忆 / Skill 执行日志 / 媒体任务外部 ID
-- 设计来源:
--   - 业界同类实现 AI 文档：记忆管理（短/长/用户/全局）、技能管理（docx/pdf/xlsx）、多模态 /media/*
--   - ADR 0002：按 YuTong 栈重写，不引入 Milvus/WarmFlow/SnailJob
-- 约束:
--   - 主键 varchar(32) ULID
--   - Flyway placeholder-replacement=false，禁止 ${}
--   - 幂等 IF NOT EXISTS / DROP IF EXISTS
-- ============================================================

-- ------------------------------------------------------------
-- 1. ai_memory — 长期 / 用户 / 租户 / 全局记忆
-- owner_type: GLOBAL / TENANT / USER / AGENT
-- memory_kind: LONG_TERM / USER / GLOBAL（短期记忆仍走会话窗口，不落本表）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_memory (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    owner_type          varchar(16)     NOT NULL,
    owner_id            varchar(64)     NOT NULL,
    memory_kind         varchar(16)     NOT NULL,
    content             text            NOT NULL,
    source              varchar(32)     NOT NULL DEFAULT 'MANUAL',
    confidence          numeric(4,3)    NOT NULL DEFAULT 1.000,
    expires_at          timestamptz,
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_ai_memory PRIMARY KEY (id)
);

ALTER TABLE ai_memory DROP CONSTRAINT IF EXISTS chk_ai_memory_owner_type;
ALTER TABLE ai_memory ADD CONSTRAINT chk_ai_memory_owner_type
    CHECK (owner_type IN ('GLOBAL','TENANT','USER','AGENT'));

ALTER TABLE ai_memory DROP CONSTRAINT IF EXISTS chk_ai_memory_kind;
ALTER TABLE ai_memory ADD CONSTRAINT chk_ai_memory_kind
    CHECK (memory_kind IN ('LONG_TERM','USER','GLOBAL'));

ALTER TABLE ai_memory DROP CONSTRAINT IF EXISTS chk_ai_memory_source;
ALTER TABLE ai_memory ADD CONSTRAINT chk_ai_memory_source
    CHECK (source IN ('MANUAL','CHAT','KNOWLEDGE','TOOL','AGENT'));

ALTER TABLE ai_memory DROP CONSTRAINT IF EXISTS chk_ai_memory_confidence;
ALTER TABLE ai_memory ADD CONSTRAINT chk_ai_memory_confidence
    CHECK (confidence >= 0 AND confidence <= 1);

CREATE INDEX IF NOT EXISTS idx_ai_memory_tenant_owner ON ai_memory (tenant_id, owner_type, owner_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_memory_kind ON ai_memory (tenant_id, memory_kind) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_memory_expires ON ai_memory (expires_at) WHERE deleted = false AND expires_at IS NOT NULL;

COMMENT ON TABLE ai_memory IS 'AI 四层记忆中的长期层 — V044：短期仍走会话窗口，本表保存跨会话事实/偏好/全局规则';
COMMENT ON COLUMN ai_memory.owner_type IS '归属类型: GLOBAL/TENANT/USER/AGENT';
COMMENT ON COLUMN ai_memory.owner_id IS '归属 ID：GLOBAL 用 *，TENANT 用 tenantId，USER 用 userId，AGENT 用 agentId';
COMMENT ON COLUMN ai_memory.memory_kind IS '记忆种类: LONG_TERM/USER/GLOBAL';
COMMENT ON COLUMN ai_memory.source IS '来源: MANUAL/CHAT/KNOWLEDGE/TOOL/AGENT';
COMMENT ON COLUMN ai_memory.confidence IS '可信度 0-1';
COMMENT ON COLUMN ai_memory.expires_at IS '过期时间，空表示永久';

-- ------------------------------------------------------------
-- 2. ai_skill_run — Skill 执行审计
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_skill_run (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    skill_id            varchar(32)     NOT NULL,
    skill_code          varchar(64)     NOT NULL,
    action              varchar(32)     NOT NULL,
    status              varchar(16)     NOT NULL DEFAULT 'SUCCESS',
    input_json          jsonb,
    output_json         jsonb,
    output_file_id      varchar(32),
    error_message       varchar(1024),
    latency_ms          int,
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_ai_skill_run PRIMARY KEY (id)
);

ALTER TABLE ai_skill_run DROP CONSTRAINT IF EXISTS chk_ai_skill_run_status;
ALTER TABLE ai_skill_run ADD CONSTRAINT chk_ai_skill_run_status
    CHECK (status IN ('SUCCESS','FAILED'));

CREATE INDEX IF NOT EXISTS idx_ai_skill_run_tenant_skill ON ai_skill_run (tenant_id, skill_code) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_skill_run_created ON ai_skill_run (tenant_id, created_time DESC) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_skill_run_input_gin ON ai_skill_run USING GIN (input_json);

COMMENT ON TABLE ai_skill_run IS 'Skill 执行日志 — V044：激活技能、调用脚本、生成文件可审计';
COMMENT ON COLUMN ai_skill_run.action IS '动作: analyze/create';
COMMENT ON COLUMN ai_skill_run.output_file_id IS '生成文件 sys_file.id，可空';

-- ------------------------------------------------------------
-- 3. ai_media_job — 外部任务 ID（视频异步查询）
-- ------------------------------------------------------------
ALTER TABLE ai_media_job ADD COLUMN IF NOT EXISTS external_job_id varchar(128);
COMMENT ON COLUMN ai_media_job.external_job_id IS '厂商异步任务 ID（videoId/predictionId），同步任务可空';
CREATE INDEX IF NOT EXISTS idx_ai_media_job_external ON ai_media_job (tenant_id, external_job_id) WHERE deleted = false AND external_job_id IS NOT NULL;
