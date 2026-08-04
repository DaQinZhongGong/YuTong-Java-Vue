-- ============================================================
-- V032__init_plugin_registry_and_code_template.sql
-- 插件与模板生态设计 E0（v0.4）内置组件与代码模板基础框架
-- 创建 plugin_registry（插件注册表）和 code_template（代码生成模板）
-- 主键采用 varchar(32) 字符串主键（ULID），与平台约定一致
-- ============================================================

-- ===== 1. plugin_registry（插件注册表）=====
CREATE TABLE IF NOT EXISTS plugin_registry (
    id                      varchar(32)   NOT NULL,
    tenant_id               varchar(32)   NOT NULL DEFAULT 'default',
    plugin_code             varchar(64)   NOT NULL,
    plugin_name             varchar(128)  NOT NULL,
    plugin_version          varchar(32)   NOT NULL DEFAULT '1.0.0',
    plugin_type             varchar(32)   NOT NULL,
    status                  varchar(16)   NOT NULL DEFAULT 'ACTIVE',
    description             text,
    entry_class             varchar(256),
    icon_url                varchar(512),
    tags                    varchar(256),
    -- 通用字段（BaseEntity 约束）
    created_by              varchar(32),
    created_time            timestamptz   NOT NULL DEFAULT now(),
    updated_by              varchar(32),
    updated_time            timestamptz   NOT NULL DEFAULT now(),
    deleted                 boolean       NOT NULL DEFAULT false,
    version                 int           NOT NULL DEFAULT 0,
    remark                  varchar(256),
    CONSTRAINT pk_plugin_registry PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_plugin_registry_code ON plugin_registry (tenant_id, plugin_code) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_plugin_registry_type ON plugin_registry (tenant_id, plugin_type) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_plugin_registry_status ON plugin_registry (tenant_id, status) WHERE deleted = false;

COMMENT ON TABLE plugin_registry IS '插件注册表（45 号文档 E0 内置组件与代码模板）保存内置插件元数据';
COMMENT ON COLUMN plugin_registry.plugin_code IS '插件编码，租户内唯一';
COMMENT ON COLUMN plugin_registry.plugin_version IS '插件版本，semver';
COMMENT ON COLUMN plugin_registry.plugin_type IS '插件类型 TEMPLATE/LOWCODE_COMPONENT';
COMMENT ON COLUMN plugin_registry.status IS '状态 ACTIVE/INACTIVE/DEPRECATED';
COMMENT ON COLUMN plugin_registry.entry_class IS '入口类或组件路径';
COMMENT ON COLUMN plugin_registry.tags IS '标签（逗号分隔）';

-- ===== 2. code_template（代码生成模板）=====
CREATE TABLE IF NOT EXISTS code_template (
    id                      varchar(32)   NOT NULL,
    tenant_id               varchar(32)   NOT NULL DEFAULT 'default',
    template_code           varchar(64)   NOT NULL,
    template_name           varchar(128)  NOT NULL,
    category                varchar(32)   NOT NULL,
    tags                    varchar(256),
    engine_type             varchar(16)   NOT NULL DEFAULT 'FREEMARKER',
    content                 text          NOT NULL,
    description             text,
    status                  varchar(16)   NOT NULL DEFAULT 'ACTIVE',
    -- 通用字段（BaseEntity 约束）
    created_by              varchar(32),
    created_time            timestamptz   NOT NULL DEFAULT now(),
    updated_by              varchar(32),
    updated_time            timestamptz   NOT NULL DEFAULT now(),
    deleted                 boolean       NOT NULL DEFAULT false,
    version                 int           NOT NULL DEFAULT 0,
    remark                  varchar(256),
    CONSTRAINT pk_code_template PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_code_template_code ON code_template (tenant_id, template_code) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_code_template_category ON code_template (tenant_id, category) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_code_template_engine ON code_template (tenant_id, engine_type) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_code_template_status ON code_template (tenant_id, status) WHERE deleted = false;

COMMENT ON TABLE code_template IS '代码生成模板表（45 号文档 E0 内置组件与代码模板）支持 FREEMARKER/VELOCITY 引擎';
COMMENT ON COLUMN code_template.template_code IS '模板编码，租户内唯一';
COMMENT ON COLUMN code_template.category IS '模板分类 ENTITY/CONTROLLER/SERVICE/MAPPER/VUE_LIST/VUE_FORM/DDL 等';
COMMENT ON COLUMN code_template.engine_type IS '模板引擎 FREEMARKER/VELOCITY';
COMMENT ON COLUMN code_template.content IS '模板内容';
COMMENT ON COLUMN code_template.tags IS '标签（逗号分隔）';
COMMENT ON COLUMN code_template.status IS '状态 ACTIVE/INACTIVE';

-- ============================================================
-- 种子数据: 2 个内置插件 + 3 个示例代码模板
-- ============================================================

-- ===== 内置插件 =====
INSERT INTO plugin_registry (id, tenant_id, plugin_code, plugin_name, plugin_version, plugin_type, status, description, entry_class, icon_url, tags, created_by)
VALUES ('01PLGREG000000000000000001', 'default', 'built-in-chart', '内置图表组件', '1.0.0', 'LOWCODE_COMPONENT', 'ACTIVE', '平台内置图表组件，支持柱状图、折线图、饼图', 'com.yutong.lowcode.plugin.component.ChartComponent', NULL, 'chart,visualization', 'system')
ON CONFLICT (tenant_id, plugin_code) WHERE deleted = false DO NOTHING;

INSERT INTO plugin_registry (id, tenant_id, plugin_code, plugin_name, plugin_version, plugin_type, status, description, entry_class, icon_url, tags, created_by)
VALUES ('01PLGREG000000000000000002', 'default', 'built-in-form', '内置表单组件', '1.0.0', 'LOWCODE_COMPONENT', 'ACTIVE', '平台内置高级表单组件，支持动态字段和校验规则', 'com.yutong.lowcode.plugin.component.FormComponent', NULL, 'form,input', 'system')
ON CONFLICT (tenant_id, plugin_code) WHERE deleted = false DO NOTHING;

-- ===== 示例代码模板（Entity）=====
INSERT INTO code_template (id, tenant_id, template_code, template_name, category, tags, engine_type, content, description, status, created_by)
VALUES ('01TPLCOD000000000000000001', 'default', 'java-entity', 'Java Entity 模板', 'ENTITY', 'java,backend', 'FREEMARKER',
'<#-- Java Entity 模板 -->
package com.yutong.generated.${entity.moduleCode}.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;
<#if fields?seq_contains(''DECIMAL'')>
import java.math.BigDecimal;
</#if>
import java.time.OffsetDateTime;

@Getter
@Setter
@TableName("${entity.tableName}")
public class ${className} extends BaseEntity {
<#list fields as field>
    /** ${field.fieldName} */
    private ${field.javaType} ${field.fieldCodeCamel};
</#list>
}', '生成 Java Entity 类', 'ACTIVE', 'system')
ON CONFLICT (tenant_id, template_code) WHERE deleted = false DO NOTHING;

-- ===== 示例代码模板（Controller）=====
INSERT INTO code_template (id, tenant_id, template_code, template_name, category, tags, engine_type, content, description, status, created_by)
VALUES ('01TPLCOD000000000000000002', 'default', 'java-controller', 'Java Controller 模板', 'CONTROLLER', 'java,backend', 'FREEMARKER',
'<#-- Java Controller 模板 -->
package com.yutong.generated.${entity.moduleCode}.controller;

import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.auth.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "${entity.entityName}")
@RestController
@RequestMapping("${apiPrefix}/${entity.entityCode?replace(''_'',''-'')}")
public class ${className}Controller {

    @Operation(summary = "分页查询")
    @RequiresPermission("${entity.entityCode}:view")
    @GetMapping
    public Result<PageResult<${className}Response>> page(${className}PageQuery query) {
        return Result.ok(service.page(query), TraceContext.getTraceId());
    }
}', '生成 Java Controller 类', 'ACTIVE', 'system')
ON CONFLICT (tenant_id, template_code) WHERE deleted = false DO NOTHING;

-- ===== 示例代码模板（Vue List）=====
INSERT INTO code_template (id, tenant_id, template_code, template_name, category, tags, engine_type, content, description, status, created_by)
VALUES ('01TPLCOD000000000000000003', 'default', 'vue-list', 'Vue List 页面模板', 'VUE_LIST', 'vue,frontend', 'FREEMARKER',
'<#-- Vue List 页面模板 -->
<script setup lang="ts">
import { ref, reactive, onMounted } from ''vue''
import { ElMessage } from ''element-plus''

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const filters = reactive({
  keyword: '''',
})

async function loadData() {
  loading.value = true
  try {
    // TODO: call API
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <div>
    <el-card shadow="never">
      <el-table :data="tableData" border stripe>
        <#list fields as field>
        <el-table-column prop="${field.fieldCodeCamel}" label="${field.fieldName}" />
        </#list>
      </el-table>
    </el-card>
  </div>
</template>', '生成 Vue 列表页面', 'ACTIVE', 'system')
ON CONFLICT (tenant_id, template_code) WHERE deleted = false DO NOTHING;
