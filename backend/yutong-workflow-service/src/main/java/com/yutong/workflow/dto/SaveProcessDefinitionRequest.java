package com.yutong.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 保存流程定义请求。
 * <p>新建时: status 默认 DRAFT, version_no 由 service 计算最大值+1。
 * <p>更新时: 仅 DRAFT 状态可更新; PUBLISHED 仅可更新 description; DISABLED 不可更新。
 */
@Data
public class SaveProcessDefinitionRequest {

    @NotBlank
    @Size(max = 64)
    private String processKey;

    @NotBlank
    @Size(max = 128)
    private String processName;

    @Size(max = 64)
    private String categoryCode;

    @Size(max = 64)
    private String bizType;

    /** BPMN XML 内容 (DRAFT 草稿必填) */
    @NotBlank
    private String bpmnXml;

    @Size(max = 64)
    private String formPageCode;

    @Size(max = 512)
    private String description;

    /** 服务任务白名单 (如 ["message/send","todo/sync","business-callback"]) */
    private List<String> serviceTaskWhitelist;
}
