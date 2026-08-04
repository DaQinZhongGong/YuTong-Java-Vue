package com.yutong.system.file.dto;

/**
 * 文件业务绑定请求。
 *
 * @param bizType 业务类型
 * @param bizId   业务对象 ID
 * @param fileId  文件 ID
 * @param relType 关系类型
 * @param sortNo  排序号
 */
public record BindFileRequest(
        String bizType,
        String bizId,
        String fileId,
        String relType,
        Integer sortNo
) {
}
