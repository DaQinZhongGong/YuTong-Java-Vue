-- GA2-L173: 低代码生成额度扣减 + 报表导出额度扣减 冒烟测试种子 License
-- 在 GA2-L172 种子基础上追加 lowcode.generate.count + report.export.count 额度上限
-- Professional 版含全部 8 模块 + 3 个额度上限
INSERT INTO sys_license (
    id, tenant_id, license_id, edition, subject, deployment_id,
    license_schema_version, limits_json, modules_json, expire_time,
    signature, status, created_by, created_time, updated_time, deleted, version
) VALUES (
    '01TESTLICENSE000000000000001',
    'default',
    'LIC-TEST-2026-0001',
    'Professional',
    'Test Tenant (GA2-L173 Smoke)',
    'test-deployment-001',
    '1.0.0',
    '{"ai.monthly.tokens": 1000000, "lowcode.generate.count": 100, "report.export.count": 50}'::jsonb,
    '["system","sample","lowcode","ai","report","workflow","datasource","plugin"]'::jsonb,
    '2027-12-31 23:59:59+08:00',
    'test-signature-ga2-l173',
    'ACTIVE',
    'system',
    now(),
    now(),
    false,
    0
) ON CONFLICT (license_id) DO UPDATE SET
    status = 'ACTIVE',
    limits_json = '{"ai.monthly.tokens": 1000000, "lowcode.generate.count": 100, "report.export.count": 50}'::jsonb,
    modules_json = '["system","sample","lowcode","ai","report","workflow","datasource","plugin"]'::jsonb,
    expire_time = '2027-12-31 23:59:59+08:00';

-- 清理本轮测试的额度记录（如有），确保冒烟从 0 开始
DELETE FROM sys_license_usage WHERE tenant_id = 'default' AND quota_code IN ('lowcode.generate.count', 'report.export.count');
