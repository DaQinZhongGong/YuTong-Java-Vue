package com.yutong.ai.rag.service;

import com.yutong.ai.rag.domain.AiKnowledgeBase;
import com.yutong.auth.AuthAdapter;
import com.yutong.auth.AuthContext;
import com.yutong.common.exception.BusinessConflictException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 检索 ACL 过滤服务。设计来源: 13-AI能力设计
 * <p>
 * 核心约束: 先做结构化授权过滤，再做向量召回。
 * 用户无权访问的文档不能进入召回候选，也不能在引用来源中暴露。
 * 安全原则: 权限校验失败即拒绝（fail-closed），宁可拒答也不能泄露无权内容。
 */
@Service
public class RagAclService {

    /** admin 通配权限码，与 MockAuthAdapter.requirePermission 保持一致。 */
    private static final String WILDCARD_PERMISSION = "*";

    private final AuthAdapter authAdapter;

    public RagAclService(AuthAdapter authAdapter) {
        this.authAdapter = authAdapter;
    }

    /**
     * 校验用户是否有权访问知识库。
     *
     * @param visibility      知识库可见性 PRIVATE/TENANT/PUBLIC
     * @param ownerUserId     知识库所有者
     * @param permissionCode  权限码
     * @param currentUserId   当前用户
     * @param currentTenantId 当前租户
     * @param kbTenantId      知识库租户
     */
    public void checkKnowledgeBaseAccess(String visibility, String ownerUserId, String permissionCode,
                                         String currentUserId, String currentTenantId, String kbTenantId) {
        // PUBLIC: 允许所有租户访问
        if (AiKnowledgeBase.VISIBILITY_PUBLIC.equals(visibility)) {
            return;
        }
        // TENANT: 必须同租户
        if (AiKnowledgeBase.VISIBILITY_TENANT.equals(visibility)) {
            if (currentTenantId == null || !currentTenantId.equals(kbTenantId)) {
                throw new BusinessConflictException("无权访问知识库");
            }
            return;
        }
        // PRIVATE: 必须同租户且（是所有者 或 持有 permissionCode 权限）
        if (AiKnowledgeBase.VISIBILITY_PRIVATE.equals(visibility)) {
            if (currentTenantId == null || !currentTenantId.equals(kbTenantId)) {
                throw new BusinessConflictException("无权访问知识库");
            }
            if (currentUserId != null && currentUserId.equals(ownerUserId)) {
                return;
            }
            if (hasPermission(currentUserId, permissionCode)) {
                return;
            }
            throw new BusinessConflictException("无权访问知识库");
        }
        // 未知可见性，拒绝访问
        throw new BusinessConflictException("无权访问知识库");
    }

    /**
     * 过滤文档列表，只返回用户有权访问的文档。
     * 同时按 visibility 与 sensitivityLevel 做双重过滤，取更严格者。
     *
     * @param candidateIds     候选文档 ID 列表
     * @param aclInfos         候选文档的 ACL 信息
     * @param currentUserId    当前用户
     * @param currentTenantId  当前租户
     * @return 用户有权访问的文档 ID 列表
     */
    public List<String> filterAccessibleDocumentIds(List<String> candidateIds,
                                                    List<DocumentAclInfo> aclInfos,
                                                    String currentUserId, String currentTenantId) {
        List<String> accessible = new ArrayList<>();
        if (candidateIds == null || candidateIds.isEmpty() || aclInfos == null) {
            return accessible;
        }
        for (DocumentAclInfo info : aclInfos) {
            if (info == null || !candidateIds.contains(info.documentId())) {
                continue;
            }
            if (isDocumentAccessible(info, currentUserId, currentTenantId)) {
                accessible.add(info.documentId());
            }
        }
        return accessible;
    }

    /**
     * 校验文档状态可被检索: 必须为 ACTIVE，其他状态抛异常。
     * 禁止继续使用旧列名 parse_status。
     */
    public void validateDocumentRetrievable(String documentStatus) {
        if (!"ACTIVE".equals(documentStatus)) {
            throw new BusinessConflictException("文档状态不可检索: " + documentStatus + "，仅 ACTIVE 可召回");
        }
    }

    /**
     * 判断单个文档是否可被当前用户访问。
     * 综合可见性与敏感等级，取更严格约束。
     */
    private boolean isDocumentAccessible(DocumentAclInfo info, String currentUserId, String currentTenantId) {
        // 敏感等级约束（叠加在可见性之上，取更严格者）
        // RESTRICTED: 仅所有者可访问
        if (AiKnowledgeBase.SENSITIVITY_RESTRICTED.equals(info.sensitivityLevel())) {
            return currentUserId != null && currentUserId.equals(info.ownerUserId());
        }
        // CONFIDENTIAL: 必须同租户
        if (AiKnowledgeBase.SENSITIVITY_CONFIDENTIAL.equals(info.sensitivityLevel())) {
            if (currentTenantId == null || !currentTenantId.equals(info.tenantId())) {
                return false;
            }
        }
        // 可见性约束
        String visibility = info.visibility();
        // PUBLIC: 允许所有租户
        if (AiKnowledgeBase.VISIBILITY_PUBLIC.equals(visibility)) {
            return true;
        }
        // TENANT: 必须同租户
        if (AiKnowledgeBase.VISIBILITY_TENANT.equals(visibility)) {
            return currentTenantId != null && currentTenantId.equals(info.tenantId());
        }
        // PRIVATE: 必须同租户且（是所有者 或 持有权限码）
        if (AiKnowledgeBase.VISIBILITY_PRIVATE.equals(visibility)) {
            if (currentTenantId == null || !currentTenantId.equals(info.tenantId())) {
                return false;
            }
            if (currentUserId != null && currentUserId.equals(info.ownerUserId())) {
                return true;
            }
            return hasPermission(currentUserId, info.permissionCode());
        }
        // 未知可见性，拒绝
        return false;
    }

    /**
     * 校验当前用户是否持有指定权限码。
     * <p>
     * 通过 {@link AuthAdapter#current()} 获取当前用户 {@link AuthContext}，
     * 校验其权限集合是否包含 {@code permissionCode}。
     * <ul>
     *   <li>admin 通配权限 "*" 直接放行（与 {@code MockAuthAdapter.requirePermission} 一致）。</li>
     *   <li>无登录上下文（{@code current()} 返回 null）或权限集合为空 → fail-closed 返回 false。</li>
     *   <li>{@code permissionCode} 为空 → fail-closed 返回 false（拒绝，避免空权限码误放行）。</li>
     * </ul>
     * 安全原则: 任何异常均 fail-closed 返回 false，禁止因鉴权异常而泄露内容。
     */
    private boolean hasPermission(String currentUserId, String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        try {
            AuthContext ctx = authAdapter.current();
            if (ctx == null || ctx.permissions() == null) {
                return false;
            }
            // admin 通配权限放行
            if (ctx.permissions().contains(WILDCARD_PERMISSION)) {
                return true;
            }
            return ctx.permissions().contains(permissionCode);
        } catch (RuntimeException e) {
            // 鉴权服务异常 → fail-closed，宁可拒答也不泄露无权内容
            return false;
        }
    }

    /**
     * 文档 ACL 信息载体。
     *
     * @param documentId       文档 ID
     * @param visibility       可见性
     * @param permissionCode   权限码
     * @param ownerUserId      所有者用户 ID（继承自知识库）
     * @param tenantId         所属租户
     * @param sensitivityLevel 敏感等级
     */
    public record DocumentAclInfo(String documentId, String visibility, String permissionCode,
                                  String ownerUserId, String tenantId, String sensitivityLevel) {
    }
}
