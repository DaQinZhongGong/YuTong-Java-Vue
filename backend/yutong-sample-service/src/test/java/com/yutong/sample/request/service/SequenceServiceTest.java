package com.yutong.sample.request.service;

import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.sample.request.domain.Sequence;
import com.yutong.sample.request.mapper.SequenceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 序列号服务单元测试。设计来源: 18-样例业务详细设计 编码规则
 * 申请单号格式: REQyyyyMMddNNNN
 *  - REQ 固定前缀
 *  - yyyyMMdd 8 位日期
 *  - NNNN 4 位序号 (从 0001 开始，按 tenant + bizDate 隔离)
 * 重试: 乐观锁冲突最多重试 3 次，重试耗尽抛 BusinessConflictException
 *
 * 注: MyBatis-Plus 3.5.16 的 BaseMapper 同时存在 insert(T) 与 insert(Collection<T>) 重载，
 * 匹配器在 insert/updateById 上需使用 Mockito.<T>any() 显式指定泛型类型避免歧义；
 * selectOne 仅接受 Wrapper<T> 单一签名，直接 any() 即可。
 * verify(insert(argThat(...))) 同样存在歧义，使用 Mockito.<T>argThat() 显式指定。
 */
@DisplayName("序列号服务")
@ExtendWith(MockitoExtension.class)
class SequenceServiceTest {

    private static final Pattern REQ_NO_PATTERN = Pattern.compile("^REQ\\d{8}\\d{4}$");

    @Mock
    private SequenceMapper sequenceMapper;

    @InjectMocks
    private SequenceService sequenceService;

    @BeforeEach
    void setUpContext() {
        CurrentUserContext.set("01TESTUSER0000000000000001", "default", "测试员");
    }

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    // ==================== 格式校验 ====================

    @Nested
    @DisplayName("格式: REQyyyyMMddNNNN")
    class Format {

        @Test
        @DisplayName("首次插入: 返回 REQyyyyMMdd0001")
        void firstInsertReturns0001() {
            when(sequenceMapper.selectOne(any())).thenReturn(null);
            when(sequenceMapper.insert(org.mockito.Mockito.<Sequence>any())).thenReturn(1);

            String requestNo = sequenceService.nextRequestNo();

            String expectedDate = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            String expected = "REQ" + expectedDate + "0001";
            assertEquals(expected, requestNo);
            assertTrue(REQ_NO_PATTERN.matcher(requestNo).matches(),
                    "申请单号格式应为 REQyyyyMMddNNNN，实际: " + requestNo);
        }

        @Test
        @DisplayName("首次插入: 长度为 15 (REQ + 8 + 4)")
        void firstInsertLength15() {
            when(sequenceMapper.selectOne(any())).thenReturn(null);
            when(sequenceMapper.insert(org.mockito.Mockito.<Sequence>any())).thenReturn(1);

            String requestNo = sequenceService.nextRequestNo();

            assertEquals(15, requestNo.length());
        }

        @Test
        @DisplayName("首次插入: Sequence 实体字段正确填充")
        void firstInsertEntityFields() {
            when(sequenceMapper.selectOne(any())).thenReturn(null);
            when(sequenceMapper.insert(org.mockito.Mockito.<Sequence>any())).thenReturn(1);

            sequenceService.nextRequestNo();

            verify(sequenceMapper).insert(org.mockito.Mockito.<Sequence>argThat(seq ->
                    Sequence.CODE_BIZ_REQUEST_NO.equals(seq.getSequenceCode())
                            && seq.getCurrentValue() == 1
                            && seq.getStep() == 1
                            && Sequence.RESET_POLICY_DAILY.equals(seq.getResetPolicy())
                            && seq.getTenantId().equals("default")
                            && seq.getBizDate() != null
                            && seq.getBizDate().length() == 8
                            && seq.getId() != null
            ));
        }
    }

    // ==================== 递增 ====================

    @Nested
    @DisplayName("递增: 已有序列 + step")
    class Increment {

        @Test
        @DisplayName("已存在 currentValue=5: 返回 0006")
        void existingReturnsIncremented() {
            Sequence existing = buildSequence(5, 1, 0);
            when(sequenceMapper.selectOne(any())).thenReturn(existing);
            when(sequenceMapper.updateById(org.mockito.Mockito.<Sequence>any())).thenReturn(1);

            String requestNo = sequenceService.nextRequestNo();

            String expectedDate = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            assertEquals("REQ" + expectedDate + "0006", requestNo);
            assertEquals(6, existing.getCurrentValue());
        }

        @Test
        @DisplayName("已存在 currentValue=9999: 返回 10000 (5 位溢出)")
        void existingLargeValueOverflow() {
            Sequence existing = buildSequence(9999, 1, 0);
            when(sequenceMapper.selectOne(any())).thenReturn(existing);
            when(sequenceMapper.updateById(org.mockito.Mockito.<Sequence>any())).thenReturn(1);

            String requestNo = sequenceService.nextRequestNo();

            String expectedDate = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            assertEquals("REQ" + expectedDate + "10000", requestNo);
        }

