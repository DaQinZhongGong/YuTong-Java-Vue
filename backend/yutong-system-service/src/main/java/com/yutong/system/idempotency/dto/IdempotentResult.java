package com.yutong.system.idempotency.dto;

/**
 * 幂等执行结果。由 IdempotencyService.execute 返回，供 IdempotentAspect 透传到 Controller。
 *
 * <p>设计来源: 98-后端实现蓝图与代码骨架详设 (幂等实现模板第 4.2 步)
 *
 * @param data   业务返回值；replay=true 时为从 response_snapshot 反序列化的重放结果
 * @param replay 是否为重放 (true=来自快照，false=本次新执行)
 * @param <T>    业务返回类型
 */
public record IdempotentResult<T>(
        T data,
        boolean replay
) {
    /** 构造本次新执行结果。 */
    public static <T> IdempotentResult<T> success(T data) {
        return new IdempotentResult<>(data, false);
    }

    /** 构造重放结果 (来自 response_snapshot)。 */
    public static <T> IdempotentResult<T> replay(T data) {
        return new IdempotentResult<>(data, true);
    }
}
