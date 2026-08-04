package com.yutong.common.response;

import java.util.List;

/**
 * 分页结果。统一输出结构，不向后端上层泄露 MyBatis-Plus 的 Page 对象。
 * 设计来源: 98-后端实现蓝图与代码骨架详设
 *
 * <p>GA2-09-2 扩展: 新增 {@code nextCursor} 字段，支持 keyset pagination (PERF-002 调优)。
 * <ul>
 *   <li>OFFSET 模式 (默认): nextCursor = null，total 为精确总数</li>
 *   <li>keyset 模式: nextCursor 为下一页游标 (到底时为 null)，total = -1 表示无精确总数</li>
 * </ul>
 * 现有调用点零修改：{@link #of} / {@link #empty} 保持 nextCursor=null。
 */
public record PageResult<T>(
        List<T> records,
        long total,
        int page,
        int size,
        String nextCursor
) {
    public static <T> PageResult<T> of(List<T> records, long total, int page, int size) {
        return new PageResult<>(records, total, page, size, null);
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(List.of(), 0L, page, size, null);
    }

    /**
     * keyset pagination 工厂方法。
     *
     * @param records    当前页记录 (已截断到 size)
     * @param page       页码 (keyset 模式通常为 1，仅作占位)
     * @param size       每页大小
     * @param nextCursor 下一页游标，null 表示已到末尾
     * @return PageResult，total=-1 表示 keyset 模式无精确总数
     */
    public static <T> PageResult<T> ofKeyset(List<T> records, int page, int size, String nextCursor) {
        return new PageResult<>(records, -1L, page, size, nextCursor);
    }
}
