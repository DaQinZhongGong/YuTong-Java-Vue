-- ============================================================
-- V043__p0_platform_parity_complement.sql
-- P0 业界同类实现 AI 平价补齐 — MCP 市场 / 知识图谱 / 短剧扩展 / 链路追踪 / Skill 扩展
-- 设计来源:
--   - 业界同类实现 AI 商业版 P0 缺口：MCP 市场分发、知识图谱构建、短剧垂类扩展、Trace 链路追踪、Skill 脚本化
--   - 本地基线: V038 ai_mcp_server/ai_skill, V041 drama_script/drama_scene, V005 ai_knowledge_base
--   - 约束: 幂等 IF NOT EXISTS / DROP IF EXISTS，禁止 Flyway 占位 ${}（placeholder-replacement=false）
--   - 所有新增表遵循 AGENTS.md 硬约束：ULID varchar(32) PK、deleted/version/created_* 规范、GIN for jsonb、tenant_id 索引、CHECK 约束、中文 COMMENT
-- ============================================================

-- ------------------------------------------------------------
-- 1. ai_mcp_market — MCP 市场（可分发市场条目）
-- 来源: 业界同类实现 AI MCP Market，租户内 code 唯一，前端可视化配置 config_json 实时生效
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_mcp_market (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    name                varchar(128)    NOT NULL,
    code                varchar(64)     NOT NULL,
    description         text,
    icon_url            varchar(1024),
    provider            varchar(64),
    category            varchar(32),
    rating              numeric(3,2)    NOT NULL DEFAULT 0,
    install_count       int             NOT NULL DEFAULT 0,
    status              varchar(16)     NOT NULL DEFAULT 'DRAFT',
    config_json         jsonb,
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_ai_mcp_market PRIMARY KEY (id)
);

ALTER TABLE ai_mcp_market DROP CONSTRAINT IF EXISTS chk_ai_mcp_market_status;
ALTER TABLE ai_mcp_market ADD CONSTRAINT chk_ai_mcp_market_status
    CHECK (status IN ('DRAFT','PUBLISHED','OFFLINE','DISABLED'));

ALTER TABLE ai_mcp_market DROP CONSTRAINT IF EXISTS chk_ai_mcp_market_rating;
ALTER TABLE ai_mcp_market ADD CONSTRAINT chk_ai_mcp_market_rating
    CHECK (rating >= 0 AND rating <= 5);

