-- V016: 实时通知运营表 (GA2-40)
-- 设计来源: 35-样例业务矩阵扩展设计 P2 实时通知
-- 验证能力: 站内信 + 未读数 + 实时推送 + 移动端订阅消息 + 消息模板 + 消息重试
-- 复用: sys_message (V002 已建) + sys_message_template (V002 已建, 本迁移扩展列) + PushService + RealtimeEnvelope (GA2-32 已落地)
-- 本迁移:
--   1. ALTER TABLE sys_message_template 追加 msg_type/target_route_id/priority/delivery_mode 列
--   2. CREATE TABLE sys_notification_dispatch_log (分发日志)
--   3. CREATE TABLE sys_notification_subscription (移动端订阅)
--   4. 灌入种子数据 (6 模板 + 6 订阅 + 3 分发日志 + 3 sys_message)
-- 注意: 模板种子数据含 ${var} 字面量 (业务侧模板变量占位符)
--       application.yml 中已设置 spring.flyway.placeholder-replacement=false 关闭 Flyway 占位符替换

-- ===== 1. 扩展 sys_message_template (V002 已建) =====
-- V002 原有列: id/tenant_id/template_code/template_name/channel/title_template/content_template/params_schema/status/...
-- V016 追加: msg_type/target_route_id/priority/delivery_mode (用于通知运营场景的模板配置)
ALTER TABLE sys_message_template ADD COLUMN IF NOT EXISTS msg_type        varchar(32);
ALTER TABLE sys_message_template ADD COLUMN IF NOT EXISTS target_route_id varchar(128);
ALTER TABLE sys_message_template ADD COLUMN IF NOT EXISTS priority        varchar(16)  NOT NULL DEFAULT 'NORMAL';
ALTER TABLE sys_message_template ADD COLUMN IF NOT EXISTS delivery_mode   varchar(32)  NOT NULL DEFAULT 'PERSIST_THEN_PUSH';

COMMENT ON COLUMN sys_message_template.msg_type        IS '消息类型: SYSTEM/BIZ/APPROVAL/EXPORT/WORKFLOW/ALERT (V016 追加)';
COMMENT ON COLUMN sys_message_template.target_route_id IS '默认跳转路由 (V016 追加)';
COMMENT ON COLUMN sys_message_template.priority        IS '默认优先级: LOW/NORMAL/HIGH/URGENT (V016 追加)';
COMMENT ON COLUMN sys_message_template.delivery_mode   IS '默认投递模式: PERSIST_THEN_PUSH / PUSH_ONLY (V016 追加)';

