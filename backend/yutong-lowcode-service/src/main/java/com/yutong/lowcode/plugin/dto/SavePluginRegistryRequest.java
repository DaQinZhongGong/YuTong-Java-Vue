package com.yutong.lowcode.plugin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存插件注册表请求。
 * id 为空表示新建，非空表示修改（修改时 version 必填用于乐观锁）
 */
@Getter
@Setter
public class SavePluginRegistryRequest {

    /** 为空表示新建 */
    private String id;

    /** 修改时必填，乐观锁版本号 */
    private Integer version;

    @NotBlank(message = "插件编码不能为空")
    @Size(max = 64, message = "插件编码长度不能超过64")
    private String pluginCode;

    @NotBlank(message = "插件名称不能为空")
    @Size(max = 128, message = "插件名称长度不能超过128")
    private String pluginName;

    @Size(max = 32, message = "插件版本长度不能超过32")
    private String pluginVersion;

    @NotBlank(message = "插件类型不能为空")
    @Size(max = 32, message = "插件类型长度不能超过32")
    private String pluginType;

    @Size(max = 16, message = "状态长度不能超过16")
    private String status;

    @Size(max = 512, message = "描述长度不能超过512")
    private String description;

    @Size(max = 256, message = "入口类长度不能超过256")
    private String entryClass;

    @Size(max = 512, message = "图标 URL 长度不能超过512")
    private String iconUrl;

    @Size(max = 256, message = "标签长度不能超过256")
    private String tags;

    @Size(max = 256, message = "备注长度不能超过256")
    private String remark;
}
