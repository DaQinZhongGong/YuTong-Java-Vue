package com.yutong.ai.harness.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Coding Harness 会话。表: ai_harness_session（V059）。
 */
@Getter
@Setter
@TableName("ai_harness_session")
public class HarnessSession extends BaseEntity {

    private String userId;
    private String title;
    private String workspacePath;
    /** workspace_manifest jsonb，以 JSON 字符串承载 */
    private String workspaceManifest;
    private String model;
    private String permissionMode;
    private String approvalPolicy;
    private String thinkingLevel;
    private String verificationMode;
    private String activeRunId;
    private String idempotencyKey;
    private OffsetDateTime pinnedAt;
    /** 业务乐观锁版本（权限/审批 resolve 必须校验），与 BaseEntity.version 分离 */
    private Long revision;
}
