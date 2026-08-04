-- V030: AI 供应商增加 protocol 字段
-- 设计来源: P6-02 免费 LLM 供应商集成
ALTER TABLE ai_provider
    ADD COLUMN IF NOT EXISTS protocol varchar(32) NOT NULL DEFAULT 'OPENAI_COMPATIBLE';

COMMENT ON COLUMN ai_provider.protocol IS '供应商协议: OPENAI_COMPATIBLE / CUSTOM，默认 OPENAI_COMPATIBLE';
