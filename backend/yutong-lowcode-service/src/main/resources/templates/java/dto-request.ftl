<#-- Java Request DTO 模板 | 设计来源: 65-低代码代码生成模板详设
  约束: Controller 只接收 Request DTO
-->
<#-- @ftlvariable name="entity" type="com.yutong.lowcode.generator.service.CodeTemplateService.TemplateEntity" -->
<#-- @ftlvariable name="fields" type="java.util.List<com.yutong.lowcode.generator.service.CodeTemplateService.TemplateField>" -->
<#-- @ftlvariable name="className" type="String" -->
// 由低代码生成器生成，可二开但需保留生成标记
package com.yutong.generated.${entity.moduleCode!'lowcode'}.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
 * ${entity.entityName} 创建/更新请求 DTO
 */
@Data
public class ${className}Request {

<#list fields as f><#if !(f.primaryFlag!false) || f.fieldCode != 'id'>
    <#if f.nullable?? && !f.nullable>
    <#if f.javaType == 'String'>
    @NotBlank
    <#else>
    @NotNull
    </#if>
    </#if>
    private ${f.javaType} ${f.fieldCodeCamel};

</#if></#list>
}
