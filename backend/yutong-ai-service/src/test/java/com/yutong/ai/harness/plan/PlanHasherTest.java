package com.yutong.ai.harness.plan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * CanonicalPlanHasher 纯函数单测。
 */
@DisplayName("CanonicalPlanHasher")
class PlanHasherTest {

    @Test
    @DisplayName("相同输入产生相同哈希")
    void stable() {
        String h1 = CanonicalPlanHasher.hash("t1", 0, "PLAN", "AWAITING_APPROVAL", "# plan", "[1]");
        String h2 = CanonicalPlanHasher.hash("t1", 0, "PLAN", "AWAITING_APPROVAL", "# plan", "[1]");
        assertEquals(h1, h2);
        assertEquals(64, h1.length());
    }

    @Test
    @DisplayName("任一字段变化改变哈希")
    void eachFieldMatters() {
        String base = CanonicalPlanHasher.hash("t1", 0, "PLAN", "DRAFT", "md", "[]");
        assertNotEquals(base, CanonicalPlanHasher.hash("t2", 0, "PLAN", "DRAFT", "md", "[]"));
        assertNotEquals(base, CanonicalPlanHasher.hash("t1", 1, "PLAN", "DRAFT", "md", "[]"));
        assertNotEquals(base, CanonicalPlanHasher.hash("t1", 0, "BUILD", "DRAFT", "md", "[]"));
        assertNotEquals(base, CanonicalPlanHasher.hash("t1", 0, "PLAN", "APPROVED", "md", "[]"));
        assertNotEquals(base, CanonicalPlanHasher.hash("t1", 0, "PLAN", "DRAFT", "md2", "[]"));
        assertNotEquals(base, CanonicalPlanHasher.hash("t1", 0, "PLAN", "DRAFT", "md", "[1]"));
    }

    @Test
    @DisplayName("null 字段按空串规范化，不抛异常")
    void nullSafe() {
        String h = CanonicalPlanHasher.hash(null, 0, null, null, null, null);
        assertEquals(CanonicalPlanHasher.hash("", 0, "", "", "", ""), h);
    }

    @Test
    @DisplayName("与 CanonicalHashes.sha256Hex 一致（管道拼接）")
    void matchesDirectSha256() {
        String expected = com.yutong.ai.harness.approval.CanonicalHashes.sha256Hex(
                "1|t1|0|PLAN|DRAFT|md|[]");
        assertEquals(expected, CanonicalPlanHasher.hash("t1", 0, "PLAN", "DRAFT", "md", "[]"));
    }

    @Test
    @DisplayName("SCHEMA_VERSION 参与哈希拼接")
    void schemaVersionPresent() {
        assertEquals("1", CanonicalPlanHasher.SCHEMA_VERSION);
        // schema 变更会导致历史计划哈希失效，调用方需显式升级 SCHEMA_VERSION
        String withV1 = CanonicalPlanHasher.hash("t", 0, "PLAN", "DRAFT", "md", "[]");
        assertEquals(withV1, com.yutong.ai.harness.approval.CanonicalHashes.sha256Hex(
                "1|t|0|PLAN|DRAFT|md|[]"));
    }
}
