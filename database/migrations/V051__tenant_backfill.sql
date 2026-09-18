-- ============================================================
-- V051__tenant_backfill.sql
-- P2-A 租户行级隔离: 历史 NULL tenant_id 回填 'default'
-- 设计来源: ADR 0004 P2-A (common-tenant) + YutongTenantLineHandler
-- 约束: 幂等 DO 块 (重复执行无副作用), 禁止 Flyway 占位 ${}
-- 说明: 遍历 public schema 下所有含 tenant_id 列的表, 将 NULL 回填 'default'
--      (与 YutongMetaObjectHandler 默认租户一致)。种子数据均已带租户, 本迁移主要覆盖
--      早期手工建表/测试残留行。
-- ============================================================

DO $$
DECLARE
    r record;
    v_updated bigint;
BEGIN
    FOR r IN
        SELECT DISTINCT c.table_name
        FROM information_schema.columns c
        JOIN information_schema.tables t
          ON t.table_schema = c.table_schema AND t.table_name = c.table_name
        WHERE c.table_schema = 'public'
          AND c.column_name = 'tenant_id'
          AND t.table_type = 'BASE TABLE'
    LOOP
        EXECUTE format('UPDATE %I SET tenant_id = ''default'' WHERE tenant_id IS NULL', r.table_name);
        GET DIAGNOSTICS v_updated = ROW_COUNT;
        IF v_updated > 0 THEN
            RAISE NOTICE 'V051 backfilled % rows in %', v_updated, r.table_name;
        END IF;
    END LOOP;
END $$;
