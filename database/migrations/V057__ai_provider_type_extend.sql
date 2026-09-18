-- ============================================================
-- V057__ai_provider_type_extend.sql
-- P3 扩展 AI 供应商类型枚举: 新增 ppio / atla
-- 设计来源: 业界同类实现 PpioChatServiceImpl / AtlaServiceImpl + ADR 0005 P3
-- 约束: 幂等 ALTER, 更新 chk_ai_provider_provider_type CHECK
-- ============================================================

-- 先删旧约束再加新约束 (幂等)
ALTER TABLE ai_provider DROP CONSTRAINT IF EXISTS chk_ai_provider_provider_type;
ALTER TABLE ai_provider ADD CONSTRAINT chk_ai_provider_provider_type
    CHECK (provider_type IN (
        'openai','deepseek','qianwen','zhipu','ollama','minimax',
        'atlas','xiaomi','dify','coze','ppio','atla','custom_api'
    ));

COMMENT ON COLUMN ai_provider.provider_type IS '供应商类型 13 枚举: openai/deepseek/qianwen/zhipu/ollama/minimax/atlas/xiaomi/dify/coze/ppio/atla/custom_api';
