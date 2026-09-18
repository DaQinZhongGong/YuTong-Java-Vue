-- ============================================================
-- V055__chat_global_config_seed.sql
-- P2 聊天全局配置: 向 sys_config 灌入聊天相关配置键种子
-- 设计来源: 业界同类实现 ChatConfigController + ADR 0005 P2
-- 说明: 复用既有 sys_config 键值体系, 不新建表; 管理页走既有 ConfigList.vue
-- 约束: 幂等 (config_key 唯一索引已存在, WHERE NOT EXISTS)
-- ============================================================

INSERT INTO sys_config (id, tenant_id, config_key, config_value, value_type, config_group, status, editable, sensitive, remark)
SELECT '01JCHATCONFIGDEFAULTMODEL000001', 'default',
       'chat.default.model', '', 'STRING', 'chat',
       'ENABLED', true, false,
       '聊天默认模型编码, 留空则使用第一个可用供应商的默认模型'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'chat.default.model' AND deleted = false
);

INSERT INTO sys_config (id, tenant_id, config_key, config_value, value_type, config_group, status, editable, sensitive, remark)
SELECT '01JCHATCONFIGDEFAULTPROVIDER01', 'default',
       'chat.default.provider', '', 'STRING', 'chat',
       'ENABLED', true, false,
       '聊天默认供应商编码, 留空则自动选择优先级最高的可用供应商'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'chat.default.provider' AND deleted = false
);

INSERT INTO sys_config (id, tenant_id, config_key, config_value, value_type, config_group, status, editable, sensitive, remark)
SELECT '01JCHATCONFIGTEMPERATURE00001', 'default',
       'chat.default.temperature', '0.7', 'NUMBER', 'chat',
       'ENABLED', true, false,
       '聊天默认温度, LLM temperature 参数, 范围 0.0-2.0'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'chat.default.temperature' AND deleted = false
);

INSERT INTO sys_config (id, tenant_id, config_key, config_value, value_type, config_group, status, editable, sensitive, remark)
SELECT '01JCHATCONFIGMAXTOKENS0000001', 'default',
       'chat.default.maxTokens', '2000', 'NUMBER', 'chat',
       'ENABLED', true, false,
       '聊天最大输出 Token, LLM max_tokens 参数'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'chat.default.maxTokens' AND deleted = false
);

INSERT INTO sys_config (id, tenant_id, config_key, config_value, value_type, config_group, status, editable, sensitive, remark)
SELECT '01JCHATCONFIGSYSTEMPROMPT0001', 'default',
       'chat.default.systemPrompt', '你是 YuTong AI 助手，乐于助人、准确专业。', 'STRING', 'chat',
       'ENABLED', true, false,
       '聊天默认系统提示词, 所有新会话的默认 system prompt'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'chat.default.systemPrompt' AND deleted = false
);

INSERT INTO sys_config (id, tenant_id, config_key, config_value, value_type, config_group, status, editable, sensitive, remark)
SELECT '01JCHATCONFIGSTREAMENABLED01', 'default',
       'chat.stream.enabled', 'true', 'BOOLEAN', 'chat',
       'ENABLED', true, false,
       '流式输出开关, 是否启用 SSE 流式输出'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'chat.stream.enabled' AND deleted = false
);

INSERT INTO sys_config (id, tenant_id, config_key, config_value, value_type, config_group, status, editable, sensitive, remark)
SELECT '01JCHATCONFIGRATELIMIT000001', 'default',
       'chat.rateLimit.perMinute', '30', 'NUMBER', 'chat',
       'ENABLED', true, false,
       '聊天每分钟限流, 单用户每分钟最大对话次数'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'chat.rateLimit.perMinute' AND deleted = false
);

INSERT INTO sys_config (id, tenant_id, config_key, config_value, value_type, config_group, status, editable, sensitive, remark)
SELECT '01JCHATCONFIGMEMORYWINDOW001', 'default',
       'chat.memory.windowSize', '20', 'NUMBER', 'chat',
       'ENABLED', true, false,
       '记忆窗口大小, 对话记忆保留的最大消息数'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'chat.memory.windowSize' AND deleted = false
);
