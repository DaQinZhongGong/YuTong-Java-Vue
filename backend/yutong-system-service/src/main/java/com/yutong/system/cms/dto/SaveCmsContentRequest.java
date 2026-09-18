package com.yutong.system.cms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * CMS 内容保存请求
 * 落点: 84-CMS 运营详设
 */
@Data
public class SaveCmsContentRequest {

    private String id; // 编辑时填, 创建时为空

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不超过 200 字")
    private String title;

    @NotBlank(message = "slug 不能为空")
    @Size(max = 100, message = "slug 不超过 100 字")
    private String slug;

    @NotBlank(message = "正文不能为空")
    private String contentMd;

    private String summary;

    /** 分类 */
    private String category;

    /** 标签, 逗号分隔 */
    private String tags;

    /** DRAFT / PUBLISHED / ARCHIVED */
    private String status;
}
