-- R__seed_demo_data: v0.9 RC Demo 增强种子数据 (可重复执行)
-- 设计来源: 18-样例业务详细设计、13-AI能力设计、14-低代码平台设计、68-演示环境与样例数据剧本详设
-- 覆盖: 申请单/明细/审批/待办/消息 + AI 供应商/Prompt/知识库/文档/分块 + 低代码实体
-- ID 约定: 26 字符固定串，前缀 01JYYDEMO + 业务标识，便于演示识别
-- 用户 ID: 01MOCKUSER0000000000000USER (Mock Auth 默认用户)

-- ===== 清理旧 Demo 数据 (按 ID 前缀，保证可重复执行) =====
-- GA2-L178: 追加 biz_customer / biz_product demo 数据清理 (ID 前缀 01JYYDEMOCLI / 01JYYDEMOPROD)
DELETE FROM ai_embedding WHERE chunk_id LIKE '01JYYDEMOCHUNK%';
DELETE FROM ai_document_chunk WHERE id LIKE '01JYYDEMO%';
DELETE FROM ai_document WHERE id LIKE '01JYYDEMO%';
DELETE FROM ai_knowledge_base WHERE id LIKE '01JYYDEMO%';
DELETE FROM ai_prompt_template WHERE id LIKE '01JYYDEMO%';
DELETE FROM ai_provider WHERE id LIKE '01JYYDEMO%';
DELETE FROM lc_entity WHERE id LIKE '01JYYDEMO%';
DELETE FROM sys_message WHERE id LIKE '01JYYDEMO%';
DELETE FROM sys_todo_task WHERE id LIKE '01JYYDEMO%';
DELETE FROM biz_approval_record WHERE id LIKE '01JYYDEMO%';
DELETE FROM biz_request_item WHERE id LIKE '01JYYDEMO%';
DELETE FROM biz_request WHERE id LIKE '01JYYDEMO%';
DELETE FROM biz_product WHERE id LIKE '01JYYDEMO%';
DELETE FROM biz_customer WHERE id LIKE '01JYYDEMO%';

-- ===== 申请单 (覆盖 5 种状态) =====
INSERT INTO biz_request (id, tenant_id, request_no, title, customer_id, customer_name_snapshot, apply_reason, request_status, total_amount, applicant_id, applicant_name_snapshot, owner_user_id, owner_dept_id, owner_dept_path, submitted_time, approved_time, archived_time, created_time)
VALUES
  ('01JYYDEMOREQDRAFT0000001', 'default', 'BIZ20260714001', '北京示例科技-企业版授权采购', '01JYYYYYYCUSTSAMPLE00001', '北京示例科技有限公司', '续约企业版授权一年', 'DRAFT',     98000.00,  '01MOCKUSER0000000000000USER', '系统管理员', '01MOCKUSER0000000000000USER', 'dept-001', '/corp/sales', NULL,                      NULL,                      NULL,                      now() - interval '1 hour'),
  ('01JYYDEMOREQSUBMIT000001', 'default', 'BIZ20260714002', '上海示范贸易-实施服务采购',     '01JYYYYYYCUSTSAMPLE00002', '上海示范贸易有限公司', '新项目实施服务 10 人天', 'SUBMITTED', 25000.00,  '01MOCKUSER0000000000000USER', '系统管理员', '01MOCKUSER0000000000000USER', 'dept-001', '/corp/sales', now() - interval '2 hour',  NULL,                      NULL,                      now() - interval '2 hour'),
  ('01JYYDEMOREQSUBMIT000002', 'default', 'BIZ20260714003', '深圳样例数据-培训服务采购',     '01JYYYYYYCUSTSAMPLE00003', '深圳样例数据股份公司', '团队培训 2 次',          'SUBMITTED', 16000.00,  '01MOCKUSER0000000000000USER', '系统管理员', '01MOCKUSER0000000000000USER', 'dept-001', '/corp/sales', now() - interval '3 hour',  NULL,                      NULL,                      now() - interval '3 hour'),
  ('01JYYDEMOREQAPPROV000001', 'default', 'BIZ20260713001', '北京示例科技-服务器采购',       '01JYYYYYYCUSTSAMPLE00001', '北京示例科技有限公司', '扩容服务器 2 台',         'APPROVED',  36000.00,  '01MOCKUSER0000000000000USER', '系统管理员', '01MOCKUSER0000000000000USER', 'dept-001', '/corp/sales', now() - interval '1 day',   now() - interval '20 hour', NULL,                      now() - interval '1 day'),
  ('01JYYDEMOREQAPPROV000002', 'default', 'BIZ20260712001', '上海示范贸易-培训与实施',       '01JYYYYYYCUSTSAMPLE00002', '上海示范贸易有限公司', '培训 1 次 + 实施 4 人天', 'APPROVED',  18000.00,  '01MOCKUSER0000000000000USER', '系统管理员', '01MOCKUSER0000000000000USER', 'dept-001', '/corp/sales', now() - interval '2 day',   now() - interval '40 hour', NULL,                      now() - interval '2 day'),
  ('01JYYDEMOREQREJECT000001', 'default', 'BIZ20260711001', '深圳样例数据-企业版授权采购', '01JYYYYYYCUSTSAMPLE00003', '深圳样例数据股份公司', '预算超标，需重新评估',    'REJECTED',  98000.00,  '01MOCKUSER0000000000000USER', '系统管理员', '01MOCKUSER0000000000000USER', 'dept-001', '/corp/sales', now() - interval '3 day',   now() - interval '65 hour', NULL,                      now() - interval '3 day'),
  ('01JYYDEMOREQARCHIV000001', 'default', 'BIZ20260710001', '北京示例科技-历史培训服务',     '01JYYYYYYCUSTSAMPLE00001', '北京示例科技有限公司', '已完成的历史培训',        'ARCHIVED',  8000.00,   '01MOCKUSER0000000000000USER', '系统管理员', '01MOCKUSER0000000000000USER', 'dept-001', '/corp/sales', now() - interval '10 day',  now() - interval '9 day',   now() - interval '5 day',   now() - interval '10 day')
ON CONFLICT DO NOTHING;

-- ===== 申请单明细 =====
INSERT INTO biz_request_item (id, tenant_id, request_id, product_id, product_code_snapshot, product_name_snapshot, unit, quantity, unit_price, line_amount, sort_no, created_time)
VALUES
  ('01JYYDEMOITEM000000001', 'default', '01JYYDEMOREQDRAFT0000001', '01JYYYYYYPRODSAMPLE00001', 'P001', '企业版授权 (年)', 'PCS', 1.0, 98000.00, 98000.00, 1, now() - interval '1 hour'),
  ('01JYYDEMOITEM000000002', 'default', '01JYYDEMOREQSUBMIT000001', '01JYYYYYYPRODSAMPLE00002', 'P002', '实施服务 (人天)',  'PCS', 10.0, 2500.00,  25000.00, 1, now() - interval '2 hour'),
  ('01JYYDEMOITEM000000003', 'default', '01JYYDEMOREQSUBMIT000002', '01JYYYYYYPRODSAMPLE00003', 'P003', '培训服务 (次)',    'PCS', 2.0, 8000.00,  16000.00, 1, now() - interval '3 hour'),
  ('01JYYDEMOITEM000000004', 'default', '01JYYDEMOREQAPPROV000001', '01JYYYYYYPRODSAMPLE00004', 'P004', '服务器 (台)',       'BOX', 2.0, 18000.00, 36000.00, 1, now() - interval '1 day'),
  ('01JYYDEMOITEM000000005', 'default', '01JYYDEMOREQAPPROV000002', '01JYYYYYYPRODSAMPLE00003', 'P003', '培训服务 (次)',    'PCS', 1.0, 8000.00,  8000.00,  1, now() - interval '2 day'),
  ('01JYYDEMOITEM000000006', 'default', '01JYYDEMOREQAPPROV000002', '01JYYYYYYPRODSAMPLE00002', 'P002', '实施服务 (人天)',  'PCS', 4.0, 2500.00,  10000.00, 2, now() - interval '2 day'),
  ('01JYYDEMOITEM000000007', 'default', '01JYYDEMOREQREJECT000001', '01JYYYYYYPRODSAMPLE00001', 'P001', '企业版授权 (年)', 'PCS', 1.0, 98000.00, 98000.00, 1, now() - interval '3 day'),
  ('01JYYDEMOITEM000000008', 'default', '01JYYDEMOREQARCHIV000001', '01JYYYYYYPRODSAMPLE00003', 'P003', '培训服务 (次)',    'PCS', 1.0, 8000.00,  8000.00,  1, now() - interval '10 day')
