package com.yutong.ai.rag.service;

import com.yutong.ai.rag.domain.AiKnowledgeBase;
import com.yutong.ai.rag.service.RagAclService.DocumentAclInfo;
import com.yutong.auth.AuthAdapter;
import com.yutong.auth.AuthContext;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.exception.BusinessConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RAG ACL 过滤服务单元测试。设计来源: 13-AI能力设计 检索策略与权限
 * 安全原则: fail-closed，权限校验失败即拒绝；用户无权访问的文档不进入召回。
 */
@DisplayName("RAG ACL 过滤服务")
class RagAclServiceTest {

    private AuthAdapter authAdapter;
    private RagAclService aclService;

    @BeforeEach
    void setUp() {
        authAdapter = mock(AuthAdapter.class);
        // 默认返回空权限上下文 (fail-closed)：非所有者访问 PRIVATE 资源将被拒绝
        when(authAdapter.current()).thenReturn(new AuthContext(
                "test-user", "test-user", "default",
                Set.of(), Set.of(), DataScopeType.SELF.name(),
                null, null, false));
        aclService = new RagAclService(authAdapter);
    }

    private DocumentAclInfo doc(String docId, String visibility, String sensitivity,
                                 String ownerUserId, String tenantId, String permCode) {
        return new DocumentAclInfo(docId, visibility, permCode, ownerUserId, tenantId, sensitivity);
    }

    // ==================== checkKnowledgeBaseAccess ====================

    @Nested
    @DisplayName("checkKnowledgeBaseAccess: PUBLIC")
    class PublicKb {

        @Test
        @DisplayName("PUBLIC 知识库允许任意租户访问")
        void publicKbAllAccess() {
            assertDoesNotThrow(() -> aclService.checkKnowledgeBaseAccess(
                    AiKnowledgeBase.VISIBILITY_PUBLIC,
                    "owner-1", "ai:kb:read",
                    "user-1", "tenant-A", "tenant-B"));
        }
    }

    @Nested
    @DisplayName("checkKnowledgeBaseAccess: TENANT")
    class TenantKb {

        @Test
        @DisplayName("TENANT 同租户允许")
        void sameTenantAccess() {
            assertDoesNotThrow(() -> aclService.checkKnowledgeBaseAccess(
                    AiKnowledgeBase.VISIBILITY_TENANT,
                    "owner-1", null,
                    "user-1", "tenant-A", "tenant-A"));
        }

        @Test
        @DisplayName("TENANT 不同租户拒绝")
        void crossTenantRejected() {
            assertThrows(BusinessConflictException.class, () -> aclService.checkKnowledgeBaseAccess(
                    AiKnowledgeBase.VISIBILITY_TENANT,
                    "owner-1", null,
                    "user-1", "tenant-A", "tenant-B"));
        }

        @Test
        @DisplayName("TENANT 当前租户 null 拒绝")
        void nullTenantRejected() {
            assertThrows(BusinessConflictException.class, () -> aclService.checkKnowledgeBaseAccess(
                    AiKnowledgeBase.VISIBILITY_TENANT,
                    "owner-1", null,
                    "user-1", null, "tenant-B"));
        }
    }

    @Nested
    @DisplayName("checkKnowledgeBaseAccess: PRIVATE")
    class PrivateKb {

        @Test
        @DisplayName("PRIVATE 所有者本人允许")
        void ownerAccess() {
            assertDoesNotThrow(() -> aclService.checkKnowledgeBaseAccess(
                    AiKnowledgeBase.VISIBILITY_PRIVATE,
                    "owner-1", "ai:kb:read",
                    "owner-1", "tenant-A", "tenant-A"));
        }

        @Test
        @DisplayName("PRIVATE 非所有者同租户但无权限码 - fail-closed 拒绝")
        void nonOwnerNoPermissionRejected() {
            assertThrows(BusinessConflictException.class, () -> aclService.checkKnowledgeBaseAccess(
                    AiKnowledgeBase.VISIBILITY_PRIVATE,
                    "owner-1", "ai:kb:read",
                    "user-2", "tenant-A", "tenant-A"));
        }

        @Test
        @DisplayName("PRIVATE 不同租户拒绝")
        void crossTenantRejected() {
            assertThrows(BusinessConflictException.class, () -> aclService.checkKnowledgeBaseAccess(
                    AiKnowledgeBase.VISIBILITY_PRIVATE,
                    "owner-1", null,
                    "user-2", "tenant-A", "tenant-B"));
        }
    }

