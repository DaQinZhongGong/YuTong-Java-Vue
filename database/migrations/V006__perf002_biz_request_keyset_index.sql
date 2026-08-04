-- V006: PERF-002 申请单 keyset pagination 调优索引
-- 设计来源: 72-性能容量规划与压测方案详设 (调优策略: 索引、覆盖查询)
-- 问题: 申请单分页默认按 created_time DESC 排序，深翻页时 OFFSET 性能急剧下降。
--       现有 idx_biz_request_status (tenant_id, request_status, submitted_time) 前导列为 request_status，
--       不支持通用列表的 created_time DESC 排序场景，导致 OFFSET 分页走全表扫描 + 排序。
-- 优化:
--   1. 新增 (tenant_id, created_time DESC, id) 复合索引，支撑默认排序下的 keyset pagination
--      查询条件 WHERE tenant_id=? AND created_time < ? ORDER BY created_time DESC, id DESC LIMIT N
--      可完全走索引，避免回表排序。
--   2. keyset pagination 用 WHERE created_time < cursor 替代 OFFSET，深翻页性能稳定 O(log N)。
-- 幂等: 使用 IF NOT EXISTS，支持重复执行不报错。

CREATE INDEX IF NOT EXISTS idx_biz_request_keyset_time
    ON biz_request (tenant_id, created_time DESC, id);

COMMENT ON INDEX idx_biz_request_keyset_time IS 'PERF-002 keyset pagination 调优索引，支撑 created_time DESC 默认排序分页';
