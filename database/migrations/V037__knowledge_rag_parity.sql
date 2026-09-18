-- ============================================================
-- V037__knowledge_rag_parity.sql
-- RAG 知识库对等扩展 — 切分参数 / 装载器类型 / Reranker / Hybrid 检索
-- 设计来源:
--   - 业界同类实现 AI 模块: 知识库切分配置、文档装载器、重排与混合检索
--   - YuTong 设计: 13-AI能力设计 RAG 分块与检索 / 57-完整DDL清单 ai_knowledge_base / ai_document
--   - ars20260711.md: jd/9_reranker.md 混合检索与重排
-- 约束:
--   - 主键 varchar(32) ULID (AGENTS.md 硬约束)
--   - Flyway placeholder-replacement=false 兼容，禁止 ${}
--   - 所有 ADD COLUMN 均 IF NOT EXISTS 幂等
--   - CHECK 约束通过 DROP IF EXISTS + ADD 保证可重入
--   - jsonb 字段用于前端可视化配置 chunk_params
--   - api_key_ref 不落明文，密钥由保管箱解析
-- ============================================================

-- ------------------------------------------------------------
-- 1. ai_knowledge_base 扩展列 (幂等)
-- ------------------------------------------------------------
ALTER TABLE ai_knowledge_base ADD COLUMN IF NOT EXISTS chunk_params_json jsonb;
ALTER TABLE ai_knowledge_base ADD COLUMN IF NOT EXISTS loader_type varchar(32);
ALTER TABLE ai_knowledge_base ADD COLUMN IF NOT EXISTS reranker_enabled boolean DEFAULT false;
ALTER TABLE ai_knowledge_base ADD COLUMN IF NOT EXISTS reranker_provider varchar(32);
ALTER TABLE ai_knowledge_base ADD COLUMN IF NOT EXISTS hybrid_enabled boolean DEFAULT false;

-- ------------------------------------------------------------
-- 2. ai_document 扩展列 (文档级覆盖知识库默认值，按需)
-- ------------------------------------------------------------
ALTER TABLE ai_document ADD COLUMN IF NOT EXISTS chunk_params_json jsonb;
ALTER TABLE ai_document ADD COLUMN IF NOT EXISTS loader_type varchar(32);
ALTER TABLE ai_document ADD COLUMN IF NOT EXISTS reranker_enabled boolean DEFAULT false;
ALTER TABLE ai_document ADD COLUMN IF NOT EXISTS reranker_provider varchar(32);
ALTER TABLE ai_document ADD COLUMN IF NOT EXISTS hybrid_enabled boolean DEFAULT false;

-- ------------------------------------------------------------
-- 3. ai_document_chunk 扩展: 全文索引所需 tsvector 列 (混合检索)
--    chunk_text 的 tsvector 预计算，GIN 索引加速 pg 全文检索
-- ------------------------------------------------------------
ALTER TABLE ai_document_chunk ADD COLUMN IF NOT EXISTS content_tsv tsvector;

-- 触发器: chunk_text 变更时自动更新 content_tsv (pg_catalog.english + simple 兼容中文分词简化首版用 simple)
-- 注: 中文建议后续替换为 zhparser，首版用 simple 保证可运行
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_ai_chunk_tsv_update') THEN
        CREATE OR REPLACE FUNCTION ai_chunk_tsv_trigger() RETURNS trigger AS $func$
        BEGIN
            NEW.content_tsv := to_tsvector('simple', COALESCE(NEW.chunk_text, ''));
            RETURN NEW;
        END
        $func$ LANGUAGE plpgsql;

        CREATE TRIGGER trg_ai_chunk_tsv_update
            BEFORE INSERT OR UPDATE OF chunk_text ON ai_document_chunk
            FOR EACH ROW EXECUTE FUNCTION ai_chunk_tsv_trigger();
    END IF;
END
$$;

-- 回填存量数据 tsv (幂等: 仅对 content_tsv IS NULL 行)
UPDATE ai_document_chunk SET content_tsv = to_tsvector('simple', COALESCE(chunk_text, '')) WHERE content_tsv IS NULL;

-- ------------------------------------------------------------
-- 4. 默认值回填 (仅 NULL 行)
-- ------------------------------------------------------------
UPDATE ai_knowledge_base SET reranker_enabled = false WHERE reranker_enabled IS NULL;
UPDATE ai_knowledge_base SET hybrid_enabled = false WHERE hybrid_enabled IS NULL;
UPDATE ai_document SET reranker_enabled = false WHERE reranker_enabled IS NULL;
UPDATE ai_document SET hybrid_enabled = false WHERE hybrid_enabled IS NULL;

