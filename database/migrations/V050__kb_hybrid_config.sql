-- ============================================================
-- V050__kb_hybrid_config.sql
-- P2-E 混合检索可配: 知识库级向量权重/返回数/准入门限
-- 设计来源: ADR 0004 P2-E (Hybrid 评分可配, 此前 weight 硬编码 0.7)
-- 约束: 幂等 ADD COLUMN IF NOT EXISTS, 禁止 Flyway 占位 ${}
-- 生效: HybridRetrievalService 以 KB 配置为 fallback (显式传参优先),
--      RagRetrievalService 改传 KB 配置, 知识库运营页可视化调参实时生效
-- ============================================================

ALTER TABLE ai_knowledge_base ADD COLUMN IF NOT EXISTS hybrid_vector_weight numeric(3,2) NOT NULL DEFAULT 0.70;
ALTER TABLE ai_knowledge_base ADD COLUMN IF NOT EXISTS hybrid_top_k int NOT NULL DEFAULT 5;
ALTER TABLE ai_knowledge_base ADD COLUMN IF NOT EXISTS hybrid_min_score numeric(6,4) NOT NULL DEFAULT 0.0100;

ALTER TABLE ai_knowledge_base DROP CONSTRAINT IF EXISTS chk_ai_kb_hybrid_weight;
ALTER TABLE ai_knowledge_base ADD CONSTRAINT chk_ai_kb_hybrid_weight
    CHECK (hybrid_vector_weight >= 0 AND hybrid_vector_weight <= 1);

ALTER TABLE ai_knowledge_base DROP CONSTRAINT IF EXISTS chk_ai_kb_hybrid_topk;
ALTER TABLE ai_knowledge_base ADD CONSTRAINT chk_ai_kb_hybrid_topk
    CHECK (hybrid_top_k >= 1 AND hybrid_top_k <= 50);

ALTER TABLE ai_knowledge_base DROP CONSTRAINT IF EXISTS chk_ai_kb_hybrid_minscore;
ALTER TABLE ai_knowledge_base ADD CONSTRAINT chk_ai_kb_hybrid_minscore
    CHECK (hybrid_min_score >= 0 AND hybrid_min_score <= 1);

COMMENT ON COLUMN ai_knowledge_base.hybrid_vector_weight IS '混合检索向量权重 0~1 (V050 P2-E, 默认 0.70, 全文权重=1-向量权重)';
COMMENT ON COLUMN ai_knowledge_base.hybrid_top_k IS '混合检索返回数量 1~50 (V050 P2-E, 默认 5)';
COMMENT ON COLUMN ai_knowledge_base.hybrid_min_score IS '混合分数准入门限 0~1 (V050 P2-E, 默认 0.01)';
