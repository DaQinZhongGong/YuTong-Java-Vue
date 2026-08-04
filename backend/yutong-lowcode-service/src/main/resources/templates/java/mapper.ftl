<#-- Java Mapper 模板 | 设计来源: 65-低代码代码生成模板详设 -->
<#-- @ftlvariable name="entity" type="com.yutong.lowcode.generator.service.CodeTemplateService.TemplateEntity" -->
<#-- @ftlvariable name="className" type="String" -->
// 由低代码生成器生成，可二开但需保留生成标记
package com.yutong.generated.${entity.moduleCode!'lowcode'}.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.generated.${entity.moduleCode!'lowcode'}.${className};
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ${className}Mapper extends BaseMapper<${className}> {
}
