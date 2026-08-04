package com.yutong.ai.rag.dto;

/**
 * 文档受控入库请求体。设计来源: 13-AI能力设计 受控管理面入库
 *
 * @param kbId             知识库 ID
 * @param docTitle         文档标题
 * @param sourceType       源类型: DESIGN_DOC / OPENAPI / DATA_DICT / FAQ
 * @param sourceUri        源地址 URI
 * @param content          文档正文
 * @param visibility       可见性: PRIVATE / TENANT / PUBLIC
 * @param sensitivityLevel 敏感等级: INTERNAL / CONFIDENTIAL / RESTRICTED
 * @param permissionCode   访问所需权限码
 */
public record IngestDocumentRequest(
        String kbId,
        String docTitle,
        String sourceType,
        String sourceUri,
        String content,
        String visibility,
        String sensitivityLevel,
        String permissionCode
) {
}
