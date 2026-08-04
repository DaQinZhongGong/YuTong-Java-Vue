-- V012: 报表分析表 (GA2-36)
-- 设计来源: 35-样例业务矩阵扩展设计 P1 报表分析、42-报表与大屏可视化设计 R1
-- 验证能力: 物化视图刷新 + ECharts 图表 + 大数据量导出异步化 + AI 指标解释 + 数据权限下的报表过滤
-- 范围: R1 可配置统计报表（不做 R2 拖拽设计器 / R3 大屏 / R4 订阅）

-- ===== 数据集定义 =====
CREATE TABLE rpt_dataset (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    dataset_code        varchar(64)   NOT NULL,
    dataset_name        varchar(128)  NOT NULL,
    source_type         varchar(32)   NOT NULL DEFAULT 'SQL',
    -- SQL/VIEW 数据集的查询文本，参数使用 :paramName 命名占位符
    query_text          text          NOT NULL,
    -- 参数 schema (jsonb): [{"name":"days","type":"int","required":false,"default":7}]
    params_schema       jsonb,
    cache_seconds       int           NOT NULL DEFAULT 0,
    risk_level          varchar(16)   NOT NULL DEFAULT 'LOW',
    owner_user_id       varchar(64),
    permission_code     varchar(128),
    -- 列级脱敏策略 (jsonb): {"amount":{"viewer":"MASK"}}
    sensitive_columns   jsonb,
    max_rows            int           NOT NULL DEFAULT 1000,
    timeout_ms          int           NOT NULL DEFAULT 5000,
    review_status       varchar(16)   NOT NULL DEFAULT 'APPROVED',
    status              varchar(16)   NOT NULL DEFAULT 'PUBLISHED',
    description         varchar(512),
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_rpt_dataset PRIMARY KEY (id),
    CONSTRAINT uk_rpt_dataset_tenant_code UNIQUE (tenant_id, dataset_code)
);
CREATE INDEX idx_rpt_dataset_status ON rpt_dataset (tenant_id, status, deleted);
COMMENT ON TABLE rpt_dataset IS '报表数据集。设计来源: 42-报表与大屏可视化设计 rpt_dataset';

-- ===== 报表定义 =====
CREATE TABLE rpt_report (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    report_code         varchar(64)   NOT NULL,
    report_name         varchar(128)  NOT NULL,
    report_type         varchar(32)   NOT NULL DEFAULT 'CHART',
    -- 布局 JSON (jsonb): {canvas, components:[{type, datasetCode, props, layout}]}
    layout_json         jsonb         NOT NULL,
    version_no          int           NOT NULL DEFAULT 1,
    permission_code     varchar(128),
    status              varchar(16)   NOT NULL DEFAULT 'PUBLISHED',
    description         varchar(512),
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_rpt_report PRIMARY KEY (id),
    CONSTRAINT uk_rpt_report_tenant_code UNIQUE (tenant_id, report_code)
);
CREATE INDEX idx_rpt_report_status ON rpt_report (tenant_id, status, deleted);
COMMENT ON TABLE rpt_report IS '报表定义。设计来源: 42-报表与大屏可视化设计 rpt_report';

-- ===== 物化视图: 申请单状态统计 (验证物化视图刷新能力) =====
-- 设计来源: 42 号文档 biz_request_status_stat 内置数据集
CREATE MATERIALIZED VIEW rpt_mv_request_status_stat AS
SELECT
    tenant_id,
    request_status,
    COUNT(*) AS cnt,
    COALESCE(SUM(total_amount), 0) AS total_amount,
    now() AS refreshed_at
FROM biz_request
WHERE deleted = false
GROUP BY tenant_id, request_status
WITH NO DATA;
CREATE UNIQUE INDEX idx_rpt_mv_request_status_stat_tenant_status
    ON rpt_mv_request_status_stat (tenant_id, request_status);
COMMENT ON MATERIALIZED VIEW rpt_mv_request_status_stat IS '申请单状态统计物化视图。GA2-36 验证物化视图刷新能力';

-- ===== 物化视图: 近 7 日提交趋势 (对齐 biz_request_trend_7d 数据集) =====
CREATE MATERIALIZED VIEW rpt_mv_request_trend_7d AS
SELECT
    tenant_id,
    (created_time AT TIME ZONE 'Asia/Shanghai')::date AS stat_date,
    COUNT(*) AS cnt,
    COALESCE(SUM(total_amount), 0) AS total_amount,
    now() AS refreshed_at
FROM biz_request
WHERE deleted = false
  AND created_time >= now() - INTERVAL '7 days'
GROUP BY tenant_id, (created_time AT TIME ZONE 'Asia/Shanghai')::date
WITH NO DATA;
CREATE UNIQUE INDEX idx_rpt_mv_request_trend_7d_tenant_date
    ON rpt_mv_request_trend_7d (tenant_id, stat_date);
COMMENT ON MATERIALIZED VIEW rpt_mv_request_trend_7d IS '近7日提交趋势物化视图。GA2-36 验证物化视图刷新能力';

