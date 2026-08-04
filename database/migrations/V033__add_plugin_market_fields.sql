-- ============================================================
-- V033__add_plugin_market_fields.sql
-- 插件市场完整闭环：扩展 plugin_package 支持作者与依赖声明
-- ============================================================

ALTER TABLE plugin_package ADD COLUMN IF NOT EXISTS author varchar(64);
ALTER TABLE plugin_package ADD COLUMN IF NOT EXISTS dependencies_json text;
ALTER TABLE plugin_package ADD COLUMN IF NOT EXISTS description text;

COMMENT ON COLUMN plugin_package.author IS '插件作者';
COMMENT ON COLUMN plugin_package.dependencies_json IS '依赖声明 JSON（依赖插件编码列表）';
COMMENT ON COLUMN plugin_package.description IS '插件描述';

-- 更新种子数据，补充作者与描述
UPDATE plugin_package SET author = 'YuTong Official', description = '官方图表组件插件，支持柱状图、折线图、饼图等常见图表' WHERE plugin_code = 'demo-chart-widget';
UPDATE plugin_package SET author = 'YuTong Official', description = '官方表单模板插件，提供高级表单布局与校验能力' WHERE plugin_code = 'demo-form-template';
UPDATE plugin_package SET author = 'YuTong Official', description = '官方报表组件插件，支持复杂报表渲染与导出' WHERE plugin_code = 'demo-report-component';
