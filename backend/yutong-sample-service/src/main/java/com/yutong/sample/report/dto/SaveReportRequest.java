package com.yutong.sample.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建/更新报表请求。设计来源: 42-报表与大屏可视化设计 /api/v1/report/reports CRUD。
 */
@Data
public class SaveReportRequest {

    @NotBlank(message = "报表编码不能为空")
    @Size(max = 64, message = "报表编码长度不能超过 64")
    private String reportCode;

    @NotBlank(message = "报表名称不能为空")
    @Size(max = 128, message = "报表名称长度不能超过 128")
    private String reportName;

    /** TABLE / CHART / MIX / DASHBOARD */
    private String reportType;

    @NotBlank(message = "布局 JSON 不能为空")
    private String layoutJson;

    private String permissionCode;

    private String description;
}
