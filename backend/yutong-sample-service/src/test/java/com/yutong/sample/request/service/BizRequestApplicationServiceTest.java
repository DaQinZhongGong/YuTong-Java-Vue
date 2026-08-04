package com.yutong.sample.request.service;

import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.sample.request.domain.ApprovalRecord;
import com.yutong.sample.request.domain.BizRequest;
import com.yutong.sample.request.domain.BizRequestItem;
import com.yutong.sample.request.dto.SaveBizRequestRequest;
import com.yutong.sample.request.mapper.ApprovalRecordMapper;
import com.yutong.sample.request.mapper.BizRequestItemMapper;
import com.yutong.sample.request.mapper.BizRequestMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 申请单应用服务单元测试。设计来源: 18-样例业务详细设计 金额规则 + 状态流转
 * 覆盖:
 *  - 金额计算 (line_amount = quantity * unit_price, total_amount = sum)
 *  - saveDraft 新建/修改流程
 *  - 状态流转 (submit/approve/reject/withdraw/archive)
 *  - reject 必填 opinion 校验
 *
 * GA2-02 扩展: 新增 DataScopeResolver mock，默认返回 admin ALL 范围以兼容历史用例。
 *
 * 注: MyBatis-Plus 3.5.16 的 BaseMapper 同时存在 insert(T) 与 insert(Collection<T>) 重载，
 * 匹配器在 insert/updateById 上需使用 Mockito.<T>any() 显式指定泛型类型避免歧义；
 * selectOne/delete 仅接受 Wrapper<T> 单一签名，直接 any() 即可。
 */
@DisplayName("申请单应用服务")
@ExtendWith(MockitoExtension.class)
class BizRequestApplicationServiceTest {

    @Mock
    private BizRequestMapper bizRequestMapper;
    @Mock
    private BizRequestItemMapper bizRequestItemMapper;
    @Mock
    private ApprovalRecordMapper approvalRecordMapper;
    @Mock
    private SequenceService sequenceService;
    @Mock
    private BizRequestDomainService domainService;
    @Mock
    private DataScopeResolver dataScopeResolver;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock
    private com.yutong.sample.masterdata.mapper.CustomerMapper customerMapper;
    @Mock
    private com.yutong.sample.masterdata.mapper.ProductMapper productMapper;

    @InjectMocks
    private BizRequestApplicationService applicationService;

    @BeforeEach
    void setUpContext() {
        CurrentUserContext.set("01TESTUSER0000000000000001", "default", "测试员");
        // GA2-02: 默认 mock admin ALL 范围，兼容历史用例
        lenient().when(dataScopeResolver.resolve(anyString()))
                .thenReturn(DataScope.all("01TESTUSER0000000000000001", "default", "biz:request"));
    }

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    // ==================== 金额计算（通过 saveDraft 间接验证） ====================

    @Nested
    @DisplayName("金额计算: line_amount = quantity * unit_price, total_amount = sum")
    class AmountCalculation {

        @Test
        @DisplayName("新建草稿: total_amount = 2 * 98000 + 3 * 1500 = 200500.00")
        void saveDraftNewCalculatesTotal() {
            SaveBizRequestRequest request = new SaveBizRequestRequest();
            request.setTitle("测试申请单");
            request.setCustomerId("01CUSTOMER0000000000000001");
            request.setCustomerNameSnapshot("客户A");
            request.setApplyReason("测试用途");
            request.setItems(List.of(
                    buildItem("P001", "商品A", 2, new BigDecimal("98000"), 1),
                    buildItem("P002", "商品B", 3, new BigDecimal("1500"), 2)
            ));

            when(sequenceService.nextRequestNo()).thenReturn("REQ202607130001");
            when(bizRequestMapper.insert(org.mockito.Mockito.<BizRequest>any())).thenReturn(1);
            when(bizRequestItemMapper.delete(any())).thenReturn(0);
            when(bizRequestItemMapper.insert(org.mockito.Mockito.<BizRequestItem>any())).thenReturn(1);

            BizRequest result = applicationService.saveDraft(request);

            // 2 * 98000 + 3 * 1500 = 196000 + 4500 = 200500
            BigDecimal expected = new BigDecimal("200500.00");
            assertEquals(expected, result.getTotalAmount());
            assertEquals(BizRequest.STATUS_DRAFT, result.getRequestStatus());
            assertEquals("REQ202607130001", result.getRequestNo());
            assertNotNull(result.getApplicantId());
        }

