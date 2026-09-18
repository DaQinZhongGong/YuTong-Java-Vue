-- ============================================================
-- V039__ai_agent_memory_parity.sql
-- Agent + Memory — AI 智能体与记忆窗口
-- 设计来源:
--   - Phase 4: Agent + Memory parity (enterprise AI platform + YuTong 13-AI能力)
--   - yutong-ai-service: AgentService / ReActEngine / AgentMemoryService
-- 约束:
--   - 主键 varchar(32) ULID (AGENTS.md 硬约束)
--   - Flyway placeholder-replacement=false 兼容，禁止 ${}
--   - 幂等 CREATE TABLE IF NOT EXISTS + 索引/约束 IF NOT EXISTS 兼容
--   - jsonb 字段前端可视化配置实时生效
-- ============================================================

-- ------------------------------------------------------------
-- 1. ai_agent — 智能体定义 (react/supervisor/sequence/parallel/condition)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_agent (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    agent_code          varchar(64)     NOT NULL,
    agent_name          varchar(128)    NOT NULL,
    agent_type          varchar(32)     NOT NULL,
    system_prompt       text,
    tool_ids            jsonb,
    skill_ids           jsonb,
    mcp_server_ids      jsonb,
    memory_config_json  jsonb,
    status              varchar(16)     NOT NULL DEFAULT 'DRAFT',
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_ai_agent PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_agent_code ON ai_agent (tenant_id, agent_code) WHERE deleted = false;

ALTER TABLE ai_agent DROP CONSTRAINT IF EXISTS chk_ai_agent_type;
ALTER TABLE ai_agent ADD CONSTRAINT chk_ai_agent_type
    CHECK (agent_type IN ('react','supervisor','sequence','parallel','condition'));

ALTER TABLE ai_agent DROP CONSTRAINT IF EXISTS chk_ai_agent_status;
ALTER TABLE ai_agent ADD CONSTRAINT chk_ai_agent_status
    CHECK (status IN ('DRAFT','PUBLISHED'));

CREATE INDEX IF NOT EXISTS idx_ai_agent_tenant_status ON ai_agent (tenant_id, status) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_agent_type ON ai_agent (tenant_id, agent_type) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_agent_tool_gin ON ai_agent USING GIN (tool_ids);
CREATE INDEX IF NOT EXISTS idx_ai_agent_skill_gin ON ai_agent USING GIN (skill_ids);
CREATE INDEX IF NOT EXISTS idx_ai_agent_memory_gin ON ai_agent USING GIN (memory_config_json);

COMMENT ON TABLE ai_agent IS 'AI 智能体 — V039 Phase 4: agent_type=react/supervisor/sequence/parallel/condition，tool/skill/mcp 绑定，memory_config_json={windowSize,summarizeThreshold}';
COMMENT ON COLUMN ai_agent.agent_code IS '智能体编码，租户内唯一';
COMMENT ON COLUMN ai_agent.agent_name IS '智能体名称';
COMMENT ON COLUMN ai_agent.agent_type IS '智能体类型: react/supervisor/sequence/parallel/condition';
COMMENT ON COLUMN ai_agent.system_prompt IS '系统提示词';
COMMENT ON COLUMN ai_agent.tool_ids IS '绑定工具编码数组 (jsonb)';
COMMENT ON COLUMN ai_agent.skill_ids IS '绑定 Skill 编码数组 (jsonb)';
COMMENT ON COLUMN ai_agent.mcp_server_ids IS '绑定 MCP 服务编码数组 (jsonb)';
COMMENT ON COLUMN ai_agent.memory_config_json IS '记忆配置 JSON: {windowSize:10, summarizeThreshold:20}';
COMMENT ON COLUMN ai_agent.status IS '状态: DRAFT/PUBLISHED';

-- ------------------------------------------------------------
-- 2. ai_agent_run — 智能体执行记录 (ReAct trace)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_agent_run (
    id              varchar(32)     NOT NULL,
    tenant_id       varchar(32)     NOT NULL,
    agent_id        varchar(32)     NOT NULL,
    conversation_id varchar(32),
    status          varchar(16)     NOT NULL DEFAULT 'RUNNING',
    input_json      jsonb,
    output_json     jsonb,
    trace_json      jsonb,
    created_by      varchar(64),
    created_time    timestamptz     NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean         NOT NULL DEFAULT false,
    version         int             NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_ai_agent_run PRIMARY KEY (id),
    CONSTRAINT fk_ai_agent_run_agent FOREIGN KEY (agent_id) REFERENCES ai_agent(id)
);

ALTER TABLE ai_agent_run DROP CONSTRAINT IF EXISTS chk_ai_agent_run_status;
ALTER TABLE ai_agent_run ADD CONSTRAINT chk_ai_agent_run_status
    CHECK (status IN ('RUNNING','SUCCESS','FAILED'));

CREATE INDEX IF NOT EXISTS idx_ai_agent_run_agent ON ai_agent_run (tenant_id, agent_id, created_time);
CREATE INDEX IF NOT EXISTS idx_ai_agent_run_conversation ON ai_agent_run (tenant_id, conversation_id);
CREATE INDEX IF NOT EXISTS idx_ai_agent_run_status ON ai_agent_run (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_ai_agent_run_trace_gin ON ai_agent_run USING GIN (trace_json);

COMMENT ON TABLE ai_agent_run IS 'AI 智能体执行记录 — V039 Phase 4: ReAct Thought/Action/Observation 轨迹';
COMMENT ON COLUMN ai_agent_run.agent_id IS '所属智能体 ID';
COMMENT ON COLUMN ai_agent_run.conversation_id IS '关联会话 ID (可选，绑定 ai_conversation)';
COMMENT ON COLUMN ai_agent_run.status IS '执行状态: RUNNING/SUCCESS/FAILED';
COMMENT ON COLUMN ai_agent_run.input_json IS '输入 JSON: {query, params}';
COMMENT ON COLUMN ai_agent_run.output_json IS '输出 JSON: {answer, summary}';
COMMENT ON COLUMN ai_agent_run.trace_json IS '执行轨迹 JSON 数组: [{step, type:Thought/Action/Observation, content, tool, result}]';

-- ------------------------------------------------------------
-- 3. ai_conversation 扩展 — 记忆窗口
-- ------------------------------------------------------------
ALTER TABLE ai_conversation ADD COLUMN IF NOT EXISTS memory_config_json jsonb;
ALTER TABLE ai_conversation ADD COLUMN IF NOT EXISTS summary text;
ALTER TABLE ai_conversation ADD COLUMN IF NOT EXISTS memory_window int;

COMMENT ON COLUMN ai_conversation.memory_config_json IS '记忆配置 JSON: {windowSize, summarizeThreshold}，继承自 ai_agent 或会话级覆盖';
COMMENT ON COLUMN ai_conversation.summary IS '会话摘要 (超过阈值时 LLM 总结)';
COMMENT ON COLUMN ai_conversation.memory_window IS '记忆窗口大小 (默认 10)';

CREATE INDEX IF NOT EXISTS idx_ai_conversation_memory_gin ON ai_conversation USING GIN (memory_config_json);

