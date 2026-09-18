-- ============================================================
-- V040__aiflow_parity.sql
-- AIFlow — AI 可视化编排 (DAG Flow)
-- 设计来源:
--   - Phase 5: AIFlow parity (enterprise AI platform + YuTong 13-AI能力)
--   - 关联 yutong-ai-service: AiflowEngine / AiflowService / DAG SSE
--   - yutong-workflow-service LightWorkflowEngine 保持独立，融合路径见代码注释
-- 约束:
--   - 主键 varchar(32) ULID (AGENTS.md 硬约束)
--   - Flyway placeholder-replacement=false 兼容，禁止 ${}
--   - 幂等 CREATE TABLE IF NOT EXISTS + 索引/约束 IF NOT EXISTS 兼容
--   - jsonb 字段前端可视化配置实时生效
-- Node types:
--   - 业务节点: model/rag/mcp/skill/email/human/sql/http (>=10 种)
--   - 网关节点: condition / parallel (分支/汇聚)
--   - dag_json 结构: {"nodes":[{"id","type","config":{}}], "edges":[{"source","target","condition"}]}
-- ============================================================

-- ------------------------------------------------------------
-- 1. aiflow_definition — 流程定义 (版本化 DAG)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS aiflow_definition (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    flow_code           varchar(64)     NOT NULL,
    flow_name           varchar(128)    NOT NULL,
    version_no          int             NOT NULL DEFAULT 1,
    dag_json            jsonb,
    status              varchar(16)     NOT NULL DEFAULT 'DRAFT',
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_aiflow_definition PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_aiflow_definition_code_version
    ON aiflow_definition (tenant_id, flow_code, version_no) WHERE deleted = false;

ALTER TABLE aiflow_definition DROP CONSTRAINT IF EXISTS chk_aiflow_def_status;
ALTER TABLE aiflow_definition ADD CONSTRAINT chk_aiflow_def_status
    CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED'));

CREATE INDEX IF NOT EXISTS idx_aiflow_def_tenant_status ON aiflow_definition (tenant_id, status) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_aiflow_def_flow_code ON aiflow_definition (tenant_id, flow_code) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_aiflow_def_dag_gin ON aiflow_definition USING GIN (dag_json);

COMMENT ON TABLE aiflow_definition IS 'AI Flow 定义 — V040 Phase 5: 版本化 DAG (nodes/edges)，status DRAFT/PUBLISHED/ARCHIVED，前端可视化编排实时生效';
COMMENT ON COLUMN aiflow_definition.flow_code IS '流程编码，租户内 code+version 唯一';
COMMENT ON COLUMN aiflow_definition.flow_name IS '流程名称';
COMMENT ON COLUMN aiflow_definition.version_no IS '业务版本号，发布时自增';
COMMENT ON COLUMN aiflow_definition.dag_json IS 'DAG JSON: {nodes:[{id,type,config}], edges:[{source,target,condition}]} 类型含 model/rag/mcp/skill/email/human/sql/http/condition/parallel';
COMMENT ON COLUMN aiflow_definition.status IS '状态: DRAFT/PUBLISHED/ARCHIVED';

-- ------------------------------------------------------------
-- 2. aiflow_instance — 流程实例 (执行快照 + 节点状态)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS aiflow_instance (
    id              varchar(32)     NOT NULL,
    tenant_id       varchar(32)     NOT NULL,
    flow_id         varchar(32)     NOT NULL,
    status          varchar(16)     NOT NULL DEFAULT 'RUNNING',
    input_json      jsonb,
    output_json     jsonb,
    dag_snapshot    jsonb,
    node_states     jsonb,
    created_by      varchar(64),
    created_time    timestamptz     NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean         NOT NULL DEFAULT false,
    version         int             NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_aiflow_instance PRIMARY KEY (id),
    CONSTRAINT fk_aiflow_instance_flow FOREIGN KEY (flow_id) REFERENCES aiflow_definition(id)
);

ALTER TABLE aiflow_instance DROP CONSTRAINT IF EXISTS chk_aiflow_inst_status;
ALTER TABLE aiflow_instance ADD CONSTRAINT chk_aiflow_inst_status
    CHECK (status IN ('RUNNING','SUCCESS','FAILED','CANCELLED'));

CREATE INDEX IF NOT EXISTS idx_aiflow_inst_flow ON aiflow_instance (tenant_id, flow_id, created_time);
CREATE INDEX IF NOT EXISTS idx_aiflow_inst_status ON aiflow_instance (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_aiflow_inst_input_gin ON aiflow_instance USING GIN (input_json);
CREATE INDEX IF NOT EXISTS idx_aiflow_inst_node_states_gin ON aiflow_instance USING GIN (node_states);

COMMENT ON TABLE aiflow_instance IS 'AI Flow 实例 — V040 Phase 5: 运行时快照 + per-node 状态流式推送';
COMMENT ON COLUMN aiflow_instance.flow_id IS '所属流程定义 ID (aiflow_definition.id)';
COMMENT ON COLUMN aiflow_instance.status IS '实例状态: RUNNING/SUCCESS/FAILED/CANCELLED';
COMMENT ON COLUMN aiflow_instance.input_json IS '输入 JSON: {query, params, variables}';
COMMENT ON COLUMN aiflow_instance.output_json IS '输出 JSON: {result, error}';
COMMENT ON COLUMN aiflow_instance.dag_snapshot IS '执行时 DAG 快照 (发布态 dag_json 拷贝)';
COMMENT ON COLUMN aiflow_instance.node_states IS '节点状态 JSON: {nodeId: {status:PENDING/RUNNING/SUCCESS/FAILED/SKIPPED, output, error, startedAt, finishedAt}}';

