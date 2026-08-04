-- V027: 报表设计器、大屏、Widget 组件表 (42-报表与大屏可视化设计 R2/R3)
-- 设计来源: 42-报表与大屏可视化设计 rpt_report(扩展)、rpt_dashboard、rpt_widget
-- 范围: R2 拖拽式报表设计器 + R3 数据大屏（全屏可视化）+ Widget 组件库

-- ===== 报表定义（扩展字段：dataset_bindings、owner_user_id） =====
-- 注意: rpt_report 表已在 V012 创建，此处仅添加 R2 新增字段
DO $$
BEGIN
    -- dataset_bindings: 数据集与组件绑定
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='rpt_report' AND column_name='dataset_bindings') THEN
        ALTER TABLE rpt_report ADD COLUMN dataset_bindings jsonb;
    END IF;
    -- owner_user_id: 报表负责人
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='rpt_report' AND column_name='owner_user_id') THEN
        ALTER TABLE rpt_report ADD COLUMN owner_user_id varchar(64);
    END IF;
END $$;

-- ===== 大屏定义 =====
CREATE TABLE IF NOT EXISTS rpt_dashboard (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    dashboard_code      varchar(64)   NOT NULL,
    dashboard_name      varchar(128)  NOT NULL,
    canvas_width        int           NOT NULL DEFAULT 1920,
    canvas_height       int           NOT NULL DEFAULT 1080,
    theme               varchar(16)   NOT NULL DEFAULT 'dark',
    background_image    varchar(512),
    layout_json         jsonb,
    component_bindings  jsonb,
    refresh_interval    int           NOT NULL DEFAULT 30,
    status              varchar(16)   NOT NULL DEFAULT 'DRAFT',
    owner_user_id       varchar(64),
    permission_code     varchar(128),
    description         varchar(512),
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_rpt_dashboard PRIMARY KEY (id),
    CONSTRAINT uk_rpt_dashboard_tenant_code UNIQUE (tenant_id, dashboard_code)
);
CREATE INDEX IF NOT EXISTS idx_rpt_dashboard_status ON rpt_dashboard (tenant_id, status, deleted);
COMMENT ON TABLE rpt_dashboard IS '大屏定义。设计来源: 42-报表与大屏可视化设计 rpt_dashboard';

-- ===== Widget 组件 =====
CREATE TABLE IF NOT EXISTS rpt_widget (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    widget_code         varchar(64)   NOT NULL,
    widget_name         varchar(128)  NOT NULL,
    widget_type         varchar(32)   NOT NULL,
    dataset_code        varchar(64),
    props_json          jsonb,
    style_json          jsonb,
    description         varchar(512),
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_rpt_widget PRIMARY KEY (id),
    CONSTRAINT uk_rpt_widget_tenant_code UNIQUE (tenant_id, widget_code)
);
CREATE INDEX IF NOT EXISTS idx_rpt_widget_type ON rpt_widget (tenant_id, widget_type, deleted);
COMMENT ON TABLE rpt_widget IS 'Widget 组件定义。设计来源: 42-报表与大屏可视化设计 rpt_widget';

-- ===== 种子数据: 2 个报表（biz-request-overview-report, biz-request-trend-report） =====
INSERT INTO rpt_report (id, tenant_id, report_code, report_name, report_type, layout_json, dataset_bindings, version_no, permission_code, status, owner_user_id, description, created_by, version)
SELECT '01RPTREPORT000000000000DASH01', 'default', 'biz-request-overview-report', '业务申请总览报表', 'MIX',
       '{"layoutSchemaVersion":"1.0","canvas":{"title":"业务申请总览","theme":"light"},"components":[{"id":"c1","type":"pie-chart","datasetCode":"biz_request_status_stat","props":{"nameField":"request_status","valueField":"cnt","title":"状态分布"},"layout":{"x":0,"y":0,"w":12,"h":6}},{"id":"c2","type":"bar-chart","datasetCode":"biz_request_amount_top10","props":{"xField":"request_no","yField":"total_amount","title":"金额Top10"},"layout":{"x":12,"y":0,"w":12,"h":6}}]}'::jsonb,
       '[{"componentId":"c1","datasetCode":"biz_request_status_stat"},{"componentId":"c2","datasetCode":"biz_request_amount_top10"}]'::jsonb,
       1, 'report:view', 'PUBLISHED', '01MOCKUSER0000000000000ADMIN', '业务申请总览报表（R2 设计器种子数据）', 'system', 0
WHERE NOT EXISTS (SELECT 1 FROM rpt_report WHERE tenant_id='default' AND report_code='biz-request-overview-report');

INSERT INTO rpt_report (id, tenant_id, report_code, report_name, report_type, layout_json, dataset_bindings, version_no, permission_code, status, owner_user_id, description, created_by, version)
SELECT '01RPTREPORT000000000000DASH02', 'default', 'biz-request-trend-report', '业务申请趋势报表', 'CHART',
       '{"layoutSchemaVersion":"1.0","canvas":{"title":"近7日提交趋势","theme":"light"},"components":[{"id":"c1","type":"line-chart","datasetCode":"biz_request_trend_7d","props":{"xField":"stat_date","yField":"cnt","title":"每日提交数"},"layout":{"x":0,"y":0,"w":24,"h":8}}]}'::jsonb,
       '[{"componentId":"c1","datasetCode":"biz_request_trend_7d"}]'::jsonb,
       1, 'report:view', 'PUBLISHED', '01MOCKUSER0000000000000ADMIN', '业务申请趋势报表（R2 设计器种子数据）', 'system', 0
