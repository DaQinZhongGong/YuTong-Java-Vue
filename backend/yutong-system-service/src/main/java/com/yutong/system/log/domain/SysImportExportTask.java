package com.yutong.system.log.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 导入导出任务。设计来源: 57-完整DDL清单 sys_import_export_task
 *  GA2-36 修复: started_time/finished_time 列为 TIMESTAMPTZ，使用 OffsetDateTime 避免 PgResultSet 转换异常。 */
@Getter
@Setter
@TableName("sys_import_export_task")
public class SysImportExportTask extends BaseEntity {
    private String taskType;
    private String bizType;
    private String fileId;
    private String status;
    private Integer totalRows;
    private Integer successRows;
    private Integer failRows;
    private String errorFileId;
    private String errorMessage;
    private OffsetDateTime startedTime;
    private OffsetDateTime finishedTime;
}
