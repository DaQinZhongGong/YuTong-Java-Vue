package com.yutong.ai.copilot.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AiCopilotDraftResponse {
    private String targetType;
    private String suggestedCode;
    private String suggestedName;
    private List<Map<String, Object>> fields;
    private String layoutJson;
    private String message;
    private String providerCode;
    private String modelCode;
    private boolean mock;
    private String runId;
    private String status;
}
