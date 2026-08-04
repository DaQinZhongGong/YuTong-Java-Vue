package com.yutong.common.response;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Result 统一响应测试
 */
class ResultTest {

    @Test
    void shouldCreateSuccessResult() {
        Result<String> result = Result.ok("test-data", "trace-123");
        assertEquals("0", result.code());
        assertEquals("success", result.message());
        assertEquals("test-data", result.data());
        assertEquals("trace-123", result.traceId());
        assertNotNull(result.timestamp());
    }

    @Test
    void shouldCreateSuccessResultWithoutTraceId() {
        Result<String> result = Result.ok("test-data");
        assertEquals("0", result.code());
        assertEquals("success", result.message());
        assertEquals("test-data", result.data());
    }

    @Test
    void shouldCreateEmptySuccessResult() {
        Result<Void> result = Result.ok();
        assertEquals("0", result.code());
        assertEquals("success", result.message());
        assertNull(result.data());
    }

    @Test
    void shouldCreateFailResult() {
        Result<Void> result = Result.fail("SYS-500001", "Internal error", "trace-789");
        assertEquals("SYS-500001", result.code());
        assertEquals("Internal error", result.message());
        assertNull(result.data());
        assertEquals("trace-789", result.traceId());
    }

    @Test
    void shouldCheckSuccess() {
        Result<String> success = Result.ok("data", "trace");
        assertTrue(success.success());

        Result<Void> fail = Result.fail("SYS-500001", "error", "trace");
        assertFalse(fail.success());
    }
}
