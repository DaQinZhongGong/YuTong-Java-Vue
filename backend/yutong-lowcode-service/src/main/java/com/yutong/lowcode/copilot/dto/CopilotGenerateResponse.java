package com.yutong.lowcode.copilot.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Copilot 生成响应 — 返回草稿配置而非直接落库。
 */
@Data
public class CopilotGenerateResponse {

    /** 生成类型: form/page/entity */
    private String targetType;

    /** 建议的实体/页面编码 */
    private String suggestedCode;

    /** 建议的展示名 */
    private String suggestedName;

    /** 生成的字段/组件草稿 (前端可视化预览) */
    private List<Map<String, Object>> fields;

    /** 生成的 layout/form JSON (可直接预览) */
    private String layoutJson;

    /** 提示信息 */
    private String message;

    /** 是否 mock 生成 */
    private boolean mock;
}
