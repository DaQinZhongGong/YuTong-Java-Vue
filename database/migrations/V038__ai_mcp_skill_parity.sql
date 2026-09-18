-- ============================================================
-- V038__ai_mcp_skill_parity.sql
-- Tool/MCP + Skill — MCP 服务注册、本地工具定义、Skill 版本化
-- 设计来源:
--   - 13-AI能力设计 Tool/MCP/Skill 扩展
--   - 57-完整DDL清单 ai_mcp_server / ai_skill / ai_tool_definition
--   - yutong-ai-service Phase 3: McpClientRegistry / SkillRegistry
-- 约束:
--   - 主键 varchar(32) ULID (AGENTS.md 硬约束)
--   - Flyway placeholder-replacement=false 兼容，禁止 ${}
--   - 幂等 CREATE TABLE IF NOT EXISTS + 索引/约束 IF NOT EXISTS 兼容
--   - jsonb 字段用于前端可视化配置 config_json / input_schema
--   - endpoint/config_json 不落明文密钥，密钥走保管箱 *_ref 约定
-- ============================================================

-- ------------------------------------------------------------
-- 1. ai_mcp_server — MCP 服务注册 (SSE / STDIO / HTTP)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_mcp_server (
    id              varchar(32)     NOT NULL,
    tenant_id       varchar(32)     NOT NULL,
    server_code     varchar(64)     NOT NULL,
    server_name     varchar(128)    NOT NULL,
    transport       varchar(16)     NOT NULL,
    endpoint        varchar(512),
    config_json     jsonb,
    enabled         boolean         NOT NULL DEFAULT true,
    status          varchar(16)     NOT NULL DEFAULT 'UNKNOWN',
    created_by      varchar(64),
    created_time    timestamptz     NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean         NOT NULL DEFAULT false,
    version         int             NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_ai_mcp_server PRIMARY KEY (id)
);

-- 租户内 server_code 唯一 (软删过滤)
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_mcp_server_code ON ai_mcp_server (tenant_id, server_code) WHERE deleted = false;

-- 传输类型 CHECK (幂等: 先删后建)
ALTER TABLE ai_mcp_server DROP CONSTRAINT IF EXISTS chk_ai_mcp_transport;
ALTER TABLE ai_mcp_server ADD CONSTRAINT chk_ai_mcp_transport
    CHECK (transport IN ('sse','stdio','http'));

-- 状态 CHECK
ALTER TABLE ai_mcp_server DROP CONSTRAINT IF EXISTS chk_ai_mcp_status;
ALTER TABLE ai_mcp_server ADD CONSTRAINT chk_ai_mcp_status
    CHECK (status IN ('UNKNOWN','HEALTHY','UNHEALTHY','DISABLED','CONNECTING'));

-- 索引
CREATE INDEX IF NOT EXISTS idx_ai_mcp_server_tenant_enabled ON ai_mcp_server (tenant_id, enabled) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_mcp_server_transport ON ai_mcp_server (tenant_id, transport) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_mcp_server_status ON ai_mcp_server (tenant_id, status) WHERE deleted = false;

-- GIN 索引加速 config_json 查询 (按 key 检索)
CREATE INDEX IF NOT EXISTS idx_ai_mcp_server_config_gin ON ai_mcp_server USING GIN (config_json);

-- 注释
COMMENT ON TABLE ai_mcp_server IS 'AI MCP 服务注册 — V038 Phase 3: transport=sse/stdio/http，前端可视化配置实时生效';
COMMENT ON COLUMN ai_mcp_server.server_code IS 'MCP 服务编码，租户内唯一';
COMMENT ON COLUMN ai_mcp_server.server_name IS 'MCP 服务名称';
COMMENT ON COLUMN ai_mcp_server.transport IS '传输类型: sse/stdio/http，sse=http SSE，stdio=本地子进程，http=Streamable HTTP';
COMMENT ON COLUMN ai_mcp_server.endpoint IS '服务端点 URL (sse/http 必填，stdio 可为空)';
COMMENT ON COLUMN ai_mcp_server.config_json IS '传输配置 JSON: sse={headers, timeoutMs}, stdio={command, args, env}, http={headers, authRef}，不落明文密钥';
COMMENT ON COLUMN ai_mcp_server.enabled IS '是否启用，前端开关实时生效';
COMMENT ON COLUMN ai_mcp_server.status IS '运行状态: UNKNOWN/HEALTHY/UNHEALTHY/DISABLED/CONNECTING，由 McpClientRegistry 健康检查更新';

