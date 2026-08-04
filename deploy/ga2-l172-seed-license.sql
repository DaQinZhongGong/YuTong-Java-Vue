-- GA2-L172 冒烟测试: 插入 Professional 版 License (含 ai 模块 + 额度上限)
-- 用于验证 AI 额度扣减 checkQuota + recordQuotaUsage 完整流程
INSERT INTO sys_license (
    id, tenant_id, license_id, edition, subject, deployment_id,
    license_schema_version, limits_json, modules_json, expire_time,
    signature, status, created_by, created_time, updated_time, deleted, version
) VALUES (
    '01TESTLICENSE000000000000001',
    'default',
    'LIC-TEST-2026-0001',
    'Professional',
    'Test Tenant (GA2-L172 Smoke)',
    'test-deployment-001',
    '1.0.0',
    '{"ai.monthly.tokens": 1000000}'::jsonb,
    '["system","sample","lowcode","ai","report","workflow","datasource","plugin"]'::jsonb,
    '2027-12-31 23:59:59+08:00',
    'test-signature-ga2-l172',
    'ACTIVE',
    'system',
    now(),
    now(),
    false,
    0
) ON CONFLICT (license_id) DO UPDATE SET
    status = 'ACTIVE',
    limits_json = '{"ai.monthly.tokens": 1000000}'::jsonb,
    modules_json = '["system","sample","lowcode","ai","report","workflow","datasource","plugin"]'::jsonb,
    expire_time = '2027-12-31 23:59:59+08:00';

-- 清理之前的额度使用记录 (如果存在), 便于验证从 0 开始累加
DELETE FROM sys_license_usage WHERE tenant_id = 'default' AND quota_code = 'ai.monthly.tokens';
