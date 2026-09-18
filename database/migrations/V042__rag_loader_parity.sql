-- ============================================================
-- V042__rag_loader_parity.sql
-- RAG 文档解析缺口补齐 — 文件入库链路加固
-- 设计来源:
--   - P0-1 audit: DocumentIngestApplicationService 仅支持纯文本，无 pdf/docx/xlsx/csv/md 分流器
--   - V037 已落地 loader_type / chunk_params / reranker / hybrid 扩展
--   - 本迁移仅补充文件入库所需的索引与约束幂等加固，不重复 V037 列
-- 约束:
--   - 主键 ULID varchar(32)
--   - Flyway placeholder-replacement=false，禁止 ${}
--   - 所有 DDL 均 IF NOT EXISTS / DROP IF EXISTS 幂等
-- ============================================================

-- ------------------------------------------------------------
-- 1. ai_document.file_id 索引（文件入库按 fileId 溯源与去重）
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_ai_document_file ON ai_document (tenant_id, file_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_document_kb_file ON ai_document (tenant_id, kb_id, file_id) WHERE deleted = false;

-- ------------------------------------------------------------
-- 2. ai_document_chunk.document_id 辅助索引已存在，补充 chunk_hash 检索加速（去重查询）
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_ai_chunk_hash ON ai_document_chunk (tenant_id, document_id, chunk_hash) WHERE deleted = false;

-- ------------------------------------------------------------
-- 3. 确保 loader_type CHECK 约束覆盖 P0-1 新增的 6 种装载器
--    V037 已包含 pdf/word/excel/csv/md/txt/json/code/folder/github，此处幂等重申
-- ------------------------------------------------------------
DO $$
BEGIN
    -- ai_knowledge_base 约束已在 V037 创建，此处仅确保存在性（幂等）
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_kb_loader_type') THEN
        ALTER TABLE ai_knowledge_base ADD CONSTRAINT chk_kb_loader_type
            CHECK (loader_type IS NULL OR loader_type IN ('pdf','word','excel','csv','md','txt','json','code','folder','github'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_doc_loader_type') THEN
        ALTER TABLE ai_document ADD CONSTRAINT chk_doc_loader_type
            CHECK (loader_type IS NULL OR loader_type IN ('pdf','word','excel','csv','md','txt','json','code','folder','github'));
    END IF;
END
$$;

-- ------------------------------------------------------------
-- 4. 列注释补齐（便于运维与文档生成器识别）
-- ------------------------------------------------------------
COMMENT ON COLUMN ai_document.file_id IS '关联文件 ID（sys_file.id），文件入库时必填，MinIO 原文件溯源';
COMMENT ON COLUMN ai_document.loader_type IS '文档装载器类型覆盖：pdf/word(excel)/csv/md/txt/json/code，NULL 继承知识库';
COMMENT ON COLUMN ai_document.error_message IS '解析/索引失败信息（FAILED 时展示），成功置空';