        @Test
        @DisplayName("新建草稿: 单行明细金额 = 数量 * 单价")
        void saveDraftSingleLineAmount() {
            SaveBizRequestRequest request = new SaveBizRequestRequest();
            request.setTitle("单行测试");
            request.setCustomerId("C001");
            request.setCustomerNameSnapshot("客户A");
            request.setItems(List.of(
                    buildItem("P001", "商品A", 5, new BigDecimal("1234.56"), 1)
            ));

            when(sequenceService.nextRequestNo()).thenReturn("REQ202607130002");
            when(bizRequestMapper.insert(org.mockito.Mockito.<BizRequest>any())).thenReturn(1);
            when(bizRequestItemMapper.delete(any())).thenReturn(0);
            when(bizRequestItemMapper.insert(org.mockito.Mockito.<BizRequestItem>any())).thenReturn(1);

            BizRequest result = applicationService.saveDraft(request);

            // 5 * 1234.56 = 6172.80
            assertEquals(new BigDecimal("6172.80"), result.getTotalAmount());
        }

        @Test
        @DisplayName("新建草稿: 无明细时 total_amount = 0.00")
        void saveDraftEmptyItemsCalculatesZero() {
            SaveBizRequestRequest request = new SaveBizRequestRequest();
            request.setTitle("空明细");
            request.setCustomerId("C001");
            request.setCustomerNameSnapshot("客户A");
            request.setItems(List.of());

            when(sequenceService.nextRequestNo()).thenReturn("REQ202607130003");
            when(bizRequestMapper.insert(org.mockito.Mockito.<BizRequest>any())).thenReturn(1);

            BizRequest result = applicationService.saveDraft(request);

            assertEquals(new BigDecimal("0.00"), result.getTotalAmount());
        }

        @Test
        @DisplayName("新建草稿: null 明细时 total_amount = 0.00")
        void saveDraftNullItemsCalculatesZero() {
            SaveBizRequestRequest request = new SaveBizRequestRequest();
            request.setTitle("空明细");
            request.setCustomerId("C001");
            request.setCustomerNameSnapshot("客户A");
            request.setItems(null);

            when(sequenceService.nextRequestNo()).thenReturn("REQ202607130004");
            when(bizRequestMapper.insert(org.mockito.Mockito.<BizRequest>any())).thenReturn(1);

            BizRequest result = applicationService.saveDraft(request);

            assertEquals(new BigDecimal("0.00"), result.getTotalAmount());
        }

        @Test
        @DisplayName("新建草稿: null 数量按 0 处理")
        void saveDraftNullQuantity() {
            SaveBizRequestRequest request = new SaveBizRequestRequest();
            request.setTitle("null数量测试");
            request.setCustomerId("C001");
            request.setCustomerNameSnapshot("客户A");
            SaveBizRequestRequest.Item item = new SaveBizRequestRequest.Item();
            item.setProductId("P001");
            item.setProductCodeSnapshot("P001");
            item.setProductNameSnapshot("商品A");
            item.setUnit("台");
            item.setQuantity(null);
            item.setUnitPrice(new BigDecimal("1000"));
            item.setSortNo(1);
            request.setItems(List.of(item));

            when(sequenceService.nextRequestNo()).thenReturn("REQ202607130005");
            when(bizRequestMapper.insert(org.mockito.Mockito.<BizRequest>any())).thenReturn(1);
            when(bizRequestItemMapper.delete(any())).thenReturn(0);
            when(bizRequestItemMapper.insert(org.mockito.Mockito.<BizRequestItem>any())).thenReturn(1);

            BizRequest result = applicationService.saveDraft(request);

            assertEquals(new BigDecimal("0.00"), result.getTotalAmount());
        }

        @Test
        @DisplayName("新建草稿: 小数四舍五入到 2 位")
        void saveDraftRoundingHalfUp() {
            SaveBizRequestRequest request = new SaveBizRequestRequest();
            request.setTitle("四舍五入测试");
            request.setCustomerId("C001");
            request.setCustomerNameSnapshot("客户A");
            // 3 * 100.005 = 300.015 -> 四舍五入到 300.02
            request.setItems(List.of(
                    buildItem("P001", "商品A", 3, new BigDecimal("100.005"), 1)
            ));

            when(sequenceService.nextRequestNo()).thenReturn("REQ202607130006");
            when(bizRequestMapper.insert(org.mockito.Mockito.<BizRequest>any())).thenReturn(1);
            when(bizRequestItemMapper.delete(any())).thenReturn(0);
            when(bizRequestItemMapper.insert(org.mockito.Mockito.<BizRequestItem>any())).thenReturn(1);

            BizRequest result = applicationService.saveDraft(request);

            assertEquals(new BigDecimal("300.02"), result.getTotalAmount());
        }

