-- V031: AI 成本额度日用量表
-- 设计来源: GA2-45 成本治理闭环
-- 用途: 按租户/用户/场景 + 模型 + 日期累计 token 与金额用量，支撑额度校验与监控统计。

CREATE TABLE ai_cost_quota_usage (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    quota_scope         varchar(16)   NOT NULL,
    scope_key           varchar(128)  NOT NULL,
    model_code          varchar(64)   NOT NULL,
    usage_date          date          NOT NULL,
    token_used          bigint        NOT NULL DEFAULT 0,
    cost_used           numeric(18,4) NOT NULL DEFAULT 0.0000,
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_ai_cost_quota_usage PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_ai_cost_quota_usage_scope
    ON ai_cost_quota_usage (tenant_id, quota_scope, scope_key, model_code, usage_date)
    WHERE deleted = false;

COMMENT ON TABLE ai_cost_quota_usage IS 'AI 成本额度日用量（TENANT/USER/SCENARIO 三维 + 模型 + 日期）';
