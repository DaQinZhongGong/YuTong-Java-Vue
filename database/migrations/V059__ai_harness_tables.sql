-- ============================================================
-- V059__ai_harness_tables.sql
-- Coding Harness 深度对标 Phase 1 (业界同类实现 coding/harness)
-- 设计来源: docs/compose/spec/ai-depth-parity.md S2.1
-- 约束: 幂等 IF NOT EXISTS / ULID varchar(32) / tenant 隔离 / revision 乐观锁 / 中文 COMMENT
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_harness_session (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    user_id             varchar(32)     NOT NULL,
    title               varchar(200)    NOT NULL DEFAULT '未命名会话',
    workspace_path      varchar(512)    NOT NULL,
    workspace_manifest  jsonb,
    model               varchar(128),
    permission_mode     varchar(32)     NOT NULL DEFAULT 'READ_ONLY',
    approval_policy     varchar(32)     NOT NULL DEFAULT 'ON_REQUEST',
    thinking_level      varchar(16)     NOT NULL DEFAULT 'MEDIUM',
    verification_mode   varchar(32)     NOT NULL DEFAULT 'OFF',
    active_run_id       varchar(32),
    idempotency_key     varchar(128),
    pinned_at           timestamptz,
    revision            bigint          NOT NULL DEFAULT 0,
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_ai_harness_session PRIMARY KEY (id)
);

ALTER TABLE ai_harness_session DROP CONSTRAINT IF EXISTS chk_ai_harness_session_perm;
ALTER TABLE ai_harness_session ADD CONSTRAINT chk_ai_harness_session_perm
    CHECK (permission_mode IN ('READ_ONLY','WORKSPACE_WRITE','FULL_ACCESS'));

ALTER TABLE ai_harness_session DROP CONSTRAINT IF EXISTS chk_ai_harness_session_policy;
ALTER TABLE ai_harness_session ADD CONSTRAINT chk_ai_harness_session_policy
    CHECK (approval_policy IN ('ON_REQUEST','NEVER'));

