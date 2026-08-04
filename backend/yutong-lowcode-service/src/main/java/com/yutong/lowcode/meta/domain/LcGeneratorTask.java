package com.yutong.lowcode.meta.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 低代码代码生成任务。设计来源: 14-低代码平台设计、57-完整DDL清单 lc_generator_task
 * 状态机: PENDING → RUNNING → SUCCESS / FAILED / CONFLICT / CANCELLED
 * target_scope: DDL / JAVA / VUE / UNIAPP / OPENAPI
 * 禁止复用导入导出任务的 PARTIAL_SUCCESS 状态
 */
@Getter
@Setter
@TableName("lc_generator_task")
public class LcGeneratorTask extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CONFLICT = "CONFLICT";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String SCOPE_DDL = "DDL";
    public static final String SCOPE_JAVA = "JAVA";
    public static final String SCOPE_VUE = "VUE";
    public static final String SCOPE_UNIAPP = "UNIAPP";
    public static final String SCOPE_OPENAPI = "OPENAPI";

    private String taskNo;

    private String entityId;

    private String pageId;

    private String templateVersion;

    /** DDL / JAVA / VUE / UNIAPP / OPENAPI */
    private String targetScope;

    /** 差异预览 JSON：新增/删除/修改文件分组 + DDL 差异 */
    private String diffJson;

    private Integer conflictCount;

    private String status;

    private String resultFileId;

    private String errorMessage;

    private OffsetDateTime startedTime;

    private OffsetDateTime finishedTime;
}
