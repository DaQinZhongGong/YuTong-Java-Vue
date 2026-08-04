<#-- OpenAPI 路径片段模板 | 设计来源: 65-低代码代码生成模板详设
  约束:
    - operationId 符合 48 号文档规范
    - ID 使用 IdString
    - 金额使用 string
    - 错误响应引用 errors.yaml
-->
<#-- @ftlvariable name="entity" type="com.yutong.lowcode.generator.service.CodeTemplateService.TemplateEntity" -->
<#-- @ftlvariable name="fields" type="java.util.List<com.yutong.lowcode.generator.service.CodeTemplateService.TemplateField>" -->
<#-- @ftlvariable name="className" type="String" -->
<#-- @ftlvariable name="apiPrefix" type="String" -->
# 由低代码生成器生成，可二开
# entity: ${entity.entityCode} | 生成时间: ${.now?iso_local}
${apiPrefix!'/api/v1'}/${entity.entityCode?replace('_', '-')}:
  get:
    operationId: list${className}
    tags:
      - ${entity.entityName}
    summary: 分页查询${entity.entityName}
    parameters:
      - name: page
        in: query
        schema: { type: integer, minimum: 1, default: 1 }
      - name: size
        in: query
        schema: { type: integer, minimum: 1, maximum: 100, default: 20 }
      - name: keyword
        in: query
        schema: { type: string }
    responses:
      '200':
        description: 查询成功
        content:
          application/json:
            schema:
              type: object
              properties:
                code: { type: integer, example: 0 }
                data:
                  type: object
                  properties:
                    data:
                      type: array
                      items: { $ref: '#/components/schemas/${className}' }
                    total: { type: integer }
      '400': { $ref: '../errors.yaml#/BadRequest' }
      '401': { $ref: '../errors.yaml#/Unauthorized' }
      '403': { $ref: '../errors.yaml#/Forbidden' }
  post:
    operationId: create${className}
    tags:
      - ${entity.entityName}
    summary: 创建${entity.entityName}
    requestBody:
      required: true
      content:
        application/json:
          schema: { $ref: '#/components/schemas/${className}Request' }
    responses:
      '200':
        description: 创建成功
        content:
          application/json:
            schema:
              type: object
              properties:
                code: { type: integer, example: 0 }
                data: { $ref: '#/components/schemas/${className}' }
      '400': { $ref: '../errors.yaml#/BadRequest' }
      '409': { $ref: '../errors.yaml#/Conflict' }

${apiPrefix!'/api/v1'}/${entity.entityCode?replace('_', '-')}/{id}:
  get:
    operationId: get${className}
    tags:
      - ${entity.entityName}
    summary: 查询${entity.entityName}详情
    parameters:
      - name: id
        in: path
        required: true
        schema: { type: string, description: IdString }
    responses:
      '200':
        description: 查询成功
        content:
          application/json:
            schema:
              type: object
              properties:
                code: { type: integer, example: 0 }
                data: { $ref: '#/components/schemas/${className}' }
      '404': { $ref: '../errors.yaml#/NotFound' }
  put:
    operationId: update${className}
    tags:
      - ${entity.entityName}
    summary: 更新${entity.entityName}
    parameters:
      - name: id
        in: path
        required: true
        schema: { type: string, description: IdString }
    requestBody:
      required: true
      content:
        application/json:
          schema: { $ref: '#/components/schemas/${className}Request' }
    responses:
      '200':
        description: 更新成功
        content:
          application/json:
            schema:
              type: object
              properties:
                code: { type: integer, example: 0 }
                data: { $ref: '#/components/schemas/${className}' }
      '409': { $ref: '../errors.yaml#/Conflict' }
  delete:
    operationId: delete${className}
    tags:
      - ${entity.entityName}
    summary: 删除${entity.entityName}
    parameters:
      - name: id
        in: path
        required: true
        schema: { type: string, description: IdString }
    responses:
      '200':
        description: 删除成功
        content:
          application/json:
            schema:
              type: object
              properties:
                code: { type: integer, example: 0 }
                data: { type: object, nullable: true }
      '404': { $ref: '../errors.yaml#/NotFound' }

components:
  schemas:
    ${className}:
      type: object
      properties:
        id: { type: string, description: IdString }
<#list fields as f><#if !(f.primaryFlag!false) || f.fieldCode != 'id'>
        ${f.fieldCodeCamel}: { type: ${f.openapiType}, description: '${f.fieldName!''}' }
</#if></#list>
        createdBy: { type: string }
        createdTime: { type: string, format: date-time }
        updatedBy: { type: string }
        updatedTime: { type: string, format: date-time }
        version: { type: integer }
    ${className}Request:
      type: object
      properties:
<#list fields as f><#if !(f.primaryFlag!false) || f.fieldCode != 'id'>
        ${f.fieldCodeCamel}: { type: ${f.openapiType}, description: '${f.fieldName!''}' }
</#if></#list>
