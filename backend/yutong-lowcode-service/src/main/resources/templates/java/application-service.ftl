<#-- Java ApplicationService 模板 | 设计来源: 65-低代码代码生成模板详设
  约束:
    - 写接口加事务
    - 状态动作单独方法
    - 发布领域事件在事务后
-->
<#-- @ftlvariable name="entity" type="com.yutong.lowcode.generator.service.CodeTemplateService.TemplateEntity" -->
<#-- @ftlvariable name="className" type="String" -->
<#-- @ftlvariable name="apiPrefix" type="String" -->
// 由低代码生成器生成，可二开但需保留生成标记
package com.yutong.generated.${entity.moduleCode!'lowcode'}.service;

import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.generated.${entity.moduleCode!'lowcode'}.${className};
import com.yutong.generated.${entity.moduleCode!'lowcode'}.dto.${className}Request;
import com.yutong.generated.${entity.moduleCode!'lowcode'}.dto.${className}Response;
import com.yutong.generated.${entity.moduleCode!'lowcode'}.repository.${className}Repository;
import com.yutong.common.id.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
<#-- 集合字段 (List) 需做防御性拷贝，统一引入 ArrayList；无集合时该 import 为无害冗余 -->
import java.util.ArrayList;

<#-- 字段映射宏: 基于 fields 元数据进行真实遍历自动映射
     - Request 与 Entity 共享同一 javaType，故标量字段直接赋值即类型安全
     - 集合类型 (javaType 以 List 开头) 做防御性拷贝，避免外部持有同一 List 引用
     - 标量字段直接赋值天然 null 安全 (setter 接受 null)，集合字段加 null 判空
     @param field 模板字段元数据
     @param src   取值对象 (request / entity)，调用其 getXxx()
     @param dst   赋值对象 (entity / resp)，调用其 setXxx(...)
-->
<#macro mapField field src dst>
<#if (field.javaType!'')?starts_with('List')>
        if (${src}.get${field.fieldCodeCamel?cap_first}() != null) {
            ${dst}.set${field.fieldCodeCamel?cap_first}(new ArrayList<>(${src}.get${field.fieldCodeCamel?cap_first}()));
        }
<#else>
        ${dst}.set${field.fieldCodeCamel?cap_first}(${src}.get${field.fieldCodeCamel?cap_first}());
</#if>
</#macro>

@Service
public class ${className}ApplicationService {

    private final ${className}Repository repository;

    public ${className}ApplicationService(${className}Repository repository) {
        this.repository = repository;
    }

    @Transactional
    public ${className}Response create(${className}Request request) {
        ${className} entity = new ${className}();
        entity.setId(IdGenerator.nextId());
<#list fields as f><#if !(f.primaryFlag!false) || f.fieldCode != 'id'>
        <@mapField field=f src='request' dst='entity'/>
</#if></#list>
        repository.insert(entity);
        return toResponse(entity);
    }

    @Transactional
    public ${className}Response update(String id, ${className}Request request) {
        ${className} entity = repository.findById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("${className} 不存在: " + id);
        }
<#list fields as f><#if !(f.primaryFlag!false) || f.fieldCode != 'id'>
        <@mapField field=f src='request' dst='entity'/>
</#if></#list>
        int affected = repository.update(entity);
        if (affected == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        return toResponse(entity);
    }

    @Transactional
    public void delete(String id) {
        ${className} entity = repository.findById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("${className} 不存在: " + id);
        }
        // 实体继承 BaseEntity 并含 @TableLogic，deleteById 自动逻辑删除
        repository.deleteById(id);
    }

    public ${className}Response get(String id) {
        ${className} entity = repository.findById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("${className} 不存在: " + id);
        }
        return toResponse(entity);
    }

    private ${className}Response toResponse(${className} entity) {
        ${className}Response resp = new ${className}Response();
        resp.setId(entity.getId());
<#list fields as f><#if !(f.primaryFlag!false) || f.fieldCode != 'id'>
        <@mapField field=f src='entity' dst='resp'/>
</#if></#list>
        resp.setCreatedBy(entity.getCreatedBy());
        resp.setCreatedTime(entity.getCreatedTime());
        resp.setUpdatedBy(entity.getUpdatedBy());
        resp.setUpdatedTime(entity.getUpdatedTime());
        resp.setVersion(entity.getVersion());
        return resp;
    }
}
