package com.yutong.ai.harness.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.ai.harness.enums.ApprovalState;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 工具调用审批。表: ai_harness_approval（V059）。
 */
@Getter
@Setter
@TableName("ai_harness_approval")
public class HarnessApproval extends BaseEntity {

    private String sessionId;
    private String runId;
    private String toolName;
    private String toolCallId;
    private String argumentsJson;
    /** 工具参数 UTF-8 SHA-256 hex，resolve/claim 常时比较 */
    private String argumentsSha256;
    private String state;
    private Long expectedRevision;
    private Long permissionRevision;
    private String decisionId;
    /** APPROVE / DENY */
    private String decision;
    private String decidedBy;
    private OffsetDateTime decidedAt;
    private String note;
    private OffsetDateTime expiresAt;

    public ApprovalState stateEnum() {
        return state == null ? null : ApprovalState.valueOf(state);
    }
}
