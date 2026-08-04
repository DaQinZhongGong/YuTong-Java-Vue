package com.yutong.system.idempotency.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.IdempotencyConflictException;
import com.yutong.common.exception.IdempotencyProcessingException;
import com.yutong.system.idempotency.domain.IdempotencyRecord;
import com.yutong.system.idempotency.dto.IdempotentRequest;
import com.yutong.system.idempotency.dto.IdempotentResult;
import com.yutong.system.idempotency.mapper.IdempotencyRecordMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IdempotencyService 单元测试。
 * 设计来源: 98-后端实现蓝图与代码骨架详设 (验收清单: 幂等 SUCCESS 重放、PROCESSING 冲突、hash 冲突、FAILED 重试均有测试)
 *
 * <p>覆盖场景:
 * <ol>
 *   <li>首次请求: PROCESSING → SUCCESS 路径，业务正常执行</li>
 *   <li>无 Idempotency-Key: 透传业务不启用幂等</li>
 *   <li>同 key 同 hash SUCCESS 重放: 返回 snapshot</li>
 *   <li>同 key 不同 hash 冲突: SYS-409003</li>
 *   <li>同 key 同 hash PROCESSING 未超时: SYS-409002</li>
 *   <li>同 key 同 hash FAILED 不允许重试: SYS-409004</li>
 *   <li>同 key 同 hash FAILED 允许重试: 重新执行成功</li>
 *   <li>业务异常: 写 FAILED 后重抛</li>
 *   <li>Redis 短锁被占: SYS-409002</li>
 *   <li>request_hash 计算: 相同参数返回相同 hash</li>
 * </ol>
 */
class IdempotencyServiceTest {

