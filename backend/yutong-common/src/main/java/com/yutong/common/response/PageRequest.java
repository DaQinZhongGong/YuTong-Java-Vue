package com.yutong.common.response;

import java.util.List;

/**
 * 分页请求。统一字符串 ID，page 从 1 开始。
 * 设计来源: 08-API契约设计
 */
public record PageRequest(
        int page,
        int size,
        List<String> sortFields,
        List<String> sortOrders
) {
    public PageRequest {
        if (page < 1) page = 1;
        if (size < 1 || size > 500) size = 20;
        if (sortFields == null) sortFields = List.of();
        if (sortOrders == null) sortOrders = List.of();
    }

    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size, null, null);
    }

    public long offset() {
        return (long) (page - 1) * size;
    }
}
