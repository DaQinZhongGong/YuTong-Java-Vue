-- V005: AI 能力表
-- 设计来源: 13-AI能力设计、57-完整DDL清单与数据字典详设

-- ===== 模型供应商 =====
CREATE TABLE ai_provider (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    provider_code       varchar(64)   NOT NULL,
    provider_name       varchar(128)  NOT NULL,
    endpoint            varchar(512)  NOT NULL,
    api_key_ref         varchar(128),
    model_list_json     jsonb,
    enabled             boolean       NOT NULL DEFAULT true,
    priority            int           NOT NULL DEFAULT 0,
    timeout_ms          int           NOT NULL DEFAULT 60000,
    rate_limit_per_min  int,
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_ai_provider PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_ai_provider_code ON ai_provider (tenant_id, provider_code) WHERE deleted = false;
COMMENT ON TABLE ai_provider IS 'AI 模型供应商';

-- ===== Prompt 模板 =====
CREATE TABLE ai_prompt_template (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    template_code       varchar(64)   NOT NULL,
    version_no          int           NOT NULL DEFAULT 1,
    scenario            varchar(64)   NOT NULL,
    input_schema        jsonb,
    output_schema       jsonb,
    prompt_text         text          NOT NULL,
    safety_rules        jsonb,
    evaluation_set_code varchar(64),
    status              varchar(16)   NOT NULL DEFAULT 'DRAFT',
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_ai_prompt_template PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_ai_prompt_template ON ai_prompt_template (tenant_id, template_code, version_no) WHERE deleted = false;
COMMENT ON TABLE ai_prompt_template IS 'AI Prompt 模板';

-- ===== 会话 =====
CREATE TABLE ai_conversation (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    conversation_no varchar(64)   NOT NULL,
    user_id         varchar(64)   NOT NULL,
    title           varchar(256),
    scenario        varchar(64),
    locale          varchar(16)   NOT NULL DEFAULT 'zh-CN',
    model_code      varchar(64),
    status          varchar(16)   NOT NULL DEFAULT 'ACTIVE',
    last_message_time timestamptz,
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_ai_conversation PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_ai_conversation_no ON ai_conversation (tenant_id, conversation_no) WHERE deleted = false;
CREATE INDEX idx_ai_conversation_user ON ai_conversation (tenant_id, user_id, last_message_time);
COMMENT ON TABLE ai_conversation IS 'AI 会话';

-- ===== 消息 =====
CREATE TABLE ai_message (
    id                varchar(32)   NOT NULL,
    tenant_id         varchar(32)   NOT NULL,
    conversation_id   varchar(32)   NOT NULL,
    role              varchar(16)   NOT NULL,
    content_summary   text,
    content_encrypted text,
    citation_json     jsonb,
    token_input       int,
    token_output      int,
    cost_amount       numeric(18,4),
    latency_ms        int,
    created_by        varchar(64),
    created_time      timestamptz   NOT NULL DEFAULT now(),
    updated_by        varchar(64),
    updated_time      timestamptz,
    deleted           boolean       NOT NULL DEFAULT false,
    version           int           NOT NULL DEFAULT 0,
    remark            varchar(512),
    CONSTRAINT pk_ai_message PRIMARY KEY (id)
);
CREATE INDEX idx_ai_message_conversation ON ai_message (tenant_id, conversation_id, created_time);
COMMENT ON TABLE ai_message IS 'AI 消息';

-- ===== 知识库 =====
CREATE TABLE ai_knowledge_base (
    id                varchar(32)   NOT NULL,
    tenant_id         varchar(32)   NOT NULL,
    kb_code           varchar(64)   NOT NULL,
    kb_name           varchar(128)  NOT NULL,
    description       text,
    acl_policy_json   jsonb,
    embedding_model   varchar(64)   NOT NULL,
    visibility        varchar(32)   NOT NULL DEFAULT 'PRIVATE',
    permission_code   varchar(128),
    sensitivity_level varchar(16)   NOT NULL DEFAULT 'INTERNAL',
    status            varchar(16)   NOT NULL DEFAULT 'DRAFT',
    owner_user_id     varchar(64),
    created_by        varchar(64),
    created_time      timestamptz   NOT NULL DEFAULT now(),
    updated_by        varchar(64),
    updated_time      timestamptz,
    deleted           boolean       NOT NULL DEFAULT false,
    version           int           NOT NULL DEFAULT 0,
    remark            varchar(512),
    CONSTRAINT pk_ai_knowledge_base PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_ai_kb_code ON ai_knowledge_base (tenant_id, kb_code) WHERE deleted = false;
COMMENT ON TABLE ai_knowledge_base IS 'AI 知识库';

-- ===== 文档 =====
CREATE TABLE ai_document (
    id                varchar(32)   NOT NULL,
    tenant_id         varchar(32)   NOT NULL,
    kb_id             varchar(32)   NOT NULL,
    file_id           varchar(32),
    doc_title         varchar(256)  NOT NULL,
    source_type       varchar(32)   NOT NULL,
    source_uri        varchar(512),
    visibility        varchar(32)   NOT NULL DEFAULT 'PRIVATE',
    permission_code   varchar(128),
    sensitivity_level varchar(16)   NOT NULL DEFAULT 'INTERNAL',
    document_status   varchar(16)   NOT NULL DEFAULT 'PENDING',
    chunk_count       int           NOT NULL DEFAULT 0,
    error_message     varchar(1024),
    indexed_time      timestamptz,
    created_by        varchar(64),
    created_time      timestamptz   NOT NULL DEFAULT now(),
    updated_by        varchar(64),
    updated_time      timestamptz,
    deleted           boolean       NOT NULL DEFAULT false,
    version           int           NOT NULL DEFAULT 0,
    remark            varchar(512),
    CONSTRAINT pk_ai_document PRIMARY KEY (id)
);
CREATE INDEX idx_ai_document_kb ON ai_document (tenant_id, kb_id, document_status);
COMMENT ON TABLE ai_document IS 'AI 文档';

-- ===== 文档分块 =====
CREATE TABLE ai_document_chunk (
    id                varchar(32)   NOT NULL,
    tenant_id         varchar(32)   NOT NULL,
    knowledge_base_id varchar(32)   NOT NULL,
    document_id       varchar(32)   NOT NULL,
    chunk_no          int           NOT NULL,
    chunk_text        text          NOT NULL,
    chunk_hash        varchar(128),
    token_count       int,
    section_path      varchar(512),
    permission_code   varchar(128),
    data_scope        varchar(32),
    sensitivity_level varchar(16)   NOT NULL DEFAULT 'INTERNAL',
    acl_tags_json     jsonb,
    created_by        varchar(64),
    created_time      timestamptz   NOT NULL DEFAULT now(),
    updated_by        varchar(64),
    updated_time      timestamptz,
    deleted           boolean       NOT NULL DEFAULT false,
    version           int           NOT NULL DEFAULT 0,
    remark            varchar(512),
    CONSTRAINT pk_ai_document_chunk PRIMARY KEY (id)
);
CREATE INDEX idx_ai_chunk_doc ON ai_document_chunk (tenant_id, document_id, chunk_no);
COMMENT ON TABLE ai_document_chunk IS 'AI 文档分块';

-- ===== 向量 =====
-- 向量维度 1536 对齐常见 embedding 模型；更换模型维度需新增 version 分区，不得原地改列类型
CREATE TABLE ai_embedding (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    chunk_id            varchar(32)   NOT NULL,
    embedding_model     varchar(64)   NOT NULL,
    embedding_dimension int           NOT NULL,
    embedding           vector(1536),
    embedding_hash      varchar(128),
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_ai_embedding PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_ai_embedding_chunk ON ai_embedding (tenant_id, chunk_id) WHERE deleted = false;
COMMENT ON TABLE ai_embedding IS 'AI 向量';

-- ===== 工具调用日志 =====
CREATE TABLE ai_tool_call_log (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    tool_name           varchar(128)  NOT NULL,
    tool_version        varchar(32),
    risk_level          varchar(16)   NOT NULL DEFAULT 'LOW',
    user_id             varchar(64)   NOT NULL,
    input_summary       text,
    output_summary      text,
    data_scope_summary  varchar(512),
    result              varchar(16)   NOT NULL,
    error_code          varchar(32),
    latency_ms          int,
    trace_id            varchar(64)   NOT NULL,
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_ai_tool_call_log PRIMARY KEY (id)
);
CREATE INDEX idx_ai_tool_call_user ON ai_tool_call_log (tenant_id, user_id, created_time);
CREATE INDEX idx_ai_tool_call_trace ON ai_tool_call_log (tenant_id, trace_id);
COMMENT ON TABLE ai_tool_call_log IS 'AI 工具调用日志';

-- ===== 成本日志 =====
CREATE TABLE ai_cost_log (
    id                varchar(32)   NOT NULL,
    tenant_id         varchar(32)   NOT NULL,
    provider_code     varchar(64)   NOT NULL,
    model_code        varchar(64)   NOT NULL,
    scenario          varchar(64),
    user_id           varchar(64),
    conversation_id   varchar(32),
    token_input       int,
    token_output      int,
    cost_amount       numeric(18,6) NOT NULL,
    currency          varchar(8)    NOT NULL DEFAULT 'CNY',
    latency_ms        int,
    result            varchar(16)   NOT NULL,
    created_by        varchar(64),
    created_time      timestamptz   NOT NULL DEFAULT now(),
    updated_by        varchar(64),
    updated_time      timestamptz,
    deleted           boolean       NOT NULL DEFAULT false,
    version           int           NOT NULL DEFAULT 0,
    remark            varchar(512),
    CONSTRAINT pk_ai_cost_log PRIMARY KEY (id)
);
CREATE INDEX idx_ai_cost_log_time ON ai_cost_log (tenant_id, created_time);
CREATE INDEX idx_ai_cost_log_model ON ai_cost_log (tenant_id, provider_code, model_code);
COMMENT ON TABLE ai_cost_log IS 'AI 成本日志';
