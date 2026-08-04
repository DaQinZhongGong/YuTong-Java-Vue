package com.yutong.ai.ops.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识库问答日志。设计来源: 35-样例业务矩阵扩展设计 P2 知识库运营。
 * 一次问答产生一条记录, 包含问题/答案/引用/命中分数/是否拒答。
 */
@Getter
@Setter
@TableName("kb_conversation_log")
public class KbConversationLog extends BaseEntity {
    public static final String REFUSE_NO_HITS = "NO_HITS";
    public static final String REFUSE_LOW_CONFIDENCE = "LOW_CONFIDENCE";
    public static final String REFUSE_KB_DISABLED = "KB_DISABLED";
    public static final String REFUSE_ACL_DENIED = "ACL_DENIED";

    private String conversationNo;
    private String kbId;
    private String userId;
    private String question;
    private String answer;
    private Integer hitChunkCount;
    private Double maxScore;
    private Double minScore;
    private Double avgScore;
    private Boolean isRefused;
    private String refuseReason;
    private String citedDocuments;  // JSON 字符串
    private String citedChunkIds;   // JSON 字符串
    private Long latencyMs;
    private String aiConversationId;
}
