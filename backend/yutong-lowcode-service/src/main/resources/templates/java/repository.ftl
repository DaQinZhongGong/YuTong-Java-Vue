<#-- Java Repository 模板 | 设计来源: 65-低代码代码生成模板详设 -->
<#-- @ftlvariable name="entity" type="com.yutong.lowcode.generator.service.CodeTemplateService.TemplateEntity" -->
<#-- @ftlvariable name="className" type="String" -->
// 由低代码生成器生成，可二开但需保留生成标记
package com.yutong.generated.${entity.moduleCode!'lowcode'}.repository;

import com.yutong.generated.${entity.moduleCode!'lowcode'}.${className};
import com.yutong.generated.${entity.moduleCode!'lowcode'}.mapper.${className}Mapper;
import org.springframework.stereotype.Repository;

@Repository
public class ${className}Repository {

    private final ${className}Mapper mapper;

    public ${className}Repository(${className}Mapper mapper) {
        this.mapper = mapper;
    }

    public ${className} findById(String id) {
        return mapper.selectById(id);
    }

    public int insert(${className} entity) {
        return mapper.insert(entity);
    }

    public int update(${className} entity) {
        return mapper.updateById(entity);
    }

    public int deleteById(String id) {
        return mapper.deleteById(id);
    }
}
