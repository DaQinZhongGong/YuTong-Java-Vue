package com.yutong.sample.report.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据集执行结果。设计来源: 42-报表与大屏可视化设计 DatasetResult。
 * <p>
 * 必须包含 columns、rows、rowCount、fromCache、generatedTime、datasetVersion、dataScopeApplied、maskedColumns、traceId。
 * 前端 tooltip 和导出页必须能展示统计口径、数据时间和 traceId。
 * <p>
 * 注意：@JsonCreator + @JsonProperty 用于 Redis 缓存反序列化（@Builder 不提供默认构造器）。
 */
@Getter
@Builder
public class DatasetResultVO {

    /** 列定义 */
    private final List<String> columns;

    /** 数据行（每行为 Map<columnName, value>） */
    private final List<Map<String, Object>> rows;

    /** 行数 */
    private final int rowCount;

    /** 是否命中缓存 */
    private final boolean fromCache;

    /** 数据生成时间 */
    private final OffsetDateTime generatedTime;

    /** 数据集版本 */
    private final int datasetVersion;

    /** 是否应用了 DataScope */
    private final boolean dataScopeApplied;

    /** 被脱敏的列名列表 */
    private final List<String> maskedColumns;

    /** 链路 ID */
    private final String traceId;

    @JsonCreator
    public DatasetResultVO(@JsonProperty("columns") List<String> columns,
                            @JsonProperty("rows") List<Map<String, Object>> rows,
                            @JsonProperty("rowCount") int rowCount,
                            @JsonProperty("fromCache") boolean fromCache,
                            @JsonProperty("generatedTime") OffsetDateTime generatedTime,
                            @JsonProperty("datasetVersion") int datasetVersion,
                            @JsonProperty("dataScopeApplied") boolean dataScopeApplied,
                            @JsonProperty("maskedColumns") List<String> maskedColumns,
                            @JsonProperty("traceId") String traceId) {
        this.columns = columns;
        this.rows = rows;
        this.rowCount = rowCount;
        this.fromCache = fromCache;
        this.generatedTime = generatedTime;
        this.datasetVersion = datasetVersion;
        this.dataScopeApplied = dataScopeApplied;
        this.maskedColumns = maskedColumns;
        this.traceId = traceId;
    }
}