-- chunk_params_json 默认值回填: 仅当列为 NULL 时填充合理默认值
UPDATE ai_knowledge_base
SET chunk_params_json = jsonb_build_object(
    'separator', E'\n\n',
    'blockSize', 1000,
    'overlap', 150,
    'retrieveLimit', 5,
    'similarityThreshold', 0.3
)
WHERE chunk_params_json IS NULL;

-- ------------------------------------------------------------
-- 5. CHECK 约束
--    loader_type:       pdf/word/excel/csv/md/txt/json/code/folder/github
--    reranker_provider: alibailian/siliconflow/zhipu  (NULL 允许，未启用重排时为空)
--    chunk_params_json 仅校验为 jsonb，不强校验子字段范围，由应用层校验
-- ------------------------------------------------------------
ALTER TABLE ai_knowledge_base DROP CONSTRAINT IF EXISTS chk_kb_loader_type;
ALTER TABLE ai_knowledge_base ADD CONSTRAINT chk_kb_loader_type
    CHECK (loader_type IS NULL OR loader_type IN ('pdf','word','excel','csv','md','txt','json','code','folder','github'));

ALTER TABLE ai_knowledge_base DROP CONSTRAINT IF EXISTS chk_kb_reranker_provider;
ALTER TABLE ai_knowledge_base ADD CONSTRAINT chk_kb_reranker_provider
    CHECK (reranker_provider IS NULL OR reranker_provider IN ('alibailian','siliconflow','zhipu'));

ALTER TABLE ai_document DROP CONSTRAINT IF EXISTS chk_doc_loader_type;
ALTER TABLE ai_document ADD CONSTRAINT chk_doc_loader_type
    CHECK (loader_type IS NULL OR loader_type IN ('pdf','word','excel','csv','md','txt','json','code','folder','github'));

ALTER TABLE ai_document DROP CONSTRAINT IF EXISTS chk_doc_reranker_provider;
ALTER TABLE ai_document ADD CONSTRAINT chk_doc_reranker_provider
    CHECK (reranker_provider IS NULL OR reranker_provider IN ('alibailian','siliconflow','zhipu'));

-- ------------------------------------------------------------
-- 6. 索引
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_ai_kb_reranker ON ai_knowledge_base (tenant_id, reranker_enabled) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_kb_hybrid ON ai_knowledge_base (tenant_id, hybrid_enabled) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_kb_loader ON ai_knowledge_base (tenant_id, loader_type) WHERE deleted = false;

CREATE INDEX IF NOT EXISTS idx_ai_doc_loader ON ai_document (tenant_id, loader_type) WHERE deleted = false;

-- GIN 全文索引 (混合检索)
CREATE INDEX IF NOT EXISTS idx_ai_chunk_tsv ON ai_document_chunk USING GIN (content_tsv);

-- ------------------------------------------------------------
-- 7. 列注释
-- ------------------------------------------------------------
COMMENT ON COLUMN ai_knowledge_base.chunk_params_json IS '切分参数 JSON: {separator, blockSize, overlap, retrieveLimit, similarityThreshold}，前端可视化配置实时生效';
COMMENT ON COLUMN ai_knowledge_base.loader_type IS '装载器类型: pdf/word/excel/csv/md/txt/json/code/folder/github';
COMMENT ON COLUMN ai_knowledge_base.reranker_enabled IS '是否启用重排 (Reranker)';
COMMENT ON COLUMN ai_knowledge_base.reranker_provider IS '重排供应商: alibailian/siliconflow/zhipu，NULL 表示未配置';
COMMENT ON COLUMN ai_knowledge_base.hybrid_enabled IS '是否启用混合检索 (pgvector cosine + tsvector fulltext 加权)';

COMMENT ON COLUMN ai_document.chunk_params_json IS '文档级切分参数覆盖 (NULL 则继承知识库配置)';
COMMENT ON COLUMN ai_document.loader_type IS '文档装载器类型覆盖 (NULL 则继承知识库)';
COMMENT ON COLUMN ai_document.reranker_enabled IS '文档级重排开关覆盖';
COMMENT ON COLUMN ai_document.reranker_provider IS '文档级重排供应商覆盖';
COMMENT ON COLUMN ai_document.hybrid_enabled IS '文档级混合检索开关覆盖';
COMMENT ON COLUMN ai_document_chunk.content_tsv IS '全文检索向量 (tsvector)，由触发器从 chunk_text 自动生成，GIN 索引';

COMMENT ON TABLE ai_knowledge_base IS 'AI 知识库 — V037 RAG parity 扩展 (chunk_params/loader/reranker/hybrid)';
COMMENT ON TABLE ai_document IS 'AI 文档 — V037 RAG parity 扩展 (chunk_params/loader/reranker/hybrid)';
COMMENT ON TABLE ai_document_chunk IS 'AI 文档分块 — V037 扩展 tsvector 全文索引';
