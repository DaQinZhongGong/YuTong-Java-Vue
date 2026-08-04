package com.yutong.lowcode.plugin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存代码生成模板请求。
 * id 为空表示新建，非空表示修改（修改时 version 必填用于乐观锁）
 */
@Getter
@Setter
public class SaveCodeTemplateRequest {

    /** 为空表示新建 */
    private String id;

    /** 修改时必填，乐观锁版本号 */
    private Integer version;

    @NotBlank(message = "模板编码不能为空")
    @Size(max = 64, message = "模板编码长度不能超过64")
    private String templateCode;

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 128, message = "模板名称长度不能超过128")
    private String templateName;

    @NotBlank(message = "模板分类不能为空")
    @Size(max = 32, message = "模板分类长度不能超过32")
    private String category;

    @Size(max = 256, message = "标签长度不能超过256")
    private String tags;

    @NotBlank(message = "模板引擎不能为空")
    @Size(max = 16, message = "模板引擎长度不能超过16")
    private String engineType;

    @NotBlank(message = "模板内容不能为空")
    private String content;

    @Size(max = 512, message = "描述长度不能超过512")
    private String description;

    @Size(max = 16, message = "状态长度不能超过16")
    private String status;

    @Size(max = 256, message = "备注长度不能超过256")
    private String remark;
}