        @Test
        @DisplayName("修改草稿: 重新计算 total_amount")
        void saveDraftUpdateRecalculatesTotal() {
            BizRequest existing = new BizRequest();
            existing.setId("01REQ0000000000000000000001");
            existing.setTenantId("default");
            existing.setRequestStatus(BizRequest.STATUS_DRAFT);
            existing.setVersion(0);

            SaveBizRequestRequest request = new SaveBizRequestRequest();
            request.setId(existing.getId());
            request.setVersion(0);
            request.setTitle("修改后");
            request.setCustomerId("C001");
            request.setCustomerNameSnapshot("客户A");
            request.setItems(List.of(
                    buildItem("P001", "商品A", 10, new BigDecimal("999.99"), 1)
            ));

            when(bizRequestMapper.selectById(anyString())).thenReturn(existing);
            when(bizRequestMapper.updateById(org.mockito.Mockito.<BizRequest>any())).thenReturn(1);
            when(bizRequestItemMapper.delete(any())).thenReturn(0);
            when(bizRequestItemMapper.insert(org.mockito.Mockito.<BizRequestItem>any())).thenReturn(1);

            BizRequest result = applicationService.saveDraft(request);

            // 10 * 999.99 = 9999.90
            assertEquals(new BigDecimal("9999.90"), result.getTotalAmount());
            assertEquals(BizRequest.STATUS_DRAFT, result.getRequestStatus());
        }

        @Test
        @DisplayName("修改草稿: REJECTED 状态编辑后回到 DRAFT")
        void saveDraftRejectedBackToDraft() {
            BizRequest existing = new BizRequest();
            existing.setId("01REQ0000000000000000000002");
            existing.setTenantId("default");
            existing.setRequestStatus(BizRequest.STATUS_REJECTED);
            existing.setVersion(2);

            SaveBizRequestRequest request = new SaveBizRequestRequest();
            request.setId(existing.getId());
            request.setVersion(2);
            request.setTitle("驳回后修改");
            request.setCustomerId("C001");
            request.setCustomerNameSnapshot("客户A");
            request.setItems(List.of(
                    buildItem("P001", "商品A", 1, new BigDecimal("100"), 1)
            ));

            when(bizRequestMapper.selectById(anyString())).thenReturn(existing);
            when(bizRequestMapper.updateById(org.mockito.Mockito.<BizRequest>any())).thenReturn(1);
            when(bizRequestItemMapper.delete(any())).thenReturn(0);
            when(bizRequestItemMapper.insert(org.mockito.Mockito.<BizRequestItem>any())).thenReturn(1);

            BizRequest result = applicationService.saveDraft(request);

            assertEquals(BizRequest.STATUS_DRAFT, result.getRequestStatus());
        }

        @Test
        @DisplayName("修改草稿: SUBMITTED 状态不允许编辑")
        void saveDraftSubmittedNotAllowed() {
            BizRequest existing = new BizRequest();
            existing.setId("01REQ0000000000000000000003");
            existing.setTenantId("default");
            existing.setRequestStatus(BizRequest.STATUS_SUBMITTED);
            existing.setVersion(1);

            SaveBizRequestRequest request = new SaveBizRequestRequest();
            request.setId(existing.getId());
            request.setVersion(1);
            request.setTitle("尝试修改已提交");
            request.setItems(List.of());

            when(bizRequestMapper.selectById(anyString())).thenReturn(existing);

            assertThrows(BusinessException.class, () -> applicationService.saveDraft(request));
        }

        @Test
        @DisplayName("修改草稿: 乐观锁版本不匹配抛异常")
        void saveDraftVersionMismatch() {
            BizRequest existing = new BizRequest();
            existing.setId("01REQ0000000000000000000004");
            existing.setTenantId("default");
            existing.setRequestStatus(BizRequest.STATUS_DRAFT);
            existing.setVersion(5);

            SaveBizRequestRequest request = new SaveBizRequestRequest();
            request.setId(existing.getId());
            request.setVersion(1); // 不匹配
            request.setItems(List.of());

            when(bizRequestMapper.selectById(anyString())).thenReturn(existing);

            assertThrows(BusinessException.class, () -> applicationService.saveDraft(request));
        }

        @Test
        @DisplayName("修改草稿: 申请单不存在抛 ResourceNotFoundException")
        void saveDraftNotFound() {
            SaveBizRequestRequest request = new SaveBizRequestRequest();
            request.setId("01REQ0000000000000000000099");
            request.setVersion(0);
            request.setItems(List.of());

            when(bizRequestMapper.selectById(anyString())).thenReturn(null);

            assertThrows(ResourceNotFoundException.class, () -> applicationService.saveDraft(request));
        }
    }

