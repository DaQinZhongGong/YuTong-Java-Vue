-- ============================================================
-- V058__ai_agent_thinking_level.sql
-- P3 Agent 思考等级控制: ai_agent 增加 thinking_level 列
-- 设计来源: 业界同类实现 Doubao thinkingLevel + ADR 0005 P3
-- 约束: 幂等 ALTER, CHECK 状态机, 中文 COMMENT
-- ============================================================

ALTER TABLE ai_agent ADD COLUMN IF NOT EXISTS thinking_level varchar(16) NOT NULL DEFAULT 'MEDIUM';

ALTER TABLE ai_agent DROP CONSTRAINT IF EXISTS chk_ai_agent_thinking_level;
ALTER TABLE ai_agent ADD CONSTRAINT chk_ai_agent_thinking_level
    CHECK (thinking_level IN ('NONE','LOW','MEDIUM','HIGH'));

COMMENT ON COLUMN ai_agent.thinking_level IS '思考等级: NONE 不思考 / LOW 低 / MEDIUM 中 / HIGH 高 (影响 LLM reasoning effort)';