-- ===== 2. 分发日志表 =====
-- 一条站内信可能分发到多渠道 (IN_APP/MOBILE_PUSH/SMS/EMAIL), 每次分发独立记录用于重试和审计
CREATE TABLE sys_notification_dispatch_log (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    -- 关联 sys_message id
    message_id          varchar(32)   NOT NULL,
    -- 接收人 user_id
    receiver_id         varchar(64)   NOT NULL,
    -- 分发渠道: IN_APP / MOBILE_PUSH / SMS / EMAIL
    channel             varchar(32)   NOT NULL,
    -- 分发状态: PENDING / SENT / FAILED / RETRYING / DEAD_LETTER
    dispatch_status     varchar(32)   NOT NULL DEFAULT 'PENDING',
    -- 重试次数 (从 0 开始, 每次失败 +1)
    retry_count         int           NOT NULL DEFAULT 0,
    -- 最大重试次数 (达到后进入 DEAD_LETTER)
    max_retry_count     int           NOT NULL DEFAULT 3,
    -- 退避基数 (毫秒), 实际等待 = retry_backoff_ms * 2^retry_count
    retry_backoff_ms    bigint        NOT NULL DEFAULT 1000,
    -- 下次重试时间 (调度器扫描该字段)
    next_retry_time     timestamptz,
    -- 最近一次错误信息 (截断到 2KB)
    last_error          varchar(2048),
    -- 实际发送时间
    sent_time           timestamptz,
    -- 投递载荷快照 (JSON, 如移动推送的 payload)
    payload_snapshot    text,
    -- BaseEntity 标准字段
    remark              varchar(500),
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz   NOT NULL DEFAULT now(),
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- 兼容已部署环境 (V016 首版未包含 remark 列, 此处 IF NOT EXISTS 保证幂等)
ALTER TABLE sys_notification_dispatch_log ADD COLUMN IF NOT EXISTS remark varchar(500);

CREATE INDEX idx_dispatch_log_message_id     ON sys_notification_dispatch_log (message_id);
CREATE INDEX idx_dispatch_log_receiver       ON sys_notification_dispatch_log (receiver_id, dispatch_status);
CREATE INDEX idx_dispatch_log_status_retry   ON sys_notification_dispatch_log (dispatch_status, next_retry_time)
    WHERE deleted = false;

COMMENT ON TABLE  sys_notification_dispatch_log IS '通知分发日志: 每条站内信每个渠道一条记录, 用于重试和审计';
COMMENT ON COLUMN sys_notification_dispatch_log.channel         IS '分发渠道: IN_APP / MOBILE_PUSH / SMS / EMAIL';
COMMENT ON COLUMN sys_notification_dispatch_log.dispatch_status IS 'PENDING / SENT / FAILED / RETRYING / DEAD_LETTER';
COMMENT ON COLUMN sys_notification_dispatch_log.retry_backoff_ms IS '退避基数毫秒, 实际等待 = retry_backoff_ms * 2^retry_count';

-- ===== 3. 移动端订阅表 =====
-- 用户订阅某类消息的移动推送, 支持按 topic + channel 维度开启/关闭
CREATE TABLE sys_notification_subscription (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    -- 订阅用户
    user_id             varchar(64)   NOT NULL,
    -- 订阅主题 (对齐 msg_type: SYSTEM/BIZ/APPROVAL/EXPORT/WORKFLOW/ALERT)
    topic               varchar(32)   NOT NULL,
    -- 推送渠道: MOBILE_PUSH / SMS / EMAIL
    channel             varchar(32)   NOT NULL,
    -- 是否启用
    enabled             boolean       NOT NULL DEFAULT true,
    -- 设备 token (mock 场景使用固定 mock-token-xxx)
    device_token        varchar(255),
    -- 备注
    remark              varchar(500),
    -- BaseEntity 标准字段
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz   NOT NULL DEFAULT now(),
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, user_id, topic, channel)
);

COMMENT ON TABLE  sys_notification_subscription IS '移动端订阅: 用户按 topic + channel 维度订阅推送';
COMMENT ON COLUMN sys_notification_subscription.topic        IS '订阅主题, 对齐 msg_type';
COMMENT ON COLUMN sys_notification_subscription.channel      IS '推送渠道: MOBILE_PUSH / SMS / EMAIL';

-- ===== 4. 种子数据 =====
-- 4.1 6 条消息模板 (覆盖 6 种 msg_type; 复用 V002 列 channel/status/params_schema + V016 新列)
-- channel 统一用 'IN_APP' (V002 NOT NULL 约束, 业务语义: 模板默认用于站内信)
-- status 统一用 'ENABLED' (V002 NOT NULL 约束, 业务语义: 模板启用)
INSERT INTO sys_message_template (id, tenant_id, template_code, template_name, channel, msg_type, title_template, content_template, params_schema, status, target_route_id, priority, delivery_mode, remark, created_by, created_time, updated_by, updated_time, deleted, version) VALUES
('tpl-biz-request-submitted', 'default', 'BIZ_REQUEST_SUBMITTED', '申请单提交通知', 'IN_APP', 'BIZ',
 '您有新的申请单 ${requestNo}',
 '申请单 ${requestNo} 已由 ${applicantName} 提交，金额 ${amount} 元，请及时审批。',
 null, 'ENABLED', '/workbench/todos', 'HIGH', 'PERSIST_THEN_PUSH', '申请单提交时通知审批人',
 'system', now(), 'system', now(), false, 0),

