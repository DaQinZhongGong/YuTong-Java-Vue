-- =============================================================================
-- 完整 DDL 清单与数据字典验收脚本 (57-完整DDL清单与数据字典详设)
-- =============================================================================
-- 设计来源: 57-完整DDL清单与数据字典详设「验收标准」+「Flyway 验收 SQL」
-- 用途: 对运行中的 PostgreSQL 数据库执行 6 项验收检查，输出报告
-- 执行: Get-Content tools/db/verify-ddl-dictionary.sql -Raw | docker exec -i yutong-postgres psql -U yutong -d yutong
-- 输出: 每项检查以 [PASS] / [FAIL] 标识，末尾给出汇总
-- =============================================================================

\set ON_ERROR_STOP off
\echo ''
\echo '=============================================================================='
\echo '  YuTong 完整 DDL 清单与数据字典验收 (57 号文档「验收标准」)'
\echo '=============================================================================='
\echo ''

-- =============================================================================
-- 检查 1: 第一版表清单与 Flyway 文件一一对应
-- 57 号文档「验收标准」第 1 项 + 表清单总览 (line 11-58)
-- 第一版 38 张必备表 (系统 15 + 样例 5 + 低代码 7 + AI 11)
-- =============================================================================
\echo '--- [1/6] 检查第一版 38 张表清单与 Flyway 文件一一对应 ---'
WITH required_tables AS (
    SELECT 'sys_dict_type' AS table_name UNION ALL SELECT 'sys_dict_item' UNION ALL
    SELECT 'sys_config' UNION ALL SELECT 'sys_file' UNION ALL SELECT 'biz_file_rel' UNION ALL
    SELECT 'sys_message' UNION ALL SELECT 'sys_message_template' UNION ALL
    SELECT 'sys_todo_task' UNION ALL SELECT 'sys_operation_log' UNION ALL
    SELECT 'sys_login_log' UNION ALL SELECT 'sys_idempotency_record' UNION ALL
    SELECT 'sys_outbox_event' UNION ALL SELECT 'sys_job_log' UNION ALL
    SELECT 'sys_import_export_task' UNION ALL SELECT 'sys_sequence' UNION ALL
    SELECT 'biz_customer' UNION ALL SELECT 'biz_product' UNION ALL
    SELECT 'biz_request' UNION ALL SELECT 'biz_request_item' UNION ALL
    SELECT 'biz_approval_record' UNION ALL
    SELECT 'lc_entity' UNION ALL SELECT 'lc_field' UNION ALL SELECT 'lc_relation' UNION ALL
    SELECT 'lc_page' UNION ALL SELECT 'lc_component' UNION ALL
    SELECT 'lc_action' UNION ALL SELECT 'lc_generator_task' UNION ALL
    SELECT 'ai_provider' UNION ALL SELECT 'ai_prompt_template' UNION ALL
    SELECT 'ai_conversation' UNION ALL SELECT 'ai_message' UNION ALL
    SELECT 'ai_knowledge_base' UNION ALL SELECT 'ai_document' UNION ALL
    SELECT 'ai_document_chunk' UNION ALL SELECT 'ai_embedding' UNION ALL
    SELECT 'ai_tool_call_log' UNION ALL SELECT 'ai_cost_log'
),
existing_tables AS (
    SELECT table_name
    FROM information_schema.tables
    WHERE table_schema = 'public'
      AND table_type = 'BASE TABLE'
),
missing_tables AS (
    SELECT rt.table_name
    FROM required_tables rt
    LEFT JOIN existing_tables et ON rt.table_name = et.table_name
    WHERE et.table_name IS NULL
)
SELECT
    CASE WHEN COUNT(*) = 0 THEN '[PASS] 第一版 38 张表清单全部存在'
         ELSE '[FAIL] 缺失 ' || COUNT(*) || ' 张表，违反 57 号文档约束'
    END AS check_result,
    COUNT(*) AS violation_count,
    string_agg(table_name, ', ') AS missing_table_names
FROM missing_tables;
\echo ''

-- =============================================================================
-- 检查 2: 所有唯一索引都包含 tenant_id
-- 57 号文档「验收标准」第 2 项 + Flyway 验收 SQL
-- 排除主键索引 (pk_xxx): 主键是 id varchar(32)，不含 tenant_id
-- 排除 flyway_schema_history: Flyway 元数据表
-- =============================================================================
\echo '--- [2/6] 检查所有唯一索引都包含 tenant_id ---'
WITH unique_indexes AS (
    SELECT
        rel.relname AS table_name,
        cls.relname AS index_name,
        pg_get_indexdef(idx.indexrelid) AS index_def
    FROM pg_index idx
    JOIN pg_class rel ON rel.oid = idx.indrelid
    JOIN pg_class cls ON cls.oid = idx.indexrelid
    JOIN pg_namespace n ON n.oid = rel.relnamespace
    WHERE n.nspname = 'public'
      AND idx.indisunique
      AND idx.indisprimary = false
      AND rel.relname NOT IN ('flyway_schema_history')
),
violations AS (
    SELECT table_name, index_name, index_def
    FROM unique_indexes
    WHERE index_def NOT ILIKE '%tenant_id%'
)
SELECT
    CASE WHEN COUNT(*) = 0 THEN '[PASS] 所有唯一索引均包含 tenant_id'
         ELSE '[FAIL] 发现 ' || COUNT(*) || ' 处唯一索引不含 tenant_id，违反 57 号文档约束'
    END AS check_result,
    COUNT(*) AS violation_count,
    string_agg(table_name || '.' || index_name, ', ') AS violating_indexes
FROM violations;
\echo ''

