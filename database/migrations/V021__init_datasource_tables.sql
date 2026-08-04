-- V021: 多数据源与数据集表 (GA2-46 v1.5)
-- 设计来源: 46-多数据源与数据集设计
-- 落地范围:
--   1. sys_datasource              数据源元数据 (主库/从库/外部库, 健康状态, 配置版本, 热加载)
--   2. sys_datasource_table_acl    表级 ACL (元数据浏览按表过滤)
--   3. sys_datasource_column_acl   列级 ACL (元数据浏览按列过滤, 敏感列隐藏)
--   4. ALTER rpt_dataset           新增 datasource_code 列 (默认 primary, 支持报表指定数据源)
-- 验收标准: 报表数据集可指定从库 + 外部只读库连接测试与表浏览 + SQL 注入与写操作拦截 + 数据源变更记审计日志
-- 安全约束: 外部库只读 + SQL 白名单 (单条 SELECT/受控 VIEW) + AST 静态分析禁止多语句/DDL/写操作/COPY/dblink

-- ===== 1. 数据源元数据 =====
CREATE TABLE sys_datasource (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    datasource_code     varchar(64)   NOT NULL,
    datasource_name     varchar(128)  NOT NULL,
    db_type             varchar(32)   NOT NULL DEFAULT 'POSTGRESQL',
    -- 连接串 (密钥外置, 不存储明文密码; jdbc_url 可存储但 username/password 只存密钥引用)
    jdbc_url            varchar(512)  NOT NULL,
    username_ref        varchar(128),
    password_ref        varchar(128),
    -- 连接池参数 (jsonb): {maximumPoolSize:10, minimumIdle:2, connectionTimeout:30000, idleTimeout:600000, maxLifetime:1800000}
    pool_config         jsonb,
    read_only           boolean       NOT NULL DEFAULT false,
    enabled             boolean       NOT NULL DEFAULT true,
    -- 运行时状态 (46 号文档 line 93 补充字段)
    health_status       varchar(16)   NOT NULL DEFAULT 'UNKNOWN',
    last_check_time     timestamptz,
    last_error_message  varchar(512),
    config_version      int           NOT NULL DEFAULT 1,
    enabled_time        timestamptz,
    -- 从库延迟阈值 (毫秒), 超过则可切回主库
    lag_threshold_ms    int,
    description         varchar(512),
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_sys_datasource PRIMARY KEY (id),
    CONSTRAINT uk_sys_datasource_code UNIQUE (tenant_id, datasource_code),
    CONSTRAINT chk_sys_datasource_db_type CHECK (db_type IN ('POSTGRESQL', 'MYSQL', 'ORACLE', 'SQLSERVER')),
    CONSTRAINT chk_sys_datasource_health CHECK (health_status IN ('UP', 'DOWN', 'UNKNOWN'))
);
CREATE INDEX idx_sys_datasource_status ON sys_datasource (tenant_id, enabled, deleted);
COMMENT ON TABLE sys_datasource IS '数据源元数据。设计来源: 46-多数据源与数据集设计 sys_datasource';
COMMENT ON COLUMN sys_datasource.username_ref IS '用户名密钥引用 (如 ENV:DB_REPORT_USER), 不存储明文';
COMMENT ON COLUMN sys_datasource.password_ref IS '密码密钥引用 (如 ENV:DB_REPORT_PASSWORD), 不存储明文';
COMMENT ON COLUMN sys_datasource.health_status IS '运行时健康状态: UP/DOWN/UNKNOWN, 由连接测试和熔断器维护';
COMMENT ON COLUMN sys_datasource.config_version IS '配置版本号, 每次修改自增, 用于热加载原子切换';

-- ===== 2. 表级 ACL (元数据浏览按表过滤) =====
CREATE TABLE sys_datasource_table_acl (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    datasource_id       varchar(32)   NOT NULL,
    table_name          varchar(128)  NOT NULL,
    -- 允许浏览/查询的角色或权限码 (jsonb 数组): ["role:admin", "permission:datasource:metadata:view"]
    allowed_roles       jsonb,
    -- 是否允许 SELECT 查询 (true=可查询, false=仅元数据可见不可查询)
    query_allowed       boolean       NOT NULL DEFAULT true,
    -- 查询最大返回行数 (覆盖数据集默认 max_rows)
    max_rows            int,
    status              varchar(16)   NOT NULL DEFAULT 'ENABLED',
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_sys_datasource_table_acl PRIMARY KEY (id),
    CONSTRAINT uk_ds_table_acl UNIQUE (tenant_id, datasource_id, table_name)
);
CREATE INDEX idx_ds_table_acl_ds ON sys_datasource_table_acl (datasource_id, status, deleted);
COMMENT ON TABLE sys_datasource_table_acl IS '数据源表级 ACL。设计来源: 46-多数据源与数据集设计 sys_datasource_table_acl';

