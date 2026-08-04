package com.yutong.lowcode.meta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存组件协议元数据请求。设计来源: 36-低代码高级能力设计 组件协议 API 契约
 * id 为空表示新建，非空表示修改（修改时 version 必填用于乐观锁）
 */
@Getter
@Setter
public class SaveComponentRegistryRequest {

    /** 为空表示新建 */
    private String id;

    /** 修改时必填，乐观锁版本号 */
    private Integer version;

    @NotBlank(message = "组件编码不能为空")
    @Size(max = 64, message = "组件编码长度不能超过64")
    private String componentCode;

    @NotBlank(message = "组件名称不能为空")
    @Size(max = 128, message = "组件名称长度不能超过128")
    private String componentName;

    @NotBlank(message = "组件类型不能为空")
    @Size(max = 32, message = "组件类型长度不能超过32")
    private String componentType;

    @NotBlank(message = "显示名称不能为空")
    @Size(max = 128, message = "显示名称长度不能超过128")
    private String displayName;

    /** WEB / MOBILE / BOTH */
    @Size(max = 16, message = "平台长度不能超过16")
    private String platform;

    /** INPUT / DISPLAY / CONTAINER / TABLE / BUSINESS / MOBILE / CHART */
    @NotBlank(message = "组件分类不能为空")
    @Size(max = 32, message = "组件分类长度不能超过32")
    private String category;

    /** STABLE / EXPERIMENTAL / INTERNAL */
    @Size(max = 16, message = "兼容性等级长度不能超过16")
    private String compatibilityGrade;

    /** 组件属性 JSON Schema */
    private String propsSchema;

    /** 组件事件契约 JSON */
    private String eventSchema;

    /** 数据绑定契约 JSON */
    private String dataBinding;

    private Boolean permissionSupport;

    private Boolean validationSupport;

    @Size(max = 128, message = "权限码长度不能超过128")
    private String permissionCode;

    @Size(max = 16, message = "组件版本长度不能超过16")
    private String componentVersion;

    @Size(max = 16, message = "最低平台版本长度不能超过16")
    private String minPlatformVersion;

    @Size(max = 16, message = "最高平台版本长度不能超过16")
    private String maxPlatformVersion;

    @Size(max = 512, message = "描述长度不能超过512")
    private String description;

    @Size(max = 64, message = "图标长度不能超过64")
    private String icon;

    /** DRAFT / PUBLISHED / DISABLED */
    @Size(max = 16, message = "状态长度不能超过16")
    private String status;

    private Boolean deprecated;

    @Size(max = 256, message = "废弃提示长度不能超过256")
    private String deprecatedMessage;

    private Integer sortNo;

    @Size(max = 256, message = "备注长度不能超过256")
    private String remark;
}
