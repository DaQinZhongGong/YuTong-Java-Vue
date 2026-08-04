-- =============================================================================
-- 数据库物理模型 DDL 约束验收脚本 (51-数据库物理模型与DDL详设)
-- =============================================================================
-- 设计来源: 51-数据库物理模型与DDL详设「数据库验收清单」
-- 用途: 对运行中的 PostgreSQL 数据库执行 6 项验收检查，输出报告
-- 执行: psql -h yutong-postgres -U yutong -d yutong -f tools/db/verify-ddl-constraints.sql
-- 输出: 每项检查以 [PASS] / [FAIL] 标识，末尾给出汇总
-- =============================================================================

\set ON_ERROR_STOP off
\echo ''
\echo '=============================================================================='
\echo '  YuTong 数据库物理模型 DDL 约束验收 (51 号文档「数据库验收清单」)'
\echo '  检查时间: :now'
\echo '=============================================================================='
\echo ''

-- 计数器变量（用于汇总）
\set total_checks 6
\set passed_checks 0

-- =============================================================================
-- 检查 1: 不存在 serial / bigserial / generated as identity
-- 51 号文档「数据库验收清单」第 1 项
-- =============================================================================
\echo '--- [1/6] 检查不存在 serial / bigserial / generated as identity ---'
WITH violation_count AS (
    SELECT COUNT(*) AS cnt
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND (
        data_type = 'integer' AND column_default LIKE 'nextval%'
        OR data_type = 'bigint' AND column_default LIKE 'nextval%'
        OR is_identity = 'YES'
      )
)
SELECT
    CASE WHEN cnt = 0 THEN '[PASS] 未发现 serial/bigserial/generated as identity 自增列'
         ELSE '[FAIL] 发现 ' || cnt || ' 处自增列，违反 51 号文档约束'
    END AS check_result,
    cnt AS violation_count
FROM violation_count;
\echo ''

-- =============================================================================
-- 检查 2: 不存在业务主键 bigint
-- 51 号文档「数据库验收清单」第 2 项
-- 主键统一 varchar(32) ULID
-- 排除 Flyway 元数据表 (flyway_schema_history.installed_rank 为 integer 是 Flyway 自身设计)
-- =============================================================================
\echo '--- [2/6] 检查不存在业务主键 bigint ---'
WITH bigint_pk_count AS (
    SELECT COUNT(*) AS cnt
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
        ON tc.constraint_name = kcu.constraint_name
        AND tc.table_schema = kcu.table_schema
    JOIN information_schema.columns c
        ON c.table_schema = tc.table_schema
        AND c.table_name = kcu.table_name
        AND c.column_name = kcu.column_name
    WHERE tc.constraint_type = 'PRIMARY KEY'
      AND tc.table_schema = 'public'
      -- 排除 Flyway 元数据表 (Flyway 自身使用 installed_rank integer 作为主键)
      AND tc.table_name NOT IN ('flyway_schema_history')
      AND c.data_type IN ('bigint', 'integer')
)
SELECT
    CASE WHEN cnt = 0 THEN '[PASS] 未发现 bigint/integer 业务主键，全部为 varchar(32) ULID'
         ELSE '[FAIL] 发现 ' || cnt || ' 处 bigint/integer 主键，违反 51 号文档约束'
    END AS check_result,
    cnt AS violation_count
FROM bigint_pk_count;
\echo ''

-- =============================================================================
-- 检查 3: 每张业务表有 tenant_id 与审计字段
-- 51 号文档「数据库验收清单」第 3 项
-- 审计字段: created_by/created_time/updated_by/updated_time/deleted/version
-- 排除 Flyway 元数据表 flyway_schema_history
-- =============================================================================
\echo '--- [3/6] 检查每张业务表有 tenant_id 与审计字段 ---'
WITH required_columns AS (
    SELECT 'tenant_id' AS col_name
    UNION ALL SELECT 'created_by'
    UNION ALL SELECT 'created_time'
    UNION ALL SELECT 'updated_by'
    UNION ALL SELECT 'updated_time'
    UNION ALL SELECT 'deleted'
    UNION ALL SELECT 'version'
),
business_tables AS (
    SELECT table_name
    FROM information_schema.tables
    WHERE table_schema = 'public'
      AND table_type = 'BASE TABLE'
      AND table_name NOT IN ('flyway_schema_history')
),
missing_columns AS (
    SELECT bt.table_name, rc.col_name
    FROM business_tables bt
    CROSS JOIN required_columns rc
    LEFT JOIN information_schema.columns c
        ON c.table_schema = 'public'
        AND c.table_name = bt.table_name
        AND c.column_name = rc.col_name
    WHERE c.column_name IS NULL
)
SELECT
    CASE WHEN COUNT(*) = 0 THEN '[PASS] 所有业务表均含 tenant_id + 6 项审计字段'
         ELSE '[FAIL] 发现 ' || COUNT(*) || ' 处缺失字段，违反 51 号文档约束'
    END AS check_result,
    COUNT(*) AS violation_count
