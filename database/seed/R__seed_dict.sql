-- R__seed_dict: 字典种子数据 (可重复执行)
-- 设计来源: 57-完整DDL清单与数据字典详设
-- tenant_id 使用 'default' 作为本地开发默认租户

-- 字典类型
INSERT INTO sys_dict_type (id, tenant_id, dict_type, dict_name, status, system_flag, sort_no, created_time)
VALUES
  ('01JYYYYYYYYYYYYDICTTYPE0001', 'default', 'sys_common_status',      '通用状态',     'ENABLED', true, 1, now()),
  ('01JYYYYYYYYYYYYDICTTYPE0002', 'default', 'biz_request_status',     '申请单状态',   'ENABLED', true, 2, now()),
  ('01JYYYYYYYYYYYYDICTTYPE0003', 'default', 'biz_approval_action',    '审批动作',     'ENABLED', true, 3, now()),
  ('01JYYYYYYYYYYYYDICTTYPE0004', 'default', 'biz_approval_result',    '审批结果',     'ENABLED', true, 4, now()),
  ('01JYYYYYYYYYYYYDICTTYPE0005', 'default', 'import_export_task_status','导入导出状态','ENABLED', true, 5, now()),
  ('01JYYYYYYYYYYYYDICTTYPE0006', 'default', 'biz_product_unit',       '商品单位',     'ENABLED', true, 6, now()),
  ('01JYYYYYYYYYYYYDICTTYPE0007', 'default', 'sys_todo_type',          '待办类型',     'ENABLED', true, 7, now()),
  ('01JYYYYYYYYYYYYDICTTYPE0008', 'default', 'sys_todo_status',        '待办状态',     'ENABLED', true, 8, now())
ON CONFLICT DO NOTHING;

-- 字典项
INSERT INTO sys_dict_item (id, tenant_id, dict_type, item_code, item_label, item_value, status, sort_no, color_token, created_time)
VALUES
  ('01JYYYYYYYYYYDICTITEMS0001', 'default', 'sys_common_status', 'ENABLED',  '启用',     'ENABLED',  'ENABLED', 1, 'success', now()),
  ('01JYYYYYYYYYYDICTITEMS0002', 'default', 'sys_common_status', 'DISABLED', '禁用',     'DISABLED', 'ENABLED', 2, 'danger',  now()),
  ('01JYYYYYYYYYYDICTITEMS0003', 'default', 'biz_request_status','DRAFT',     '草稿',     'DRAFT',     'ENABLED', 1, 'info',    now()),
  ('01JYYYYYYYYYYDICTITEMS0004', 'default', 'biz_request_status','SUBMITTED', '已提交',   'SUBMITTED', 'ENABLED', 2, 'warning', now()),
  ('01JYYYYYYYYYYDICTITEMS0005', 'default', 'biz_request_status','APPROVED',  '已通过',   'APPROVED',  'ENABLED', 3, 'success', now()),
  ('01JYYYYYYYYYYDICTITEMS0006', 'default', 'biz_request_status','REJECTED',  '已驳回',   'REJECTED',  'ENABLED', 4, 'danger',  now()),
  ('01JYYYYYYYYYYDICTITEMS0007', 'default', 'biz_request_status','ARCHIVED',  '已归档',   'ARCHIVED',  'ENABLED', 5, 'info',    now()),
  ('01JYYYYYYYYYYDICTITEMS0008', 'default', 'biz_approval_action','SUBMIT',   '提交',     'SUBMIT',    'ENABLED', 1, 'primary', now()),
  ('01JYYYYYYYYYYDICTITEMS0009', 'default', 'biz_approval_action','APPROVE',  '审核通过', 'APPROVE',   'ENABLED', 2, 'success', now()),
  ('01JYYYYYYYYYYDICTITEMS0010', 'default', 'biz_approval_action','REJECT',   '驳回',     'REJECT',    'ENABLED', 3, 'danger',  now()),
  ('01JYYYYYYYYYYDICTITEMS0011', 'default', 'biz_approval_action','WITHDRAW', '撤回',     'WITHDRAW',  'ENABLED', 4, 'warning', now()),
  ('01JYYYYYYYYYYDICTITEMS0012', 'default', 'biz_approval_action','ARCHIVE',  '归档',     'ARCHIVE',   'ENABLED', 5, 'info',    now()),
  ('01JYYYYYYYYYYDICTITEMS0013', 'default', 'biz_approval_result','APPROVED', '通过',     'APPROVED',  'ENABLED', 1, 'success', now()),
  ('01JYYYYYYYYYYDICTITEMS0014', 'default', 'biz_approval_result','REJECTED', '驳回',     'REJECTED',  'ENABLED', 2, 'danger',  now()),
  ('01JYYYYYYYYYYDICTITEMS0015', 'default', 'import_export_task_status','PENDING',          '等待中',   'PENDING',          'ENABLED', 1, 'info',    now()),
  ('01JYYYYYYYYYYDICTITEMS0016', 'default', 'import_export_task_status','RUNNING',          '运行中',   'RUNNING',          'ENABLED', 2, 'warning', now()),
  ('01JYYYYYYYYYYDICTITEMS0017', 'default', 'import_export_task_status','SUCCESS',          '成功',     'SUCCESS',          'ENABLED', 3, 'success', now()),
  ('01JYYYYYYYYYYDICTITEMS0018', 'default', 'import_export_task_status','PARTIAL_SUCCESS',  '部分成功', 'PARTIAL_SUCCESS',  'ENABLED', 4, 'warning', now()),
  ('01JYYYYYYYYYYDICTITEMS0019', 'default', 'import_export_task_status','FAILED',           '失败',     'FAILED',           'ENABLED', 5, 'danger',  now()),
  ('01JYYYYYYYYYYDICTITEMS0020', 'default', 'biz_product_unit','PCS', '件',   'PCS', 'ENABLED', 1, null, now()),
  ('01JYYYYYYYYYYDICTITEMS0021', 'default', 'biz_product_unit','BOX','箱',   'BOX', 'ENABLED', 2, null, now()),
  ('01JYYYYYYYYYYDICTITEMS0022', 'default', 'biz_product_unit','KG', '千克', 'KG',  'ENABLED', 3, null, now()),
  ('01JYYYYYYYYYYDICTITEMS0023', 'default', 'sys_todo_type','APPROVAL','审批待办','APPROVAL','ENABLED', 1, null, now()),
  ('01JYYYYYYYYYYDICTITEMS0024', 'default', 'sys_todo_type','NOTICE','通知待办','NOTICE',   'ENABLED', 2, null, now()),
  ('01JYYYYYYYYYYDICTITEMS0025', 'default', 'sys_todo_type','TASK','任务待办','TASK',       'ENABLED', 3, null, now()),
  ('01JYYYYYYYYYYDICTITEMS0026', 'default', 'sys_todo_status','PENDING','待处理','PENDING', 'ENABLED', 1, 'warning', now()),
  ('01JYYYYYYYYYYDICTITEMS0027', 'default', 'sys_todo_status','DONE','已完成','DONE',       'ENABLED', 2, 'success', now()),
  ('01JYYYYYYYYYYDICTITEMS0028', 'default', 'sys_todo_status','CANCELLED','已取消','CANCELLED','ENABLED', 3, 'info', now())
ON CONFLICT DO NOTHING;