('tpl-approval-result', 'default', 'APPROVAL_RESULT', '审批结果通知', 'IN_APP', 'APPROVAL',
 '您的申请单 ${requestNo} 审批${result}',
 '申请单 ${requestNo}（${requestTitle}）审批${result}，审批人 ${approverName}，意见：${comment}。',
 null, 'ENABLED', '/workbench/todos', 'NORMAL', 'PERSIST_THEN_PUSH', '审批完成时通知申请人',
 'system', now(), 'system', now(), false, 0),

('tpl-export-complete', 'default', 'EXPORT_COMPLETE', '导出完成通知', 'IN_APP', 'EXPORT',
 '导出任务已完成 ${fileName}',
 '您请求的导出文件 ${fileName} 已生成，大小 ${fileSize}，点击前往下载。',
 null, 'ENABLED', '/system/import-export', 'LOW', 'PERSIST_THEN_PUSH', '异步导出完成通知',
 'system', now(), 'system', now(), false, 0),

('tpl-system-announce', 'default', 'SYSTEM_ANNOUNCE', '系统公告', 'IN_APP', 'SYSTEM',
 '系统公告 ${announceTitle}',
 '${announceContent}',
 null, 'ENABLED', null, 'NORMAL', 'PERSIST_THEN_PUSH', '系统公告广播',
 'system', now(), 'system', now(), false, 0),

('tpl-workflow-notify', 'default', 'WORKFLOW_NOTIFY', '工作流节点通知', 'IN_APP', 'WORKFLOW',
 '工作流待办 ${workflowName}-${nodeName}',
 '${approverName} 您好，工作流 ${workflowName} 的节点 ${nodeName} 需要您处理，提交人 ${submitterName}。',
 null, 'ENABLED', '/workbench/todos', 'HIGH', 'PERSIST_THEN_PUSH', '工作流节点流转通知',
 'system', now(), 'system', now(), false, 0),

('tpl-alert-threshold', 'default', 'ALERT_THRESHOLD', '阈值告警通知', 'IN_APP', 'ALERT',
 '告警 ${alertName} 触发',
 '指标 ${metricName} 当前值 ${currentValue}，已超过阈值 ${thresholdValue}，请及时处理。',
 null, 'ENABLED', null, 'URGENT', 'PERSIST_THEN_PUSH', '阈值告警通知（高优先级）',
 'system', now(), 'system', now(), false, 0);

-- 4.2 移动端订阅种子 (admin 用户订阅 BIZ/APPROVAL/WORKFLOW/ALERT 4 个 topic 的 MOBILE_PUSH)
-- admin 用户 ID 与 MockAuthAdapter 一致 (mock-user-admin)
INSERT INTO sys_notification_subscription (id, tenant_id, user_id, topic, channel, enabled, device_token, remark, created_by, created_time, updated_by, updated_time, deleted, version) VALUES
('sub-admin-biz-push',       'default', 'mock-user-admin', 'BIZ',      'MOBILE_PUSH', true, 'mock-token-admin-001', 'admin 订阅业务通知',  'system', now(), 'system', now(), false, 0),
('sub-admin-approval-push',  'default', 'mock-user-admin', 'APPROVAL', 'MOBILE_PUSH', true, 'mock-token-admin-001', 'admin 订阅审批通知',  'system', now(), 'system', now(), false, 0),
('sub-admin-workflow-push',  'default', 'mock-user-admin', 'WORKFLOW', 'MOBILE_PUSH', true, 'mock-token-admin-001', 'admin 订阅工作流通知','system', now(), 'system', now(), false, 0),
('sub-admin-alert-push',     'default', 'mock-user-admin', 'ALERT',    'MOBILE_PUSH', true, 'mock-token-admin-001', 'admin 订阅告警通知',  'system', now(), 'system', now(), false, 0),
-- biz 用户仅订阅 BIZ/APPROVAL 2 个 topic
('sub-biz-biz-push',         'default', 'mock-user-biz',   'BIZ',      'MOBILE_PUSH', true, 'mock-token-biz-001',   'biz 订阅业务通知',    'system', now(), 'system', now(), false, 0),
('sub-biz-approval-push',    'default', 'mock-user-biz',   'APPROVAL', 'MOBILE_PUSH', true, 'mock-token-biz-001',   'biz 订阅审批通知',    'system', now(), 'system', now(), false, 0);

