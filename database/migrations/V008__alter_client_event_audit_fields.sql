-- ============================================================
-- V008: 放宽 sys_client_event 审计字段长度
-- 设计来源: 94-端侧埋点与体验监控详设 (GA2-18 冒烟验证发现)
-- 背景: sys_client_event 的 batch 端点为 PublicEndpoint，接受未登录页面上报。
--       local profile 下 MockAuthAdapter 注入的 mock userId 长度 28 字符
--       (如 01MOCKUSER0000000000000ADMIN)，超过 varchar(26) 导致 500。
--       其他业务表 created_by 仅由认证用户操作（真实 ULID 26 字符），不受影响。
-- 修复: 将 sys_client_event 的 created_by/updated_by 放宽至 varchar(64)，
--       兼容 ULID(26) 和 mock/外部 userId 变体。
-- ============================================================

ALTER TABLE sys_client_event
    ALTER COLUMN created_by TYPE varchar(64),
    ALTER COLUMN updated_by TYPE varchar(64);

COMMENT ON COLUMN sys_client_event.created_by IS '创建人 userId（放宽至 64 兼容 PublicEndpoint mock 用户）';
COMMENT ON COLUMN sys_client_event.updated_by IS '更新人 userId（放宽至 64 兼容 PublicEndpoint mock 用户）';
