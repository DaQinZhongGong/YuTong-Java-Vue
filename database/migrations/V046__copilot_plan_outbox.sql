-- ============================================================
-- V046__copilot_plan_outbox.sql
-- Copilot Durable Plan + Outbox（审批后写事件，不自动落低代码库）
-- ============================================================

ALTER TABLE ai_copilot_run ADD COLUMN IF NOT EXISTS plan_json jsonb;
ALTER TABLE ai_copilot_run ADD COLUMN IF NOT EXISTS outbox_json jsonb;
ALTER TABLE ai_copilot_run ADD COLUMN IF NOT EXISTS loop_count int NOT NULL DEFAULT 1;

ALTER TABLE ai_copilot_run DROP CONSTRAINT IF EXISTS chk_ai_copilot_run_loop;
ALTER TABLE ai_copilot_run ADD CONSTRAINT chk_ai_copilot_run_loop
    CHECK (loop_count >= 1 AND loop_count <= 8);

CREATE INDEX IF NOT EXISTS idx_ai_copilot_run_plan_gin ON ai_copilot_run USING GIN (plan_json);
CREATE INDEX IF NOT EXISTS idx_ai_copilot_run_outbox_gin ON ai_copilot_run USING GIN (outbox_json);

COMMENT ON COLUMN ai_copilot_run.plan_json IS '生成计划：步骤列表，供审计与循环执行';
COMMENT ON COLUMN ai_copilot_run.outbox_json IS '审批后的出箱事件 {eventType,published,publishedAt}，下游低代码发布自行消费';
COMMENT ON COLUMN ai_copilot_run.loop_count IS 'LLM 生成循环次数，默认 1';