ALTER TABLE ai_mcp_market DROP CONSTRAINT IF EXISTS chk_ai_mcp_market_install;
ALTER TABLE ai_mcp_market ADD CONSTRAINT chk_ai_mcp_market_install
    CHECK (install_count >= 0);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_mcp_market_code ON ai_mcp_market (tenant_id, code) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_mcp_market_tenant ON ai_mcp_market (tenant_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_mcp_market_tenant_status ON ai_mcp_market (tenant_id, status) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_mcp_market_category ON ai_mcp_market (tenant_id, category) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_mcp_market_provider ON ai_mcp_market (tenant_id, provider) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_mcp_market_config_gin ON ai_mcp_market USING GIN (config_json);

COMMENT ON TABLE ai_mcp_market IS 'MCP 市场 — V043 P0 业界同类实现 平价补齐：租户内 code 唯一，config_json 可视化配置，rating/install_count 市场排序';
COMMENT ON COLUMN ai_mcp_market.tenant_id IS '租户 ID';
COMMENT ON COLUMN ai_mcp_market.name IS '市场条目名称';
COMMENT ON COLUMN ai_mcp_market.code IS '市场编码，租户内唯一（软删过滤）';
COMMENT ON COLUMN ai_mcp_market.description IS '描述';
COMMENT ON COLUMN ai_mcp_market.icon_url IS '图标 URL';
COMMENT ON COLUMN ai_mcp_market.provider IS '提供方/作者';
COMMENT ON COLUMN ai_mcp_market.category IS '分类（工具/数据源/业务等）';
COMMENT ON COLUMN ai_mcp_market.rating IS '评分 0.00-5.00';
COMMENT ON COLUMN ai_mcp_market.install_count IS '安装次数';
COMMENT ON COLUMN ai_mcp_market.status IS '状态: DRAFT/PUBLISHED/OFFLINE/DISABLED';
COMMENT ON COLUMN ai_mcp_market.config_json IS '市场配置 JSON：含 mcp 配置、依赖、权限等，前端可视化实时生效，不落明文密钥';

-- ------------------------------------------------------------
-- 2. ai_mcp_market_tool — 市场工具明细（归属 ai_mcp_market）
-- 来源: 业界同类实现 AI MCP Market Tool，input_schema JSON Schema 前端可视化
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_mcp_market_tool (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    market_id           varchar(32)     NOT NULL,
    tool_name           varchar(128)    NOT NULL,
    tool_desc           text,
    input_schema        jsonb,
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_ai_mcp_market_tool PRIMARY KEY (id),
    CONSTRAINT fk_ai_mcp_market_tool_market FOREIGN KEY (market_id) REFERENCES ai_mcp_market(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_mcp_market_tool_name ON ai_mcp_market_tool (market_id, tool_name) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_mcp_market_tool_tenant ON ai_mcp_market_tool (tenant_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_mcp_market_tool_market ON ai_mcp_market_tool (tenant_id, market_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_mcp_market_tool_schema_gin ON ai_mcp_market_tool USING GIN (input_schema);

COMMENT ON TABLE ai_mcp_market_tool IS 'MCP 市场工具 — V043 P0 业界同类实现 平价补齐：归属 ai_mcp_market，input_schema 为 JSON Schema';
COMMENT ON COLUMN ai_mcp_market_tool.tenant_id IS '租户 ID';
COMMENT ON COLUMN ai_mcp_market_tool.market_id IS '所属市场 ID (ai_mcp_market.id)';
COMMENT ON COLUMN ai_mcp_market_tool.tool_name IS '工具名称，市场内唯一';
COMMENT ON COLUMN ai_mcp_market_tool.tool_desc IS '工具描述，供 LLM function calling 展示';
COMMENT ON COLUMN ai_mcp_market_tool.input_schema IS '输入 JSON Schema (jsonb)，前端可视化配置实时生效';

-- ------------------------------------------------------------
-- 3. ai_knowledge_graph — 知识图谱（归属知识库可选）
-- 来源: 业界同类实现 AI 知识图谱，自动抽取实体/关系构建图谱
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_knowledge_graph (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    kb_id               varchar(32),
    name                varchar(128)    NOT NULL,
    status              varchar(16)     NOT NULL DEFAULT 'DRAFT',
    graph_json          jsonb,
    entity_count        int             NOT NULL DEFAULT 0,
    relation_count      int             NOT NULL DEFAULT 0,
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_ai_knowledge_graph PRIMARY KEY (id),
    CONSTRAINT fk_ai_kg_kb FOREIGN KEY (kb_id) REFERENCES ai_knowledge_base(id)
);

ALTER TABLE ai_knowledge_graph DROP CONSTRAINT IF EXISTS chk_ai_kg_status;
ALTER TABLE ai_knowledge_graph ADD CONSTRAINT chk_ai_kg_status
    CHECK (status IN ('DRAFT','BUILDING','READY','FAILED'));

ALTER TABLE ai_knowledge_graph DROP CONSTRAINT IF EXISTS chk_ai_kg_entity;
ALTER TABLE ai_knowledge_graph ADD CONSTRAINT chk_ai_kg_entity CHECK (entity_count >= 0);

ALTER TABLE ai_knowledge_graph DROP CONSTRAINT IF EXISTS chk_ai_kg_relation;
ALTER TABLE ai_knowledge_graph ADD CONSTRAINT chk_ai_kg_relation CHECK (relation_count >= 0);

CREATE INDEX IF NOT EXISTS idx_ai_kg_tenant ON ai_knowledge_graph (tenant_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_kg_tenant_kb ON ai_knowledge_graph (tenant_id, kb_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_kg_tenant_status ON ai_knowledge_graph (tenant_id, status) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_kg_graph_gin ON ai_knowledge_graph USING GIN (graph_json);

COMMENT ON TABLE ai_knowledge_graph IS '知识图谱 — V043 P0 业界同类实现 平价补齐：kb_id 可空关联 ai_knowledge_base，status DRAFT/BUILDING/READY/FAILED，graph_json 存储图谱快照';
COMMENT ON COLUMN ai_knowledge_graph.tenant_id IS '租户 ID';
COMMENT ON COLUMN ai_knowledge_graph.kb_id IS '关联知识库 ID (ai_knowledge_base.id)，可空表示独立图谱';
COMMENT ON COLUMN ai_knowledge_graph.name IS '图谱名称';
COMMENT ON COLUMN ai_knowledge_graph.status IS '构建状态: DRAFT/BUILDING/READY/FAILED';
COMMENT ON COLUMN ai_knowledge_graph.graph_json IS '图谱 JSON：{nodes:[{id,label,type,props}], edges:[{source,target,relation}]} 前端可视化实时生效';
COMMENT ON COLUMN ai_knowledge_graph.entity_count IS '实体数量';
COMMENT ON COLUMN ai_knowledge_graph.relation_count IS '关系数量';

-- ------------------------------------------------------------
-- 4. ai_knowledge_graph_segment — 图谱分段抽取结果
-- 来源: 业界同类实现 AI 知识图谱分段，记录每块 chunk 抽取的实体/关系
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_knowledge_graph_segment (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    graph_id            varchar(32)     NOT NULL,
    source_chunk_id     varchar(32),
    entity_json         jsonb,
    relation_json       jsonb,
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_ai_kg_segment PRIMARY KEY (id),
    CONSTRAINT fk_ai_kg_segment_graph FOREIGN KEY (graph_id) REFERENCES ai_knowledge_graph(id)
);

CREATE INDEX IF NOT EXISTS idx_ai_kg_seg_tenant ON ai_knowledge_graph_segment (tenant_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_kg_seg_graph ON ai_knowledge_graph_segment (tenant_id, graph_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_kg_seg_chunk ON ai_knowledge_graph_segment (source_chunk_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_kg_seg_entity_gin ON ai_knowledge_graph_segment USING GIN (entity_json);
CREATE INDEX IF NOT EXISTS idx_ai_kg_seg_relation_gin ON ai_knowledge_graph_segment USING GIN (relation_json);

COMMENT ON TABLE ai_knowledge_graph_segment IS '知识图谱分段 — V043 P0 业界同类实现 平价补齐：graph_id 归属图谱，source_chunk_id 溯源 ai_document_chunk，entity/relation 分开展示';
COMMENT ON COLUMN ai_knowledge_graph_segment.tenant_id IS '租户 ID';
COMMENT ON COLUMN ai_knowledge_graph_segment.graph_id IS '所属图谱 ID (ai_knowledge_graph.id)';
COMMENT ON COLUMN ai_knowledge_graph_segment.source_chunk_id IS '来源分块 ID (ai_document_chunk.id)，varchar32 ULID';
COMMENT ON COLUMN ai_knowledge_graph_segment.entity_json IS '实体 JSON：[{id, label, type, props}]';
COMMENT ON COLUMN ai_knowledge_graph_segment.relation_json IS '关系 JSON：[{source, target, relation, props}]';

-- ------------------------------------------------------------
-- 5. 短剧扩展 5 表 — 与现有 drama_script/drama_scene 不冲突（IF NOT EXISTS）
-- 来源: 业界同类实现 短剧垂类 平价能力，补充角色/形象/场景地/音频/分镜
-- ------------------------------------------------------------

-- 5.1 drama_character — 短剧角色
CREATE TABLE IF NOT EXISTS drama_character (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    drama_id            varchar(32)     NOT NULL,
    name                varchar(128)    NOT NULL,
    role                varchar(64),
    description         text,
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_drama_character PRIMARY KEY (id),
    CONSTRAINT fk_drama_character_drama FOREIGN KEY (drama_id) REFERENCES drama_script(id)
);

CREATE INDEX IF NOT EXISTS idx_drama_character_tenant ON drama_character (tenant_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_drama_character_drama ON drama_character (tenant_id, drama_id) WHERE deleted = false;

COMMENT ON TABLE drama_character IS '短剧角色 — V043 P0 业界同类实现 平价补齐：归属 drama_script，与现有 drama_script/drama_scene 不冲突';
COMMENT ON COLUMN drama_character.tenant_id IS '租户 ID';
COMMENT ON COLUMN drama_character.drama_id IS '所属剧本 ID (drama_script.id)';
COMMENT ON COLUMN drama_character.name IS '角色名称';
COMMENT ON COLUMN drama_character.role IS '角色定位（主角/配角/反派等）';
COMMENT ON COLUMN drama_character.description IS '角色描述/人设';

-- 5.2 drama_character_appearance — 角色形象（多形象择一）
CREATE TABLE IF NOT EXISTS drama_character_appearance (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    character_id        varchar(32)     NOT NULL,
    appearance_json     jsonb,
    image_url           varchar(1024),
    is_selected         boolean         NOT NULL DEFAULT false,
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_drama_char_appear PRIMARY KEY (id),
    CONSTRAINT fk_drama_char_appear_char FOREIGN KEY (character_id) REFERENCES drama_character(id)
);

CREATE INDEX IF NOT EXISTS idx_drama_char_appear_tenant ON drama_character_appearance (tenant_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_drama_char_appear_char ON drama_character_appearance (tenant_id, character_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_drama_char_appear_selected ON drama_character_appearance (character_id, is_selected) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_drama_char_appear_json_gin ON drama_character_appearance USING GIN (appearance_json);

COMMENT ON TABLE drama_character_appearance IS '角色形象 — V043 P0 业界同类实现 平价补齐：同一角色多形象，is_selected 标记选中形象，appearance_json 可视化配置';
COMMENT ON COLUMN drama_character_appearance.tenant_id IS '租户 ID';
COMMENT ON COLUMN drama_character_appearance.character_id IS '所属角色 ID (drama_character.id)';
COMMENT ON COLUMN drama_character_appearance.appearance_json IS '形象 JSON：{prompt, style, age, gender, clothing, ...} 前端可视化实时生效';
COMMENT ON COLUMN drama_character_appearance.image_url IS '形象图片 URL（生成结果）';
COMMENT ON COLUMN drama_character_appearance.is_selected IS '是否选中形象（同一角色仅一选中，由业务层保证）';

-- 5.3 drama_location — 短剧场景地
CREATE TABLE IF NOT EXISTS drama_location (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    drama_id            varchar(32)     NOT NULL,
    name                varchar(128)    NOT NULL,
    description         text,
    image_url           varchar(1024),
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_drama_location PRIMARY KEY (id),
    CONSTRAINT fk_drama_location_drama FOREIGN KEY (drama_id) REFERENCES drama_script(id)
);

CREATE INDEX IF NOT EXISTS idx_drama_location_tenant ON drama_location (tenant_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_drama_location_drama ON drama_location (tenant_id, drama_id) WHERE deleted = false;

COMMENT ON TABLE drama_location IS '短剧场景地 — V043 P0 业界同类实现 平价补齐：归属 drama_script，image_url 场景预览图';
COMMENT ON COLUMN drama_location.tenant_id IS '租户 ID';
COMMENT ON COLUMN drama_location.drama_id IS '所属剧本 ID (drama_script.id)';
COMMENT ON COLUMN drama_location.name IS '场景地名称';
COMMENT ON COLUMN drama_location.description IS '场景描述';
COMMENT ON COLUMN drama_location.image_url IS '场景图片 URL';

-- 5.4 drama_audio — 短剧音频（配音/音效）
CREATE TABLE IF NOT EXISTS drama_audio (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    drama_id            varchar(32)     NOT NULL,
    name                varchar(128)    NOT NULL,
    audio_url           varchar(1024),
    duration_seconds    int,
    voice_id            varchar(64),
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_drama_audio PRIMARY KEY (id),
    CONSTRAINT fk_drama_audio_drama FOREIGN KEY (drama_id) REFERENCES drama_script(id)
);

ALTER TABLE drama_audio DROP CONSTRAINT IF EXISTS chk_drama_audio_duration;
ALTER TABLE drama_audio ADD CONSTRAINT chk_drama_audio_duration CHECK (duration_seconds IS NULL OR duration_seconds >= 0);

CREATE INDEX IF NOT EXISTS idx_drama_audio_tenant ON drama_audio (tenant_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_drama_audio_drama ON drama_audio (tenant_id, drama_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_drama_audio_voice ON drama_audio (tenant_id, voice_id) WHERE deleted = false;

COMMENT ON TABLE drama_audio IS '短剧音频 — V043 P0 业界同类实现 平价补齐：归属 drama_script，voice_id 关联 TTS 音色';
COMMENT ON COLUMN drama_audio.tenant_id IS '租户 ID';
COMMENT ON COLUMN drama_audio.drama_id IS '所属剧本 ID (drama_script.id)';
COMMENT ON COLUMN drama_audio.name IS '音频名称';
COMMENT ON COLUMN drama_audio.audio_url IS '音频文件 URL';
COMMENT ON COLUMN drama_audio.duration_seconds IS '时长秒数';
COMMENT ON COLUMN drama_audio.voice_id IS '音色 ID（TTS 声库）';

-- 5.5 drama_storyboard — 分镜脚本（关联场景与音频可选）
CREATE TABLE IF NOT EXISTS drama_storyboard (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    drama_id            varchar(32)     NOT NULL,
    scene_id            varchar(32),
    storyboard_no       int             NOT NULL,
    prompt              text,
    image_url           varchar(1024),
    audio_id            varchar(32),
    status              varchar(16)     NOT NULL DEFAULT 'DRAFT',
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_drama_storyboard PRIMARY KEY (id),
    CONSTRAINT fk_drama_storyboard_drama FOREIGN KEY (drama_id) REFERENCES drama_script(id),
    CONSTRAINT fk_drama_storyboard_scene FOREIGN KEY (scene_id) REFERENCES drama_scene(id),
    CONSTRAINT fk_drama_storyboard_audio FOREIGN KEY (audio_id) REFERENCES drama_audio(id)
);

ALTER TABLE drama_storyboard DROP CONSTRAINT IF EXISTS chk_drama_storyboard_no;
ALTER TABLE drama_storyboard ADD CONSTRAINT chk_drama_storyboard_no CHECK (storyboard_no >= 1);

ALTER TABLE drama_storyboard DROP CONSTRAINT IF EXISTS chk_drama_storyboard_status;
ALTER TABLE drama_storyboard ADD CONSTRAINT chk_drama_storyboard_status
    CHECK (status IN ('DRAFT','GENERATING','SUCCESS','FAILED'));

CREATE UNIQUE INDEX IF NOT EXISTS uk_drama_storyboard_no ON drama_storyboard (drama_id, storyboard_no) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_drama_storyboard_tenant ON drama_storyboard (tenant_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_drama_storyboard_drama ON drama_storyboard (tenant_id, drama_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_drama_storyboard_scene ON drama_storyboard (scene_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_drama_storyboard_audio ON drama_storyboard (audio_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_drama_storyboard_status ON drama_storyboard (tenant_id, status) WHERE deleted = false;

COMMENT ON TABLE drama_storyboard IS '分镜脚本 — V043 P0 业界同类实现 平价补齐：归属 drama_script，scene_id 可空关联 drama_scene，audio_id 可空关联 drama_audio，storyboard_no 剧内唯一';
COMMENT ON COLUMN drama_storyboard.tenant_id IS '租户 ID';
COMMENT ON COLUMN drama_storyboard.drama_id IS '所属剧本 ID (drama_script.id)';
COMMENT ON COLUMN drama_storyboard.scene_id IS '关联场景 ID (drama_scene.id)，可空';
COMMENT ON COLUMN drama_storyboard.storyboard_no IS '分镜序号，剧内唯一 >=1';
COMMENT ON COLUMN drama_storyboard.prompt IS '分镜提示词/画面描述';
COMMENT ON COLUMN drama_storyboard.image_url IS '分镜图片 URL（生成结果）';
COMMENT ON COLUMN drama_storyboard.audio_id IS '关联音频 ID (drama_audio.id)，可空';
COMMENT ON COLUMN drama_storyboard.status IS '状态: DRAFT/GENERATING/SUCCESS/FAILED';

-- ------------------------------------------------------------
-- 6. ai_trace_run — 链路追踪主表（一次调用/编排的完整链路）
-- 来源: 业界同类实现 AI Trace/RAG 审计，关联 conversation/flow/rag 全链路
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_trace_run (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    trace_type          varchar(32)     NOT NULL,
    input_json          jsonb,
    output_json         jsonb,
    status              varchar(16)     NOT NULL DEFAULT 'RUNNING',
    latency_ms          int,
    error_message       varchar(1024),
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_ai_trace_run PRIMARY KEY (id)
);

ALTER TABLE ai_trace_run DROP CONSTRAINT IF EXISTS chk_ai_trace_run_type;
ALTER TABLE ai_trace_run ADD CONSTRAINT chk_ai_trace_run_type
    CHECK (trace_type IN ('AGENT','FLOW','RAG','MCP','SKILL','TOOL','LLM','MEDIA','CUSTOM'));

ALTER TABLE ai_trace_run DROP CONSTRAINT IF EXISTS chk_ai_trace_run_status;
ALTER TABLE ai_trace_run ADD CONSTRAINT chk_ai_trace_run_status
    CHECK (status IN ('RUNNING','SUCCESS','FAILED','CANCELLED'));

ALTER TABLE ai_trace_run DROP CONSTRAINT IF EXISTS chk_ai_trace_run_latency;
ALTER TABLE ai_trace_run ADD CONSTRAINT chk_ai_trace_run_latency CHECK (latency_ms IS NULL OR latency_ms >= 0);

CREATE INDEX IF NOT EXISTS idx_ai_trace_run_tenant ON ai_trace_run (tenant_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_trace_run_tenant_type ON ai_trace_run (tenant_id, trace_type) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_trace_run_tenant_status ON ai_trace_run (tenant_id, status) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_trace_run_tenant_created ON ai_trace_run (tenant_id, created_time DESC) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_trace_run_input_gin ON ai_trace_run USING GIN (input_json);
CREATE INDEX IF NOT EXISTS idx_ai_trace_run_output_gin ON ai_trace_run USING GIN (output_json);

COMMENT ON TABLE ai_trace_run IS '链路追踪主表 — V043 P0 业界同类实现 平价补齐：trace_type 区分 AGENT/FLOW/RAG/MCP/SKILL/TOOL/LLM/MEDIA/CUSTOM，input/output 可视化审计';
COMMENT ON COLUMN ai_trace_run.tenant_id IS '租户 ID';
COMMENT ON COLUMN ai_trace_run.trace_type IS '链路类型: AGENT/FLOW/RAG/MCP/SKILL/TOOL/LLM/MEDIA/CUSTOM';
COMMENT ON COLUMN ai_trace_run.input_json IS '输入 JSON：含 prompt/params/context 等';
COMMENT ON COLUMN ai_trace_run.output_json IS '输出 JSON：含 result/error/tokens 等';
COMMENT ON COLUMN ai_trace_run.status IS '状态: RUNNING/SUCCESS/FAILED/CANCELLED';
COMMENT ON COLUMN ai_trace_run.latency_ms IS '总耗时毫秒';
COMMENT ON COLUMN ai_trace_run.error_message IS '失败信息';

-- ------------------------------------------------------------
-- 7. ai_trace_node — 链路节点明细（归属 ai_trace_run）
-- 来源: 业界同类实现 AI Trace Node，记录 DAG/调用链中每个节点的输入输出与耗时
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_trace_node (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    run_id              varchar(32)     NOT NULL,
    node_type           varchar(32)     NOT NULL,
    input_json          jsonb,
    output_json         jsonb,
    status              varchar(16)     NOT NULL DEFAULT 'RUNNING',
    latency_ms          int,
    error_message       varchar(1024),
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_ai_trace_node PRIMARY KEY (id),
    CONSTRAINT fk_ai_trace_node_run FOREIGN KEY (run_id) REFERENCES ai_trace_run(id)
);

ALTER TABLE ai_trace_node DROP CONSTRAINT IF EXISTS chk_ai_trace_node_status;
ALTER TABLE ai_trace_node ADD CONSTRAINT chk_ai_trace_node_status
    CHECK (status IN ('PENDING','RUNNING','SUCCESS','FAILED','SKIPPED','CANCELLED'));

ALTER TABLE ai_trace_node DROP CONSTRAINT IF EXISTS chk_ai_trace_node_latency;
ALTER TABLE ai_trace_node ADD CONSTRAINT chk_ai_trace_node_latency CHECK (latency_ms IS NULL OR latency_ms >= 0);

CREATE INDEX IF NOT EXISTS idx_ai_trace_node_tenant ON ai_trace_node (tenant_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_trace_node_run ON ai_trace_node (tenant_id, run_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_trace_node_run_type ON ai_trace_node (run_id, node_type) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_trace_node_status ON ai_trace_node (tenant_id, status) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_trace_node_input_gin ON ai_trace_node USING GIN (input_json);
CREATE INDEX IF NOT EXISTS idx_ai_trace_node_output_gin ON ai_trace_node USING GIN (output_json);

COMMENT ON TABLE ai_trace_node IS '链路节点明细 — V043 P0 业界同类实现 平价补齐：归属 ai_trace_run，记录每节点输入输出、状态与耗时，GIN 加速 JSON 检索';
COMMENT ON COLUMN ai_trace_node.tenant_id IS '租户 ID';
COMMENT ON COLUMN ai_trace_node.run_id IS '所属追踪主表 ID (ai_trace_run.id)';
COMMENT ON COLUMN ai_trace_node.node_type IS '节点类型: model/rag/mcp/skill/tool/http/sql/human/condition/parallel/llm/media/custom';
COMMENT ON COLUMN ai_trace_node.input_json IS '节点输入 JSON';
COMMENT ON COLUMN ai_trace_node.output_json IS '节点输出 JSON';
COMMENT ON COLUMN ai_trace_node.status IS '节点状态: PENDING/RUNNING/SUCCESS/FAILED/SKIPPED/CANCELLED';
COMMENT ON COLUMN ai_trace_node.latency_ms IS '节点耗时毫秒';
COMMENT ON COLUMN ai_trace_node.error_message IS '失败信息';

-- ------------------------------------------------------------
-- 8. ai_skill 扩展列 — 幂等增量（ALTER TABLE IF NOT EXISTS）
-- 来源: 业界同类实现 AI Skill 脚本化 + 市场关联，skill_type 收敛为 varchar16、script_ref 脚本引用、market_id 关联市场
-- ------------------------------------------------------------
-- 显式 IF NOT EXISTS 满足任务要求；skill_type 已在 V038 存在（varchar32），此处 IF NOT EXISTS 为幂等空操作，保留存量类型
ALTER TABLE ai_skill ADD COLUMN IF NOT EXISTS script_ref varchar(512);
ALTER TABLE ai_skill ADD COLUMN IF NOT EXISTS market_id varchar(32);
-- skill_type 幂等补充（仅当列不存在时创建 varchar16；存量库已存在则 no-op）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'ai_skill' AND column_name = 'skill_type') THEN
        ALTER TABLE ai_skill ADD COLUMN skill_type varchar(16);
    END IF;
END
$$;

-- market_id 外键幂等（仅当约束不存在时创建）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_ai_skill_market') THEN
        ALTER TABLE ai_skill ADD CONSTRAINT fk_ai_skill_market FOREIGN KEY (market_id) REFERENCES ai_mcp_market(id);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_ai_skill_market_id ON ai_skill (market_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_skill_script_ref ON ai_skill (script_ref) WHERE deleted = false;

COMMENT ON COLUMN ai_skill.skill_type IS 'Skill 类型 — V043 P0 业界同类实现 平价补齐：varchar16 语义收敛，存量 V038 为 varchar32 兼容保留，取值 docx/pdf/xlsx/custom/script/mcp';
COMMENT ON COLUMN ai_skill.script_ref IS '脚本引用 — V043 P0 业界同类实现 平价补齐：脚本路径或对象存储 key (varchar512)，为空表示非脚本类 Skill';
COMMENT ON COLUMN ai_skill.market_id IS '关联市场 ID — V043 P0 业界同类实现 平价补齐：FK ai_mcp_market.id，可空表示自建 Skill';
