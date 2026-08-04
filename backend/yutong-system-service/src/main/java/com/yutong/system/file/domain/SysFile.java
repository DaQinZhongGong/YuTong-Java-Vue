package com.yutong.system.file.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 文件元数据。设计来源: 57-完整DDL清单 sys_file */
@Getter
@Setter
@TableName("sys_file")
public class SysFile extends BaseEntity {

    /** 原始文件名 */
    private String fileName;

    /** 存储键 (租户隔离: {tenantId}/{yyyy/MM/dd}/{ULID}.{ext}) */
    private String fileKey;

    /** 文件大小 (字节) */
    private Long fileSize;

    /** 内容类型 (MIME) */
    private String contentType;

    /** 文件扩展名 (不含点) */
    private String fileExt;

    /** 存储类型: MINIO / LOCAL */
    private String storageType;

    /** 校验和 (SHA-256) */
    private String checksum;

    /** 上传状态: SUCCESS / FAILED */
    private String uploadStatus;
}
