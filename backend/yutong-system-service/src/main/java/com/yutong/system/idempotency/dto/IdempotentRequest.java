package com.yutong.system.idempotency.dto;

/**
 * 幂等请求参数。由 IdempotentAspect 从 HTTP 请求头、注解元数据和方法参数构建。
 *
 * <p>设计来源: 98-后端实现蓝图与代码骨架详设 (幂等实现模板第 1 步)
 *
 * @param idempotencyKey 客户端生成的幂等键 (来自 Idempotency-Key 头)
 * @param resourceType   资源类型 (如 biz:request)
 * @param resourceId     资源 ID (CREATE 场景为 null，UPDATE/APPROVE 场景为业务 ID)
 * @param action         动作 (如 CREATE/UPDATE/APPROVE)
 * @param requestHash    请求体 SHA-256 hash (hex 截断 128 字符)
 * @param ttlSeconds     PROCESSING 锁定时长 (秒，30~120)
 * @param retryOnFailed  业务失败是否允许同 key 重试
 */
public record IdempotentRequest(
        String idempotencyKey,
        String resourceType,
        String resourceId,
        String action,
        String requestHash,
        long ttlSeconds,
        boolean retryOnFailed
) {
}