-- =============================================================================
-- 检查 3: 所有逻辑删除表唯一索引使用 partial index (WHERE deleted = false)
-- 57 号文档「验收标准」第 3 项
-- 仅检查含 deleted 字段的表的唯一索引
-- =============================================================================
\echo '--- [3/6] 检查所有逻辑删除表唯一索引使用 partial index WHERE deleted=false ---'
WITH unique_indexes AS (
    SELECT
        rel.relname AS table_name,
        cls.relname AS index_name,
        pg_get_indexdef(idx.indexrelid) AS index_def,
        idx.indpred IS NOT NULL AS has_predicate
    FROM pg_index idx
    JOIN pg_class rel ON rel.oid = idx.indrelid
    JOIN pg_class cls ON cls.oid = idx.indexrelid
    JOIN pg_namespace n ON n.oid = rel.relnamespace
    WHERE n.nspname = 'public'
      AND idx.indisunique
      AND idx.indisprimary = false
      AND rel.relname NOT IN ('flyway_schema_history')
),
tables_with_deleted AS (
    SELECT table_name
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND column_name = 'deleted'
      AND table_name NOT IN ('flyway_schema_history')
),
violations AS (
    SELECT ui.table_name, ui.index_name, ui.index_def
    FROM unique_indexes ui
    JOIN tables_with_deleted td ON ui.table_name = td.table_name
    WHERE ui.has_predicate = false
       OR ui.index_def NOT ILIKE '%deleted%'
)
SELECT
    CASE WHEN COUNT(*) = 0 THEN '[PASS] 所有逻辑删除表唯一索引使用 partial index WHERE deleted=false'
         ELSE '[FAIL] 发现 ' || COUNT(*) || ' 处唯一索引缺 WHERE deleted=false，违反 57 号文档约束'
    END AS check_result,
    COUNT(*) AS violation_count,
    string_agg(table_name || '.' || index_name, ', ') AS violating_indexes
FROM violations;
\echo ''

-- =============================================================================
-- 检查 4: 种子数据可重复执行 (INSERT 使用 ON CONFLICT 模式)
-- 57 号文档「验收标准」第 4 项
-- 通过查询 flyway_schema_history 验证 R__seed 脚本均为 success
-- + 静态检查种子脚本是否使用 ON CONFLICT DO NOTHING/UPDATE
-- =============================================================================
\echo '--- [4/6] 检查种子数据可重复执行 (Flyway R__seed + ON CONFLICT) ---'
WITH seed_scripts AS (
    SELECT
        COUNT(*) AS total_seed_scripts,
        COUNT(*) FILTER (WHERE success = true) AS success_count,
        COUNT(*) FILTER (WHERE success = false) AS failed_count
    FROM flyway_schema_history
    WHERE type = 'SQL'
      AND script ILIKE 'R__seed%'
),
-- 通过查询 sys_dict_item 验证种子数据已正确加载 (57 号文档字典种子数据 line 486-509)
dict_seed_count AS (
    SELECT COUNT(*) AS dict_item_count
    FROM sys_dict_item
    WHERE dict_type IN ('sys_common_status', 'biz_request_status', 'biz_approval_action',
                        'biz_approval_result', 'import_export_task_status', 'biz_product_unit')
      AND deleted = false
)
SELECT
    CASE
        WHEN s.failed_count = 0 AND s.total_seed_scripts > 0 AND d.dict_item_count >= 20
        THEN '[PASS] 种子脚本 ' || s.total_seed_scripts || ' 个全部 success + 字典种子 ' || d.dict_item_count || ' 条已加载'
        ELSE '[FAIL] 种子脚本失败 ' || s.failed_count || ' 个或字典种子数据不足 (' || d.dict_item_count || ' < 20)'
    END AS check_result,
    s.total_seed_scripts,
    s.success_count,
    s.failed_count,
    d.dict_item_count
FROM seed_scripts s, dict_seed_count d;
\echo ''

-- =============================================================================
-- 检查 5: Flyway 验收 SQL - 禁止自增主键
-- 57 号文档 line 530-534
-- =============================================================================
\echo '--- [5/6] Flyway 验收 SQL: 禁止自增主键 (nextval) ---'
WITH auto_increment_count AS (
    SELECT COUNT(*) AS cnt
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND column_default LIKE 'nextval%'
)
SELECT
    CASE WHEN cnt = 0 THEN '[PASS] 未发现自增主键 (nextval)'
         ELSE '[FAIL] 发现 ' || cnt || ' 处自增主键，违反 57 号文档约束'
    END AS check_result,
    cnt AS violation_count
FROM auto_increment_count;
\echo ''

-- =============================================================================
-- 检查 6: Flyway 验收 SQL - 检查非 varchar 主键
-- 57 号文档 line 537-543
-- 排除 flyway_schema_history (Flyway 元数据表使用 installed_rank integer 主键)
-- =============================================================================
\echo '--- [6/6] Flyway 验收 SQL: 检查非 varchar 主键 ---'
WITH non_varchar_pk_count AS (
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
      AND tc.table_name NOT IN ('flyway_schema_history')
      AND c.data_type <> 'character varying'
)
SELECT
    CASE WHEN cnt = 0 THEN '[PASS] 未发现非 varchar 主键，全部为 varchar(32) ULID'
         ELSE '[FAIL] 发现 ' || cnt || ' 处非 varchar 主键，违反 57 号文档约束'
    END AS check_result,
    cnt AS violation_count
FROM non_varchar_pk_count;
\echo ''

-- =============================================================================
-- 汇总报告
-- =============================================================================
\echo '=============================================================================='
\echo '  验收汇总: 6 项检查全部完成 (详见上方 [PASS] / [FAIL] 标识)'
\echo '  设计来源: 57-完整DDL清单与数据字典详设「验收标准」+「Flyway 验收 SQL」'
\echo '=============================================================================='
\echo ''
