package com.yutong.sample.request.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.exception.DataScopeDeniedException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.sample.request.domain.ApprovalRecord;
import com.yutong.sample.request.domain.BizRequest;
import com.yutong.sample.request.mapper.ApprovalRecordMapper;
import com.yutong.sample.request.mapper.BizRequestItemMapper;
import com.yutong.sample.request.mapper.BizRequestMapper;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 申请单应用服务 DataScope 单元测试。设计来源: 67-数据权限与审计日志详设 第 75-79 行测试用例
 *
 * 覆盖 4 类 Mock 用户的 DataScope 校验:
 *  - admin    ALL    → 全部申请单可见，审批/归档放行
 *  - biz     SELF    → 仅本人 owner_user_id 的申请单可见/可编辑；越权抛 DataScopeDeniedException
 *  - approver CUSTOM → 仅白名单 resourceIds 中的申请单可见；空白名单 → 空结果；越权抛 DataScopeDeniedException
 *  - viewer  TENANT  → 租户内全部申请单可见（但 includeSensitive=false，脱敏由应用层处理）
 *
 * 重点测试 67 号文档第 77-79 行硬约束:
 *  - 越权详情/审核 → AUTH-403002
 *  - CUSTOM 白名单为空 → 返回空列表，不允许降级到租户全量
 *  - NONE → 必须返回空结果
 */
@DisplayName("申请单 DataScope 数据权限")
@ExtendWith(MockitoExtension.class)
class BizRequestApplicationServiceDataScopeTest {

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
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BizRequestApplicationService applicationService;

    private static final String USER_ADMIN = "01MOCKUSER0000000000000ADMIN";
    private static final String USER_BIZ = "01MOCKUSER00000000000000BIZ";
    private static final String USER_APPROVER = "01MOCKUSER0000000000APPROVER";
    private static final String USER_VIEWER = "01MOCKUSER000000000000VIEWER";
    private static final String TENANT = "default";

    @BeforeEach
    void setUpContext() {
        // 默认设置基础上下文（不带 dept 和 dataScopeType）
        CurrentUserContext.set(USER_ADMIN, TENANT, "admin");
    }

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    /** 构造一个测试申请单。 */
    private BizRequest buildRequest(String id, String ownerUserId, String ownerDeptId, String ownerDeptPath) {
        BizRequest request = new BizRequest();
        request.setId(id);
        request.setTenantId(TENANT);
        request.setRequestNo("REQ202607140001");
        request.setTitle("测试申请单");
        request.setRequestStatus(BizRequest.STATUS_SUBMITTED);
        request.setOwnerUserId(ownerUserId);
        request.setOwnerDeptId(ownerDeptId);
        request.setOwnerDeptPath(ownerDeptPath);
        request.setVersion(1);
        return request;
    }

    // ==================== admin ALL 范围 ====================

    @Nested
    @DisplayName("admin ALL: 全部申请单可见，审批放行")
    class AdminAllScope {

