package com.yutong.ai.chat.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 应用 AI 建议请求。设计来源: 13-AI能力设计 applyAiSuggestion
 * 关键约束: AI 生成结果只能进入草稿区，必须回传 schema 版本、哈希、expectedVersion 和幂等键
 * 任一不匹配均拒绝写入
 */
@Getter
@Setter
public class ApplySuggestionRequest {

    /** 草稿 ID（低代码草稿区中的草稿） */
    private String draftId;

    /** 草稿类型: PAGE / ENTITY / SQL */
    private String draftType;

    /** 草稿内容 JSON */
    private String draftContent;

    /** schema 版本 */
    private String schemaVersion;

    /** 配置哈希 */
    private String configHash;

    /** 期望版本号（乐观锁） */
    private Integer expectedVersion;

    /** 幂等键 */
    private String idempotencyKey;
}
