package com.yutong.common.id;

import com.github.f4b6a3.ulid.UlidCreator;

/**
 * 主键生成器。统一生成 26 字符 ULID，作为字符串主键使用。
 * 设计来源: 05-数据架构设计、06-技术选型决策记录、product-baseline.yaml idPolicy
 * 约束: 禁止使用数据库自增主键；所有业务表 id 由本工具生成。
 */
public final class IdGenerator {

    private IdGenerator() {}

    /** 生成 26 字符 ULID 字符串。 */
    public static String nextId() {
        return UlidCreator.getMonotonicUlid().toString();
    }
}