    // ==================== 状态流转 ====================

    @Nested
    @DisplayName("状态流转: 通过 doTransition 编排")
    class Transitions {

        @Test
        @DisplayName("submit: 幂等(目标状态已匹配)直接返回，不写审批记录")
        void submitIdempotent() {
            BizRequest existing = buildRequest(BizRequest.STATUS_SUBMITTED, 1);
            when(bizRequestMapper.selectById(anyString())).thenReturn(existing);
            when(domainService.nextStatus(ApprovalRecord.ACTION_SUBMIT))
                    .thenReturn(BizRequest.STATUS_SUBMITTED);
            // 幂等: 目标状态已匹配，直接返回
            BizRequest result = applicationService.submit(existing.getId(), 1, "k1");
            assertEquals(BizRequest.STATUS_SUBMITTED, result.getRequestStatus());
            verify(approvalRecordMapper, never()).insert(org.mockito.Mockito.<ApprovalRecord>any());
        }

        @Test
        @DisplayName("approve: 调用后更新状态并写审批记录")
        void approveSuccess() {
            BizRequest existing = buildRequest(BizRequest.STATUS_SUBMITTED, 1);
            when(bizRequestMapper.selectById(anyString())).thenReturn(existing);
            when(domainService.nextStatus(ApprovalRecord.ACTION_APPROVE))
                    .thenReturn(BizRequest.STATUS_APPROVED);
            when(bizRequestMapper.updateById(org.mockito.Mockito.<BizRequest>any())).thenReturn(1);
            when(approvalRecordMapper.insert(org.mockito.Mockito.<ApprovalRecord>any())).thenReturn(1);

            BizRequest result = applicationService.approve(existing.getId(), "同意", 1, "k1");

            assertEquals(BizRequest.STATUS_APPROVED, result.getRequestStatus());
            assertNotNull(result.getApprovedTime());
            verify(approvalRecordMapper, times(1)).insert(org.mockito.Mockito.<ApprovalRecord>any());
        }

        @Test
        @DisplayName("reject: opinion 为空抛异常")
        void rejectEmptyOpinion() {
            BizRequest existing = buildRequest(BizRequest.STATUS_SUBMITTED, 1);
            assertThrows(BusinessException.class,
                    () -> applicationService.reject(existing.getId(), "", 1, "k1"));
            assertThrows(BusinessException.class,
                    () -> applicationService.reject(existing.getId(), null, 1, "k1"));
            verify(bizRequestMapper, never()).selectById(anyString());
        }

        @Test
        @DisplayName("reject: 正常驳回写审批记录")
        void rejectSuccess() {
            BizRequest existing = buildRequest(BizRequest.STATUS_SUBMITTED, 1);
            when(bizRequestMapper.selectById(anyString())).thenReturn(existing);
            when(domainService.nextStatus(ApprovalRecord.ACTION_REJECT))
                    .thenReturn(BizRequest.STATUS_REJECTED);
            when(bizRequestMapper.updateById(org.mockito.Mockito.<BizRequest>any())).thenReturn(1);
            when(approvalRecordMapper.insert(org.mockito.Mockito.<ApprovalRecord>any())).thenReturn(1);

            BizRequest result = applicationService.reject(existing.getId(), "理由不充分", 1, "k1");

            assertEquals(BizRequest.STATUS_REJECTED, result.getRequestStatus());
            verify(approvalRecordMapper, times(1)).insert(org.mockito.Mockito.<ApprovalRecord>any());
        }

        @Test
        @DisplayName("withdraw: 撤回到 DRAFT")
        void withdrawSuccess() {
            BizRequest existing = buildRequest(BizRequest.STATUS_SUBMITTED, 1);
            when(bizRequestMapper.selectById(anyString())).thenReturn(existing);
            when(domainService.nextStatus(ApprovalRecord.ACTION_WITHDRAW))
                    .thenReturn(BizRequest.STATUS_DRAFT);
            when(bizRequestMapper.updateById(org.mockito.Mockito.<BizRequest>any())).thenReturn(1);
            when(approvalRecordMapper.insert(org.mockito.Mockito.<ApprovalRecord>any())).thenReturn(1);

            BizRequest result = applicationService.withdraw(existing.getId(), "撤销", 1, "k1");

            assertEquals(BizRequest.STATUS_DRAFT, result.getRequestStatus());
        }

