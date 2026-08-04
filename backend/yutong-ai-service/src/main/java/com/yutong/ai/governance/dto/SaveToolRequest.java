package com.yutong.ai.governance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保存 AI 工具注册请求。设计来源: 37-AI治理与评测设计 AI 工具治理章节
 */
@Data
public class SaveToolRequest {

    /** 工具名（租户内唯一）, 主键为空时使用 */
    private String id;

    @NotBlank(message = "工具名不能为空")
    @Size(max = 128, message = "工具名长度不能超过 128")
    private String toolName;

    @Size(max = 32, message = "工具版本长度不能超过 32")
    private String toolVersion;

    /** LOW / MEDIUM / HIGH / CRITICAL */
    @NotBlank(message = "风险等级不能为空")
    private String riskLevel;

    /** A0~A4 */
    @NotBlank(message = "AI 能力等级不能为空")
    private String aiCapabilityLevel;

    @Size(max = 128, message = "权限码长度不能超过 128")
    private String permissionCode;

    @Size(max = 512, message = "描述长度不能超过 512")
    private String description;

    /** 输入 JSON Schema 字符串 */
    private String inputSchema;
    /** 输出 JSON Schema 字符串 */
    private String outputSchema;

    private Boolean isReadonly = true;
    private Boolean needsHumanReview = true;
    private Boolean accessBusinessData = false;
    private String dataScopeStrategy;
    private String fieldMaskingStrategy;
    private Integer maxResults = 100;
    private Integer timeoutMs = 60000;
    private Integer rateLimitPerMin;

    /** 是否禁止工具 (37 号文档明令禁止的 6 类) */
    private Boolean isForbidden = false;
    private String forbiddenReason;
    private Boolean enabled = true;
    private String ownerUserId;

    /** 乐观锁版本号 */
    private Integer version;
}