    private IdempotencyRecordMapper mapper;
    private StringRedisTemplate redis;
    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> valueOps;
    private PlatformTransactionManager txManager;
    private TransactionTemplate transactionTemplate;
    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        mapper = mock(IdempotencyRecordMapper.class);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        txManager = mock(PlatformTransactionManager.class);
        transactionTemplate = mock(TransactionTemplate.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        // 模拟 REQUIRES_NEW 事务: 直接执行 callback
        doAnswer(inv -> {
            Consumer<TransactionStatus> cb = inv.getArgument(0);
            TransactionStatus status = mock(TransactionStatus.class);
            cb.accept(status);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        doAnswer(inv -> {
            org.springframework.transaction.support.TransactionCallback<?> cb = inv.getArgument(0);
            TransactionStatus status = mock(TransactionStatus.class);
            return cb.doInTransaction(status);
        }).when(transactionTemplate).execute(any());

        // 通过反射构造 IdempotencyService 并注入 mock transactionTemplate
        // 直接用真实 TransactionTemplate 但 mock PlatformTransactionManager
        // 真实 TransactionTemplate 会调用 txManager.getTransaction/commit
        // 简化: 让 txManager 的 getTransaction 返回 mock status，commit 不做任何事
        TransactionStatus mockStatus = mock(TransactionStatus.class);
        when(txManager.getTransaction(any())).thenReturn(mockStatus);
        doAnswer(inv -> null).when(txManager).commit(any());

        // 直接构造 service，但替换 requiresNewTx 为 mock
        service = new IdempotencyServiceForTest(mapper, redis, new ObjectMapper(), txManager, transactionTemplate);

        // 设置 CurrentUserContext
        CurrentUserContext.set("01USER0000000000000000000A", "01TENANT00000000000000000A", "test-user");
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    @DisplayName("首次请求: PROCESSING → SUCCESS，业务正常执行")
    void testFirstRequestSuccess() {
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(mapper.insert(any(IdempotencyRecord.class))).thenReturn(1);
        when(mapper.updateById(any(IdempotencyRecord.class))).thenReturn(1);

        IdempotentRequest request = buildRequest("key-1", "hash-1", false);
        IdempotentResult<String> result = service.execute(request, () -> "OK", String.class);

        assertFalse(result.replay());
        assertEquals("OK", result.data());
        verify(mapper, times(1)).insert(any(IdempotencyRecord.class));
        verify(mapper, times(1)).updateById(any(IdempotencyRecord.class));
        verify(redis, times(1)).delete(anyString());
    }

    @Test
    @DisplayName("无 Idempotency-Key: 透传业务不启用幂等")
    void testNoIdempotencyKeyPassthrough() {
        IdempotentRequest request = new IdempotentRequest(
                "", "biz:request", null, "CREATE", "hash", 60L, false);
        IdempotentResult<String> result = service.execute(request, () -> "DIRECT", String.class);

        assertFalse(result.replay());
        assertEquals("DIRECT", result.data());
        verify(mapper, never()).insert(any(IdempotencyRecord.class));
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("同 key 同 hash SUCCESS 重放: 返回 snapshot")
    void testSuccessReplay() {
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        // 模拟插入冲突
        when(mapper.insert(any(IdempotencyRecord.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        // 查询返回 SUCCESS 记录
        IdempotencyRecord existing = new IdempotencyRecord();
        existing.setId("rec-1");
        existing.setStatus(IdempotencyService.STATUS_SUCCESS);
        existing.setRequestHash("hash-1");
        existing.setResponseSnapshot("\"REPLAY-OK\"");
        when(mapper.selectOne(any())).thenReturn(existing);

        IdempotentRequest request = buildRequest("key-1", "hash-1", false);
        IdempotentResult<String> result = service.execute(request, () -> "NEW", String.class);

        assertTrue(result.replay());
        assertEquals("REPLAY-OK", result.data());
        verify(mapper, never()).updateById(any(IdempotencyRecord.class));
    }

    @Test
    @DisplayName("同 key 不同 hash 冲突: 抛 SYS-409003")
    void testHashConflict() {
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(mapper.insert(any(IdempotencyRecord.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        IdempotencyRecord existing = new IdempotencyRecord();
        existing.setId("rec-1");
        existing.setStatus(IdempotencyService.STATUS_SUCCESS);
        existing.setRequestHash("different-hash");
        when(mapper.selectOne(any())).thenReturn(existing);

        IdempotentRequest request = buildRequest("key-1", "hash-1", false);
        assertThrows(IdempotencyConflictException.class,
                () -> service.execute(request, () -> "NEW", String.class));
    }

    @Test
    @DisplayName("同 key 同 hash PROCESSING 未超时: 抛 SYS-409002")
    void testProcessingConflict() {
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(mapper.insert(any(IdempotencyRecord.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        IdempotencyRecord existing = new IdempotencyRecord();
        existing.setId("rec-1");
        existing.setStatus(IdempotencyService.STATUS_PROCESSING);
        existing.setRequestHash("hash-1");
        existing.setLockedUntil(OffsetDateTime.now().plusSeconds(60));
        when(mapper.selectOne(any())).thenReturn(existing);

        IdempotentRequest request = buildRequest("key-1", "hash-1", false);
        assertThrows(IdempotencyProcessingException.class,
                () -> service.execute(request, () -> "NEW", String.class));
    }

    @Test
    @DisplayName("同 key 同 hash FAILED 不允许重试: 抛 SYS-409004")
    void testFailedNoRetry() {
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(mapper.insert(any(IdempotencyRecord.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        IdempotencyRecord existing = new IdempotencyRecord();
        existing.setId("rec-1");
        existing.setStatus(IdempotencyService.STATUS_FAILED);
        existing.setRequestHash("hash-1");
        existing.setVersion(1);
        when(mapper.selectOne(any())).thenReturn(existing);

        IdempotentRequest request = buildRequest("key-1", "hash-1", false);
        assertThrows(BusinessConflictException.class,
                () -> service.execute(request, () -> "NEW", String.class));
    }

    @Test
    @DisplayName("同 key 同 hash FAILED 允许重试: CAS 成功后重新执行")
    void testFailedRetrySuccess() {
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(mapper.insert(any(IdempotencyRecord.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        IdempotencyRecord existing = new IdempotencyRecord();
        existing.setId("rec-1");
        existing.setStatus(IdempotencyService.STATUS_FAILED);
        existing.setRequestHash("hash-1");
        existing.setVersion(1);
        when(mapper.selectOne(any())).thenReturn(existing);
        // CAS 成功
        when(mapper.updateById(any(IdempotencyRecord.class))).thenReturn(1);

        IdempotentRequest request = buildRequest("key-1", "hash-1", true);
        IdempotentResult<String> result = service.execute(request, () -> "RETRY-OK", String.class);

        assertFalse(result.replay());
        assertEquals("RETRY-OK", result.data());
    }

    @Test
    @DisplayName("业务异常: 写 FAILED 后重抛原异常")
    void testBusinessFailureWritesFailed() {
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(mapper.insert(any(IdempotencyRecord.class))).thenReturn(1);
        when(mapper.updateById(any(IdempotencyRecord.class))).thenReturn(1);

        IdempotentRequest request = buildRequest("key-1", "hash-1", false);
        RuntimeException businessError = new RuntimeException("business failed");
        assertThrows(RuntimeException.class,
                () -> service.execute(request, () -> {
                    throw businessError;
                }, String.class));

        // 验证 FAILED 写入
        verify(mapper, times(1)).updateById(any(IdempotencyRecord.class));
    }

    @Test
    @DisplayName("Redis 短锁被占: 抛 SYS-409002")
    void testRedisLockHeld() {
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(false);

        IdempotentRequest request = buildRequest("key-1", "hash-1", false);
        assertThrows(IdempotencyProcessingException.class,
                () -> service.execute(request, () -> "OK", String.class));
        verify(mapper, never()).insert(any(IdempotencyRecord.class));
    }

    @Test
    @DisplayName("request_hash 计算: 相同参数返回相同 hash")
    void testComputeRequestHashDeterministic() {
        String h1 = IdempotencyService.computeRequestHash("arg1", 123, true);
        String h2 = IdempotencyService.computeRequestHash("arg1", 123, true);
        assertEquals(h1, h2);
        assertNotNull(h1);
        assertTrue(h1.length() <= 128);
    }

    @Test
    @DisplayName("request_hash 计算: 空参数返回 empty 常量")
    void testComputeRequestHashEmpty() {
        String hash = IdempotencyService.computeRequestHash();
        assertEquals("empty", hash);
    }

    @Test
    @DisplayName("scanStaleProcessing: 查询 PROCESSING + locked_until < 阈值")
    void testScanStaleProcessing() {
        when(mapper.selectList(any())).thenReturn(java.util.List.of());
        service.scanStaleProcessing(Duration.ofMinutes(5));
        verify(mapper, times(1)).selectList(any());
    }

    private IdempotentRequest buildRequest(String key, String hash, boolean retry) {
        return new IdempotentRequest(
                key, "biz:request", null, "CREATE", hash, 60L, retry);
    }

    /**
     * 子类用于注入 mock 的 TransactionTemplate (绕过父类构造器中真实 TransactionTemplate 的初始化)。
     */
    static class IdempotencyServiceForTest extends IdempotencyService {
        private final StringRedisTemplate redis;

        IdempotencyServiceForTest(IdempotencyRecordMapper mapper,
                                  StringRedisTemplate redis,
                                  ObjectMapper objectMapper,
                                  PlatformTransactionManager txManager,
                                  TransactionTemplate mockTx) {
            super(mapper, redis, objectMapper, txManager);
            this.redis = redis;
            // 通过反射替换 requiresNewTx 字段为 mock
            try {
                java.lang.reflect.Field f = IdempotencyService.class
                        .getDeclaredField("requiresNewTx");
                f.setAccessible(true);
                f.set(this, mockTx);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected void releaseLockSafely(String lockKey, String lockValue) {
            redis.delete(lockKey);
        }
    }
}
