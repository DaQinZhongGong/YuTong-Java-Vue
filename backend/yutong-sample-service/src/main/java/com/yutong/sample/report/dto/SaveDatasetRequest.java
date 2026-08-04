package com.yutong.sample.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建/更新数据集请求。设计来源: 42-报表与大屏可视化设计 /api/v1/report/datasets CRUD。
 */
@Data
public class SaveDatasetRequest {

    @NotBlank(message = "数据集编码不能为空")
    @Size(max = 64, message = "数据集编码长度不能超过 64")
    private String datasetCode;

    @NotBlank(message = "数据集名称不能为空")
    @Size(max = 128, message = "数据集名称长度不能超过 128")
    private String datasetName;

    /** SQL / VIEW，默认 SQL */
    private String sourceType;

    @NotBlank(message = "查询文本不能为空")
    private String queryText;

    /** 参数 schema JSON 字符串 */
    private String paramsSchema;

    private Integer cacheSeconds;

    /** LOW/MEDIUM/HIGH */
    private String riskLevel;

    private String permissionCode;

    /** 列级脱敏策略 JSON 字符串 */
    private String sensitiveColumns;

    private Integer maxRows;

    private Integer timeoutMs;

    private String description;

    /**
     * GA2-46 v1.5: 数据源编码, 指向 sys_datasource.datasource_code。
     * 默认 "primary" 走主库; "report_ro" 走只读从库。
     */
    private String datasourceCode;
}
