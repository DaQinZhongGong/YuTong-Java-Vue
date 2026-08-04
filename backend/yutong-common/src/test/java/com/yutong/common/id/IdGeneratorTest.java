package com.yutong.common.id;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * IdGenerator 单元测试
 * 验证 ULID 生成器的基本功能
 */
class IdGeneratorTest {

    @Test
    void shouldGenerate26CharUlid() {
        String ulid = IdGenerator.nextId();
        assertNotNull(ulid);
        assertEquals(26, ulid.length(), "ULID should be 26 characters");
    }

    @Test
    void shouldGenerateUniqueIds() {
        String id1 = IdGenerator.nextId();
        String id2 = IdGenerator.nextId();
        assertNotEquals(id1, id2, "Generated IDs should be unique");
    }

    @Test
    void shouldGenerateMonotonicIds() {
        // Monotonic ULIDs should be sortable
        String id1 = IdGenerator.nextId();
        String id2 = IdGenerator.nextId();
        String id3 = IdGenerator.nextId();
        assertTrue(id1.compareTo(id2) < 0, "id1 should be less than id2");
        assertTrue(id2.compareTo(id3) < 0, "id2 should be less than id3");
    }

    @Test
    void shouldGenerateMultipleIdsRapidly() {
        for (int i = 0; i < 100; i++) {
            String id = IdGenerator.nextId();
            assertNotNull(id);
            assertEquals(26, id.length());
        }
    }
}