-- ------------------------------------------------------------
-- 2. ai_skill — Skill 版本化 (docx/pdf/xlsx/custom)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_skill (
    id              varchar(32)     NOT NULL,
    tenant_id       varchar(32)     NOT NULL,
    skill_code      varchar(64)     NOT NULL,
    skill_name      varchar(128)    NOT NULL,
    skill_type      varchar(32)     NOT NULL,
    source_path     varchar(512),
    skill_md        text,
    version_no      int             NOT NULL DEFAULT 1,
    status          varchar(16)     NOT NULL DEFAULT 'DRAFT',
    config_json     jsonb,
    created_by      varchar(64),
    created_time    timestamptz     NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean         NOT NULL DEFAULT false,
    version         int             NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_ai_skill PRIMARY KEY (id)
);

-- 租户内 skill_code + version_no 唯一
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_skill_code_version ON ai_skill (tenant_id, skill_code, version_no) WHERE deleted = false;

-- skill_type CHECK
ALTER TABLE ai_skill DROP CONSTRAINT IF EXISTS chk_ai_skill_type;
ALTER TABLE ai_skill ADD CONSTRAINT chk_ai_skill_type
    CHECK (skill_type IN ('docx','pdf','xlsx','custom'));

-- status CHECK
ALTER TABLE ai_skill DROP CONSTRAINT IF EXISTS chk_ai_skill_status;
ALTER TABLE ai_skill ADD CONSTRAINT chk_ai_skill_status
    CHECK (status IN ('DRAFT','PUBLISHED','DISABLED'));

-- 索引
CREATE INDEX IF NOT EXISTS idx_ai_skill_tenant_status ON ai_skill (tenant_id, status) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_skill_type ON ai_skill (tenant_id, skill_type) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_skill_code ON ai_skill (tenant_id, skill_code) WHERE deleted = false;

-- GIN on config_json
CREATE INDEX IF NOT EXISTS idx_ai_skill_config_gin ON ai_skill USING GIN (config_json);

COMMENT ON TABLE ai_skill IS 'AI Skill 注册 — V038 Phase 3: 版本化 SKILL.md，前端可视化配置，activate_skill 缓存 per tenant';
COMMENT ON COLUMN ai_skill.skill_code IS 'Skill 编码，租户内 code+version 唯一';
COMMENT ON COLUMN ai_skill.skill_name IS 'Skill 名称';
COMMENT ON COLUMN ai_skill.skill_type IS 'Skill 类型: docx/pdf/xlsx/custom';
COMMENT ON COLUMN ai_skill.source_path IS 'Skill 源路径 (仓库相对路径或对象存储 key)';
COMMENT ON COLUMN ai_skill.skill_md IS 'SKILL.md 原文内容，activate 时解析并缓存';
COMMENT ON COLUMN ai_skill.version_no IS '业务版本号，发布时自增';
COMMENT ON COLUMN ai_skill.status IS '状态: DRAFT/PUBLISHED/DISABLED，仅 PUBLISHED 可被 activate';
COMMENT ON COLUMN ai_skill.config_json IS 'Skill 配置 JSON: 输入输出 schema、依赖工具、权限码等';

-- ------------------------------------------------------------
-- 3. ai_tool_definition — 本地工具注册 (handler_class 路由)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_tool_definition (
    id              varchar(32)     NOT NULL,
    tenant_id       varchar(32)     NOT NULL,
    tool_code       varchar(64)     NOT NULL,
    tool_name       varchar(128)    NOT NULL,
    description     text,
    input_schema    jsonb,
    handler_class   varchar(256),
    enabled         boolean         NOT NULL DEFAULT true,
    created_by      varchar(64),
    created_time    timestamptz     NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean         NOT NULL DEFAULT false,
    version         int             NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_ai_tool_definition PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_tool_def_code ON ai_tool_definition (tenant_id, tool_code) WHERE deleted = false;

CREATE INDEX IF NOT EXISTS idx_ai_tool_def_enabled ON ai_tool_definition (tenant_id, enabled) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_tool_def_handler ON ai_tool_definition (handler_class) WHERE deleted = false;

-- GIN on input_schema
CREATE INDEX IF NOT EXISTS idx_ai_tool_def_schema_gin ON ai_tool_definition USING GIN (input_schema);

COMMENT ON TABLE ai_tool_definition IS 'AI 本地工具注册 — V038 Phase 3: handler_class 路由到 Spring Bean，input_schema 为 JSON Schema';
COMMENT ON COLUMN ai_tool_definition.tool_code IS '工具编码，租户内唯一';
COMMENT ON COLUMN ai_tool_definition.tool_name IS '工具名称';
COMMENT ON COLUMN ai_tool_definition.description IS '工具描述，供 LLM function calling 使用';
COMMENT ON COLUMN ai_tool_definition.input_schema IS '输入 JSON Schema (jsonb)，定义工具参数';
COMMENT ON COLUMN ai_tool_definition.handler_class IS '处理器类全限定名，需实现 ToolHandler 接口并注册为 Spring Bean';
COMMENT ON COLUMN ai_tool_definition.enabled IS '是否启用，禁用后不向 LLM 暴露';
