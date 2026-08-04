<#-- Java Response DTO 模板 | 设计来源: 65-低代码代码生成模板详设 -->
<#-- @ftlvariable name="entity" type="com.yutong.lowcode.generator.service.CodeTemplateService.TemplateEntity" -->
<#-- @ftlvariable name="fields" type="java.util.List<com.yutong.lowcode.generator.service.CodeTemplateService.TemplateField>" -->
<#-- @ftlvariable name="className" type="String" -->
// 由低代码生成器生成，可二开但需保留生成标记
package com.yutong.generated.${entity.moduleCode!'lowcode'}.dto;

import lombok.Data;
<#list fields as f><#if f.javaType == 'BigDecimal'>
import java.math.BigDecimal;
<#break>
</#if></#list>
<#list fields as f><#if f.javaType == 'OffsetDateTime'>
import java.time.OffsetDateTime;
<#break>
</#if></#list>

/**
 * ${entity.entityName} 响应 DTO
 */
@Data
public class ${className}Response {

    private String id;

<#list fields as f><#if !(f.primaryFlag!false) || f.fieldCode != 'id'>
    /** ${f.fieldName!''} */
    private ${f.javaType} ${f.fieldCodeCamel};

</#if></#list>
    private String createdBy;
    private java.time.OffsetDateTime createdTime;
    private String updatedBy;
    private java.time.OffsetDateTime updatedTime;
    private Integer version;
}
