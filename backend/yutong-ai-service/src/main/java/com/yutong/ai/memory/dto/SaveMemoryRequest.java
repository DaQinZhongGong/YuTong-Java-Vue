package com.yutong.ai.memory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class SaveMemoryRequest {

    /** GLOBAL / TENANT / USER / AGENT，默认 USER */
    private String ownerType;

    /** 空则按 ownerType 自动填充当前用户/租户 */
    private String ownerId;

    /** LONG_TERM / USER / GLOBAL，默认 LONG_TERM */
    private String memoryKind;

    @NotBlank(message = "content 不能为空")
    private String content;

    /** MANUAL / CHAT / KNOWLEDGE / TOOL / AGENT */
    private String source;

    private BigDecimal confidence;
    private OffsetDateTime expiresAt;
    private String remark;
}
