package com.yutong.ai.governance.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 触发 AI 评测运行请求。设计来源: 37-AI治理与评测设计 AI 发布门禁章节
 */
@Data
public class TriggerEvalRunRequest {

    /** 应用版本快照 */
    private String appVersion;

    /** Prompt 版本快照 */
    private String promptVersion;

    /** 模型路由版本快照 */
    private String modelRouteVersion;

    /** 知识库版本快照 */
    private String kbVersion;

    /**
     * 数据集过滤条件, 如 "scenario in (rag-core, security-boundary)"。
     * 为空时评测全部 enabled=true 的样本。
     */
    @Size(max = 256, message = "过滤条件长度不能超过 256")
    private String datasetFilter;
}
