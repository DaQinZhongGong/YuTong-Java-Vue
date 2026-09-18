package com.yutong.ai.media.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 多模态媒体任务实体。
 * 设计来源: V041 ai_media_job, Phase 7 多模态 /media/* parity
 * media_type: image/video/audio/ppt, status: PENDING/RUNNING/SUCCESS/FAILED
 * 行业媒体生成能力最小可用字段集。
 */
@Getter
@Setter
@TableName(value = "ai_media_job", autoResultMap = true)
public class MediaJob extends BaseEntity {

    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_AUDIO = "audio";
    public static final String TYPE_PPT = "ppt";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    /** 媒体类型: image/video/audio/ppt */
    private String mediaType;

    /** 生成提示词 */
    private String prompt;

    /** 提供方编码 (mock/local/第三方) */
    private String providerCode;

    /** 模型编码 */
    private String modelCode;

    /** 任务状态: PENDING/RUNNING/SUCCESS/FAILED */
    private String status;

    /** 输入 JSON: {prompt, params, style, size, ...} */
    @TableField("input_json")
    private String inputJson;

    /** 输出资源 URL (mock 返回 fake url) */
    private String outputUrl;

    /** 输出 JSON: {url, meta, duration, ...} */
    @TableField("output_json")
    private String outputJson;

    /** 本次任务成本 (计费预留) */
    private BigDecimal cost;

    /** 厂商异步任务 ID（videoId/predictionId） */
    @TableField("external_job_id")
    private String externalJobId;
}