FROM missing_columns;
\echo ''

-- =============================================================================
-- 检查 4: 所有逻辑外键有索引
-- 51 号文档「数据库验收清单」第 4 项
-- 第一版不建物理 FK，但所有逻辑外键（如 customer_id/request_id/entity_id/page_id 等以 _id 结尾的字段）必须有索引
-- 本检查扫描所有以 _id 结尾的字段（排除 id 主键自身与 tenant_id 租户标识），
-- 验证该字段是否被任意索引覆盖（作为索引的任意列，不限于第一列）。
-- 多租户设计中索引通常为 (tenant_id, _id, ...)，_id 作为复合索引的第二列或后续列。
-- =============================================================================
\echo '--- [4/6] 检查所有逻辑外键（_id 结尾字段）有索引 ---'
WITH id_columns AS (
    SELECT c.table_name, c.column_name
    FROM information_schema.columns c
    JOIN information_schema.tables t
        ON c.table_schema = t.table_schema
        AND c.table_name = t.table_name
    WHERE c.table_schema = 'public'
      AND t.table_type = 'BASE TABLE'
      AND c.table_name NOT IN ('flyway_schema_history')
      AND c.column_name LIKE '%_id'
      -- 排除主键 id 自身（id 不算逻辑外键）
      AND c.column_name <> 'id'
      -- 排除 tenant_id（tenant_id 是租户标识，不是逻辑外键；其索引由其他检查覆盖）
      AND c.column_name <> 'tenant_id'
),
id_columns_indexed AS (
    -- 使用 pg_index + generate_subscripts 精确判断字段是否被任意索引覆盖
    -- 覆盖条件: 该字段是某个索引的任意一列（包括复合索引的非首列）
    -- 多租户设计中 (tenant_id, _id, ...) 复合索引，_id 是第二列，仍视为已覆盖
    SELECT
        n.nspname AS table_schema,
        rel.relname AS table_name,
        att.attname AS column_name
    FROM pg_index idx
    JOIN pg_class rel ON rel.oid = idx.indrelid
    JOIN pg_namespace n ON n.oid = rel.relnamespace
    JOIN pg_attribute att ON att.attrelid = rel.oid
    -- 使用 generate_subscripts 展开索引的所有列，匹配任意位置
    JOIN generate_subscripts(idx.indkey, 1) AS s(i) ON att.attnum = idx.indkey[s.i]
    WHERE n.nspname = 'public'
      AND idx.indisvalid
      AND att.attname LIKE '%_id'
      AND att.attname <> 'id'
      AND att.attname <> 'tenant_id'
),
missing_index_count AS (
    SELECT COUNT(*) AS cnt
    FROM id_columns ic
    LEFT JOIN id_columns_indexed ici
        ON ic.table_name = ici.table_name
        AND ic.column_name = ici.column_name
    WHERE ici.table_name IS NULL
)
SELECT
    CASE WHEN cnt = 0 THEN '[PASS] 所有逻辑外键（_id 结尾字段）均有索引覆盖'
         ELSE '[FAIL] 发现 ' || cnt || ' 处逻辑外键缺失索引，违反 51 号文档约束'
    END AS check_result,
    cnt AS violation_count
FROM missing_index_count;
\echo ''