        @Test
        @DisplayName("admin 访问他人申请单详情 → 放行")
        void adminCanViewOthersRequest() {
            CurrentUserContext.set(USER_ADMIN, TENANT, "admin");
            when(dataScopeResolver.resolve(anyString()))
                    .thenReturn(DataScope.all(USER_ADMIN, TENANT, "biz:request"));
            BizRequest request = buildRequest("r-001", USER_BIZ, "dept-001", "/corp/sales");
            when(bizRequestMapper.selectById("r-001")).thenReturn(request);
            when(bizRequestItemMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(approvalRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

            assertDoesNotThrow(() -> applicationService.getRequestDetail("r-001"));
        }

        @Test
        @DisplayName("admin 审批他人申请单 → 放行")
        void adminCanApproveOthersRequest() {
            CurrentUserContext.set(USER_ADMIN, TENANT, "admin");
            when(dataScopeResolver.resolve(anyString()))
                    .thenReturn(DataScope.all(USER_ADMIN, TENANT, "biz:request"));
            BizRequest request = buildRequest("r-001", USER_BIZ, "dept-001", "/corp/sales");
            when(bizRequestMapper.selectById("r-001")).thenReturn(request);
            when(domainService.nextStatus(ApprovalRecord.ACTION_APPROVE)).thenReturn(BizRequest.STATUS_APPROVED);
            when(bizRequestMapper.updateById(org.mockito.Mockito.<BizRequest>any())).thenReturn(1);

            assertDoesNotThrow(() -> applicationService.approve("r-001", "同意", 1, null));
        }
    }

    // ==================== biz SELF 范围 ====================

    @Nested
    @DisplayName("biz SELF: 仅本人 owner_user_id 的申请单可见")
    class BizSelfScope {

        @Test
        @DisplayName("biz 访问本人申请单详情 → 放行")
        void bizCanViewOwnRequest() {
            CurrentUserContext.set(USER_BIZ, TENANT, "biz_user");
            when(dataScopeResolver.resolve(anyString()))
                    .thenReturn(DataScope.self(USER_BIZ, TENANT, "biz:request"));
            BizRequest request = buildRequest("r-001", USER_BIZ, "dept-sales", "/corp/sales");
            when(bizRequestMapper.selectById("r-001")).thenReturn(request);
            when(bizRequestItemMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(approvalRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

            assertDoesNotThrow(() -> applicationService.getRequestDetail("r-001"));
        }

        @Test
        @DisplayName("biz 访问他人申请单详情 → 抛 DataScopeDeniedException (AUTH-403002)")
        void bizCannotViewOthersRequest() {
            CurrentUserContext.set(USER_BIZ, TENANT, "biz_user");
            when(dataScopeResolver.resolve(anyString()))
                    .thenReturn(DataScope.self(USER_BIZ, TENANT, "biz:request"));
            // 申请单 ownerUserId 是 admin，不是 biz_user
            BizRequest request = buildRequest("r-001", USER_ADMIN, "dept-root", "/corp");
            when(bizRequestMapper.selectById("r-001")).thenReturn(request);

            DataScopeDeniedException ex = assertThrows(DataScopeDeniedException.class,
                    () -> applicationService.getRequestDetail("r-001"));
            // 验证错误码为 AUTH_DATA_SCOPE_DENIED (AUTH-403002)
            assertEquals("AUTH-403002", ex.errorCode().code());
            assertEquals(403, ex.errorCode().httpStatus());
        }

        @Test
        @DisplayName("biz 审批他人申请单 → 抛 DataScopeDeniedException")
        void bizCannotApproveOthersRequest() {
            CurrentUserContext.set(USER_BIZ, TENANT, "biz_user");
            when(dataScopeResolver.resolve(anyString()))
                    .thenReturn(DataScope.self(USER_BIZ, TENANT, "biz:request"));
            BizRequest request = buildRequest("r-001", USER_ADMIN, "dept-root", "/corp");
            when(bizRequestMapper.selectById("r-001")).thenReturn(request);

            assertThrows(DataScopeDeniedException.class,
                    () -> applicationService.approve("r-001", "同意", 1, null));
        }
    }

    // ==================== approver CUSTOM 范围 ====================

    @Nested
    @DisplayName("approver CUSTOM: 仅白名单 resourceIds 中的申请单可见")
    class ApproverCustomScope {

        @Test
        @DisplayName("approver 访问白名单内申请单 → 放行")
        void approverCanViewWhitelistedRequest() {
            CurrentUserContext.set(USER_APPROVER, TENANT, "approver");
            when(dataScopeResolver.resolve(anyString()))
                    .thenReturn(DataScope.custom(USER_APPROVER, TENANT, "biz:request",
                            Set.of("r-001", "r-002"), true));
            BizRequest request = buildRequest("r-001", USER_BIZ, "dept-sales", "/corp/sales");
            when(bizRequestMapper.selectById("r-001")).thenReturn(request);
            when(bizRequestItemMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(approvalRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

            assertDoesNotThrow(() -> applicationService.getRequestDetail("r-001"));
        }

        @Test
        @DisplayName("approver 访问白名单外申请单 → 抛 DataScopeDeniedException")
        void approverCannotViewNonWhitelistedRequest() {
            CurrentUserContext.set(USER_APPROVER, TENANT, "approver");
            when(dataScopeResolver.resolve(anyString()))
                    .thenReturn(DataScope.custom(USER_APPROVER, TENANT, "biz:request",
                            Set.of("r-001"), true));
            // r-002 不在白名单内
            BizRequest request = buildRequest("r-002", USER_BIZ, "dept-sales", "/corp/sales");
            when(bizRequestMapper.selectById("r-002")).thenReturn(request);

            DataScopeDeniedException ex = assertThrows(DataScopeDeniedException.class,
                    () -> applicationService.getRequestDetail("r-002"));
            assertEquals("AUTH-403002", ex.errorCode().code());
        }

        @Test
        @DisplayName("approver 空白名单 → 访问任何申请单都抛 DataScopeDeniedException（严禁降级为 ALL）")
        void approverEmptyWhitelistDeniesAll() {
            CurrentUserContext.set(USER_APPROVER, TENANT, "approver");
            // 67 号文档第 78 行硬约束: 白名单为空 → 返回空列表，不允许降级到租户全量
            when(dataScopeResolver.resolve(anyString()))
                    .thenReturn(DataScope.custom(USER_APPROVER, TENANT, "biz:request",
                            Set.of(), true));  // 空白名单
            BizRequest request = buildRequest("r-001", USER_BIZ, "dept-sales", "/corp/sales");
            when(bizRequestMapper.selectById("r-001")).thenReturn(request);

            // 空白名单 → 越权拒绝
            assertThrows(DataScopeDeniedException.class,
                    () -> applicationService.getRequestDetail("r-001"));
        }

        @Test
        @DisplayName("approver 空白名单 → 分页查询返回空列表（不抛异常，SQL 层 1=0）")
        void approverEmptyWhitelistReturnsEmptyPage() {
            CurrentUserContext.set(USER_APPROVER, TENANT, "approver");
            when(dataScopeResolver.resolve(anyString()))
                    .thenReturn(DataScope.custom(USER_APPROVER, TENANT, "biz:request",
                            Set.of(), true));
            // mock selectPage 返回空 Page（因为 SQL 追加 1=0，查询结果为空）
            Page<BizRequest> emptyPage = new Page<>(1, 10);
            emptyPage.setRecords(Collections.emptyList());
            emptyPage.setTotal(0);
            when(bizRequestMapper.selectPage(any(), any())).thenReturn(emptyPage);

            PageRequest req = PageRequest.of(1, 10);
            PageResult<BizRequest> result = applicationService.pageRequests(req, null, null, null, null);

            assertNotNull(result);
            assertEquals(0, result.total());
            assertTrue(result.records().isEmpty());
        }
    }

    // ==================== viewer TENANT 范围 ====================

    @Nested
    @DisplayName("viewer TENANT: 租户内全部申请单可见（敏感字段脱敏）")
    class ViewerTenantScope {

        @Test
        @DisplayName("viewer 访问他人申请单详情 → 放行（tenant 范围内）")
        void viewerCanViewAnyRequestInTenant() {
            CurrentUserContext.set(USER_VIEWER, TENANT, "viewer");
            // viewer TENANT + includeSensitive=false
            when(dataScopeResolver.resolve(anyString()))
                    .thenReturn(new DataScope(DataScopeType.TENANT, USER_VIEWER, TENANT, "biz:request",
                            Set.of(), Set.of(), Set.of(), Set.of(), false));
            BizRequest request = buildRequest("r-001", USER_BIZ, "dept-sales", "/corp/sales");
            when(bizRequestMapper.selectById("r-001")).thenReturn(request);
            when(bizRequestItemMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(approvalRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

            assertDoesNotThrow(() -> applicationService.getRequestDetail("r-001"));
        }
    }

    // ==================== NONE 范围 ====================

    @Nested
    @DisplayName("NONE: 拒绝全部业务数据")
    class NoneScope {

        @Test
        @DisplayName("NONE 访问任何申请单 → 抛 DataScopeDeniedException")
        void noneDeniesAllRequests() {
            CurrentUserContext.set(USER_ADMIN, TENANT, "admin");
            when(dataScopeResolver.resolve(anyString()))
                    .thenReturn(DataScope.none(USER_ADMIN, TENANT, "biz:request"));
            BizRequest request = buildRequest("r-001", USER_ADMIN, "dept-root", "/corp");
            when(bizRequestMapper.selectById("r-001")).thenReturn(request);

            assertThrows(DataScopeDeniedException.class,
                    () -> applicationService.getRequestDetail("r-001"));
        }
    }

    // ==================== DEPT 范围 ====================

    @Nested
    @DisplayName("DEPT: 本部门数据")
    class DeptScope {

        @Test
        @DisplayName("DEPT 访问本部门申请单 → 放行")
        void deptCanViewOwnDeptRequest() {
            CurrentUserContext.set(USER_BIZ, TENANT, "biz_user");
            when(dataScopeResolver.resolve(anyString()))
                    .thenReturn(DataScope.dept(USER_BIZ, TENANT, "biz:request", Set.of("dept-sales")));
            BizRequest request = buildRequest("r-001", USER_ADMIN, "dept-sales", "/corp/sales");
            when(bizRequestMapper.selectById("r-001")).thenReturn(request);
            when(bizRequestItemMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(approvalRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

            assertDoesNotThrow(() -> applicationService.getRequestDetail("r-001"));
        }

        @Test
        @DisplayName("DEPT 访问他部门申请单 → 抛 DataScopeDeniedException")
        void deptCannotViewOtherDeptRequest() {
            CurrentUserContext.set(USER_BIZ, TENANT, "biz_user");
            when(dataScopeResolver.resolve(anyString()))
                    .thenReturn(DataScope.dept(USER_BIZ, TENANT, "biz:request", Set.of("dept-sales")));
            // 申请单 deptId=dept-engineering，不在 dept-sales 集合内
            BizRequest request = buildRequest("r-001", USER_ADMIN, "dept-engineering", "/corp/eng");
            when(bizRequestMapper.selectById("r-001")).thenReturn(request);

            assertThrows(DataScopeDeniedException.class,
                    () -> applicationService.getRequestDetail("r-001"));
        }
    }

    // ==================== DEPT_AND_CHILD 范围 ====================

    @Nested
    @DisplayName("DEPT_AND_CHILD: 本部门及下级")
    class DeptAndChildScope {

        @Test
        @DisplayName("DEPT_AND_CHILD 访问子部门申请单 → 放行")
        void deptAndChildCanViewSubDeptRequest() {
            CurrentUserContext.set(USER_ADMIN, TENANT, "admin");
            when(dataScopeResolver.resolve(anyString()))
                    .thenReturn(DataScope.deptAndChild(USER_ADMIN, TENANT, "biz:request",
                            Set.of("/corp/sales")));
            // 申请单 deptPath=/corp/sales/east，是 /corp/sales 的子路径
            BizRequest request = buildRequest("r-001", USER_BIZ, "dept-sales-east", "/corp/sales/east");
            when(bizRequestMapper.selectById("r-001")).thenReturn(request);
            when(bizRequestItemMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(approvalRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

            assertDoesNotThrow(() -> applicationService.getRequestDetail("r-001"));
        }

        @Test
        @DisplayName("DEPT_AND_CHILD 访问非子部门申请单 → 抛 DataScopeDeniedException")
        void deptAndChildCannotViewNonSubDeptRequest() {
            CurrentUserContext.set(USER_ADMIN, TENANT, "admin");
            when(dataScopeResolver.resolve(anyString()))
                    .thenReturn(DataScope.deptAndChild(USER_ADMIN, TENANT, "biz:request",
                            Set.of("/corp/sales")));
            // 申请单 deptPath=/corp/eng，不是 /corp/sales 的子路径
            BizRequest request = buildRequest("r-001", USER_BIZ, "dept-eng", "/corp/eng");
            when(bizRequestMapper.selectById("r-001")).thenReturn(request);

            assertThrows(DataScopeDeniedException.class,
                    () -> applicationService.getRequestDetail("r-001"));
        }
    }

    // ==================== 兜底 ====================

    @Nested
    @DisplayName("兜底: DataScopeResolver 返回 null → 安全拒绝")
    class FallbackNullScope {

        @Test
        @DisplayName("resolve 返回 null → 抛 DataScopeDeniedException（安全默认）")
        void nullScopeDenies() {
            CurrentUserContext.set(USER_ADMIN, TENANT, "admin");
            when(dataScopeResolver.resolve(anyString())).thenReturn(null);
            BizRequest request = buildRequest("r-001", USER_ADMIN, "dept-root", "/corp");
            when(bizRequestMapper.selectById("r-001")).thenReturn(request);

            assertThrows(DataScopeDeniedException.class,
                    () -> applicationService.getRequestDetail("r-001"));
        }
    }
}