        @Test
        @DisplayName("step=2: currentValue=1 → 3")
        void incrementWithStep2() {
            Sequence existing = buildSequence(1, 2, 0);
            when(sequenceMapper.selectOne(any())).thenReturn(existing);
            when(sequenceMapper.updateById(org.mockito.Mockito.<Sequence>any())).thenReturn(1);

            String requestNo = sequenceService.nextRequestNo();

            String expectedDate = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            assertEquals("REQ" + expectedDate + "0003", requestNo);
            assertEquals(3, existing.getCurrentValue());
        }
    }

    // ==================== 重试与冲突 ====================

    @Nested
    @DisplayName("重试: 乐观锁冲突与并发插入")
    class Retry {

        @Test
        @DisplayName("首次插入 DuplicateKeyException → 重试 → 第二次走 update 路径成功")
        void insertDuplicateKeyRetriesAndSucceeds() {
            Sequence existingAfterDup = buildSequence(1, 1, 1);
            when(sequenceMapper.selectOne(any()))
                    .thenReturn(null)   // 第一次: 序列不存在，尝试插入
                    .thenReturn(existingAfterDup); // 第二次: 已有记录，走 update 路径
            when(sequenceMapper.insert(org.mockito.Mockito.<Sequence>any()))
                    .thenThrow(new DuplicateKeyException("dup"));
            when(sequenceMapper.updateById(org.mockito.Mockito.<Sequence>any())).thenReturn(1);

            String requestNo = sequenceService.nextRequestNo();

            assertNotNull(requestNo);
            assertTrue(REQ_NO_PATTERN.matcher(requestNo).matches());
        }

        @Test
        @DisplayName("updateById 返回 0 (乐观锁冲突) → 重试 → 成功")
        void updateByIdZeroRetries() {
            Sequence firstRead = buildSequence(10, 1, 5);
            Sequence secondRead = buildSequence(10, 1, 6);
            when(sequenceMapper.selectOne(any()))
                    .thenReturn(firstRead)
                    .thenReturn(secondRead);
            when(sequenceMapper.updateById(org.mockito.Mockito.<Sequence>any()))
                    .thenReturn(0)  // 第一次更新失败
                    .thenReturn(1); // 第二次更新成功

            String requestNo = sequenceService.nextRequestNo();

            String expectedDate = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            // 第二次读取 currentValue=10, +1 = 11
            assertEquals("REQ" + expectedDate + "0011", requestNo);
        }

        @Test
        @DisplayName("重试 3 次仍失败 → 抛 BusinessConflictException")
        void retryExhaustedThrows() {
            Sequence seq = buildSequence(100, 1, 1);
            when(sequenceMapper.selectOne(any())).thenReturn(seq);
            when(sequenceMapper.updateById(org.mockito.Mockito.<Sequence>any())).thenReturn(0); // 永远失败

            BusinessConflictException ex = assertThrows(BusinessConflictException.class,
                    () -> sequenceService.nextRequestNo());
            assertTrue(ex.getMessage().contains("重试次数耗尽"));
        }

        @Test
        @DisplayName("连续 3 次 DuplicateKeyException → 抛 BusinessConflictException")
        void insertAlwaysDuplicateThrows() {
            when(sequenceMapper.selectOne(any())).thenReturn(null);
            when(sequenceMapper.insert(org.mockito.Mockito.<Sequence>any()))
                    .thenThrow(new DuplicateKeyException("dup"));

            assertThrows(BusinessConflictException.class,
                    () -> sequenceService.nextRequestNo());
        }
    }

    // ==================== 隔离 ====================

    @Test
    @DisplayName("隔离: 使用当前上下文的 tenantId")
    void usesCurrentTenantId() {
        CurrentUserContext.set("01USER0000000000000000000002", "tenant-xyz", "用户B");
        when(sequenceMapper.selectOne(any())).thenReturn(null);
        when(sequenceMapper.insert(org.mockito.Mockito.<Sequence>any())).thenReturn(1);

        sequenceService.nextRequestNo();

        verify(sequenceMapper).insert(org.mockito.Mockito.<Sequence>argThat(seq -> "tenant-xyz".equals(seq.getTenantId())));
    }

    @Test
    @DisplayName("隔离: bizDate 为当日 yyyyMMdd")
    void bizDateIsToday() {
        when(sequenceMapper.selectOne(any())).thenReturn(null);
        when(sequenceMapper.insert(org.mockito.Mockito.<Sequence>any())).thenReturn(1);

        sequenceService.nextRequestNo();

        String expected = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        verify(sequenceMapper).insert(org.mockito.Mockito.<Sequence>argThat(seq -> expected.equals(seq.getBizDate())));
    }

    // ==================== 辅助方法 ====================

    private Sequence buildSequence(int currentValue, int step, int version) {
        Sequence seq = new Sequence();
        seq.setId("01SEQ" + System.nanoTime());
        seq.setTenantId("default");
        seq.setSequenceCode(Sequence.CODE_BIZ_REQUEST_NO);
        seq.setBizDate(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
        seq.setCurrentValue(currentValue);
        seq.setStep(step);
        seq.setResetPolicy(Sequence.RESET_POLICY_DAILY);
        seq.setVersion(version);
        return seq;
    }
}