ON CONFLICT DO NOTHING;

-- ===== 审批记录 (SUBMITTED/APPROVED/REJECTED) =====
INSERT INTO biz_approval_record (id, tenant_id, request_id, action, result, opinion, operator_id, operated_time, created_time)
VALUES
  ('01JYYDEMOAPPR000000001', 'default', '01JYYDEMOREQAPPROV000001', 'APPROVE', 'APPROVED', '同意采购', '01MOCKUSER0000000000APPROVER', now() - interval '20 hour', now() - interval '20 hour'),
  ('01JYYDEMOAPPR000000002', 'default', '01JYYDEMOREQAPPROV000002', 'APPROVE', 'APPROVED', '同意',     '01MOCKUSER0000000000APPROVER', now() - interval '40 hour', now() - interval '40 hour'),
  ('01JYYDEMOAPPR000000003', 'default', '01JYYDEMOREQREJECT000001', 'APPROVE', 'REJECTED', '预算超标', '01MOCKUSER0000000000APPROVER', now() - interval '65 hour', now() - interval '65 hour')
ON CONFLICT DO NOTHING;

-- ===== 待办任务 (SUBMITTED 申请单产生审批待办) =====
INSERT INTO sys_todo_task (id, tenant_id, todo_type, biz_type, biz_id, title, assignee_id, todo_status, priority, due_time, created_time)
VALUES
  ('01JYYDEMOTEAM000000001', 'default', 'APPROVAL', 'BIZ_REQUEST', '01JYYDEMOREQSUBMIT000001', '上海示范贸易-实施服务采购 审批', '01MOCKUSER0000000000APPROVER', 'PENDING', 'HIGH',   now() + interval '2 day', now() - interval '2 hour'),
  ('01JYYDEMOTEAM000000002', 'default', 'APPROVAL', 'BIZ_REQUEST', '01JYYDEMOREQSUBMIT000002', '深圳样例数据-培训服务采购 审批', '01MOCKUSER0000000000000USER', 'PENDING', 'NORMAL', now() + interval '3 day', now() - interval '3 hour'),
  ('01JYYDEMOTEAM000000003', 'default', 'APPROVAL', 'BIZ_REQUEST', '01JYYDEMOREQAPPROV000001', '北京示例科技-服务器采购 审批', '01MOCKUSER0000000000APPROVER', 'DONE',    'NORMAL', NULL,                     now() - interval '20 hour'),
  ('01JYYDEMOTEAM000000004', 'default', 'APPROVAL', 'BIZ_REQUEST', '01JYYDEMOREQREJECT000001', '深圳样例数据-企业版授权采购 审批', '01MOCKUSER0000000000000USER', 'DONE',    'HIGH',   NULL,                     now() - interval '65 hour')
ON CONFLICT DO NOTHING;

-- ===== 站内消息 =====
INSERT INTO sys_message (id, tenant_id, receiver_id, msg_type, title, content, read_status, read_time, biz_type, biz_id, target_route_id, target_params, created_time)
VALUES
  ('01JYYDEMOMSG000000001', 'default', '01MOCKUSER0000000000000USER', 'TODO',     '您有新的审批待办: 上海示范贸易-实施服务采购',      '请尽快处理申请单 BIZ20260714002', 'UNREAD', NULL, 'BIZ_REQUEST', '01JYYDEMOREQSUBMIT000001', '/requests', '{"id":"01JYYDEMOREQSUBMIT000001"}', now() - interval '2 hour'),
  ('01JYYDEMOMSG000000002', 'default', '01MOCKUSER0000000000000USER', 'TODO',     '您有新的审批待办: 深圳样例数据-培训服务采购',      '请尽快处理申请单 BIZ20260714003', 'UNREAD', NULL, 'BIZ_REQUEST', '01JYYDEMOREQSUBMIT000002', '/requests', '{"id":"01JYYDEMOREQSUBMIT000002"}', now() - interval '3 hour'),
  ('01JYYDEMOMSG000000003', 'default', '01MOCKUSER0000000000000USER', 'NOTICE',   '申请单 BIZ20260713001 已审批通过',                  '北京示例科技-服务器采购 已通过', 'READ',   now() - interval '19 hour', 'BIZ_REQUEST', '01JYYDEMOREQAPPROV000001', '/requests', '{"id":"01JYYDEMOREQAPPROV000001"}', now() - interval '20 hour'),
  ('01JYYDEMOMSG000000004', 'default', '01MOCKUSER0000000000000USER', 'NOTICE',   '申请单 BIZ20260711001 已驳回',                      '预算超标，需重新评估',           'READ',   now() - interval '64 hour', 'BIZ_REQUEST', '01JYYDEMOREQREJECT000001', '/requests', '{"id":"01JYYDEMOREQREJECT000001"}', now() - interval '65 hour'),
  ('01JYYDEMOMSG000000005', 'default', '01MOCKUSER0000000000000USER', 'SYSTEM',   '欢迎使用 YuTong v0.9 RC',                            '工作台、AI 助手、低代码平台已就绪', 'UNREAD', NULL, NULL,          NULL,                       '/dashboard', '{}',                                now() - interval '1 day')
ON CONFLICT DO NOTHING;

