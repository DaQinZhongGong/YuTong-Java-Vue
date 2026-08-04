-- V014: 外部接口同步表 (GA2-38)
-- 设计来源: 35-样例业务矩阵扩展设计 P2 外部接口同步
-- 验证能力: HTTP Client 适配 + 签名鉴权 + 失败重试 + 幂等写入 + 死信/错误队列 + 同步监控
-- 状态机:
--   同步任务 ext_sync_task: ACTIVE / DISABLED (启用/停用)
--   同步记录 ext_sync_record: PENDING → RUNNING → SUCCESS / FAILED / PARTIAL (同步执行状态)
--   错误明细 ext_sync_error: PENDING → RETRYING → RESOLVED / DEAD_LETTER (重试与死信状态)

-- ===== 外部系统注册 =====
-- 每条记录描述一个待集成的第三方系统 (endpoint + 鉴权配置 + 重试策略)
CREATE TABLE ext_system (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    system_code         varchar(64)   NOT NULL,
    system_name         varchar(128)  NOT NULL,
    description         varchar(512),
    -- 接入端点 (基地址, 同步任务拼接具体路径)
    endpoint            varchar(512)  NOT NULL,
    -- 鉴权方式: NONE / HMAC_SHA256 / API_KEY / BEARER_TOKEN
    auth_type           varchar(32)   NOT NULL DEFAULT 'HMAC_SHA256',
    -- 鉴权凭据: HMAC_SHA256 存 access_key/secret_key; API_KEY 存 header_name/api_key; BEARER_TOKEN 存 token
    -- 出于安全考虑, 密钥以 jsonb 存储 (生产环境应走 SecretManager, 此处为样例验证)
    credentials         jsonb,
    -- HTTP 超时配置 (秒)
    connect_timeout     int           NOT NULL DEFAULT 5,
    read_timeout        int           NOT NULL DEFAULT 15,
    -- 失败重试策略
    max_retry_count     int           NOT NULL DEFAULT 3,
    retry_backoff_ms    int           NOT NULL DEFAULT 1000,
    status              varchar(16)   NOT NULL DEFAULT 'ACTIVE',
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    PRIMARY KEY (id)
);

CREATE INDEX idx_ext_system_tenant_code ON ext_system (tenant_id, system_code);
CREATE UNIQUE INDEX uk_ext_system_tenant_code ON ext_system (tenant_id, system_code) WHERE deleted = false;

-- ===== 同步任务定义 =====
-- 描述一条从外部系统拉取数据并写入本地的同步规则
CREATE TABLE ext_sync_task (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    task_code           varchar(64)   NOT NULL,
    task_name           varchar(128)  NOT NULL,
    system_id           varchar(32)   NOT NULL,
    description         varchar(512),
    -- 源 API: 相对 endpoint 的路径, 例如 /api/v1/orders
    source_api          varchar(256)  NOT NULL,
    -- HTTP 方法: GET / POST
    http_method         varchar(8)    NOT NULL DEFAULT 'GET',
    -- 请求体模板 (POST 时使用, 支持 YESTERDAY/TODAY 等占位符, 由应用层替换)
    request_template    text,
    -- 业务键字段: 从响应 JSON 提取的唯一键, 用于幂等写入
    business_key_field  varchar(64)   NOT NULL DEFAULT 'id',
    -- 同步模式: FULL (全量) / INCREMENTAL (增量, 基于 last_sync_time)
    sync_mode           varchar(16)   NOT NULL DEFAULT 'FULL',
    -- 目标表 (预留, v1.0 仅记录到 ext_sync_record, 不真正写入业务表)
    target_table        varchar(64),
    -- 定时表达式 (cron, 留空表示仅手动触发)
    cron_expression     varchar(64),
    -- 上次同步时间 (INCREMENTAL 模式的水位线)
    last_sync_time      timestamptz,
    status              varchar(16)   NOT NULL DEFAULT 'ACTIVE',
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    PRIMARY KEY (id)
);

CREATE INDEX idx_ext_sync_task_tenant_code ON ext_sync_task (tenant_id, task_code);
CREATE INDEX idx_ext_sync_task_system ON ext_sync_task (system_id);
CREATE UNIQUE INDEX uk_ext_sync_task_tenant_code ON ext_sync_task (tenant_id, task_code) WHERE deleted = false;

-- ===== 同步记录 =====
-- 一次同步执行产生一条记录, 包含统计信息和请求/响应快照
CREATE TABLE ext_sync_record (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    record_no           varchar(64)   NOT NULL,
    task_id             varchar(32)   NOT NULL,
    system_id           varchar(32)   NOT NULL,
    -- 批次号 (yyyymmddHHmmss + 短随机后缀)
    batch_no            varchar(48)   NOT NULL,
    status              varchar(16)   NOT NULL DEFAULT 'PENDING',
    -- 触发方式: MANUAL / SCHEDULED
    trigger_type        varchar(16)   NOT NULL DEFAULT 'MANUAL',
    -- 同步统计
    total_count         int           NOT NULL DEFAULT 0,
    success_count       int           NOT NULL DEFAULT 0,
    failed_count        int           NOT NULL DEFAULT 0,
    -- 请求/响应快照 (响应体可能较大, 截断到 16KB)
    request_snapshot    text,
    response_snapshot   text,
    http_status         int,
    error_message       varchar(1024),
    started_time        timestamptz,
    finished_time       timestamptz,
    duration_ms         bigint,
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    PRIMARY KEY (id)
);

