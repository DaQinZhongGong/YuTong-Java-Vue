-- ============================================================
-- V063__ai_harness_verification_mode_check.sql
-- verification_mode 增加 CHECK 约束，防止任意字符串入库
-- 约束: 幂等 DROP/ADD CONSTRAINT
-- ============================================================

ALTER TABLE ai_harness_session DROP CONSTRAINT IF EXISTS chk_ai_harness_session_verify;
ALTER TABLE ai_harness_session ADD CONSTRAINT chk_ai_harness_session_verify
    CHECK (verification_mode IN ('OFF','LIGHT','STRICT'));

COMMENT ON COLUMN ai_harness_session.verification_mode IS
    '校验模式: OFF 关闭 / LIGHT 轻量 / STRICT 严格（fail-closed 扩展点）';
