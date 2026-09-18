-- ============================================================
-- V035__ai_platform_baseline.sql
-- Phase 0 baseline marker for AI platform capability plan Phases 0-7
-- 设计来源: YuTong AI platform capability (Phases 0-7 总览)
--   Phase 0 - 基线与迁移骨架 (本文件)
--   Phase 1 - ai_provider 供应商模型扩展
--   Phase 2 - 媒体 / 多模态能力 (/media/*)
--   Phase 3 - RAG / 知识库对等
--   Phase 4 - 工作流 / 审批对等
--   Phase 5 - 低代码 / 报表对等
--   Phase 6 - 网关 / 限流 / 熔断对等
--   Phase 7 - 可观测 / 运营对等与发布证据
-- 约束: Flyway placeholder-replacement=false 兼容，禁止使用 ${} 占位符
--      本次仅写入基线校验，不产生 DDL，便于后续增量迁移按序执行
-- ============================================================

-- 校验 ai_provider 表已存在，未存在则后续 V036 等增量迁移将失败
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'ai_provider'
    ) THEN
        RAISE EXCEPTION 'baseline check failed: ai_provider table not found, please ensure V005__init_ai_tables.sql has been applied';
    END IF;
    RAISE NOTICE 'V035 baseline check passed: ai_provider exists, ready for AI platform capability Phases 1-7';
END $$;

-- 幂等标记：记录基线版本已执行（可选审计）
COMMENT ON TABLE ai_provider IS 'AI 模型供应商 - V035 AI platform capability baseline verified (Phases 0-7)';