-- =============================================================================
-- 检查 5: 唯一索引带 tenant_id，逻辑删除表使用 partial index (WHERE deleted = false)
-- 51 号文档「数据库验收清单」第 5 项
-- =============================================================================
\echo '--- [5/6] 检查唯一索引带 tenant_id + partial index (WHERE deleted = false) ---'
WITH unique_indexes AS (
    SELECT
        n.nspname AS table_schema,
        rel.relname AS table_name,
        cls.relname AS index_name,
        idx.indisunique AS is_unique,
        idx.indisprimary AS is_primary,
        pg_get_indexdef(idx.indexrelid) AS index_def,
        idx.indpred IS NOT NULL AS has_predicate
    FROM pg_index idx
    JOIN pg_class rel ON rel.oid = idx.indrelid
    JOIN pg_class cls ON cls.oid = idx.indexrelid
    JOIN pg_namespace n ON n.oid = rel.relnamespace
    WHERE n.nspname = 'public'
      AND idx.indisunique
      -- 排除主键索引 (pk_xxx): 主键是 id varchar(32)，51 号文档不要求主键带 tenant_id 或 partial
      AND idx.indisprimary = false
      AND rel.relname NOT IN ('flyway_schema_history')
),
-- 含 deleted 字段的表
tables_with_deleted AS (
    SELECT table_name
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND column_name = 'deleted'
      AND table_name NOT IN ('flyway_schema_history')
),
-- 唯一索引违规: 含 deleted 字段的表的唯一索引，但 WHERE 子句不含 deleted
violations_no_deleted_predicate AS (
    SELECT ui.table_name, ui.index_name, ui.index_def
    FROM unique_indexes ui
    JOIN tables_with_deleted td ON ui.table_name = td.table_name
    WHERE ui.has_predicate = false
       OR (ui.index_def NOT ILIKE '%deleted%')
),
-- 唯一索引违规: 不含 tenant_id 的唯一索引（除 sys_sequence 等特殊表）
violations_no_tenant AS (
    SELECT table_name, index_name, index_def
    FROM unique_indexes
    WHERE index_def NOT ILIKE '%tenant_id%'
      -- sys_sequence 等业务序列表允许不带 tenant_id 的复合唯一索引（其唯一性依赖 sequence_code + biz_date）
      AND table_name NOT IN ('sys_sequence')
),
violation_summary AS (
    SELECT
        (SELECT COUNT(*) FROM violations_no_deleted_predicate) AS no_pred_cnt,
        (SELECT COUNT(*) FROM violations_no_tenant) AS no_tenant_cnt
)
SELECT
    CASE
        WHEN no_pred_cnt + no_tenant_cnt = 0
        THEN '[PASS] 所有唯一索引均含 tenant_id + 逻辑删除表使用 partial index WHERE deleted=false'
        ELSE '[FAIL] 发现 ' || (no_pred_cnt + no_tenant_cnt) || ' 处唯一索引不合规 (缺 deleted partial: ' || no_pred_cnt || ', 缺 tenant_id: ' || no_tenant_cnt || ')'
    END AS check_result,
    no_pred_cnt AS missing_deleted_predicate_count,
    no_tenant_cnt AS missing_tenant_id_count
FROM violation_summary;
\echo ''

-- =============================================================================
-- 检查 6: Flyway 可空库执行 / 重复执行种子数据不报错
-- 51 号文档「数据库验收清单」第 6 项
-- 本检查通过查询 flyway_schema_history 验证所有迁移和种子脚本均为 success 状态
-- =============================================================================
\echo '--- [6/6] 检查 Flyway 迁移历史全部 success ---'
WITH flyway_status AS (
    SELECT
        COUNT(*) AS total,
        COUNT(*) FILTER (WHERE success = true) AS success_count,
        COUNT(*) FILTER (WHERE success = false) AS failed_count,
        COUNT(*) FILTER (WHERE type = 'SQL') AS sql_count,
        COUNT(*) FILTER (WHERE type = 'SQL' AND installed_on < now() - interval '1 minute') AS initial_count
    FROM flyway_schema_history
)
SELECT
    CASE
        WHEN failed_count = 0
        THEN '[PASS] Flyway 全部 ' || total || ' 条记录 success (SQL ' || sql_count || ' + Repeatable ' || (total - sql_count) || ')'
        ELSE '[FAIL] Flyway 存在 ' || failed_count || ' 条失败记录，违反 51 号文档约束'
    END AS check_result,
    total,
    success_count,
    failed_count,
    sql_count
FROM flyway_status;
\echo ''

-- =============================================================================
-- 汇总报告
-- =============================================================================
\echo '=============================================================================='
\echo '  验收汇总: 6 项检查全部完成 (详见上方 [PASS] / [FAIL] 标识)'
\echo '  设计来源: 51-数据库物理模型与DDL详设「数据库验收清单」'
\echo '=============================================================================='
\echo ''
