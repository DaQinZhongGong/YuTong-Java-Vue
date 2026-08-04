<#-- Java Entity 模板 | 设计来源: 65-低代码代码生成模板详设
  约束:
    - 继承 BaseEntity
    - @TableName("{tableName}")
    - ID 使用 String
    - decimal 使用 BigDecimal
    - 时间使用 OffsetDateTime
-->
<#-- @ftlvariable name="entity" type="com.yutong.lowcode.generator.service.CodeTemplateService.TemplateEntity" -->
<#-- @ftlvariable name="fields" type="java.util.List<com.yutong.lowcode.generator.service.CodeTemplateService.TemplateField>" -->
<#-- @ftlvariable name="className" type="String" -->
// 由低代码生成器生成，可二开但需保留生成标记
// entity: ${entity.entityCode} | version: ${entity.versionNo!1} | 生成时间: ${.now?iso_local}
package com.yutong.generated.${entity.moduleCode!'lowcode'};

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;
<#list fields as f><#if f.javaType == 'BigDecimal'>
import java.math.BigDecimal;
<#break>
</#if></#list>
<#list fields as f><#if f.javaType == 'OffsetDateTime'>
import java.time.OffsetDateTime;
<#break>
</#if></#list>

@Getter
@Setter
@TableName("${entity.tableName}")
public class ${className} extends BaseEntity {

<#list fields as f><#if !(f.primaryFlag!false) || f.fieldCode != 'id'>
    /** ${f.fieldName!''} */
    private ${f.javaType} ${f.fieldCodeCamel};

</#if></#list>
}