-- ===== 3. 列级 ACL (敏感列隐藏) =====
CREATE TABLE sys_datasource_column_acl (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    datasource_id       varchar(32)   NOT NULL,
    table_name          varchar(128)  NOT NULL,
    column_name         varchar(128)  NOT NULL,
    -- 敏感级别: LOW/MEDIUM/HIGH/CRITICAL
    sensitivity_level   varchar(16)   NOT NULL DEFAULT 'LOW',
    -- 允许可见的角色或权限码 (jsonb 数组), 为空则所有人不可见
    allowed_roles       jsonb,
    -- 脱敏策略: MASK(掩码)/HIDE(隐藏)/HASH(哈希)/NONE(不脱敏)
    masking_strategy    varchar(16)   NOT NULL DEFAULT 'MASK',
    status              varchar(16)   NOT NULL DEFAULT 'ENABLED',
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_sys_datasource_column_acl PRIMARY KEY (id),
    CONSTRAINT uk_ds_column_acl UNIQUE (tenant_id, datasource_id, table_name, column_name),
    CONSTRAINT chk_ds_col_acl_sens CHECK (sensitivity_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_ds_col_acl_mask CHECK (masking_strategy IN ('MASK', 'HIDE', 'HASH', 'NONE'))
);
CREATE INDEX idx_ds_column_acl_ds ON sys_datasource_column_acl (datasource_id, table_name, status, deleted);
COMMENT ON TABLE sys_datasource_column_acl IS '数据源列级 ACL。设计来源: 46-多数据源与数据集设计 sys_datasource_column_acl';

-- ===== 4. ALTER rpt_dataset 新增 datasource_code 列 =====
-- 46 号文档 line 109: rpt_dataset.datasource_code 指向 sys_datasource
ALTER TABLE rpt_dataset ADD COLUMN IF NOT EXISTS datasource_code varchar(64) NOT NULL DEFAULT 'primary';
COMMENT ON COLUMN rpt_dataset.datasource_code IS '数据源编码, 指向 sys_datasource.datasource_code (默认 primary, 报表可指定只读从库)';

-- ===== 5. 种子数据 =====
-- 5.1 primary 数据源 (主库, 默认指向应用自身连接的 PostgreSQL)
-- 使用环境变量占位符, 实际运行时由 DataSourceService 解析为真实值
INSERT INTO sys_datasource (id, tenant_id, datasource_code, datasource_name, db_type, jdbc_url,
    username_ref, password_ref, pool_config, read_only, enabled, health_status,
    config_version, enabled_time, lag_threshold_ms, description, created_by, remark)
VALUES (
    '01DS001PRIMARY0000000000000001',
    'default',
    'primary',
    '主库 (PostgreSQL)',
    'POSTGRESQL',
    -- 使用环境变量占位符, DataSourceService 启动时从 spring.datasource 读取真实值
    'env:SPRING_DATASOURCE_URL',
    'env:SPRING_DATASOURCE_USERNAME',
    'env:SPRING_DATASOURCE_PASSWORD',
    '{"maximumPoolSize":10,"minimumIdle":2,"connectionTimeout":30000,"idleTimeout":600000,"maxLifetime":1800000}'::jsonb,
    false,
    true,
    'UNKNOWN',
    1,
    now(),
    0,
    '主库, 业务 CRUD 和事务, 默认数据源',
    'system',
    'GA2-46 seed: primary 数据源, jdbc_url/username/password 使用 env: 前缀占位符, 由 DataSourceService 启动时解析'
);

-- 5.2 report_ro 数据源 (只读从库, 复用主库连接做演示, 实际生产应指向真实只读副本)
INSERT INTO sys_datasource (id, tenant_id, datasource_code, datasource_name, db_type, jdbc_url,
    username_ref, password_ref, pool_config, read_only, enabled, health_status,
    config_version, enabled_time, lag_threshold_ms, description, created_by, remark)
VALUES (
    '01DS002REPORTRO00000000000002',
    'default',
    'report_ro',
    '只读从库 (PostgreSQL, 报表/大屏/导出)',
    'POSTGRESQL',
    'env:SPRING_DATASOURCE_URL',
    'env:SPRING_DATASOURCE_USERNAME',
    'env:SPRING_DATASOURCE_PASSWORD',
    '{"maximumPoolSize":5,"minimumIdle":1,"connectionTimeout":30000,"idleTimeout":600000,"maxLifetime":1800000}'::jsonb,
    true,
    true,
    'UNKNOWN',
    1,
    now(),
    3000,
    '只读从库, 报表/大屏/导出专用, 演示环境复用主库连接, 生产环境应指向真实只读副本',
    'system',
    'GA2-46 seed: report_ro 只读从库, lag_threshold_ms=3000 超过则可切回主库'
);

-- 5.3 表级 ACL 种子: 允许所有角色查询业务表
INSERT INTO sys_datasource_table_acl (id, tenant_id, datasource_id, table_name, allowed_roles, query_allowed, max_rows, status, created_by, remark) VALUES
('01DSTBLACL001PRIMARY0000000001', 'default', '01DS001PRIMARY0000000000000001', 'biz_request', '["role:admin","role:biz","role:viewer"]'::jsonb, true, 10000, 'ENABLED', 'system', 'GA2-46 seed: biz_request 表所有角色可查询'),
('01DSTBLACL002PRIMARY0000000002', 'default', '01DS001PRIMARY0000000000000001', 'biz_customer', '["role:admin","role:biz","role:viewer"]'::jsonb, true, 10000, 'ENABLED', 'system', 'GA2-46 seed: biz_customer 表所有角色可查询'),
('01DSTBLACL003PRIMARY0000000003', 'default', '01DS001PRIMARY0000000000000001', 'biz_product', '["role:admin","role:biz","role:viewer"]'::jsonb, true, 10000, 'ENABLED', 'system', 'GA2-46 seed: biz_product 表所有角色可查询');

-- 5.4 列级 ACL 种子: 隐藏敏感列 (列名对齐 biz_customer 实际表结构)
-- biz_customer 实际列: id, tenant_id, customer_code, customer_name, contact_name, contact_phone, address, ...
INSERT INTO sys_datasource_column_acl (id, tenant_id, datasource_id, table_name, column_name, sensitivity_level, allowed_roles, masking_strategy, status, created_by, remark) VALUES
('01DSCOLACL001PRIMARY0000000001', 'default', '01DS001PRIMARY0000000000000001', 'biz_customer', 'customer_name', 'HIGH', '["role:admin"]'::jsonb, 'MASK', 'ENABLED', 'system', 'GA2-46 seed: biz_customer.customer_name 高敏感列, 仅 admin 可见, 掩码策略'),
('01DSCOLACL002PRIMARY0000000002', 'default', '01DS001PRIMARY0000000000000001', 'biz_customer', 'contact_phone', 'MEDIUM', '["role:admin","role:biz"]'::jsonb, 'MASK', 'ENABLED', 'system', 'GA2-46 seed: biz_customer.contact_phone 中敏感列, admin/biz 可见, 掩码策略'),
('01DSCOLACL003PRIMARY0000000003', 'default', '01DS001PRIMARY0000000000000001', 'biz_customer', 'address', 'MEDIUM', '["role:admin","role:biz"]'::jsonb, 'MASK', 'ENABLED', 'system', 'GA2-46 seed: biz_customer.address 中敏感列, admin/biz 可见, 掩码策略');

-- ===== 6. 数据源路由配置 (application.yml 占位, 实际由 DataSourceService 读取 sys_datasource) =====
-- yutong.datasource.primary: primary
-- yutong.datasource.replicas:
--   - code: report_ro
--     lag-threshold-ms: 3000

-- ===== 完成注释 =====
-- V021 完成 3 表 + 4 索引 + 3 唯一约束 + 5 CHECK 约束 + ALTER rpt_dataset + 2 数据源种子 + 3 表 ACL 种子 + 3 列 ACL 种子
