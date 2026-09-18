-- ============================================================
-- V052__message_parent.sql
-- P2-C 会话分支地基: 消息父子链
-- 设计来源: ADR 0004 P2-C (会话分支; 树 UI 后续批次, 本批次先落链路数据)
-- 约束: 幂等 ADD COLUMN IF NOT EXISTS, 禁止 Flyway 占位 ${}
-- 语义: parent_message_id 为空 = 线性链首条; 新消息 parent = 发送时视图末条;
--      重试沿用同样规则。树形导航 UI 读取本链组装, 无需额外接口
--      (GET /conversations/{id}/messages 已返回全字段)。
-- 无硬外键 (软删除 + ULID 模型, 跨库迁移友好)。
-- ============================================================

ALTER TABLE ai_message ADD COLUMN IF NOT EXISTS parent_message_id varchar(32);

CREATE INDEX IF NOT EXISTS idx_ai_message_parent ON ai_message (tenant_id, parent_message_id) WHERE deleted = false;

COMMENT ON COLUMN ai_message.parent_message_id IS '父消息 ID (V052 P2-C 分支链), 空=链首; 同一会话内必须指向本会话消息 (service 层校验)';
