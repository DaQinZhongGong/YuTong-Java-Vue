-- V009: Fix sys_client_event.id column width from varchar(26) to varchar(32)
-- Design: 05-数据架构设计, 51-数据库物理模型与DDL详设, 99-CI流水线与发布证据自动化详设
-- Reason: All other GA tables use varchar(32) for ULID primary keys (with 6-char reserve).
--         V007 used varchar(26) (exact ULID length) causing check-id-policy violation.
--         This migration aligns sys_client_event with the platform-wide varchar(32) standard.
-- Safety: ALTER COLUMN TYPE varchar(32) is a widening operation, always safe for existing data.

ALTER TABLE sys_client_event ALTER COLUMN id TYPE varchar(32);