WHERE NOT EXISTS (SELECT 1 FROM rpt_report WHERE tenant_id='default' AND report_code='biz-request-trend-report');

-- ===== 种子数据: 1 个大屏（biz-dashboard-dark） =====
INSERT INTO rpt_dashboard (id, tenant_id, dashboard_code, dashboard_name, canvas_width, canvas_height, theme, layout_json, component_bindings, refresh_interval, status, owner_user_id, permission_code, description, created_by, version)
SELECT '01RPTDASHBD0000000000000001', 'default', 'biz-dashboard-dark', '业务数据大屏（深色科技风）', 1920, 1080, 'dark',
       '{"layoutSchemaVersion":"1.0","canvas":{"width":1920,"height":1080,"theme":"dark","backgroundImage":""},"components":[{"id":"d1","type":"kpi-card","widgetCode":"kpi-card-widget","props":{"title":"待办数","datasetCode":"dashboard_todo_count","valueField":"pending_todos"},"layout":{"x":0,"y":0,"w":6,"h":3}},{"id":"d2","type":"line-chart","widgetCode":"line-chart-widget","props":{"title":"近7日趋势","datasetCode":"biz_request_trend_7d","xField":"stat_date","yField":"cnt"},"layout":{"x":6,"y":0,"w":12,"h":6}},{"id":"d3","type":"data-table","widgetCode":"data-table-widget","props":{"title":"金额Top10","datasetCode":"biz_request_amount_top10"},"layout":{"x":18,"y":0,"w":6,"h":6}}]}'::jsonb,
       '[{"componentId":"d1","widgetCode":"kpi-card-widget"},{"componentId":"d2","widgetCode":"line-chart-widget"},{"componentId":"d3","widgetCode":"data-table-widget"}]'::jsonb,
       30, 'PUBLISHED', '01MOCKUSER0000000000000ADMIN', 'dashboard:view', '业务数据深色科技风大屏（R3 种子数据）', 'system', 0
WHERE NOT EXISTS (SELECT 1 FROM rpt_dashboard WHERE tenant_id='default' AND dashboard_code='biz-dashboard-dark');

-- ===== 种子数据: 3 个 Widget（line-chart-widget, kpi-card-widget, data-table-widget） =====
INSERT INTO rpt_widget (id, tenant_id, widget_code, widget_name, widget_type, dataset_code, props_json, style_json, description, created_by, version)
SELECT '01RPTWIDGET00000000000000001', 'default', 'line-chart-widget', '折线图组件', 'line-chart', 'biz_request_trend_7d',
       '{"xField":"stat_date","yField":"cnt","title":"趋势折线图","smooth":true}'::jsonb,
       '{"borderColor":"#409EFF","backgroundColor":"rgba(0,0,0,0.2)","textColor":"#FFFFFF"}'::jsonb,
       '折线图 Widget，用于大屏趋势展示', 'system', 0
WHERE NOT EXISTS (SELECT 1 FROM rpt_widget WHERE tenant_id='default' AND widget_code='line-chart-widget');

INSERT INTO rpt_widget (id, tenant_id, widget_code, widget_name, widget_type, dataset_code, props_json, style_json, description, created_by, version)
SELECT '01RPTWIDGET00000000000000002', 'default', 'kpi-card-widget', 'KPI 指标卡', 'kpi-card', 'dashboard_todo_count',
       '{"title":"待办数","valueField":"pending_todos","unit":"","fontSize":48}'::jsonb,
       '{"borderColor":"#67C23A","backgroundColor":"rgba(0,0,0,0.3)","textColor":"#67C23A"}'::jsonb,
       'KPI 指标卡 Widget，用于大屏关键指标展示', 'system', 0
WHERE NOT EXISTS (SELECT 1 FROM rpt_widget WHERE tenant_id='default' AND widget_code='kpi-card-widget');

INSERT INTO rpt_widget (id, tenant_id, widget_code, widget_name, widget_type, dataset_code, props_json, style_json, description, created_by, version)
SELECT '01RPTWIDGET00000000000000003', 'default', 'data-table-widget', '数据表格组件', 'data-table', 'biz_request_amount_top10',
       '{"title":"金额Top10","columns":["request_no","title","total_amount"],"pageSize":10}'::jsonb,
       '{"borderColor":"#E6A23C","backgroundColor":"rgba(0,0,0,0.2)","textColor":"#FFFFFF","headerBg":"rgba(230,162,60,0.3)"}'::jsonb,
       '数据表格 Widget，用于大屏数据明细展示', 'system', 0
WHERE NOT EXISTS (SELECT 1 FROM rpt_widget WHERE tenant_id='default' AND widget_code='data-table-widget');
