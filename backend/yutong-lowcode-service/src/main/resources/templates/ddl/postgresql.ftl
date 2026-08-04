<#-- DDL 模板 (PostgreSQL) | 设计来源: 65-低代码代码生成模板详设
  约束:
    - 主键固定 id varchar(32)
    - 自动包含通用字段 tenant_id/created_by/created_time/updated_by/updated_time/deleted/version/remark
    - 唯一索引包含 tenant_id
    - partial unique index 带 where deleted = false
    - 不生成物理外键
-->
<#-- @ftlvariable name="entity" type="com.yutong.lowcode.generator.service.CodeTemplateService.TemplateEntity" -->
<#-- @ftlvariable name="fields" type="java.util.List<com.yutong.lowcode.generator.service.CodeTemplateService.TemplateField>" -->
-- 由低代码生成器生成，请纳入 Flyway 迁移流程
-- entity: ${entity.entityCode} | version: ${entity.versionNo!1} | schema: ${entity.schemaVersion!'1.0'}
-- 生成时间: ${.now?iso_local}

CREATE TABLE ${entity.tableName} (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
<#list fields as f><#if !(f.primaryFlag!false) || f.fieldCode != 'id'>
    ${f.dbColumn?left_pad(16)} ${f.sqlType}<#if f.nullable?? && !f.nullable>   NOT NULL</#if>,
</#if></#list>
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_${entity.tableName} PRIMARY KEY (id)
);

COMMENT ON TABLE ${entity.tableName} IS '${entity.entityName}';
<#list fields as f><#if f.fieldName?? && f.fieldName != ''>
COMMENT ON COLUMN ${entity.tableName}.${f.dbColumn} IS '${f.fieldName}';
</#if></#list>

-- 唯一索引 (含 tenant_id, partial where deleted = false)
CREATE UNIQUE INDEX uk_${entity.tableName}_tenant_id ON ${entity.tableName} (tenant_id, id) WHERE deleted = false;
