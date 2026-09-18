-- ============================================================
-- V045__copilot_run_drama_image.sql
-- Copilot 草稿 Run 审计（待审批，不直接落低代码库）
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_copilot_run (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    target_type         varchar(16)     NOT NULL DEFAULT 'form',
    prompt              text            NOT NULL,
    status              varchar(16)     NOT NULL DEFAULT 'DRAFT',
    provider_code       varchar(64),
    model_code          varchar(128),
    output_json         jsonb,
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_ai_copilot_run PRIMARY KEY (id)
);

ALTER TABLE ai_copilot_run DROP CONSTRAINT IF EXISTS chk_ai_copilot_run_status;
ALTER TABLE ai_copilot_run ADD CONSTRAINT chk_ai_copilot_run_status
    CHECK (status IN ('DRAFT','APPROVED','REJECTED','FAILED'));

ALTER TABLE ai_copilot_run DROP CONSTRAINT IF EXISTS chk_ai_copilot_run_target;
ALTER TABLE ai_copilot_run ADD CONSTRAINT chk_ai_copilot_run_target
    CHECK (target_type IN ('form','page','entity'));

CREATE INDEX IF NOT EXISTS idx_ai_copilot_run_tenant ON ai_copilot_run (tenant_id, created_time DESC) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_copilot_run_status ON ai_copilot_run (tenant_id, status) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_copilot_run_output_gin ON ai_copilot_run USING GIN (output_json);

COMMENT ON TABLE ai_copilot_run IS 'Copilot 草稿 Run — V045：LLM 生成后处于 DRAFT，需审批后才可进入低代码发布';
COMMENT ON COLUMN ai_copilot_run.status IS 'DRAFT=待审 / APPROVED=已批准未落库 / REJECTED=驳回 / FAILED=生成失败';