    @Nested
    @DisplayName("checkKnowledgeBaseAccess: 未知可见性")
    class UnknownVisibility {

        @Test
        @DisplayName("未知可见性 fail-closed 拒绝")
        void unknownVisibilityRejected() {
            assertThrows(BusinessConflictException.class, () -> aclService.checkKnowledgeBaseAccess(
                    "UNKNOWN", "owner-1", null,
                    "user-1", "tenant-A", "tenant-A"));
        }
    }

    // ==================== filterAccessibleDocumentIds ====================

    @Nested
    @DisplayName("filterAccessibleDocumentIds: 文档过滤")
    class DocumentFilter {

        @Test
        @DisplayName("PUBLIC + INTERNAL 文档允许任意用户访问")
        void publicInternalDocAccessible() {
            List<String> ids = List.of("doc-1");
            List<DocumentAclInfo> infos = List.of(
                    doc("doc-1", AiKnowledgeBase.VISIBILITY_PUBLIC,
                            AiKnowledgeBase.SENSITIVITY_INTERNAL,
                            "owner-1", "tenant-A", null));

            List<String> accessible = aclService.filterAccessibleDocumentIds(
                    ids, infos, "user-1", "tenant-B");

            assertEquals(1, accessible.size());
            assertTrue(accessible.contains("doc-1"));
        }

        @Test
        @DisplayName("TENANT + INTERNAL 同租户允许")
        void tenantInternalSameTenant() {
            List<String> ids = List.of("doc-1");
            List<DocumentAclInfo> infos = List.of(
                    doc("doc-1", AiKnowledgeBase.VISIBILITY_TENANT,
                            AiKnowledgeBase.SENSITIVITY_INTERNAL,
                            "owner-1", "tenant-A", null));

            List<String> accessible = aclService.filterAccessibleDocumentIds(
                    ids, infos, "user-1", "tenant-A");
            assertEquals(1, accessible.size());
        }

        @Test
        @DisplayName("TENANT 不同租户拒绝")
        void tenantCrossTenantRejected() {
            List<String> ids = List.of("doc-1");
            List<DocumentAclInfo> infos = List.of(
                    doc("doc-1", AiKnowledgeBase.VISIBILITY_TENANT,
                            AiKnowledgeBase.SENSITIVITY_INTERNAL,
                            "owner-1", "tenant-A", null));

            List<String> accessible = aclService.filterAccessibleDocumentIds(
                    ids, infos, "user-1", "tenant-B");
            assertTrue(accessible.isEmpty());
        }

        @Test
        @DisplayName("PRIVATE + RESTRICTED 仅所有者可访问")
        void privateRestrictedOnlyOwner() {
            List<String> ids = List.of("doc-1", "doc-2");
            List<DocumentAclInfo> infos = List.of(
                    doc("doc-1", AiKnowledgeBase.VISIBILITY_PRIVATE,
                            AiKnowledgeBase.SENSITIVITY_RESTRICTED,
                            "owner-1", "tenant-A", null),
                    doc("doc-2", AiKnowledgeBase.VISIBILITY_PRIVATE,
                            AiKnowledgeBase.SENSITIVITY_RESTRICTED,
                            "owner-2", "tenant-A", null));

            // 以 owner-1 身份访问，应只看到 doc-1
            List<String> accessible = aclService.filterAccessibleDocumentIds(
                    ids, infos, "owner-1", "tenant-A");
            assertEquals(1, accessible.size());
            assertTrue(accessible.contains("doc-1"));
        }

        @Test
        @DisplayName("PRIVATE 非所有者无权限码 fail-closed 拒绝")
        void privateNonOwnerRejected() {
            List<String> ids = List.of("doc-1");
            List<DocumentAclInfo> infos = List.of(
                    doc("doc-1", AiKnowledgeBase.VISIBILITY_PRIVATE,
                            AiKnowledgeBase.SENSITIVITY_INTERNAL,
                            "owner-1", "tenant-A", "ai:doc:read"));

            // 第一版 hasPermission 返回 false，所以非所有者 fail-closed 拒绝
            List<String> accessible = aclService.filterAccessibleDocumentIds(
                    ids, infos, "user-2", "tenant-A");
            assertTrue(accessible.isEmpty());
        }

