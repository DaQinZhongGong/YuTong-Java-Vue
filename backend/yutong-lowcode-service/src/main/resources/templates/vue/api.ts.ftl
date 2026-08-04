<#-- Vue API TypeScript 模板 | 设计来源: 65-低代码代码生成模板详设 -->
<#-- @ftlvariable name="entity" type="com.yutong.lowcode.generator.service.CodeTemplateService.TemplateEntity" -->
<#-- @ftlvariable name="fields" type="java.util.List<com.yutong.lowcode.generator.service.CodeTemplateService.TemplateField>" -->
<#-- @ftlvariable name="className" type="String" -->
<#-- @ftlvariable name="apiPrefix" type="String" -->
// 由低代码生成器生成，可二开
// entity: ${entity.entityCode} | 生成时间: ${.now?iso_local}
import { http } from '@/utils/http'

export interface ${className} {
  id: string
<#list fields as f><#if !(f.primaryFlag!false) || f.fieldCode != 'id'>
  ${f.fieldCodeCamel}?: ${f.tsType}
</#if></#list>
  createdBy?: string
  createdTime?: string
  updatedBy?: string
  updatedTime?: string
  version?: number
}

export interface ${className}Request {
<#list fields as f><#if !(f.primaryFlag!false) || f.fieldCode != 'id'>
  ${f.fieldCodeCamel}?: ${f.tsType}
</#if></#list>
}

export interface ${className}Query {
  page?: number
  size?: number
  keyword?: string
}

export function list${className}(query: ${className}Query) {
  return http.get<{ data: ${className}[]; total: number }>('${apiPrefix!'/api/v1'}/${entity.entityCode?replace('_', '-')}', { params: query })
}

export function get${className}(id: string) {
  return http.get<${className}>('${apiPrefix!'/api/v1'}/${entity.entityCode?replace('_', '-')}/' + id)
}

export function create${className}(data: ${className}Request) {
  return http.post<${className}>('${apiPrefix!'/api/v1'}/${entity.entityCode?replace('_', '-')}', data)
}

export function update${className}(id: string, data: ${className}Request) {
  return http.put<${className}>('${apiPrefix!'/api/v1'}/${entity.entityCode?replace('_', '-')}/' + id, data)
}

export function delete${className}(id: string) {
  return http.delete<void>('${apiPrefix!'/api/v1'}/${entity.entityCode?replace('_', '-')}/' + id)
}
