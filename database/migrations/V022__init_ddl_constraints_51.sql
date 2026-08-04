-- V022: 数据库物理模型补齐索引 (51-数据库物理模型与DDL详设)
-- 设计来源: 51-数据库物理模型与DDL详设 (GA2-49)
-- 用途: 补齐 51 号文档要求但 V003/V004 未创建的索引
-- 约束: 主键 varchar(32) ULID + tenant_id 必带 + partial unique index where deleted=false

-- ===== 1. lc_component 唯一索引 (51 号文档 line 140) =====
-- V004 创建 lc_component 表时未建唯一索引，51 号文档要求 uk_lc_component_page_code(tenant_id, page_id, component_code)
-- 用于保证同一页面下组件编码唯一，防止重复组件配置
CREATE UNIQUE INDEX IF NOT EXISTS uk_lc_component_page_code
    ON lc_component (tenant_id, page_id, component_code)
    WHERE deleted = false;

-- ===== 2. biz_request 客户维度索引 (51 号文档 line 112) =====
-- V003 缺失 idx_biz_request_customer(tenant_id, customer_id)，用于按客户查询申请单的性能优化
CREATE INDEX IF NOT EXISTS idx_biz_request_customer
    ON biz_request (tenant_id, customer_id)
    WHERE deleted = false;

-- ===== 3. biz_request 创建时间索引 (51 号文档 line 113) =====
-- V003 缺失 idx_biz_request_created(tenant_id, created_time desc)，用于按创建时间倒序分页/查询
-- 已有 V006 idx_biz_request_keyset_time (tenant_id, created_time DESC, id) 可覆盖此场景，
-- 但 51 号文档明确要求 idx_biz_request_created，按文档要求显式创建（keyset 索引可继续用于 keyset 分页）
CREATE INDEX IF NOT EXISTS idx_biz_request_created
    ON biz_request (tenant_id, created_time DESC)
    WHERE deleted = false;

-- ===== 4. 索引注释 =====
COMMENT ON INDEX uk_lc_component_page_code IS '51 号文档: 低代码组件表唯一索引 (tenant_id, page_id, component_code)';
COMMENT ON INDEX idx_biz_request_customer IS '51 号文档: 申请单表客户维度索引 (tenant_id, customer_id)';
COMMENT ON INDEX idx_biz_request_created IS '51 号文档: 申请单表创建时间倒序索引 (tenant_id, created_time DESC)';
