package com.yutong.sample.report.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 报表数据集。设计来源: 35-样例业务矩阵扩展设计 P1 报表分析、42-报表与大屏可视化设计 rpt_dataset。
 * <p>
 * 数据集是报表和大屏的唯一取数入口，工作台 R0 指标后续也应逐步复用它，避免同一指标多处 SQL 口径不一致。
 * <p>
 * GA2-36 验证能力: SQL 执行 + 参数化 + 缓存 + DataScope 过滤 + 列级脱敏 + 行数限制 + 超时控制。
 */
@Getter
@Setter
@TableName("rpt_dataset")
public class RptDataset extends BaseEntity {

    public static final String SOURCE_SQL = "SQL";
    public static final String SOURCE_VIEW = "VIEW";

    public static final String RISK_LOW = "LOW";
    public static final String RISK_MEDIUM = "MEDIUM";
    public static final String RISK_HIGH = "HIGH";

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    public static final String REVIEW_DRAFT = "DRAFT";
    public static final String REVIEW_REVIEWING = "REVIEWING";
    public static final String REVIEW_APPROVED = "APPROVED";
    public static final String REVIEW_REJECTED = "REJECTED";

    /** 唯一编码 */
    private String datasetCode;

    private String datasetName;

    /** SQL / VIEW */
    private String sourceType;

    /** SQL/VIEW 查询文本，参数使用 :paramName 命名占位符 */
    private String queryText;

    /** 参数 schema (jsonb 序列化为字符串): [{"name":"days","type":"int","required":false,"default":7}] */
    private String paramsSchema;

    /** 缓存秒数，0 表示不缓存 */
    private Integer cacheSeconds;

    /** LOW/MEDIUM/HIGH，决定评审和导出策略 */
    private String riskLevel;

    private String ownerUserId;

    /** 查看或预览所需权限码 */
    private String permissionCode;

    /** 列级脱敏策略 (jsonb 序列化为字符串): {"amount":{"viewer":"MASK"}} */
    private String sensitiveColumns;

    private Integer maxRows;

    private Integer timeoutMs;

    /** DRAFT/REVIEWING/APPROVED/REJECTED */
    private String reviewStatus;

    /** DRAFT/PUBLISHED */
    private String status;

    /**
     * 数据源编码, 指向 sys_datasource.datasource_code。
     * GA2-46 v1.5: 报表数据集可指定只读从库, 不影响业务写入。
     * 默认 "primary", 走主库; "report_ro" 走只读从库。
     */
    private String datasourceCode;

    private String description;
}
