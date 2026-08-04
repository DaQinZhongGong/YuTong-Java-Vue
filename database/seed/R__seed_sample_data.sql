-- R__seed_sample_data: 样例业务数据 (可重复执行)
-- 设计来源: 18-样例业务详细设计、68-演示环境与样例数据剧本详设

-- 客户
INSERT INTO biz_customer (id, tenant_id, customer_code, customer_name, contact_name, contact_phone, address, status, created_time)
VALUES
  ('01JYYYYYYCUSTSAMPLE00001', 'default', 'C001', '北京示例科技有限公司', '张经理', '13800000001', '北京市海淀区中关村大街1号', 'ENABLED', now()),
  ('01JYYYYYYCUSTSAMPLE00002', 'default', 'C002', '上海示范贸易有限公司', '李经理', '13800000002', '上海市浦东新区世纪大道100号', 'ENABLED', now()),
  ('01JYYYYYYCUSTSAMPLE00003', 'default', 'C003', '深圳样例数据股份公司', '王经理', '13800000003', '深圳市南山区科技园南路10号', 'ENABLED', now())
ON CONFLICT DO NOTHING;

-- 商品
INSERT INTO biz_product (id, tenant_id, product_code, product_name, unit, price, status, created_time)
VALUES
  ('01JYYYYYYPRODSAMPLE00001', 'default', 'P001', '企业版授权 (年)', 'PCS', 98000.00,  'ENABLED', now()),
  ('01JYYYYYYPRODSAMPLE00002', 'default', 'P002', '实施服务 (人天)',  'PCS', 2500.00,   'ENABLED', now()),
  ('01JYYYYYYPRODSAMPLE00003', 'default', 'P003', '培训服务 (次)',    'PCS', 8000.00,   'ENABLED', now()),
  ('01JYYYYYYPRODSAMPLE00004', 'default', 'P004', '服务器 (台)',       'BOX', 18000.00,  'ENABLED', now())
ON CONFLICT DO NOTHING;

-- 业务编码序列
INSERT INTO sys_sequence (id, tenant_id, sequence_code, biz_date, current_value, step, reset_policy, created_time)
VALUES
  ('01JYYYYYYSEQSAMPLE00001', 'default', 'BIZ_REQUEST_NO', to_char(now(), 'YYYYMMDD'), 0, 1, 'DAILY', now())
ON CONFLICT DO NOTHING;
