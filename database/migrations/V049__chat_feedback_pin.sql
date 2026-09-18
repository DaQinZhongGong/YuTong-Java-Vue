-- ============================================================
-- V049__chat_feedback_pin.sql
-- P2-C 会话管理增强: 消息反馈 + 会话置顶
-- 设计来源: ADR 0004 P2-C (会话分支/收藏/置顶/反馈/重新生成)
--   - 本批次: 反馈 (LIKE/DISLIKE) + 置顶 (pinned)
--   - 重新生成: 纯前端 (重发上一条用户消息), 无需后端变更
--   - 会话分支: 后续批次 (需 parent_message_id 树结构)
-- 约束: 幂等 ADD COLUMN IF NOT EXISTS, 禁止 Flyway 占位 ${}
-- ============================================================

-- ------------------------------------------------------------
-- 1. ai_message.feedback — 用户对 assistant 回复的评价
-- 仅 assistant 消息可评价 (service 层校验 role), null = 未评价
-- ------------------------------------------------------------
ALTER TABLE ai_message ADD COLUMN IF NOT EXISTS feedback varchar(16);

ALTER TABLE ai_message DROP CONSTRAINT IF EXISTS chk_ai_message_feedback;
ALTER TABLE ai_message ADD CONSTRAINT chk_ai_message_feedback
    CHECK (feedback IS NULL OR feedback IN ('LIKE','DISLIKE'));

CREATE INDEX IF NOT EXISTS idx_ai_message_feedback ON ai_message (tenant_id, feedback) WHERE deleted = false AND feedback IS NOT NULL;

COMMENT ON COLUMN ai_message.feedback IS '用户反馈 LIKE/DISLIKE (V049 P2-C), null=未评价, 仅 assistant 消息可评价';

-- ------------------------------------------------------------
-- 2. ai_conversation.pinned — 会话置顶
-- true 排在分页最前 (service 按 pinned DESC, last_message_time DESC)
-- ------------------------------------------------------------
ALTER TABLE ai_conversation ADD COLUMN IF NOT EXISTS pinned boolean NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_ai_conversation_user_pinned ON ai_conversation (tenant_id, user_id, pinned DESC, last_message_time DESC) WHERE deleted = false;

COMMENT ON COLUMN ai_conversation.pinned IS '会话置顶 (V049 P2-C), true 在列表最前';
