-- ============================================================
-- V061__ai_embedding_modality.sql
-- 多模态 Embedding 对标 (业界同类实现 MultiModalEmbedModelService)
-- 设计来源: docs/compose/spec/ai-depth-parity.md S2.3
-- 约束: 幂等 ALTER，不破坏既有文本向量
-- ============================================================

ALTER TABLE ai_embedding ADD COLUMN IF NOT EXISTS modality varchar(16) NOT NULL DEFAULT 'text';

ALTER TABLE ai_embedding DROP CONSTRAINT IF EXISTS chk_ai_embedding_modality;
ALTER TABLE ai_embedding ADD CONSTRAINT chk_ai_embedding_modality
    CHECK (modality IN ('text','image','video','audio'));

CREATE INDEX IF NOT EXISTS idx_ai_embedding_modality
    ON ai_embedding (tenant_id, knowledge_base_id, modality);

COMMENT ON COLUMN ai_embedding.modality IS '向量模态: text 文本 / image 图像 / video 视频 / audio 音频 (多模态 Embedding)';

-- 知识库可选：声明期望 embedding 维度（模型驱动，不再写死 1536）
ALTER TABLE ai_knowledge_base ADD COLUMN IF NOT EXISTS embedding_dimension int NOT NULL DEFAULT 1536;

ALTER TABLE ai_knowledge_base DROP CONSTRAINT IF EXISTS chk_ai_kb_embedding_dim;
ALTER TABLE ai_knowledge_base ADD CONSTRAINT chk_ai_kb_embedding_dim
    CHECK (embedding_dimension > 0 AND embedding_dimension <= 8192);

COMMENT ON COLUMN ai_knowledge_base.embedding_dimension IS '期望向量维度，默认 1536；多模态/百炼模型可配置实际维度';