        @Test
        @DisplayName("混合文档: 部分可访问")
        void mixedDocuments() {
            List<String> ids = List.of("doc-1", "doc-2", "doc-3");
            List<DocumentAclInfo> infos = List.of(
                    // doc-1: PUBLIC 任意访问
                    doc("doc-1", AiKnowledgeBase.VISIBILITY_PUBLIC,
                            AiKnowledgeBase.SENSITIVITY_INTERNAL,
                            "owner-1", "tenant-A", null),
                    // doc-2: TENANT 同租户
                    doc("doc-2", AiKnowledgeBase.VISIBILITY_TENANT,
                            AiKnowledgeBase.SENSITIVITY_INTERNAL,
                            "owner-1", "tenant-A", null),
                    // doc-3: PRIVATE + RESTRICTED 仅所有者
                    doc("doc-3", AiKnowledgeBase.VISIBILITY_PRIVATE,
                            AiKnowledgeBase.SENSITIVITY_RESTRICTED,
                            "owner-1", "tenant-A", null));

            // user-2 在 tenant-A 访问: 应看到 doc-1 + doc-2，doc-3 因 RESTRICTED 被拒
            List<String> accessible = aclService.filterAccessibleDocumentIds(
                    ids, infos, "user-2", "tenant-A");
            assertEquals(2, accessible.size());
            assertTrue(accessible.contains("doc-1"));
            assertTrue(accessible.contains("doc-2"));
            assertFalse(accessible.contains("doc-3"));
        }

        @Test
        @DisplayName("空候选列表返回空")
        void emptyCandidates() {
            List<String> accessible = aclService.filterAccessibleDocumentIds(
                    List.of(), List.of(), "user-1", "tenant-A");
            assertTrue(accessible.isEmpty());
        }

        @Test
        @DisplayName("null 候选列表返回空")
        void nullCandidates() {
            List<String> accessible = aclService.filterAccessibleDocumentIds(
                    null, null, "user-1", "tenant-A");
            assertTrue(accessible.isEmpty());
        }

        @Test
        @DisplayName("aclInfos 中包含不在 candidateIds 中的文档 ID 时被忽略")
        void aclInfosNotInCandidatesIgnored() {
            List<String> ids = List.of("doc-1");
            List<DocumentAclInfo> infos = List.of(
                    doc("doc-1", AiKnowledgeBase.VISIBILITY_PUBLIC,
                            AiKnowledgeBase.SENSITIVITY_INTERNAL,
                            "owner-1", "tenant-A", null),
                    doc("doc-2", AiKnowledgeBase.VISIBILITY_PUBLIC,
                            AiKnowledgeBase.SENSITIVITY_INTERNAL,
                            "owner-1", "tenant-A", null));

            List<String> accessible = aclService.filterAccessibleDocumentIds(
                    ids, infos, "user-1", "tenant-A");
            assertEquals(1, accessible.size());
            assertTrue(accessible.contains("doc-1"));
            assertFalse(accessible.contains("doc-2"));
        }
    }

    // ==================== validateDocumentRetrievable ====================

    @Nested
    @DisplayName("validateDocumentRetrievable: 文档状态校验")
    class DocumentStatus {

        @Test
        @DisplayName("ACTIVE 状态可检索")
        void activeRetrievable() {
            assertDoesNotThrow(() -> aclService.validateDocumentRetrievable("ACTIVE"));
        }

        @Test
        @DisplayName("PENDING 状态不可检索")
        void pendingNotRetrievable() {
            assertThrows(BusinessConflictException.class,
                    () -> aclService.validateDocumentRetrievable("PENDING"));
        }

        @Test
        @DisplayName("DEPRECATED 状态不可检索")
        void deprecatedNotRetrievable() {
            assertThrows(BusinessConflictException.class,
                    () -> aclService.validateDocumentRetrievable("DEPRECATED"));
        }

        @Test
        @DisplayName("FAILED 状态不可检索")
        void failedNotRetrievable() {
            assertThrows(BusinessConflictException.class,
                    () -> aclService.validateDocumentRetrievable("FAILED"));
        }

        @Test
        @DisplayName("null 状态不可检索 (fail-closed)")
        void nullNotRetrievable() {
            assertThrows(BusinessConflictException.class,
                    () -> aclService.validateDocumentRetrievable(null));
        }
    }
}