-- ===== AI 模型供应商 =====
-- 说明：种子数据覆盖主流国内/国际免费、试用及 OpenAI 兼容供应商。
--       完全免 Key 可匿名调用或本地服务的供应商默认 enabled=true；其余需管理员在「AI-供应商管理」填入真实 API Key 后启用。
--       api_key_ref 统一使用 JSON 占位：{"apiKey":""}（Ollama 使用 {"apiKey":"ollama"}）。
INSERT INTO ai_provider (id, tenant_id, provider_code, provider_name, endpoint, api_key_ref, model_list_json, protocol, enabled, priority, timeout_ms, rate_limit_per_min, created_time)
VALUES
  -- 免 Key / 本地服务：默认启用，优先使用
  ('01JYYDEMOAIPROV000001', 'default', 'mock-local',       '本地 Mock 供应商',       'http://localhost:8080/api/v1/ai/mock',  '{"apiKey":""}',       '[{"code":"mock-chat","name":"Mock Chat（本地回显）","contextWindow":4096,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', true,  15,  60000, 600, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000022', 'default', 'ollama',           'Ollama 本地推理',        'http://host.docker.internal:11434/v1',  '{"apiKey":"ollama"}', '[{"code":"qwen2.5:0.5b","name":"Qwen 2.5 0.5B（本地轻量/CPU 可跑）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"llama3.1","name":"Llama 3.1 8B（本地免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"qwen2.5","name":"Qwen 2.5 7B（本地免费）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', true,  4,   60000, 60,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000021', 'default', 'pollinations',     'Pollinations AI',        'https://text.pollinations.ai/openai',   '{"apiKey":""}',       '[{"code":"openai","name":"OpenAI（免 Key 真实免费/不稳定）","contextWindow":8192,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"mistral","name":"Mistral（免 Key 真实免费/不稳定）","contextWindow":8192,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 6,   60000, 60,  now() - interval '1 day'),

  -- 国内主流：需 Key，按稳定性与免费额度排序
  ('01JYYDEMOAIPROV000015', 'default', 'dashscope',        '阿里云百炼 / 通义千问',   'https://dashscope.aliyuncs.com/compatible-mode/v1', '{"apiKey":""}', '[{"code":"qwen-turbo","name":"通义千问 Turbo（免费额度）","contextWindow":131072,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"qwen-plus","name":"通义千问 Plus（免费额度）","contextWindow":131072,"priceInputCny":0.0003,"priceOutputCny":0.0006},{"code":"qwen2.5-72b-instruct","name":"Qwen2.5 72B Instruct（免费额度）","contextWindow":131072,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 20,  60000, 60,  now() - interval '1 day'),
  -- QwenPaw 是 AgentScope 团队开源的本地/私有智能体框架，本身不提供公共 API；此处作为「可本地部署的聚合接入点」模板，用户自行部署后修改 endpoint 与 apiKey 即可使用。
  ('01JYYDEMOAIPROV000002', 'default', 'qwenpaw',          'QwenPaw / AgentScope（本地/私有部署）', 'http://host.docker.internal:8000/v1',   '{"apiKey":""}',       '[{"code":"qwen-turbo","name":"通义千问 Turbo（本地部署推荐）","contextWindow":8192,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"qwen-plus","name":"通义千问 Plus（本地部署推荐）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"qwen2.5-72b-instruct","name":"Qwen2.5 72B Instruct（本地部署推荐）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"qwen3-235b-a22b","name":"Qwen3 235B A22B（本地部署推荐）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"deepseek-chat","name":"DeepSeek-V3（本地部署推荐）","contextWindow":64000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"deepseek-reasoner","name":"DeepSeek-R1（本地部署推荐）","contextWindow":64000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"llama3.1-8b","name":"Llama 3.1 8B（本地部署推荐）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 25,  60000, 60,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000008', 'default', 'deepseek',         'DeepSeek',               'https://api.deepseek.com/v1',           '{"apiKey":""}',       '[{"code":"deepseek-chat","name":"DeepSeek-V3（免费额度）","contextWindow":64000,"priceInputCny":0.0010,"priceOutputCny":0.0020},{"code":"deepseek-reasoner","name":"DeepSeek-R1","contextWindow":64000,"priceInputCny":0.0020,"priceOutputCny":0.0080}]', 'OPENAI_COMPATIBLE', false, 30,  60000, 30,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000005', 'default', 'zhipu',            '智谱 AI',                'https://open.bigmodel.cn/api/paas/v4',  '{"apiKey":""}',       '[{"code":"glm-4-flash","name":"GLM-4-Flash（免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"glm-4-flash-250414","name":"GLM-4-Flash-250414（免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"glm-4-air","name":"GLM-4-Air","contextWindow":128000,"priceInputCny":0.0010,"priceOutputCny":0.0020}]', 'OPENAI_COMPATIBLE', false, 35,  60000, 60,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000012', 'default', 'moonshot',         '月之暗面 Kimi',          'https://api.moonshot.cn/v1',            '{"apiKey":""}',       '[{"code":"moonshot-v1-8k","name":"Kimi V1 8K（免费额度）","contextWindow":8192,"priceInputCny":0.0060,"priceOutputCny":0.0060},{"code":"moonshot-v1-32k","name":"Kimi V1 32K（免费额度）","contextWindow":32768,"priceInputCny":0.0060,"priceOutputCny":0.0060},{"code":"kimi-k2-0711-preview","name":"Kimi K2（免费额度）","contextWindow":256000,"priceInputCny":0.0060,"priceOutputCny":0.0060}]', 'OPENAI_COMPATIBLE', false, 40,  60000, 30,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000013', 'default', 'hunyuan',          '腾讯混元',               'https://api.hunyuan.cloud.tencent.com/v1', '{"apiKey":""}',    '[{"code":"hunyuan-lite","name":"混元 Lite（免费）","contextWindow":256000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"hunyuan-standard","name":"混元 Standard","contextWindow":32000,"priceInputCny":0.0040,"priceOutputCny":0.0040}]', 'OPENAI_COMPATIBLE', false, 45,  60000, 30,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000016', 'default', 'wenxin',           '百度千帆 / 文心一言',     'https://qianfan.baidubce.com/v2',       '{"apiKey":""}',       '[{"code":"ernie-speed-128k","name":"文心 Speed（免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"ernie-4.0-turbo-8k","name":"文心 4.0 Turbo","contextWindow":8192,"priceInputCny":0.0080,"priceOutputCny":0.0080}]', 'OPENAI_COMPATIBLE', false, 50,  60000, 30,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000017', 'default', 'spark',            '讯飞星火',               'https://spark-api-open.xf-yun.com/v1',  '{"apiKey":""}',       '[{"code":"lite","name":"星火 Lite（免费）","contextWindow":8192,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"4.0Ultra","name":"星火 4.0 Ultra","contextWindow":8192,"priceInputCny":0.0040,"priceOutputCny":0.0040},{"code":"pro-128k","name":"星火 Pro 128K","contextWindow":131072,"priceInputCny":0.0040,"priceOutputCny":0.0040}]', 'OPENAI_COMPATIBLE', false, 55,  60000, 30,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000003', 'default', 'siliconflow',      '硅基流动 SiliconFlow',   'https://api.siliconflow.cn/v1',         '{"apiKey":""}',       '[{"code":"Qwen/Qwen2.5-7B-Instruct","name":"Qwen2.5 7B（免费）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"deepseek-ai/DeepSeek-R1-0528-Qwen3-8B","name":"DeepSeek-R1-Qwen3-8B（免费）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"Qwen/Qwen3-235B-A22B","name":"Qwen3-235B-A22B","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"deepseek-ai/DeepSeek-V3","name":"DeepSeek V3","contextWindow":64000,"priceInputCny":0.0010,"priceOutputCny":0.0010},{"code":"moonshotai/Kimi-K2-Thinking","name":"Kimi-K2-Thinking","contextWindow":32768,"priceInputCny":0.0120,"priceOutputCny":0.0300}]', 'OPENAI_COMPATIBLE', false, 60,  60000, 30,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000026', 'default', 'minimax',          'MiniMax',                'https://api.minimax.chat/v1',           '{"apiKey":""}',       '[{"code":"abab6.5s-chat","name":"ABAB 6.5s Chat（免费额度）","contextWindow":245760,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"abab6.5t-chat","name":"ABAB 6.5t Chat","contextWindow":8192,"priceInputCny":0.0020,"priceOutputCny":0.0020}]', 'OPENAI_COMPATIBLE', false, 65,  60000, 60,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000014', 'default', 'baichuan',         '百川智能',               'https://api.baichuan-ai.com/v1',        '{"apiKey":""}',       '[{"code":"Baichuan4-Air","name":"百川4-Air（免费额度）","contextWindow":256000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"Baichuan3-Turbo","name":"百川3-Turbo","contextWindow":32000,"priceInputCny":0.0030,"priceOutputCny":0.0030}]', 'OPENAI_COMPATIBLE', false, 70,  60000, 30,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000043', 'default', 'stepfun',          '阶跃星辰 Stepfun',       'https://api.stepfun.com/v1',            '{"apiKey":""}',       '[{"code":"step-1-flash","name":"Step-1-Flash（免费）","contextWindow":8000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"step-1-8k","name":"Step-1 8K","contextWindow":8192,"priceInputCny":0.0010,"priceOutputCny":0.0020}]', 'OPENAI_COMPATIBLE', false, 75,  60000, 30,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000037', 'default', 'doubao-volcengine','火山方舟 / 豆包',        'https://ark.cn-beijing.volces.com/api/v3','{"apiKey":""}',      '[{"code":"doubao-1.5-lite-32k","name":"豆包 1.5 Lite 32K（免费额度）","contextWindow":32000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"doubao-1.5-pro-32k","name":"豆包 1.5 Pro 32K（免费额度）","contextWindow":32000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 80,  60000, 30,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000048', 'default', 'sensechat',        '商汤日日新 / SenseChat',  'https://api.sensenova.cn/v1',           '{"apiKey":""}',       '[{"code":"SenseChat-Turbo","name":"SenseChat Turbo（免费额度）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"SenseChat-5","name":"SenseChat 5","contextWindow":32768,"priceInputCny":0.0040,"priceOutputCny":0.0040}]', 'OPENAI_COMPATIBLE', false, 85,  60000, 30,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000047', 'default', 'yi-ai',            '零一万物 / 01.AI',       'https://api.01.ai/v1',                  '{"apiKey":""}',       '[{"code":"yi-lightning","name":"Yi Lightning（免费额度）","contextWindow":16384,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"yi-medium","name":"Yi Medium","contextWindow":16384,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 90,  60000, 20,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000049', 'default', 'skywork',          '天工 / Skywork',         'https://openapi.skywork.com/api/v1',    '{"apiKey":""}',       '[{"code":"skywork-chat","name":"天工 Chat（免费额度）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"skywork-reasoner","name":"天工 Reasoner","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 95,  60000, 20,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000050', 'default', 'tencent-cloud-lke','腾讯云 LKE / 知识引擎',   'https://lke.tencentcloudapi.com/v1',    '{"apiKey":""}',       '[{"code":"lke-lite","name":"LKE Lite（免费额度）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"lke-pro","name":"LKE Pro","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 100, 60000, 20,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000051', 'default', 'alibaba-cloud-intl','阿里云国际 / Bailian',   'https://bailian-api-intl.aliyuncs.com/v1','{"apiKey":""}',     '[{"code":"qwen-turbo","name":"通义千问 Turbo（免费额度）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"qwen-plus","name":"通义千问 Plus（免费额度）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 105, 60000, 20,  now() - interval '1 day'),

  -- 国际/开源：需 Key，按稳定性与免费额度排序
  ('01JYYDEMOAIPROV000004', 'default', 'openrouter',       'OpenRouter',             'https://openrouter.ai/api/v1',          '{"apiKey":""}',       '[{"code":"meta-llama/llama-3.1-8b-instruct:free","name":"Llama 3.1 8B（免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"google/gemini-flash-1.5:free","name":"Gemini Flash 1.5（免费）","contextWindow":1048576,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"stepfun/step-3.5-flash:free","name":"阶跃 Step-3.5-flash（免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"openai/gpt-4o-mini","name":"GPT-4o mini","contextWindow":128000,"priceInputCny":0.0015,"priceOutputCny":0.0060},{"code":"anthropic/claude-3.5-haiku","name":"Claude 3.5 Haiku","contextWindow":200000,"priceInputCny":0.0010,"priceOutputCny":0.0050}]', 'OPENAI_COMPATIBLE', false, 110, 60000, 20,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000006', 'default', 'groq',             'Groq',                   'https://api.groq.com/openai/v1',        '{"apiKey":""}',       '[{"code":"llama-3.3-70b-versatile","name":"Llama 3.3 70B Versatile（免费额度）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"llama-3.1-8b-instant","name":"Llama 3.1 8B Instant（免费额度）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"mixtral-8x7b-32768","name":"Mixtral 8x7B","contextWindow":32768,"priceInputCny":0.0005,"priceOutputCny":0.0005},{"code":"gemma2-9b-it","name":"Gemma2 9B IT","contextWindow":8192,"priceInputCny":0.0005,"priceOutputCny":0.0005}]', 'OPENAI_COMPATIBLE', false, 115, 60000, 30,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000007', 'default', 'github-models',    'GitHub Models',          'https://models.inference.ai.azure.com', '{"apiKey":""}',       '[{"code":"gpt-4o-mini","name":"GPT-4o mini（免费额度）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"meta-llama-3.1-8b-instruct","name":"Llama 3.1 8B（免费额度）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"mistral-small","name":"Mistral Small（免费额度）","contextWindow":32000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 120, 60000, 15,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000011', 'default', 'google-gemini',    'Google Gemini',          'https://generativelanguage.googleapis.com/v1beta/openai', '{"apiKey":""}', '[{"code":"gemini-2.0-flash-lite","name":"Gemini 2.0 Flash Lite（免费额度）","contextWindow":1000000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"gemini-1.5-flash","name":"Gemini 1.5 Flash","contextWindow":1000000,"priceInputCny":0.0007,"priceOutputCny":0.0021}]', 'OPENAI_COMPATIBLE', false, 125, 60000, 60,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000009', 'default', 'mistral',          'Mistral AI',             'https://api.mistral.ai/v1',             '{"apiKey":""}',       '[{"code":"mistral-tiny","name":"Mistral Tiny（免费试用）","contextWindow":32000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"open-mistral-nemo","name":"Open Mistral Nemo","contextWindow":128000,"priceInputCny":0.0003,"priceOutputCny":0.0003}]', 'OPENAI_COMPATIBLE', false, 130, 60000, 30,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000010', 'default', 'cohere',           'Cohere',                 'https://api.cohere.ai/compatibility/v1','{"apiKey":""}',       '[{"code":"command-r7b-12-2024","name":"Command R7B（免费试用）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"command-r","name":"Command R","contextWindow":128000,"priceInputCny":0.0010,"priceOutputCny":0.0020}]', 'OPENAI_COMPATIBLE', false, 135, 60000, 20,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000020', 'default', 'nvidia-nim',       'NVIDIA NIM',             'https://integrate.api.nvidia.com/v1',   '{"apiKey":""}',       '[{"code":"meta/llama-3.1-8b-instruct","name":"Llama 3.1 8B（免费额度）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"mistralai/mistral-7b-instruct-v0.3","name":"Mistral 7B（免费额度）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 140, 60000, 20,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000023', 'default', 'together-ai',      'Together AI',            'https://api.together.xyz/v1',           '{"apiKey":""}',       '[{"code":"meta-llama/Llama-3.3-70B-Instruct-Turbo-Free","name":"Llama 3.3 70B Instruct Turbo Free","contextWindow":131072,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"meta-llama/Meta-Llama-3.1-8B-Instruct-Turbo","name":"Llama 3.1 8B Instruct Turbo（新用户试用额度）","contextWindow":131072,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 145, 60000, 20,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000027', 'default', 'fireworks-ai',     'Fireworks AI',           'https://api.fireworks.ai/inference/v1', '{"apiKey":""}',       '[{"code":"accounts/fireworks/models/llama-v3p1-8b-instruct","name":"Llama 3.1 8B Instruct（新用户试用额度）","contextWindow":131072,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"accounts/fireworks/models/mixtral-8x7b-instruct","name":"Mixtral 8x7B Instruct（新用户试用额度）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 150, 60000, 20,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000028', 'default', 'ai21',             'AI21 Labs',              'https://api.ai21.com/studio/v1',        '{"apiKey":""}',       '[{"code":"jamba-1.5-mini","name":"Jamba 1.5 Mini（新用户试用额度）","contextWindow":256000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"jamba-1.5-large","name":"Jamba 1.5 Large（新用户试用额度）","contextWindow":256000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 155, 60000, 20,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000033', 'default', 'cerebras',         'Cerebras',               'https://api.cerebras.ai/v1',            '{"apiKey":""}',       '[{"code":"llama3.1-8b","name":"Llama 3.1 8B（免费额度）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"llama-3.3-70b","name":"Llama 3.3 70B（免费额度）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 160, 60000, 30,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000034', 'default', 'sambanova',        'SambaNova',              'https://api.sambanova.ai/v1',           '{"apiKey":""}',       '[{"code":"Meta-Llama-3.1-8B-Instruct","name":"Llama 3.1 8B（免费额度）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"Meta-Llama-3.1-70B-Instruct","name":"Llama 3.1 70B（免费额度）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 165, 60000, 20,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000018', 'default', 'hugging-face',     'Hugging Face',           'https://api-inference.huggingface.co/v1','{"apiKey":""}',      '[{"code":"meta-llama/Meta-Llama-3.1-8B-Instruct","name":"Llama 3.1 8B Instruct（免费额度）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"mistralai/Mistral-7B-Instruct-v0.3","name":"Mistral 7B（免费额度）","contextWindow":32000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 170, 60000, 20,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000019', 'default', 'cloudflare-ai',    'Cloudflare Workers AI',  'https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/v1', '{"apiKey":""}', '[{"code":"@cf/meta/llama-3.1-8b-instruct","name":"Llama 3.1 8B（免费额度/需替换 account_id）","contextWindow":8192,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"@cf/mistral/mistral-7b-instruct-v0.1","name":"Mistral 7B（免费额度/需替换 account_id）","contextWindow":8192,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 175, 60000, 20,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000032', 'default', 'hyperbolic',       'Hyperbolic',             'https://api.hyperbolic.xyz/v1',         '{"apiKey":""}',       '[{"code":"meta-llama/Meta-Llama-3.1-8B-Instruct","name":"Llama 3.1 8B（免费额度）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"meta-llama/Meta-Llama-3.1-70B-Instruct","name":"Llama 3.1 70B（免费额度）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 180, 60000, 20,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000024', 'default', 'nebius',           'Nebius',                 'https://api.nebius.com/v1',             '{"apiKey":""}',       '[{"code":"meta-llama/Llama-3.1-8B-Instruct","name":"Llama 3.1 8B（免费额度）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"meta-llama/Llama-3.1-70B-Instruct","name":"Llama 3.1 70B（免费额度）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 185, 60000, 20,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000030', 'default', 'upstage',          'Upstage',                'https://api.upstage.ai/v1/solar',       '{"apiKey":""}',       '[{"code":"solar-1-mini-chat","name":"Solar 1 Mini Chat（免费额度）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"solar-pro","name":"Solar Pro（免费额度）","contextWindow":4096,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 190, 60000, 20,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000035', 'default', 'scaleway-generative','Scaleway Generative APIs','https://api.scaleway.ai/v1',           '{"apiKey":""}',       '[{"code":"llama-3.1-8b-instruct","name":"Llama 3.1 8B Instruct（免费额度）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"llama-3.1-70b-instruct","name":"Llama 3.1 70B Instruct（免费额度）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 195, 60000, 20,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000036', 'default', 'infinigence-ai',   '无问芯穹',               'https://cloud.infini-ai.com/maas/v1',   '{"apiKey":""}',       '[{"code":"deepseek-r1","name":"DeepSeek-R1（免费额度）","contextWindow":64000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"deepseek-v3","name":"DeepSeek-V3（免费额度）","contextWindow":64000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 200, 60000, 20,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000025', 'default', 'modelscope',       'ModelScope',             'https://api-inference.modelscope.cn/v1','{"apiKey":""}',       '[{"code":"qwen/Qwen3-235B-A22B","name":"Qwen3-235B-A22B（免费）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"deepseek-ai/DeepSeek-R1-0528-Qwen3-8B","name":"DeepSeek-R1-0528-Qwen3-8B（免费）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 205, 60000, 20,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000031', 'default', 'chatanywhere',     'ChatAnywhere',           'https://api.chatanywhere.tech/v1',      '{"apiKey":""}',       '[{"code":"gpt-4o-mini","name":"GPT-4o mini（免费额度）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"deepseek-chat","name":"DeepSeek-V3（免费额度）","contextWindow":64000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 210, 60000, 20,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000029', 'default', 'openai',           'OpenAI',                 'https://api.openai.com/v1',             '{"apiKey":""}',       '[{"code":"gpt-4o-mini","name":"GPT-4o mini（新用户试用额度）","contextWindow":128000,"priceInputCny":0.0015,"priceOutputCny":0.0060},{"code":"gpt-3.5-turbo","name":"GPT-3.5 Turbo","contextWindow":16385,"priceInputCny":0.0030,"priceOutputCny":0.0060}]', 'OPENAI_COMPATIBLE', false, 215, 60000, 15,  now() - interval '1 day'),
  -- 2026-07 新增：市场免费/公益额度供应商（默认禁用，需用户填入 apiKey 后启用）
  ('01JYYDEMOAIPROV000052', 'default', 'agnes-ai',         'Agnes AI（全模态免费额度）', 'https://apihub.agnes-ai.com/v1',        '{"apiKey":""}',       '[{"code":"agnes-2.0-flash","name":"Agnes 2.0 Flash（文本免费）","contextWindow":1048576,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 216, 60000, 30,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000053', 'default', 'ai-ping',          'AI Ping（免费模型聚合）', 'https://www.aiping.cn/api/v1',          '{"apiKey":""}',       '[{"code":"minimax-m2.1","name":"MiniMax-M2.1（免费额度）","contextWindow":245760,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"glm-4.7","name":"GLM-4.7（免费额度）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 217, 60000, 30,  now() - interval '1 day'),
  -- 2026-07 追加：国际/开源免费额度供应商
  ('01JYYDEMOAIPROV000054', 'default', 'novita-ai',        'Novita AI',              'https://api.novita.ai/v1',              '{"apiKey":""}',       '[{"code":"meta-llama/llama-3.1-8b-instruct","name":"Llama 3.1 8B Instruct（免费额度）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"deepseek-ai/DeepSeek-V3-0324","name":"DeepSeek-V3（免费额度）","contextWindow":64000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 218, 60000, 30,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000055', 'default', 'perplexity',       'Perplexity',             'https://api.perplexity.ai',             '{"apiKey":""}',       '[{"code":"sonar","name":"Sonar（免费额度）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"sonar-reasoning","name":"Sonar Reasoning（免费额度）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"sonar-pro","name":"Sonar Pro（免费额度）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 219, 60000, 20,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000056', 'default', 'xai-grok',         'xAI Grok',               'https://api.x.ai/v1',                   '{"apiKey":""}',       '[{"code":"grok-2","name":"Grok 2（免费额度）","contextWindow":131072,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"grok-2-mini","name":"Grok 2 Mini（免费额度）","contextWindow":131072,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"grok-beta","name":"Grok Beta（免费额度）","contextWindow":131072,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 220, 60000, 20,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000057', 'default', 'lambda',           'Lambda',                 'https://api.lambda.ai/v1',              '{"apiKey":""}',       '[{"code":"llama3.1-8b","name":"Llama 3.1 8B（免费额度）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"hermes3-8b","name":"Hermes 3 8B（免费额度）","contextWindow":8192,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 221, 60000, 30,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000058', 'default', 'friendliai',       'FriendliAI',             'https://inference.friendli.ai/v1',      '{"apiKey":""}',       '[{"code":"meta-llama-3.1-8b-instruct","name":"Llama 3.1 8B Instruct（免费试用）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"mistral-8x7b-instruct","name":"Mistral 8x7B Instruct（免费试用）","contextWindow":32768,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 222, 60000, 20,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000059', 'default', 'kluster-ai',       'Kluster AI',             'https://api.kluster.ai/v1',             '{"apiKey":""}',       '[{"code":"meta-llama/Llama-3.1-8B-Instruct","name":"Llama 3.1 8B Instruct（免费额度）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"deepseek-ai/DeepSeek-V3","name":"DeepSeek-V3（免费额度）","contextWindow":64000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 223, 60000, 30,  now() - interval '1 day'),
  ('01JYYDEMOAIPROV000060', 'default', 'chutes-ai',        'Chutes AI',              'https://llm.chutes.ai/v1',              '{"apiKey":""}',       '[{"code":"meta-llama/Llama-3.1-8B-Instruct","name":"Llama 3.1 8B Instruct（免费额度）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"chutesai/Llama-4-Scout-17B-16E-Instruct","name":"Llama 4 Scout（免费额度）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 224, 60000, 20, now() - interval '1 day'),

  -- 2026-07 追加：当前市场上仍缺失的知名免费/试用额度 LLM API 供应商
  ('01JYYDEMOAIPROV000061', 'default', 'ppio',             'PPIO 派欧云',            'https://api.ppinfra.com/v3/openai',     '{"apiKey":""}',       '[{"code":"deepseek/deepseek-r1/community","name":"DeepSeek-R1（社区免费额度）","contextWindow":64000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"deepseek/deepseek-v3/community","name":"DeepSeek-V3（社区免费额度）","contextWindow":64000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 225, 60000, 30, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000062', 'default', 'vercel-ai-gateway','Vercel AI Gateway',      'https://ai-gateway.vercel.sh/v1',       '{"apiKey":""}',       '[{"code":"openai/gpt-4o-mini","name":"GPT-4o mini（按量/BYOK）","contextWindow":128000,"priceInputCny":0.0015,"priceOutputCny":0.0060},{"code":"anthropic/claude-3-5-haiku","name":"Claude 3.5 Haiku（按量/BYOK）","contextWindow":200000,"priceInputCny":0.0010,"priceOutputCny":0.0050},{"code":"google/gemini-1.5-flash","name":"Gemini 1.5 Flash（按量/BYOK）","contextWindow":1000000,"priceInputCny":0.0007,"priceOutputCny":0.0021}]', 'OPENAI_COMPATIBLE', false, 226, 60000, 20, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000063', 'default', 'kilo-gateway',     'Kilo Gateway',           'https://api.kilo.ai/api/gateway',       '{"apiKey":""}',       '[{"code":"kilo/auto-free","name":"Kilo Auto Free（免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"openrouter/free","name":"OpenRouter Free Models（免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"poolside/laguna-s-2.1","name":"Poolside Laguna S 2.1（免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 227, 60000, 30, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000064', 'default', 'opencode-zen',     'OpenCode Zen',           'https://opencode.ai/zen/v1',            '{"apiKey":""}',       '[{"code":"big-pickle","name":"Big Pickle（免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"deepseek-v4-flash-free","name":"DeepSeek V4 Flash Free（免费）","contextWindow":64000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"mimo-v2.5-free","name":"MiMo-V2.5 Free（免费）","contextWindow":128000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"kimi-k2.5","name":"Kimi K2.5（按量）","contextWindow":256000,"priceInputCny":0.0060,"priceOutputCny":0.0060}]', 'OPENAI_COMPATIBLE', false, 228, 60000, 20, now() - interval '1 day'),
  ('01JYYDEMOAIPROV000065', 'default', 'baseten',          'Baseten',                'https://inference.baseten.co/v1',       '{"apiKey":""}',       '[{"code":"deepseek-ai/DeepSeek-V4-Pro","name":"DeepSeek-V4-Pro（试用额度）","contextWindow":262000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"zai-org/GLM-4.7","name":"GLM 4.7（试用额度）","contextWindow":200000,"priceInputCny":0.0000,"priceOutputCny":0.0000},{"code":"moonshotai/Kimi-K2.6","name":"Kimi K2.6（试用额度）","contextWindow":262000,"priceInputCny":0.0000,"priceOutputCny":0.0000}]', 'OPENAI_COMPATIBLE', false, 229, 60000, 20, now() - interval '1 day')
ON CONFLICT (tenant_id, provider_code) WHERE deleted = false DO UPDATE SET
    provider_name = EXCLUDED.provider_name,
    endpoint = EXCLUDED.endpoint,
    api_key_ref = EXCLUDED.api_key_ref,
    model_list_json = EXCLUDED.model_list_json,
    protocol = EXCLUDED.protocol,
    enabled = EXCLUDED.enabled,
    priority = EXCLUDED.priority,
    timeout_ms = EXCLUDED.timeout_ms,
    rate_limit_per_min = EXCLUDED.rate_limit_per_min,
    updated_time = now();

-- ===== AI Prompt 模板 =====
INSERT INTO ai_prompt_template (id, tenant_id, template_code, version_no, scenario, input_schema, output_schema, prompt_text, safety_rules, status, created_time)
VALUES
  ('01JYYDEMOPROMPT000001', 'default', 'customer-qa',         1, 'CUSTOMER_QA',     '{"question":"string","customerId":"string"}', '{"answer":"string","citations":"array"}', '你是 YuTong 平台的客户服务助手。基于知识库回答客户问题。\n\n客户问题: {{question}}\n\n要求:\n1. 仅基于知识库内容回答\n2. 不确定时明确说明\n3. 引用来源', '["禁止泄露客户隐私","禁止承诺折扣"]', 'PUBLISHED', now() - interval '1 day'),
  ('01JYYDEMOPROMPT000002', 'default', 'product-recommend', 1, 'PRODUCT_RECOMMEND', '{"need":"string","budget":"number"}',     '{"recommendations":"array"}',            '你是 YuTong 平台的商品推荐助手。根据用户需求推荐合适商品。\n\n用户需求: {{need}}\n预算: {{budget}}\n\n要求:\n1. 推荐 3 个以内商品\n2. 说明推荐理由\n3. 总价不超过预算', '["禁止虚构商品","禁止价格欺诈"]', 'PUBLISHED', now() - interval '1 day')
ON CONFLICT DO NOTHING;

-- ===== AI 知识库 =====
INSERT INTO ai_knowledge_base (id, tenant_id, kb_code, kb_name, description, acl_policy_json, embedding_model, visibility, permission_code, sensitivity_level, status, owner_user_id, created_time)
VALUES
  ('01JYYDEMOAIKB00000001', 'default', 'platform-faq', '平台常见问题知识库', 'YuTong 平台使用常见问题与解答', '{"read":["admin"],"write":["admin"]}', 'local-hash-bow-1536', 'PRIVATE', 'kb:platform-faq', 'INTERNAL', 'ACTIVE', '01MOCKUSER0000000000000USER', now() - interval '1 day')
ON CONFLICT DO NOTHING;

-- ===== AI 文档 =====
INSERT INTO ai_document (id, tenant_id, kb_id, file_id, doc_title, source_type, source_uri, visibility, permission_code, sensitivity_level, document_status, chunk_count, error_message, indexed_time, created_time)
VALUES
  ('01JYYDEMOAIDOC000001', 'default', '01JYYDEMOAIKB00000001', NULL, 'YuTong 平台快速入门', 'TEXT', 'inline://quick-start', 'PRIVATE', 'kb:platform-faq', 'INTERNAL', 'READY', 2, NULL, now() - interval '1 day', now() - interval '1 day'),
  ('01JYYDEMOAIDOC000002', 'default', '01JYYDEMOAIKB00000001', NULL, 'YuTong 平台 FAQ',     'TEXT', 'inline://faq',         'PRIVATE', 'kb:platform-faq', 'INTERNAL', 'READY', 2, NULL, now() - interval '1 day', now() - interval '1 day')
ON CONFLICT DO NOTHING;

-- ===== AI 文档分块 (供 RAG 向量检索测试) =====
INSERT INTO ai_document_chunk (id, tenant_id, knowledge_base_id, document_id, chunk_no, chunk_text, chunk_hash, token_count, section_path, permission_code, data_scope, sensitivity_level, acl_tags_json, created_time)
VALUES
  ('01JYYDEMOCHUNK000001', 'default', '01JYYDEMOAIKB00000001', '01JYYDEMOAIDOC000001', 1, 'YuTong 平台是面向企业的全栈技术底座，提供工作台、客户管理、商品管理、申请单审批、AI 助手、低代码平台等核心能力。', 'hash-quick-start-1', 64, '/quick-start/intro', 'kb:platform-faq', 'TENANT', 'INTERNAL', '["admin"]', now() - interval '1 day'),
  ('01JYYDEMOCHUNK000002', 'default', '01JYYDEMOAIKB00000001', '01JYYDEMOAIDOC000001', 2, 'YuTong 平台采用 Java 25 + Spring Boot 4 + Vue 3 技术栈，使用 PostgreSQL 18 与 pgvector 向量数据库，支持多租户与细粒度权限控制。', 'hash-quick-start-2', 72, '/quick-start/tech', 'kb:platform-faq', 'TENANT', 'INTERNAL', '["admin"]', now() - interval '1 day'),
  ('01JYYDEMOCHUNK000003', 'default', '01JYYDEMOAIKB00000001', '01JYYDEMOAIDOC000002', 1, '如何创建申请单? 在工作台点击"申请单管理"，进入列表页后点击"新建申请单"，填写客户、商品和数量后提交即可进入审批流程。', 'hash-faq-1', 58, '/faq/create-request', 'kb:platform-faq', 'TENANT', 'INTERNAL', '["admin"]', now() - interval '1 day'),
  ('01JYYDEMOCHUNK000004', 'default', '01JYYDEMOAIKB00000001', '01JYYDEMOAIDOC000002', 2, '如何使用 AI 助手? 在左侧菜单点击"AI 助手"进入对话页面，可以基于知识库提问。AI 助手会通过 RAG 检索相关知识并给出带引用的回答。', 'hash-faq-2', 62, '/faq/ai-assistant', 'kb:platform-faq', 'TENANT', 'INTERNAL', '["admin"]', now() - interval '1 day')
ON CONFLICT DO NOTHING;

-- ===== 低代码实体 =====
INSERT INTO lc_entity (id, tenant_id, entity_code, entity_name, table_name, module_code, version_no, schema_version, config_hash, status, owner_user_id, created_time)
VALUES
  ('01JYYDEMOLCENT000001', 'default', 'project', '项目', 'lc_dyn_project', 'biz', 1, '1.0', 'hash-project-v1', 'PUBLISHED', '01MOCKUSER0000000000000USER', now() - interval '5 day'),
  ('01JYYDEMOLCENT000002', 'default', 'contract', '合同', 'lc_dyn_contract', 'biz', 1, '1.0', 'hash-contract-v1', 'DRAFT',     '01MOCKUSER0000000000000USER', now() - interval '2 day'),
  ('01JYYDEMOLCENT000003', 'default', 'ticket', '工单', 'lc_dyn_ticket', 'biz', 1, '1.0', 'hash-ticket-v1', 'DRAFT',     '01MOCKUSER0000000000000USER', now() - interval '1 day')
ON CONFLICT DO NOTHING;

-- ===== GA2-L178: 68 号文档样例数据包批量扩展 (20 客户/50 商品/100 申请单/50 消息) =====
-- 设计来源: 68-演示环境与样例数据剧本详设 line 39-53
-- 使用 generate_series 批量生成，保证可重复执行 (ON CONFLICT DO NOTHING)
-- ID 约定: 26 字符固定串，前缀 01JYYDEMO + 业务标识 + 序号

-- ===== Demo 客户 (追加 17 个，与 sample 3 个合计 20 个) =====
-- 覆盖启用/禁用状态 (68 号文档 line 43)
INSERT INTO biz_customer (id, tenant_id, customer_code, customer_name, contact_name, contact_phone, address, status, created_time)
SELECT
  '01JYYDEMOCLI' || lpad(g.n::text, 13, '0'),
  'default',
  'C' || lpad((g.n + 3)::text, 3, '0'),
  '演示客户' || g.n || '号',
  '联系人' || g.n,
  '1390000' || lpad((g.n + 1000)::text, 4, '0'),
  CASE (g.n % 3)
    WHEN 0 THEN '北京市朝阳区演示路' || g.n || '号'
    WHEN 1 THEN '上海市静安区展示街' || g.n || '号'
    ELSE '广州市天河区样板大道' || g.n || '号'
  END,
  CASE WHEN (g.n % 5) = 0 THEN 'DISABLED' ELSE 'ENABLED' END,
  now() - (g.n || ' hour')::interval
FROM generate_series(1, 17) AS g(n)
ON CONFLICT DO NOTHING;

-- ===== Demo 商品 (追加 46 个，与 sample 4 个合计 50 个) =====
-- 不同单位和价格 (68 号文档 line 44)
INSERT INTO biz_product (id, tenant_id, product_code, product_name, unit, price, status, created_time)
SELECT
  '01JYYDEMOPROD' || lpad(g.n::text, 12, '0'),
  'default',
  'P' || lpad((g.n + 4)::text, 3, '0'),
  CASE (g.n % 4)
    WHEN 0 THEN '演示软件授权包' || g.n
    WHEN 1 THEN '演示咨询服务' || g.n
    WHEN 2 THEN '演示培训课程' || g.n
    ELSE '演示硬件设备' || g.n
  END,
  CASE (g.n % 4)
    WHEN 0 THEN 'SET'
    WHEN 1 THEN 'DAY'
    WHEN 2 THEN 'SESSION'
    ELSE 'PCS'
  END,
  500.00 + (g.n * 137.50),
  CASE WHEN (g.n % 7) = 0 THEN 'DISABLED' ELSE 'ENABLED' END,
  now() - (g.n || ' hour')::interval
FROM generate_series(1, 46) AS g(n)
ON CONFLICT DO NOTHING;

-- ===== Demo 申请单 (追加 93 个，与现有 7 个合计 100 个) =====
-- 分布在 DRAFT/SUBMITTED/APPROVED/REJECTED/ARCHIVED 五种状态 (68 号文档 line 45)
-- 客户引用: 轮流使用 sample 客户 + demo 客户
-- 商品引用: 轮流使用 sample 商品 + demo 商品
INSERT INTO biz_request (
  id, tenant_id, request_no, title, customer_id, customer_name_snapshot, apply_reason,
  request_status, total_amount, applicant_id, applicant_name_snapshot,
  owner_user_id, owner_dept_id, owner_dept_path,
  submitted_time, approved_time, archived_time, created_time)
SELECT
  '01JYYDEMOREQ' || lpad(g.n::text, 13, '0'),
  'default',
  'BIZ202607' || lpad((g.n + 10)::text, 4, '0'),
  '演示申请单-' || g.n || '-业务采购',
  CASE (g.n % 3)
    WHEN 0 THEN '01JYYYYYYCUSTSAMPLE00001'
    WHEN 1 THEN '01JYYYYYYCUSTSAMPLE00002'
    ELSE '01JYYYYYYCUSTSAMPLE00003'
  END,
  CASE (g.n % 3)
    WHEN 0 THEN '北京示例科技有限公司'
    WHEN 1 THEN '上海示范贸易有限公司'
    ELSE '深圳样例数据股份公司'
  END,
  '演示用途采购申请 ' || g.n,
  CASE (g.n % 5)
    WHEN 0 THEN 'DRAFT'
    WHEN 1 THEN 'SUBMITTED'
    WHEN 2 THEN 'APPROVED'
    WHEN 3 THEN 'REJECTED'
    ELSE 'ARCHIVED'
  END,
  1000.00 + (g.n * 523.75),
  '01MOCKUSER0000000000000USER',
  '系统管理员',
  '01MOCKUSER0000000000000USER',
  'dept-001',
  '/corp/sales',
  CASE (g.n % 5)
    WHEN 0 THEN NULL
    WHEN 1 THEN now() - (g.n || ' hour')::interval
    WHEN 2 THEN now() - (g.n || ' hour')::interval
    WHEN 3 THEN now() - (g.n || ' hour')::interval
    ELSE now() - (g.n || ' hour')::interval
  END,
  CASE (g.n % 5)
    WHEN 2 THEN now() - ((g.n - 1) || ' hour')::interval
    WHEN 3 THEN now() - ((g.n - 1) || ' hour')::interval
    ELSE NULL
  END,
  CASE (g.n % 5)
    WHEN 4 THEN now() - ((g.n - 2) || ' hour')::interval
    ELSE NULL
  END,
  now() - (g.n || ' hour')::interval
FROM generate_series(1, 93) AS g(n)
ON CONFLICT DO NOTHING;

-- ===== Demo 申请单明细 (每个申请单 1-2 条明细) =====
INSERT INTO biz_request_item (id, tenant_id, request_id, product_id, product_code_snapshot, product_name_snapshot, unit, quantity, unit_price, line_amount, sort_no, created_time)
SELECT
  '01JYYDEMOITM' || lpad(g.n::text, 13, '0'),
  'default',
  '01JYYDEMOREQ' || lpad(g.n::text, 13, '0'),
  CASE (g.n % 4)
    WHEN 0 THEN '01JYYYYYYPRODSAMPLE00001'
    WHEN 1 THEN '01JYYYYYYPRODSAMPLE00002'
    WHEN 2 THEN '01JYYYYYYPRODSAMPLE00003'
    ELSE '01JYYYYYYPRODSAMPLE00004'
  END,
  CASE (g.n % 4)
    WHEN 0 THEN 'P001'
    WHEN 1 THEN 'P002'
    WHEN 2 THEN 'P003'
    ELSE 'P004'
  END,
  CASE (g.n % 4)
    WHEN 0 THEN '企业版授权 (年)'
    WHEN 1 THEN '实施服务 (人天)'
    WHEN 2 THEN '培训服务 (次)'
    ELSE '服务器 (台)'
  END,
  CASE (g.n % 4)
    WHEN 0 THEN 'PCS'
    WHEN 1 THEN 'PCS'
    WHEN 2 THEN 'PCS'
    ELSE 'BOX'
  END,
  (g.n % 5)::numeric + 1.0,
  CASE (g.n % 4)
    WHEN 0 THEN 98000.00
    WHEN 1 THEN 2500.00
    WHEN 2 THEN 8000.00
    ELSE 18000.00
  END,
  ((g.n % 5)::numeric + 1.0) *
  CASE (g.n % 4)
    WHEN 0 THEN 98000.00
    WHEN 1 THEN 2500.00
    WHEN 2 THEN 8000.00
    ELSE 18000.00
  END,
  1,
  now() - (g.n || ' hour')::interval
FROM generate_series(1, 93) AS g(n)
ON CONFLICT DO NOTHING;

-- ===== Demo 站内消息 (追加 45 个，与现有 5 个合计 50 个) =====
-- 未读/已读混合 (68 号文档 line 47)
-- 注意: target_params 列为 jsonb 类型，字符串拼接需显式 ::jsonb cast
INSERT INTO sys_message (id, tenant_id, receiver_id, msg_type, title, content, read_status, read_time, biz_type, biz_id, target_route_id, target_params, created_time)
SELECT
  '01JYYDEMOMSG' || lpad((g.n + 5)::text, 12, '0'),
  'default',
  '01MOCKUSER0000000000000USER',
  CASE (g.n % 3)
    WHEN 0 THEN 'TODO'
    WHEN 1 THEN 'NOTICE'
    ELSE 'SYSTEM'
  END,
  CASE (g.n % 3)
    WHEN 0 THEN '演示待办: 申请单 ' || g.n || ' 待审批'
    WHEN 1 THEN '演示通知: 申请单 ' || g.n || ' 状态更新'
    ELSE '演示系统消息 ' || g.n
  END,
  '这是演示消息内容 ' || g.n || '，用于展示消息中心功能。',
  CASE WHEN (g.n % 2) = 0 THEN 'READ' ELSE 'UNREAD' END,
  CASE WHEN (g.n % 2) = 0 THEN now() - ((g.n - 1) || ' hour')::interval ELSE NULL END,
  'BIZ_REQUEST',
  '01JYYDEMOREQ' || lpad(g.n::text, 13, '0'),
  '/requests',
  ('{"id":"01JYYDEMOREQ' || lpad(g.n::text, 13, '0') || '"}')::jsonb,
  now() - (g.n || ' hour')::interval
FROM generate_series(1, 45) AS g(n)
ON CONFLICT DO NOTHING;


