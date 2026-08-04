-- V029: 补充报表数据集缺失字段，对齐 42-报表与大屏可视化设计 rpt_dataset 模型
-- 设计来源: 42-报表与大屏可视化设计

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='rpt_dataset' AND column_name='datasource_code') THEN
        ALTER TABLE rpt_dataset ADD COLUMN datasource_code varchar(64) NOT NULL DEFAULT 'primary';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='rpt_dataset' AND column_name='data_scope_policy') THEN
        ALTER TABLE rpt_dataset ADD COLUMN data_scope_policy jsonb;
    END IF;
END $$;

COMMENT ON COLUMN rpt_dataset.datasource_code IS '数据源编码，默认 primary';
COMMENT ON COLUMN rpt_dataset.data_scope_policy IS '行级数据权限策略 (jsonb)';