ALTER TABLE ai_harness_session DROP CONSTRAINT IF EXISTS chk_ai_harness_session_thinking;
ALTER TABLE ai_harness_session ADD CONSTRAINT chk_ai_harness_session_thinking
    CHECK (thinking_level IN ('NONE','LOW','MEDIUM','HIGH'));

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_harness_session_idem
    ON ai_harness_session (tenant_id, user_id, idempotency_key)
    WHERE deleted = false AND idempotency_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ai_harness_session_tenant_user
    ON ai_harness_session (tenant_id, user_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_harness_session_pinned
    ON ai_harness_session (tenant_id, user_id, pinned_at DESC NULLS LAST) WHERE deleted = false;

COMMENT ON TABLE ai_harness_session IS 'Coding Harness 会话 — workspace/model/权限模式/审批策略/乐观锁 revision';
COMMENT ON COLUMN ai_harness_session.permission_mode IS '权限天花板: READ_ONLY / WORKSPACE_WRITE / FULL_ACCESS (fail-closed)';
COMMENT ON COLUMN ai_harness_session.approval_policy IS '审批策略: ON_REQUEST 高危需批 / NEVER 全放行(仍受 mode 约束)';
COMMENT ON COLUMN ai_harness_session.revision IS '乐观锁版本，权限/审批 resolve 必须校验';
COMMENT ON COLUMN ai_harness_session.idempotency_key IS '创建幂等键，同租户用户内唯一';

CREATE TABLE IF NOT EXISTS ai_harness_run (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    session_id          varchar(32)     NOT NULL,
    user_id             varchar(32)     NOT NULL,
    status              varchar(32)     NOT NULL DEFAULT 'QUEUED',
    requirement         text            NOT NULL,
    permission_mode     varchar(32)     NOT NULL DEFAULT 'READ_ONLY',
    permission_revision bigint          NOT NULL DEFAULT 0,
    budget_json         jsonb,
    usage_json          jsonb,
    plan_json           jsonb,
    iteration           int             NOT NULL DEFAULT 0,
    tool_call_count     int             NOT NULL DEFAULT 0,
    cancel_requested    boolean         NOT NULL DEFAULT false,
    idempotency_key     varchar(128),
    error_message       varchar(1000),
    revision            bigint          NOT NULL DEFAULT 0,
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_ai_harness_run PRIMARY KEY (id)
);

ALTER TABLE ai_harness_run DROP CONSTRAINT IF EXISTS chk_ai_harness_run_status;
ALTER TABLE ai_harness_run ADD CONSTRAINT chk_ai_harness_run_status
    CHECK (status IN ('QUEUED','RUNNING','WAITING_FOR_APPROVAL','WAITING_FOR_INPUT','COMPLETED','FAILED','CANCELLED'));

ALTER TABLE ai_harness_run DROP CONSTRAINT IF EXISTS chk_ai_harness_run_perm;
ALTER TABLE ai_harness_run ADD CONSTRAINT chk_ai_harness_run_perm
    CHECK (permission_mode IN ('READ_ONLY','WORKSPACE_WRITE','FULL_ACCESS'));

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_harness_run_idem
    ON ai_harness_run (session_id, idempotency_key)
    WHERE deleted = false AND idempotency_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ai_harness_run_session
    ON ai_harness_run (session_id, created_time DESC) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_harness_run_tenant_status
    ON ai_harness_run (tenant_id, status) WHERE deleted = false;

COMMENT ON TABLE ai_harness_run IS 'Coding Harness 运行实例 — 状态机 QUEUED/RUNNING/WAITING_*/终态';
COMMENT ON COLUMN ai_harness_run.budget_json IS '预算: maxToolCalls/maxInputTokens/maxOutputTokens';
COMMENT ON COLUMN ai_harness_run.usage_json IS '累计用量: inputTokens/outputTokens/toolCalls';
COMMENT ON COLUMN ai_harness_run.permission_revision IS '权限快照版本，审批 claim 必须一致';

CREATE TABLE IF NOT EXISTS ai_harness_event (
    id                  varchar(64)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    session_id          varchar(32)     NOT NULL,
    run_id              varchar(32)     NOT NULL,
    sequence_no         bigint          NOT NULL,
    event_type          varchar(64)     NOT NULL,
    step_id             varchar(64),
    tool_call_id        varchar(64),
    approval_id         varchar(32),
    payload_json        jsonb           NOT NULL DEFAULT '{}'::jsonb,
    created_time        timestamptz     NOT NULL DEFAULT now(),
    CONSTRAINT pk_ai_harness_event PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_harness_event_seq
    ON ai_harness_event (run_id, sequence_no);
CREATE INDEX IF NOT EXISTS idx_ai_harness_event_run_seq
    ON ai_harness_event (run_id, sequence_no);
CREATE INDEX IF NOT EXISTS idx_ai_harness_event_session
    ON ai_harness_event (session_id, created_time);

COMMENT ON TABLE ai_harness_event IS 'Harness 事件账本 append-only — SSE Last-Event-ID 游标基于 sequence_no';

CREATE TABLE IF NOT EXISTS ai_harness_approval (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    session_id          varchar(32)     NOT NULL,
    run_id              varchar(32)     NOT NULL,
    tool_name           varchar(128)    NOT NULL,
    tool_call_id        varchar(64)     NOT NULL,
    arguments_json      text            NOT NULL,
    arguments_sha256    varchar(64)     NOT NULL,
    state               varchar(16)     NOT NULL DEFAULT 'PENDING',
    expected_revision   bigint          NOT NULL DEFAULT 0,
    permission_revision bigint          NOT NULL DEFAULT 0,
    decision_id         varchar(64),
    decision            varchar(16),
    decided_by          varchar(64),
    decided_at          timestamptz,
    note                varchar(500),
    expires_at          timestamptz,
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_ai_harness_approval PRIMARY KEY (id)
);

ALTER TABLE ai_harness_approval DROP CONSTRAINT IF EXISTS chk_ai_harness_approval_state;
ALTER TABLE ai_harness_approval ADD CONSTRAINT chk_ai_harness_approval_state
    CHECK (state IN ('PENDING','APPROVED','DENIED','CONSUMED','EXPIRED'));

ALTER TABLE ai_harness_approval DROP CONSTRAINT IF EXISTS chk_ai_harness_approval_decision;
ALTER TABLE ai_harness_approval ADD CONSTRAINT chk_ai_harness_approval_decision
    CHECK (decision IS NULL OR decision IN ('APPROVE','DENY'));

CREATE INDEX IF NOT EXISTS idx_ai_harness_approval_run
    ON ai_harness_approval (run_id, state) WHERE deleted = false;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_harness_approval_decision
    ON ai_harness_approval (run_id, decision_id)
    WHERE deleted = false AND decision_id IS NOT NULL;

COMMENT ON TABLE ai_harness_approval IS '工具调用审批 — arguments_sha256 常时校验 + decisionId 幂等 + claim 一次性 CONSUMED';
COMMENT ON COLUMN ai_harness_approval.arguments_sha256 IS '工具参数 UTF-8 SHA-256 hex，resolve/claim 必须常时比较';
COMMENT ON COLUMN ai_harness_approval.permission_revision IS '创建时的权限版本，变更后 claim 拒绝';

CREATE TABLE IF NOT EXISTS ai_harness_plan (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    session_id          varchar(32)     NOT NULL,
    run_id              varchar(32)     NOT NULL,
    task_id             varchar(64)     NOT NULL,
    mode                varchar(16)     NOT NULL DEFAULT 'PLAN',
    review_state        varchar(32)     NOT NULL DEFAULT 'DRAFT',
    plan_md             text            NOT NULL,
    steps_json          jsonb,
    canonical_hash      varchar(64)     NOT NULL,
    feedback            text,
    expected_revision   bigint          NOT NULL DEFAULT 0,
    idempotency_key     varchar(128),
    approved_by         varchar(64),
    approved_at         timestamptz,
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_ai_harness_plan PRIMARY KEY (id)
);

ALTER TABLE ai_harness_plan DROP CONSTRAINT IF EXISTS chk_ai_harness_plan_mode;
ALTER TABLE ai_harness_plan ADD CONSTRAINT chk_ai_harness_plan_mode
    CHECK (mode IN ('PLAN','BUILD','VERIFY','BLOCKED','COMPLETED','FAILED'));

ALTER TABLE ai_harness_plan DROP CONSTRAINT IF EXISTS chk_ai_harness_plan_review;
ALTER TABLE ai_harness_plan ADD CONSTRAINT chk_ai_harness_plan_review
    CHECK (review_state IN ('DRAFT','AWAITING_APPROVAL','APPROVED','REVISION_REQUESTED'));

CREATE INDEX IF NOT EXISTS idx_ai_harness_plan_run
    ON ai_harness_plan (run_id, created_time DESC) WHERE deleted = false;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_harness_plan_idem
    ON ai_harness_plan (run_id, idempotency_key)
    WHERE deleted = false AND idempotency_key IS NOT NULL;

COMMENT ON TABLE ai_harness_plan IS 'Harness 任务计划 — canonical_hash SHA-256 + expected_revision 审批';
COMMENT ON COLUMN ai_harness_plan.canonical_hash IS '计划规范哈希，approve 必须常时比较';
