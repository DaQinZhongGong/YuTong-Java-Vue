-- ============================================================
-- V036__ai_provider_parity.sql
-- ai_provider 业界同类实现 对等扩展 - 供应商类型/模型类型/平台/多模态/健康状态
-- 设计来源:
--   - 业界同类实现 AI 模块文档：供应商 11 枚举、模型 9 枚举、平台 Dify/Coze/FastGPT
--   - YuTong 设计: 13-AI能力设计、57-完整DDL清单、P6-02 免费 LLM 供应商集成
--   - YuTong 生产级约束: 配置 via DB + UI 实时生效，禁止 .env 人肉改文件
-- 约束:
--   - 主键 varchar(32) ULID (AGENTS.md 硬约束)
--   - Flyway placeholder-replacement=false 兼容，禁止 ${}
--   - 所有 ADD COLUMN 均 IF NOT EXISTS 幂等
--   - CHECK 约束通过 DROP IF EXISTS + ADD 保证可重入
--   - jsonb 字段用于前端可视化配置与多模态能力声明
-- ============================================================

-- ------------------------------------------------------------
-- 1. 扩展列 (幂等)
-- ------------------------------------------------------------
ALTER TABLE ai_provider ADD COLUMN IF NOT EXISTS provider_type varchar(32);
ALTER TABLE ai_provider ADD COLUMN IF NOT EXISTS model_type varchar(32);
ALTER TABLE ai_provider ADD COLUMN IF NOT EXISTS platform varchar(32);
ALTER TABLE ai_provider ADD COLUMN IF NOT EXISTS model_type_list_json jsonb;
ALTER TABLE ai_provider ADD COLUMN IF NOT EXISTS multimodal_capabilities jsonb;
ALTER TABLE ai_provider ADD COLUMN IF NOT EXISTS health_status varchar(16) DEFAULT 'UNKNOWN';
ALTER TABLE ai_provider ADD COLUMN IF NOT EXISTS health_checked_time timestamptz;
ALTER TABLE ai_provider ADD COLUMN IF NOT EXISTS config_json jsonb;

-- ------------------------------------------------------------
-- 2. 默认值回填 (仅对历史存量 NULL 行)
-- ------------------------------------------------------------
UPDATE ai_provider SET provider_type = 'custom_api' WHERE provider_type IS NULL;
UPDATE ai_provider SET model_type = 'chat' WHERE model_type IS NULL;
UPDATE ai_provider SET health_status = 'UNKNOWN' WHERE health_status IS NULL;

-- ------------------------------------------------------------
-- 3. CHECK 约束
--    provider_type: 11 枚举 (openai/deepseek/qianwen/zhipu/ollama/minimax/atlas/xiaomi/dify/coze/custom_api)
--    model_type:    9 枚举 (chat/image/vector/reranker/audio/text/video/ppt/music)
--    platform:      dify/coze/fastgpt/null (允许 NULL，未绑定平台时为空)
--    health_status: UNKNOWN/HEALTHY/UNHEALTHY/DEGRADED
-- ------------------------------------------------------------
ALTER TABLE ai_provider DROP CONSTRAINT IF EXISTS chk_ai_provider_provider_type;
ALTER TABLE ai_provider ADD CONSTRAINT chk_ai_provider_provider_type
    CHECK (provider_type IN (
        'openai','deepseek','qianwen','zhipu','ollama','minimax','atlas','xiaomi','dify','coze','custom_api'
    ));

ALTER TABLE ai_provider DROP CONSTRAINT IF EXISTS chk_ai_provider_model_type;
ALTER TABLE ai_provider ADD CONSTRAINT chk_ai_provider_model_type
    CHECK (model_type IN (
        'chat','image','vector','reranker','audio','text','video','ppt','music'
    ));

ALTER TABLE ai_provider DROP CONSTRAINT IF EXISTS chk_ai_provider_platform;
ALTER TABLE ai_provider ADD CONSTRAINT chk_ai_provider_platform
    CHECK (platform IS NULL OR platform IN ('dify','coze','fastgpt'));

ALTER TABLE ai_provider DROP CONSTRAINT IF EXISTS chk_ai_provider_health_status;
ALTER TABLE ai_provider ADD CONSTRAINT chk_ai_provider_health_status
    CHECK (health_status IN ('UNKNOWN','HEALTHY','UNHEALTHY','DEGRADED'));

-- ------------------------------------------------------------
-- 4. 索引 (按租户 + 类型路由，提升 ProviderRegistry 查询性能)
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_ai_provider_type ON ai_provider (tenant_id, provider_type) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_provider_model_type ON ai_provider (tenant_id, model_type) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_provider_health ON ai_provider (tenant_id, health_status) WHERE deleted = false;

-- ------------------------------------------------------------
-- 5. 列注释 (供 DB 可视化与代码生成器读取)
-- ------------------------------------------------------------
COMMENT ON COLUMN ai_provider.provider_type IS '供应商类型: openai/deepseek/qianwen/zhipu/ollama/minimax/atlas/xiaomi/dify/coze/custom_api (11 枚举, 平价能力)';
COMMENT ON COLUMN ai_provider.model_type IS '主模型类型: chat/image/vector/reranker/audio/text/video/ppt/music (9 枚举, 主类型，用于路由优先级)';
COMMENT ON COLUMN ai_provider.platform IS 'AI 应用平台: dify/coze/fastgpt/null (NULL 表示直连模型供应商，非平台)';
COMMENT ON COLUMN ai_provider.model_type_list_json IS '支持的模型类型列表 JSON 数组，如 ["chat","vector"]，用于多能力供应商';
COMMENT ON COLUMN ai_provider.multimodal_capabilities IS '多模态能力 JSON，如 {"image":true,"video":false,"audio":true,"ppt":false}，映射 /media/* 能力';
COMMENT ON COLUMN ai_provider.health_status IS '健康状态: UNKNOWN/HEALTHY/UNHEALTHY/DEGRADED，默认 UNKNOWN，由健康检查任务更新';
COMMENT ON COLUMN ai_provider.health_checked_time IS '最近一次健康检查时间';
COMMENT ON COLUMN ai_provider.config_json IS '厂商特定配置 JSON (vendor-specific)，如 dify api_base/app_id 等，不落明文密钥';

-- 表注释追加说明
COMMENT ON TABLE ai_provider IS 'AI 模型供应商 - V036 平价能力 扩展 (provider_type/model_type/platform/multimodal/health)';