-- ===== 种子数据: 4 个内置数据集 =====
-- 对齐 42 号文档"内置数据集（样例）"表
INSERT INTO rpt_dataset (id, tenant_id, dataset_code, dataset_name, source_type, query_text, params_schema, cache_seconds, risk_level, owner_user_id, permission_code, sensitive_columns, max_rows, timeout_ms, review_status, status, description, created_by, version) VALUES
('01RPTDATASET000000000000001', 'default', 'biz_request_status_stat', '申请单状态统计', 'SQL',
 'SELECT request_status, COUNT(*) AS cnt, COALESCE(SUM(total_amount),0) AS total_amount FROM biz_request WHERE deleted=false AND tenant_id=:tenantId GROUP BY request_status ORDER BY cnt DESC',
 '[{"name":"tenantId","type":"string","required":true}]'::jsonb,
 60, 'LOW', '01MOCKUSER0000000000000ADMIN', 'report:dataset:view', NULL, 100, 3000, 'APPROVED', 'PUBLISHED',
 '各状态申请单数量统计，对齐 42 号文档 biz_request_status_stat 内置数据集', 'system', 0),
('01RPTDATASET000000000000002', 'default', 'biz_request_trend_7d', '近7日提交趋势', 'SQL',
 'SELECT (created_time AT TIME ZONE ''Asia/Shanghai'')::date AS stat_date, COUNT(*) AS cnt, COALESCE(SUM(total_amount),0) AS total_amount FROM biz_request WHERE deleted=false AND tenant_id=:tenantId AND created_time >= now() - (:days * INTERVAL ''1 day'') GROUP BY 1 ORDER BY 1',
 '[{"name":"tenantId","type":"string","required":true},{"name":"days","type":"int","required":false,"default":7}]'::jsonb,
 60, 'LOW', '01MOCKUSER0000000000000ADMIN', 'report:dataset:view', NULL, 100, 3000, 'APPROVED', 'PUBLISHED',
 '近 N 日申请单提交趋势，对齐 42 号文档 biz_request_trend_7d 内置数据集', 'system', 0),
('01RPTDATASET000000000000003', 'default', 'biz_request_amount_top10', '金额Top10申请单', 'SQL',
 'SELECT request_no, title, total_amount, customer_name_snapshot, request_status FROM biz_request WHERE deleted=false AND tenant_id=:tenantId AND request_status <> ''DRAFT'' AND total_amount IS NOT NULL ORDER BY total_amount DESC LIMIT :limit',
 '[{"name":"tenantId","type":"string","required":true},{"name":"limit","type":"int","required":false,"default":10}]'::jsonb,
 60, 'MEDIUM', '01MOCKUSER0000000000000ADMIN', 'report:dataset:view',
 '{"total_amount":{"viewer":"MASK"},"customer_name_snapshot":{"viewer":"MASK"}}'::jsonb,
 50, 3000, 'APPROVED', 'PUBLISHED',
 '金额 Top N 申请单，对齐 42 号文档 biz_request_amount_top10 内置数据集。金额和客户名对 viewer 脱敏', 'system', 0),
('01RPTDATASET000000000000004', 'default', 'dashboard_todo_count', '待办与消息计数', 'SQL',
 'SELECT (SELECT COUNT(*) FROM sys_todo_task WHERE deleted=false AND tenant_id=:tenantId AND todo_status=''PENDING'') AS pending_todos, (SELECT COUNT(*) FROM sys_message WHERE deleted=false AND tenant_id=:tenantId AND read_status=''UNREAD'') AS unread_messages',
 '[{"name":"tenantId","type":"string","required":true}]'::jsonb,
 30, 'LOW', '01MOCKUSER0000000000000ADMIN', 'report:dataset:view', NULL, 10, 2000, 'APPROVED', 'PUBLISHED',
 '待办与未读消息计数，对齐 42 号文档 dashboard_todo_count 内置数据集', 'system', 0);

-- ===== 种子数据: 2 个内置报表 =====
INSERT INTO rpt_report (id, tenant_id, report_code, report_name, report_type, layout_json, version_no, permission_code, status, description, created_by, version) VALUES
('01RPTREPORT00000000000000001', 'default', 'biz_request_overview', '申请单总览报表', 'MIX',
 '{"layoutSchemaVersion":"1.0","canvas":{"title":"申请单总览","theme":"light"},"components":[{"id":"c1","type":"pie-chart","datasetCode":"biz_request_status_stat","props":{"nameField":"request_status","valueField":"cnt","title":"状态分布"},"layout":{"x":0,"y":0,"w":12,"h":6}},{"id":"c2","type":"bar-chart","datasetCode":"biz_request_amount_top10","props":{"xField":"request_no","yField":"total_amount","title":"金额Top10"},"layout":{"x":12,"y":0,"w":12,"h":6}}]}'::jsonb,
 1, 'report:view', 'PUBLISHED', '申请单总览，含状态分布饼图 + 金额Top10柱状图，对齐 42 号文档 R1', 'system', 0),
('01RPTREPORT00000000000000002', 'default', 'biz_request_trend_report', '申请单趋势报表', 'CHART',
 '{"layoutSchemaVersion":"1.0","canvas":{"title":"近7日提交趋势","theme":"light"},"components":[{"id":"c1","type":"line-chart","datasetCode":"biz_request_trend_7d","props":{"xField":"stat_date","yField":"cnt","title":"每日提交数"},"layout":{"x":0,"y":0,"w":24,"h":8}}]}'::jsonb,
 1, 'report:view', 'PUBLISHED', '近7日申请单提交趋势折线图，对齐 42 号文档 R1', 'system', 0);
