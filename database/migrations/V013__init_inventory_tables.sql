-- V013: 库存出入库表 (GA2-37)
-- 设计来源: 35-样例业务矩阵扩展设计 P1 库存出入库
-- 验证能力: 并发扣减 + 幂等键防重复 + 乐观锁 + 导入物料 + 导出库存 + 异步任务 + 异常补偿
-- 状态机:
--   入库单 IN: DRAFT → CONFIRMED (不可逆, 触发库存增加)
--   出库单 OUT: DRAFT → CONFIRMED (不可逆, 触发库存扣减, 库存不足时拦截)
--   出库单 CONFIRMED → COMPENSATED (异常补偿, 库存回补)

-- ===== 物料主表 =====
CREATE TABLE inv_material (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    material_code       varchar(64)   NOT NULL,
    material_name       varchar(128)  NOT NULL,
    material_type       varchar(32)   NOT NULL DEFAULT 'GENERAL',
    spec                varchar(128),
    unit                varchar(32)   NOT NULL DEFAULT 'PCS',
    category            varchar(64),
    barcode             varchar(64),
    reference_price     numeric(18,4) NOT NULL DEFAULT 0,
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

CREATE UNIQUE INDEX uk_inv_material_tenant_code ON inv_material (tenant_id, material_code) WHERE deleted = false;
CREATE INDEX idx_inv_material_name ON inv_material (material_name) WHERE deleted = false;
CREATE INDEX idx_inv_material_category ON inv_material (category) WHERE deleted = false;
CREATE INDEX idx_inv_material_barcode ON inv_material (barcode) WHERE deleted = false AND barcode IS NOT NULL;

-- ===== 仓库主表 =====
CREATE TABLE inv_warehouse (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    warehouse_code      varchar(64)   NOT NULL,
    warehouse_name      varchar(128)  NOT NULL,
    warehouse_type      varchar(32)   NOT NULL DEFAULT 'CENTRAL',
    address             varchar(256),
    manager_user_id     varchar(64),
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

CREATE UNIQUE INDEX uk_inv_warehouse_tenant_code ON inv_warehouse (tenant_id, warehouse_code) WHERE deleted = false;
CREATE INDEX idx_inv_warehouse_type ON inv_warehouse (warehouse_type) WHERE deleted = false;

-- ===== 库存余额表 (物料+仓库 维度唯一, 乐观锁) =====
CREATE TABLE inv_stock_balance (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    material_id         varchar(32)   NOT NULL,
    warehouse_id        varchar(32)   NOT NULL,
    quantity            numeric(18,4) NOT NULL DEFAULT 0,
    locked_quantity     numeric(18,4) NOT NULL DEFAULT 0,
    -- 可用 = quantity - locked_quantity (由应用层计算, 不存为列避免脏读)
    last_in_time        timestamptz,
    last_out_time       timestamptz,
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    PRIMARY KEY (id)
);

-- 物料+仓库 维度唯一约束, 防止重复余额记录
CREATE UNIQUE INDEX uk_inv_balance_material_warehouse ON inv_stock_balance (tenant_id, material_id, warehouse_id) WHERE deleted = false;
CREATE INDEX idx_inv_balance_material ON inv_stock_balance (material_id) WHERE deleted = false;
CREATE INDEX idx_inv_balance_warehouse ON inv_stock_balance (warehouse_id) WHERE deleted = false;

-- ===== 入库单 =====
CREATE TABLE inv_inbound_order (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    order_no            varchar(32)   NOT NULL,
    idempotency_key     varchar(64)   NOT NULL,
    warehouse_id        varchar(32)   NOT NULL,
    material_id         varchar(32)   NOT NULL,
    quantity            numeric(18,4) NOT NULL,
    unit_cost           numeric(18,4) NOT NULL DEFAULT 0,
    total_amount        numeric(18,4) NOT NULL DEFAULT 0,
    -- 状态机: DRAFT → CONFIRMED (不可逆)
    status              varchar(16)   NOT NULL DEFAULT 'DRAFT',
    inbound_type        varchar(32)   NOT NULL DEFAULT 'PURCHASE',
    batch_no            varchar(64),
    supplier            varchar(128),
    confirmed_time      timestamptz,
    confirmed_by        varchar(64),
    balance_id          varchar(32),
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_inv_inbound_tenant_no ON inv_inbound_order (tenant_id, order_no) WHERE deleted = false;
-- 幂等键: 同一 tenant 下 idempotency_key 唯一, 防止重复提交
CREATE UNIQUE INDEX uk_inv_inbound_idempotency ON inv_inbound_order (tenant_id, idempotency_key) WHERE deleted = false;
CREATE INDEX idx_inv_inbound_status ON inv_inbound_order (status) WHERE deleted = false;
CREATE INDEX idx_inv_inbound_material ON inv_inbound_order (material_id) WHERE deleted = false;
CREATE INDEX idx_inv_inbound_warehouse ON inv_inbound_order (warehouse_id) WHERE deleted = false;

-- ===== 出库单 =====
CREATE TABLE inv_outbound_order (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    order_no            varchar(32)   NOT NULL,
    idempotency_key     varchar(64)   NOT NULL,
    warehouse_id        varchar(32)   NOT NULL,
    material_id         varchar(32)   NOT NULL,
    quantity            numeric(18,4) NOT NULL,
    unit_cost           numeric(18,4) NOT NULL DEFAULT 0,
    total_amount        numeric(18,4) NOT NULL DEFAULT 0,
    -- 状态机: DRAFT → CONFIRMED (扣减库存) → COMPENSATED (异常补偿回补)
    status              varchar(16)   NOT NULL DEFAULT 'DRAFT',
    outbound_type       varchar(32)   NOT NULL DEFAULT 'SALE',
    batch_no            varchar(64),
    customer            varchar(128),
    confirmed_time      timestamptz,
    confirmed_by        varchar(64),
    compensated_time    timestamptz,
    compensated_by      varchar(64),
    balance_id          varchar(32),
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_inv_outbound_tenant_no ON inv_outbound_order (tenant_id, order_no) WHERE deleted = false;
CREATE UNIQUE INDEX uk_inv_outbound_idempotency ON inv_outbound_order (tenant_id, idempotency_key) WHERE deleted = false;
CREATE INDEX idx_inv_outbound_status ON inv_outbound_order (status) WHERE deleted = false;
CREATE INDEX idx_inv_outbound_material ON inv_outbound_order (material_id) WHERE deleted = false;
CREATE INDEX idx_inv_outbound_warehouse ON inv_outbound_order (warehouse_id) WHERE deleted = false;

-- ===== 库存流水 (不可变, 任何库存变动都写入流水) =====
-- 注: 流水本身在业务层不提供 update/delete 接口, 但因 InvStockTransaction extends BaseEntity,
--     MyBatis-Plus 仍会引用 deleted/version/updated_by/updated_time/remark 列, 需保留这些列以避免 500 错误。
CREATE TABLE inv_stock_transaction (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    transaction_no      varchar(32)   NOT NULL,
    balance_id          varchar(32)   NOT NULL,
    material_id         varchar(32)   NOT NULL,
    warehouse_id        varchar(32)   NOT NULL,
    -- IN 入库 / OUT 出库 / COMPENSATE 补偿
    transaction_type    varchar(16)   NOT NULL,
    quantity            numeric(18,4) NOT NULL,
    -- 变动前/变动后数量, 用于审计追溯
    quantity_before     numeric(18,4) NOT NULL,
    quantity_after      numeric(18,4) NOT NULL,
    -- 关联业务单据
    biz_order_type      varchar(16)   NOT NULL,
    biz_order_id        varchar(32)   NOT NULL,
    biz_order_no        varchar(32),
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    -- BaseEntity 必需字段 (MyBatis-Plus 引用)
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_inv_transaction_tenant_no ON inv_stock_transaction (tenant_id, transaction_no);
CREATE INDEX idx_inv_transaction_balance ON inv_stock_transaction (balance_id);
CREATE INDEX idx_inv_transaction_material ON inv_stock_transaction (material_id);
CREATE INDEX idx_inv_transaction_biz ON inv_stock_transaction (biz_order_type, biz_order_id);
CREATE INDEX idx_inv_transaction_created ON inv_stock_transaction (created_time);

-- ===== 种子数据: 物料 =====
INSERT INTO inv_material (id, tenant_id, material_code, material_name, material_type, spec, unit, category, barcode, reference_price, status, remark) VALUES
('inv-mat-001', 'default', 'MAT-001', '主板 V2.0', 'ELECTRONIC', '300mm x 200mm', 'PCS', '主板', '6900000000001', 850.0000, 'ACTIVE', 'GA2-37 种子物料'),
('inv-mat-002', 'default', 'MAT-002', '内存 16G', 'ELECTRONIC', 'DDR4 16G', 'PCS', '内存', '6900000000002', 280.0000, 'ACTIVE', 'GA2-37 种子物料'),
('inv-mat-003', 'default', 'MAT-003', '硬盘 1T', 'ELECTRONIC', 'SSD 1TB', 'PCS', '硬盘', '6900000000003', 480.0000, 'ACTIVE', 'GA2-37 种子物料'),
('inv-mat-004', 'default', 'MAT-004', '机箱', 'ACCESSORY', 'ATX 中塔', 'PCS', '机箱', '6900000000004', 180.0000, 'ACTIVE', 'GA2-37 种子物料');

-- ===== 种子数据: 仓库 =====
INSERT INTO inv_warehouse (id, tenant_id, warehouse_code, warehouse_name, warehouse_type, address, manager_user_id, status, remark) VALUES
('inv-wh-001', 'default', 'WH-001', '中央仓', 'CENTRAL', '北京市朝阳区', 'mock-admin', 'ACTIVE', 'GA2-37 种子仓库'),
('inv-wh-002', 'default', 'WH-002', '上海分仓', 'BRANCH', '上海市浦东新区', 'mock-biz', 'ACTIVE', 'GA2-37 种子仓库');

-- ===== 种子数据: 库存余额 =====
INSERT INTO inv_stock_balance (id, tenant_id, material_id, warehouse_id, quantity, locked_quantity, last_in_time, remark) VALUES
('inv-bal-001', 'default', 'inv-mat-001', 'inv-wh-001', 100.0000, 0.0000, now(), 'GA2-37 种子余额: 主板 100 件'),
('inv-bal-002', 'default', 'inv-mat-002', 'inv-wh-001', 200.0000, 0.0000, now(), 'GA2-37 种子余额: 内存 200 件'),
('inv-bal-003', 'default', 'inv-mat-003', 'inv-wh-001', 50.0000, 0.0000, now(), 'GA2-37 种子余额: 硬盘 50 件'),
('inv-bal-004', 'default', 'inv-mat-001', 'inv-wh-002', 30.0000, 0.0000, now(), 'GA2-37 种子余额: 主板上海 30 件');

-- ===== 种子数据: 入库单 (1 条 CONFIRMED 历史记录, 1 条 DRAFT 草稿) =====
INSERT INTO inv_inbound_order (id, tenant_id, order_no, idempotency_key, warehouse_id, material_id, quantity, unit_cost, total_amount, status, inbound_type, batch_no, supplier, confirmed_time, confirmed_by, balance_id, remark) VALUES
('inv-inb-001', 'default', 'IN202601010001', 'idem-inb-001', 'inv-wh-001', 'inv-mat-001', 100.0000, 850.0000, 85000.0000, 'CONFIRMED', 'PURCHASE', 'BATCH-2026-001', '北京供应商', now(), 'mock-admin', 'inv-bal-001', 'GA2-37 种子入库单'),
('inv-inb-002', 'default', 'IN202607180001', 'idem-inb-002', 'inv-wh-001', 'inv-mat-002', 200.0000, 280.0000, 56000.0000, 'CONFIRMED', 'PURCHASE', 'BATCH-2026-002', '上海供应商', now(), 'mock-admin', 'inv-bal-002', 'GA2-37 种子入库单');

-- ===== 种子数据: 出库单 (1 条 CONFIRMED 历史记录) =====
INSERT INTO inv_outbound_order (id, tenant_id, order_no, idempotency_key, warehouse_id, material_id, quantity, unit_cost, total_amount, status, outbound_type, batch_no, customer, confirmed_time, confirmed_by, balance_id, remark) VALUES
('inv-out-001', 'default', 'OUT202601150001', 'idem-out-001', 'inv-wh-001', 'inv-mat-003', 10.0000, 480.0000, 4800.0000, 'CONFIRMED', 'SALE', 'BATCH-2026-003', '北京客户', now(), 'mock-admin', 'inv-bal-003', 'GA2-37 种子出库单');

-- ===== 种子数据: 库存流水 =====
INSERT INTO inv_stock_transaction (id, tenant_id, transaction_no, balance_id, material_id, warehouse_id, transaction_type, quantity, quantity_before, quantity_after, biz_order_type, biz_order_id, biz_order_no) VALUES
('inv-tx-001', 'default', 'TX202601010001', 'inv-bal-001', 'inv-mat-001', 'inv-wh-001', 'IN', 100.0000, 0.0000, 100.0000, 'INBOUND', 'inv-inb-001', 'IN202601010001'),
('inv-tx-002', 'default', 'TX202601010002', 'inv-bal-002', 'inv-mat-002', 'inv-wh-001', 'IN', 200.0000, 0.0000, 200.0000, 'INBOUND', 'inv-inb-002', 'IN202607180001'),
('inv-tx-003', 'default', 'TX202601150001', 'inv-bal-003', 'inv-mat-003', 'inv-wh-001', 'OUT', -10.0000, 60.0000, 50.0000, 'OUTBOUND', 'inv-out-001', 'OUT202601150001');