-- 4.3 分发日志种子 (3 条历史记录用于冒烟测试)
-- 4.3.1 已成功分发的站内信
INSERT INTO sys_notification_dispatch_log (id, tenant_id, message_id, receiver_id, channel, dispatch_status, retry_count, max_retry_count, retry_backoff_ms, next_retry_time, last_error, sent_time, payload_snapshot, created_by, created_time, updated_by, updated_time, deleted, version) VALUES
('disp-seed-001', 'default', 'msg-seed-001', 'mock-user-admin', 'IN_APP',       'SENT', 0, 3, 1000, null, null, now() - interval '1 hour',
 '{"title":"历史通知样例","content":"用于冒烟测试查询","msgType":"BIZ"}',
 'system', now() - interval '1 hour', 'system', now() - interval '1 hour', false, 0),
-- 4.3.2 失败待重试 (mobile_push 推送失败)
('disp-seed-002', 'default', 'msg-seed-002', 'mock-user-admin', 'MOBILE_PUSH',  'FAILED', 1, 3, 1000, now() - interval '30 minute',
 'Mock mobile push timeout',
 null,
 '{"title":"移动推送失败样例","deviceToken":"mock-token-admin-001"}',
 'system', now() - interval '1 hour', 'system', now() - interval '30 minute', false, 0),
-- 4.3.3 死信队列 (重试次数达上限)
('disp-seed-003', 'default', 'msg-seed-003', 'mock-user-biz',   'MOBILE_PUSH',  'DEAD_LETTER', 3, 3, 1000, null,
 'Mock mobile push timeout (3 retries exhausted)',
 null,
 '{"title":"死信样例","deviceToken":"mock-token-biz-001"}',
 'system', now() - interval '2 hour', 'system', now() - interval '1 hour', false, 0);

-- 4.4 关联 sys_message 种子 (3 条对应分发日志的站内信)
-- 与 sys_message 表结构对齐 (V002): target_route_id NOT NULL, target_params jsonb NOT NULL DEFAULT '{}'
INSERT INTO sys_message (id, tenant_id, receiver_id, msg_type, title, content, read_status, read_time, biz_type, biz_id, target_route_id, target_params, created_by, created_time, updated_by, updated_time, deleted, version) VALUES
('msg-seed-001', 'default', 'mock-user-admin', 'BIZ',      '历史通知样例', '用于冒烟测试查询', 'READ',   now() - interval '50 minute', 'notification_ops', 'disp-seed-001', '/workbench/todos', '{}'::jsonb, 'system', now() - interval '1 hour', 'system', now() - interval '1 hour', false, 0),
('msg-seed-002', 'default', 'mock-user-admin', 'APPROVAL', '移动推送失败样例', '该消息的移动推送失败，等待重试', 'UNREAD', null, 'notification_ops', 'disp-seed-002', '/workbench/todos', '{}'::jsonb, 'system', now() - interval '1 hour', 'system', now() - interval '1 hour', false, 0),
('msg-seed-003', 'default', 'mock-user-biz',   'BIZ',      '死信样例', '该消息的移动推送已进入死信队列', 'UNREAD', null, 'notification_ops', 'disp-seed-003', '/workbench/todos', '{}'::jsonb, 'system', now() - interval '2 hour', 'system', now() - interval '2 hour', false, 0);
