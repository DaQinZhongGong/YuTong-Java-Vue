-- R__seed_config: 参数种子数据 (可重复执行)
INSERT INTO sys_config (id, tenant_id, config_key, config_value, value_type, config_group, editable, sensitive, status, created_time)
VALUES
  ('01JYYYYYYYYYYCONFIG000001', 'default', 'sys.platform.name',        'YuTong 雨桐平台',     'STRING', 'system',  true,  false, 'ENABLED', now()),
  ('01JYYYYYYYYYYCONFIG000002', 'default', 'sys.platform.version',     '0.2.0',               'STRING', 'system',  false, false, 'ENABLED', now()),
  ('01JYYYYYYYYYYCONFIG000003', 'default', 'sys.file.max_size_mb',     '100',                 'NUMBER', 'file',    true,  false, 'ENABLED', now()),
  ('01JYYYYYYYYYYCONFIG000004', 'default', 'sys.file.allowed_ext',     'png,jpg,jpeg,pdf,docx,xlsx,zip', 'STRING', 'file', true, false, 'ENABLED', now()),
  ('01JYYYYYYYYYYCONFIG000005', 'default', 'sys.request.prefix',       'BIZ',                 'STRING', 'biz',     true,  false, 'ENABLED', now()),
  ('01JYYYYYYYYYYCONFIG000006', 'default', 'sys.request.idempotency_days', '7',               'NUMBER', 'biz',     false, false, 'ENABLED', now())
ON CONFLICT DO NOTHING;