CREATE INDEX idx_ext_sync_record_tenant ON ext_sync_record (tenant_id);
CREATE INDEX idx_ext_sync_record_task ON ext_sync_record (task_id, created_time);
CREATE INDEX idx_ext_sync_record_status ON ext_sync_record (status);
CREATE UNIQUE INDEX uk_ext_sync_record_no ON ext_sync_record (tenant_id, record_no) WHERE deleted = false;

-- ===== 错误明细 (死信队列) =====
-- 同步失败的单条业务记录, 支持 PENDING → RETRYING → RESOLVED / DEAD_LETTER 状态机
CREATE TABLE ext_sync_error (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    record_id           varchar(32)   NOT NULL,
    task_id             varchar(32)   NOT NULL,
    -- 业务键 (用于幂等定位)
    business_key        varchar(128)  NOT NULL,
    -- 业务负载 (失败的原始数据, 截断到 8KB)
    payload             text,
    -- 错误信息
    error_code          varchar(64),
    error_message       varchar(1024),
    http_status         int,
    -- 重试状态机: PENDING → RETRYING → RESOLVED / DEAD_LETTER
    status              varchar(16)   NOT NULL DEFAULT 'PENDING',
    retry_count         int           NOT NULL DEFAULT 0,
    last_retry_time     timestamptz,
    resolved_time       timestamptz,
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    PRIMARY KEY (id)
);

CREATE INDEX idx_ext_sync_error_tenant ON ext_sync_error (tenant_id);
CREATE INDEX idx_ext_sync_error_record ON ext_sync_error (record_id);
CREATE INDEX idx_ext_sync_error_status ON ext_sync_error (status, retry_count);
-- 同任务同业务键的未解决错误唯一 (防重复入队)
CREATE UNIQUE INDEX uk_ext_sync_error_task_biz_unresolved ON ext_sync_error (task_id, business_key) WHERE status IN ('PENDING', 'RETRYING') AND deleted = false;

-- ===== 种子数据 =====
-- tenant_id 使用 'default' 与 MockAuthAdapter.DEFAULT_TENANT 对齐 (V013 等种子数据已采用)
INSERT INTO ext_system (id, tenant_id, system_code, system_name, description, endpoint, auth_type, credentials, connect_timeout, read_timeout, max_retry_count, retry_backoff_ms, status, version) VALUES
('01K8EXTSYS0MOCK0000000000001', 'default', 'mock-erp', '模拟 ERP 系统', 'GA2-38 P2 样例: 外部接口同步端到端验证用 Mock ERP 系统', 'http://yutong-backend-run:8080/api/v1/ext/mock', 'HMAC_SHA256',
 '{"accessKey":"mock-access-key-001","secretKey":"mock-secret-key-001-very-long"}'::jsonb,
 5, 15, 3, 1000, 'ACTIVE', 0),
('01K8EXTSYS0MOCK0000000000002', 'default', 'mock-crm', '模拟 CRM 系统', 'GA2-38 P2 样例: 第二个外部系统 (验证签名失败)', 'http://yutong-backend-run:8080/api/v1/ext/mock', 'HMAC_SHA256',
 '{"accessKey":"mock-access-key-002","secretKey":"mock-secret-key-002-wrong"}'::jsonb,
 5, 10, 2, 500, 'ACTIVE', 0);

INSERT INTO ext_sync_task (id, tenant_id, task_code, task_name, system_id, description, source_api, http_method, request_template, business_key_field, sync_mode, target_table, cron_expression, status, version) VALUES
('01K8EXTTASK0MOCK000000000001', 'default', 'sync-mock-orders', '同步 Mock 订单数据', '01K8EXTSYS0MOCK0000000000001',
 '从 mock-erp 拉取订单数据 (全量同步, 验证 HTTP + 签名 + 幂等 + 监控)', '/orders', 'GET', NULL, 'orderNo', 'FULL', 'ext_order', NULL, 'ACTIVE', 0),
('01K8EXTTASK0MOCK000000000002', 'default', 'sync-mock-failures', '同步 Mock 失败场景', '01K8EXTSYS0MOCK0000000000002',
 '从 mock-crm 拉取数据, 凭据错误验证签名失败 + 错误队列 + 死信', '/orders', 'GET', NULL, 'orderNo', 'FULL', 'ext_order', NULL, 'ACTIVE', 0);