        @Test
        @DisplayName("archive: APPROVED→ARCHIVED")
        void archiveSuccess() {
            BizRequest existing = buildRequest(BizRequest.STATUS_APPROVED, 2);
            when(bizRequestMapper.selectById(anyString())).thenReturn(existing);
            when(domainService.nextStatus(ApprovalRecord.ACTION_ARCHIVE))
                    .thenReturn(BizRequest.STATUS_ARCHIVED);
            when(bizRequestMapper.updateById(org.mockito.Mockito.<BizRequest>any())).thenReturn(1);
            when(approvalRecordMapper.insert(org.mockito.Mockito.<ApprovalRecord>any())).thenReturn(1);

            BizRequest result = applicationService.archive(existing.getId(), 2, "k1");

            assertEquals(BizRequest.STATUS_ARCHIVED, result.getRequestStatus());
            assertNotNull(result.getArchivedTime());
        }

        @Test
        @DisplayName("transition: 申请单不存在抛 ResourceNotFoundException")
        void transitionNotFound() {
            when(bizRequestMapper.selectById(anyString())).thenReturn(null);
            assertThrows(ResourceNotFoundException.class,
                    () -> applicationService.submit("nonexistent", 1, "k1"));
        }

        @Test
        @DisplayName("transition: 版本不匹配抛 BusinessConflictException")
        void transitionVersionMismatch() {
            BizRequest existing = buildRequest(BizRequest.STATUS_DRAFT, 5);
            when(bizRequestMapper.selectById(anyString())).thenReturn(existing);

            assertThrows(BusinessException.class,
                    () -> applicationService.submit(existing.getId(), 1, "k1"));
        }

        @Test
        @DisplayName("transition: 状态机校验失败抛异常 (domainService 抛异常)")
        void transitionStateMachineIllegal() {
            BizRequest existing = buildRequest(BizRequest.STATUS_DRAFT, 0);
            when(bizRequestMapper.selectById(anyString())).thenReturn(existing);
            when(domainService.nextStatus(ApprovalRecord.ACTION_APPROVE))
                    .thenReturn(BizRequest.STATUS_APPROVED);
            doThrow(new BusinessConflictException("非法流转"))
                    .when(domainService).validateTransition(BizRequest.STATUS_DRAFT, ApprovalRecord.ACTION_APPROVE);

            assertThrows(BusinessConflictException.class,
                    () -> applicationService.approve(existing.getId(), "ok", 0, "k1"));
            verify(bizRequestMapper, never()).updateById(org.mockito.Mockito.<BizRequest>any());
        }

        @Test
        @DisplayName("transition: updateById 返回 0 抛乐观锁异常")
        void transitionOptimisticLockFailure() {
            BizRequest existing = buildRequest(BizRequest.STATUS_SUBMITTED, 1);
            when(bizRequestMapper.selectById(anyString())).thenReturn(existing);
            when(domainService.nextStatus(ApprovalRecord.ACTION_APPROVE))
                    .thenReturn(BizRequest.STATUS_APPROVED);
            when(bizRequestMapper.updateById(org.mockito.Mockito.<BizRequest>any())).thenReturn(0);

            assertThrows(BusinessConflictException.class,
                    () -> applicationService.approve(existing.getId(), "ok", 1, "k1"));
        }
    }

    // ==================== 辅助方法 ====================

    private BizRequest buildRequest(String status, int version) {
        BizRequest request = new BizRequest();
        request.setId("01REQ" + System.nanoTime());
        request.setTenantId("default");
        request.setRequestStatus(status);
        request.setVersion(version);
        return request;
    }

    private SaveBizRequestRequest.Item buildItem(String productCode, String productName,
                                                  int quantity, BigDecimal unitPrice, int sortNo) {
        SaveBizRequestRequest.Item item = new SaveBizRequestRequest.Item();
        item.setProductId("01PROD" + productCode);
        item.setProductCodeSnapshot(productCode);
        item.setProductNameSnapshot(productName);
        item.setUnit("台");
        item.setQuantity(BigDecimal.valueOf(quantity));
        item.setUnitPrice(unitPrice);
        item.setSortNo(sortNo);
        return item;
    }

    /**
     * 验证 BigDecimal 保留 2 位 HALF_UP 的纯数学规则。
     * 此测试独立于 saveDraft，直接验证 HALF_UP 语义。
     */
    @Test
    @DisplayName("BigDecimal HALF_UP 规则独立验证")
    void halfUpRoundingSemantics() {
        assertEquals(new BigDecimal("0.00"), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("0.01"), new BigDecimal("0.005").setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("0.01"), new BigDecimal("0.009").setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("1.00"), new BigDecimal("0.995").setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("100.00"), new BigDecimal("100").setScale(2, RoundingMode.HALF_UP));
    }
}
