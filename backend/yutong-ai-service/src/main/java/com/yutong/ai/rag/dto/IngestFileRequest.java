package com.yutong.ai.rag.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 基于文件入库请求体。优先使用 fileId 从 MinIO 抽取文本。
 *
 * @param kbId             知识库 ID（必填）
 * @param docTitle         文档标题（可选，默认取文件名）
 * @param fileId           文件 ID（必填，sys_file.id）
 * @param loaderType       装载器类型覆盖（可选，pdf/word/excel/csv/md/txt，为空按后缀自动路由）
 * @param sourceType       源类型（可选）
 * @param visibility       可见性
 * @param sensitivityLevel 敏感等级
 * @param permissionCode   权限码
 */
public record IngestFileRequest(
        @NotBlank String kbId,
        String docTitle,
        @NotBlank String fileId,
        String loaderType,
        String sourceType,
        String visibility,
        String sensitivityLevel,
        String permissionCode
) {}
