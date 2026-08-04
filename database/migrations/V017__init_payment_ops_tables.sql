-- V017: 支付订单 P2 表 (GA2-41)
-- 设计来源: 35-样例业务矩阵扩展设计 P2 支付订单
-- 验证能力: 支付单状态机 + 第三方回调验签 + 回调幂等 + 对账文件导入 + 金额精度 + 安全审计
-- 状态机:
--   支付单 pay_order: PENDING → PAID → REFUNDING → REFUNDED → CLOSED
--                     PENDING → FAILED (支付失败终态)
--                     PENDING → CANCELLED (用户取消终态)
--                     PAID → REFUNDING → REFUNDED → CLOSED
--   退款单 pay_refund_order: PENDING → SUCCESS / FAILED
--   对账记录 pay_reconciliation: PENDING → MATCHED / MISMATCHED / IMPORTED
-- 金额精度: 统一使用 bigint 存储分单位 (1 元 = 100 分), 避免浮点精度问题, 最大支持约 92 万亿元
-- 回调验签: HMAC-SHA256, 复用 GA2-38 ExtSignatureService 思路 (签名串 = orderNo + channel + amount + timestamp)
-- 回调幂等: pay_callback_log.idempotency_key 唯一索引 + DuplicateKeyException 兜底

-- ===== 1. 支付订单表 =====
-- 一笔业务订单对应一条支付单, 支付单号系统全局唯一
CREATE TABLE pay_order (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    -- 支付单号 (业务可读, POyyyyMMddNNNNNN 格式)
    order_no            varchar(32)   NOT NULL,
    -- 业务类型 + 业务 ID (关联业务方, 如申请单/合同/工单)
    biz_type            varchar(32)   NOT NULL,
    biz_id              varchar(64),
    -- 支付渠道: MOCK_ALIPAY / MOCK_WECHAT / MOCK_UNIONPAY
    channel             varchar(32)   NOT NULL,
    -- 金额 (单位: 分, 避免 BigDecimal 精度问题)
    amount              bigint        NOT NULL,
    -- 币种 (ISO 4217, CNY/USD/HKD)
    currency            varchar(8)    NOT NULL DEFAULT 'CNY',
    -- 订单标题
    subject             varchar(256)  NOT NULL,
    -- 支付人 (Mock 用户 ID)
    payer_id            varchar(64),
    -- 支付状态: PENDING / PAID / FAILED / CANCELLED / REFUNDING / REFUNDED / CLOSED
    status              varchar(16)   NOT NULL DEFAULT 'PENDING',
    -- 第三方流水号 (支付成功后回写, 用于对账匹配)
    channel_trade_no    varchar(64),
    -- 支付时间
    paid_time           timestamptz,
    -- 订单过期时间 (默认 30 分钟)
    expired_time        timestamptz,
    -- 关闭时间
    closed_time         timestamptz,
    -- 失败原因 (status=FAILED 时填)
    fail_reason         varchar(512),
    -- 幂等键 (业务方调用 createOrder 时携带, 防止重复创建支付单)
    idempotency_key     varchar(128),
    -- 渠道凭证 (Mock 模式存放 accessKey/secretKey 的 jsonb, 生产应走 SecretManager)
    channel_config      jsonb,
    -- 额外参数 (透传给第三方, 如 openid/return_url)
    extra_params        jsonb,
    -- BaseEntity 标准字段
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz   NOT NULL DEFAULT now(),
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(500),
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_pay_order_no            ON pay_order (tenant_id, order_no) WHERE deleted = false;
CREATE UNIQUE INDEX uk_pay_order_idempotency   ON pay_order (tenant_id, idempotency_key) WHERE deleted = false AND idempotency_key IS NOT NULL;
CREATE INDEX        idx_pay_order_biz          ON pay_order (tenant_id, biz_type, biz_id);
CREATE INDEX        idx_pay_order_status       ON pay_order (tenant_id, status, created_time);
CREATE INDEX        idx_pay_order_payer        ON pay_order (tenant_id, payer_id, created_time);
CREATE INDEX        idx_pay_order_channel_no   ON pay_order (channel, channel_trade_no);

COMMENT ON TABLE  pay_order IS '支付订单: 一笔业务订单对应一条支付单, 状态机驱动';
COMMENT ON COLUMN pay_order.amount IS '金额 (单位: 分, 避免浮点精度问题)';
COMMENT ON COLUMN pay_order.idempotency_key IS '幂等键: 业务方调用 createOrder 时携带, 防止重复创建支付单';
COMMENT ON COLUMN pay_order.channel_config IS '渠道凭证 (Mock 模式 jsonb, 生产应走 SecretManager)';

-- ===== 2. 支付回调日志表 =====
-- 每次第三方回调都记录一条, 用于审计 + 幂等控制
CREATE TABLE pay_callback_log (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    -- 关联 pay_order.id
    order_id            varchar(32)   NOT NULL,
    -- 支付单号 (冗余, 便于日志检索)
    order_no            varchar(32)   NOT NULL,
    -- 回调渠道 (与 pay_order.channel 一致)
    channel             varchar(32)   NOT NULL,
    -- 第三方流水号
    channel_trade_no    varchar(64),
    -- 回调动作: PAY_SUCCESS / PAY_FAIL / REFUND_SUCCESS / REFUND_FAIL
    callback_action     varchar(32)   NOT NULL,
    -- 接收到的签名 (十六进制)
    signature           varchar(256),
    -- 时间戳 (来自第三方, 用于验签)
    callback_timestamp  varchar(32),
    -- 原始 payload (字符串快照, 用于审计追溯)
    raw_payload         text,
    -- 解析后的 payload (JSON 字符串, 包含 orderNo/amount/channelTradeNo 等)
    parsed_payload      text,
    -- 验签结果: SUCCESS / FAILED
    verify_result       varchar(16)   NOT NULL,
    -- 处理结果: PROCESSED / IGNORED / ERROR
    process_result      varchar(16)   NOT NULL,
    -- 处理说明 (如重复回调忽略 / 状态机不匹配 / 系统错误)
    process_message     varchar(512),
    -- 幂等键 (order_no + callback_action + channel_trade_no 组合, 防止重复处理)
    idempotency_key     varchar(128),
    -- 回调接收时间
    received_time       timestamptz   NOT NULL DEFAULT now(),
    -- BaseEntity 标准字段
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz   NOT NULL DEFAULT now(),
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(500),
    PRIMARY KEY (id)
);

CREATE INDEX idx_pay_callback_order      ON pay_callback_log (tenant_id, order_id, received_time);
CREATE INDEX idx_pay_callback_channel    ON pay_callback_log (channel, callback_action, received_time);
CREATE UNIQUE INDEX uk_pay_callback_idem ON pay_callback_log (tenant_id, idempotency_key) WHERE deleted = false AND idempotency_key IS NOT NULL;

COMMENT ON TABLE  pay_callback_log IS '支付回调日志: 每次第三方回调记录一条, 用于审计 + 幂等控制';
COMMENT ON COLUMN pay_callback_log.idempotency_key IS '幂等键: order_no + callback_action + channel_trade_no 组合';

-- ===== 3. 退款单表 =====
-- 一笔支付单可发起多次部分退款, 累计退款金额不超过原支付金额
CREATE TABLE pay_refund_order (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    -- 退款单号 (RFyyyyMMddNNNNNN 格式)
    refund_no           varchar(32)   NOT NULL,
    -- 原支付单 ID
    original_order_id   varchar(32)   NOT NULL,
    -- 原支付单号 (冗余)
    original_order_no   varchar(32)   NOT NULL,
    -- 退款金额 (分, 不超过原支付金额 - 已退款金额)
    refund_amount       bigint        NOT NULL,
    -- 退款原因
    reason              varchar(256)  NOT NULL,
    -- 退款状态: PENDING / SUCCESS / FAILED
    status              varchar(16)   NOT NULL DEFAULT 'PENDING',
    -- 操作人 (发起退款的用户)
    operator_id         varchar(64)   NOT NULL,
    -- 第三方退款流水号
    channel_refund_no   varchar(64),
    -- 退款完成时间
    refunded_time       timestamptz,
    -- 失败原因
    fail_reason         varchar(512),
    -- BaseEntity 标准字段
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz   NOT NULL DEFAULT now(),
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(500),
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_pay_refund_no        ON pay_refund_order (tenant_id, refund_no) WHERE deleted = false;
CREATE INDEX        idx_pay_refund_order    ON pay_refund_order (tenant_id, original_order_id, created_time);
CREATE INDEX        idx_pay_refund_status   ON pay_refund_order (tenant_id, status, created_time);

COMMENT ON TABLE  pay_refund_order IS '退款单: 一笔支付单可发起多次部分退款, 累计不超过原支付金额';
COMMENT ON COLUMN pay_refund_order.refund_amount IS '退款金额 (分)';

-- ===== 4. 对账记录表 =====
-- 每日按渠道对账: 比对本地支付单与第三方对账文件
CREATE TABLE pay_reconciliation (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    -- 对账日期 (yyyy-MM-dd)
    recon_date          date          NOT NULL,
    -- 对账渠道
    channel             varchar(32)   NOT NULL,
    -- 对账文件名
    file_name           varchar(256),
    -- 文件总笔数 (第三方侧)
    total_count         int           NOT NULL DEFAULT 0,
    -- 文件总金额 (分)
    total_amount        bigint        NOT NULL DEFAULT 0,
    -- 匹配成功笔数 (本地有 + 第三方有, 状态一致)
    matched_count       int           NOT NULL DEFAULT 0,
    -- 匹配成功金额 (分)
    matched_amount      bigint        NOT NULL DEFAULT 0,
    -- 不一致笔数 (本地有 + 第三方有, 但金额/状态不一致)
    mismatched_count    int           NOT NULL DEFAULT 0,
    -- 不一致金额 (分)
    mismatched_amount   bigint        NOT NULL DEFAULT 0,
    -- 本地缺失笔数 (第三方有 + 本地无, 即第三方多)
    missing_count       int           NOT NULL DEFAULT 0,
    -- 本地缺失金额 (分)
    missing_amount      bigint        NOT NULL DEFAULT 0,
    -- 本地多余笔数 (本地有 + 第三方无, 即本地多)
    extra_count         int           NOT NULL DEFAULT 0,
    -- 本地多余金额 (分)
    extra_amount        bigint        NOT NULL DEFAULT 0,
    -- 对账状态: PENDING / MATCHED / MISMATCHED / IMPORTED / FAILED
    status              varchar(16)   NOT NULL DEFAULT 'PENDING',
    -- 对账明细 JSON (数组, 每条含 orderNo/channelTradeNo/localAmount/remoteAmount/diff/diffType)
    details             jsonb,
    -- 导入人
    imported_by         varchar(64),
    -- 导入时间
    imported_time       timestamptz,
    -- 处理说明
    process_message     varchar(512),
    -- BaseEntity 标准字段
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz   NOT NULL DEFAULT now(),
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(500),
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_pay_recon_date_channel ON pay_reconciliation (tenant_id, recon_date, channel) WHERE deleted = false;
CREATE INDEX        idx_pay_recon_status      ON pay_reconciliation (tenant_id, status, recon_date);

COMMENT ON TABLE  pay_reconciliation IS '对账记录: 比对本地支付单与第三方对账文件, 发现差异';
COMMENT ON COLUMN pay_reconciliation.total_amount IS '总金额 (分)';

-- ===== 5. 种子数据 =====
-- tenant_id='default' 对齐 MockAuthAdapter.DEFAULT_TENANT

-- 5.1 支付单种子 (3 条覆盖 PENDING / PAID / FAILED 状态)
INSERT INTO pay_order (id, tenant_id, order_no, biz_type, biz_id, channel, amount, currency, subject, payer_id, status, channel_trade_no, paid_time, expired_time, closed_time, fail_reason, idempotency_key, channel_config, extra_params, created_by, created_time, updated_by, updated_time, remark) VALUES
('pay-seed-001', 'default', 'PO20260719000001', 'biz_request', '01MOCKBIZREQ000000000001', 'MOCK_ALIPAY', 25000, 'CNY', 'GA2-41 冒烟测试支付单 1 - 已支付', '01MOCKUSER0000000000000ADMIN', 'PAID', 'ALIPAY-TRADE-001', '2026-07-19 14:00:00+08', null, null, null, 'idem-pay-seed-001', '{"accessKey":"mock-alipay-ak","secretKey":"mock-alipay-sk-32bytes-xxxxx"}', '{"returnUrl":"/workbench/todos"}', 'system', '2026-07-19 14:00:00+08', 'system', '2026-07-19 14:00:00+08', 'GA2-41 种子: 已支付订单'),
('pay-seed-002', 'default', 'PO20260719000002', 'biz_request', '01MOCKBIZREQ000000000002', 'MOCK_WECHAT', 8800, 'CNY', 'GA2-41 冒烟测试支付单 2 - 待支付', '01MOCKUSER00000000000000BIZ', 'PENDING', null, null, '2026-07-19 15:00:00+08', null, null, 'idem-pay-seed-002', '{"accessKey":"mock-wechat-ak","secretKey":"mock-wechat-sk-32bytes-xxxxx"}', null, 'system', '2026-07-19 14:00:00+08', 'system', '2026-07-19 14:00:00+08', 'GA2-41 种子: 待支付订单'),
('pay-seed-003', 'default', 'PO20260719000003', 'biz_request', '01MOCKBIZREQ000000000003', 'MOCK_UNIONPAY', 150000, 'CNY', 'GA2-41 冒烟测试支付单 3 - 支付失败', '01MOCKUSER0000000000APPROVER', 'FAILED', null, null, '2026-07-19 15:00:00+08', null, 'Mock 余额不足', 'idem-pay-seed-003', '{"accessKey":"mock-unionpay-ak","secretKey":"mock-unionpay-sk-32bytes-x"}', null, 'system', '2026-07-19 14:00:00+08', 'system', '2026-07-19 14:00:00+08', 'GA2-41 种子: 支付失败订单');

-- 5.2 回调日志种子 (2 条: 1 成功 + 1 验签失败)
INSERT INTO pay_callback_log (id, tenant_id, order_id, order_no, channel, channel_trade_no, callback_action, signature, callback_timestamp, raw_payload, parsed_payload, verify_result, process_result, process_message, idempotency_key, received_time, created_by, created_time, updated_by, updated_time, remark) VALUES
('cb-seed-001', 'default', 'pay-seed-001', 'PO20260719000001', 'MOCK_ALIPAY', 'ALIPAY-TRADE-001', 'PAY_SUCCESS', 'mock-signature-seed-001', '20260719140000', '{"orderNo":"PO20260719000001","channelTradeNo":"ALIPAY-TRADE-001","amount":25000,"status":"PAID"}', '{"orderNo":"PO20260719000001","channelTradeNo":"ALIPAY-TRADE-001","amount":25000,"status":"PAID"}', 'SUCCESS', 'PROCESSED', '支付成功回调已处理', 'PO20260719000001|PAY_SUCCESS|ALIPAY-TRADE-001', '2026-07-19 14:00:00+08', 'system', '2026-07-19 14:00:00+08', 'system', '2026-07-19 14:00:00+08', 'GA2-41 种子: 成功回调'),
('cb-seed-002', 'default', 'pay-seed-002', 'PO20260719000002', 'MOCK_WECHAT', null, 'PAY_FAIL', 'mock-signature-wrong', '20260719140001', '{"orderNo":"PO20260719000002","reason":"签名验证失败"}', null, 'FAILED', 'IGNORED', '验签失败, 忽略回调', 'PO20260719000002|PAY_FAIL|null', '2026-07-19 14:00:00+08', 'system', '2026-07-19 14:00:00+08', 'system', '2026-07-19 14:00:00+08', 'GA2-41 种子: 验签失败回调');

-- 5.3 退款单种子 (1 条: 1 SUCCESS, 关联 pay-seed-001 部分退款 5000 分)
INSERT INTO pay_refund_order (id, tenant_id, refund_no, original_order_id, original_order_no, refund_amount, reason, status, operator_id, channel_refund_no, refunded_time, fail_reason, created_by, created_time, updated_by, updated_time, remark) VALUES
('rf-seed-001', 'default', 'RF20260719000001', 'pay-seed-001', 'PO20260719000001', 5000, 'GA2-41 种子: 部分退款测试', 'SUCCESS', '01MOCKUSER0000000000000ADMIN', 'ALIPAY-REFUND-001', '2026-07-19 14:30:00+08', null, 'system', '2026-07-19 14:30:00+08', 'system', '2026-07-19 14:30:00+08', 'GA2-41 种子: 已完成部分退款');

-- 5.4 对账记录种子 (1 条: 2026-07-19 MOCK_ALIPAY 渠道对账匹配)
INSERT INTO pay_reconciliation (id, tenant_id, recon_date, channel, file_name, total_count, total_amount, matched_count, matched_amount, mismatched_count, mismatched_amount, missing_count, missing_amount, extra_count, extra_amount, status, details, imported_by, imported_time, process_message, created_by, created_time, updated_by, updated_time, remark) VALUES
('recon-seed-001', 'default', '2026-07-19', 'MOCK_ALIPAY', 'mock-alipay-recon-20260719.csv', 1, 25000, 1, 25000, 0, 0, 0, 0, 0, 0, 'MATCHED',
 '[{"orderNo":"PO20260719000001","channelTradeNo":"ALIPAY-TRADE-001","localAmount":25000,"remoteAmount":25000,"diff":0,"diffType":"MATCHED"}]',
 'system', '2026-07-19 14:30:00+08', '对账匹配成功', 'system', '2026-07-19 14:30:00+08', 'system', '2026-07-19 14:30:00+08', 'GA2-41 种子: 对账匹配成功');
