package com.yutong.lowcode.generator.service;

import com.yutong.common.exception.BusinessConflictException;
import com.yutong.lowcode.meta.domain.LcEntity;
import com.yutong.lowcode.meta.domain.LcField;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码生成模板服务。设计来源: 14-低代码平台设计、65-低代码代码生成模板详设
 * <p>
 * 基于 FreeMarker (.ftl) 模板引擎生成草稿内容，不写入磁盘，只进草稿区。
 * 生成范围: DDL / JAVA / VUE / UNIAPP / OPENAPI
 * <p>
 * 模板目录: classpath:/templates/{ddl,java,vue,uniapp,openapi}/*.ftl
 */
@Service
public class CodeTemplateService {

    private static final String API_PREFIX = "/api/v1";

    private final Configuration freemarkerConfig;

    public CodeTemplateService(Configuration freemarkerConfig) {
        this.freemarkerConfig = freemarkerConfig;
    }

    /**
     * 生成 DDL 草稿。设计来源: 14 DDL 生成规则、65 号模板要求
     */
    public String generateDdl(LcEntity entity, List<LcField> fields) {
        return renderTemplate("ddl/postgresql.ftl", entity, fields);
    }

    /**
     * 生成 Java Entity 草稿。
     */
    public String generateJavaEntity(LcEntity entity, List<LcField> fields) {
        return renderTemplate("java/entity.ftl", entity, fields);
    }

    /**
     * 生成 Java Request DTO 草稿。
     */
    public String generateJavaDtoRequest(LcEntity entity, List<LcField> fields) {
        return renderTemplate("java/dto-request.ftl", entity, fields);
    }

    /**
     * 生成 Java Response DTO 草稿。
     */
    public String generateJavaDtoResponse(LcEntity entity, List<LcField> fields) {
        return renderTemplate("java/dto-response.ftl", entity, fields);
    }

    /**
     * 生成 Java Mapper 草稿。
     */
    public String generateJavaMapper(LcEntity entity, List<LcField> fields) {
        return renderTemplate("java/mapper.ftl", entity, fields);
    }

    /**
     * 生成 Java Repository 草稿。
     */
    public String generateJavaRepository(LcEntity entity, List<LcField> fields) {
        return renderTemplate("java/repository.ftl", entity, fields);
    }

    /**
     * 生成 Java ApplicationService 草稿。
     */
    public String generateJavaApplicationService(LcEntity entity, List<LcField> fields) {
        return renderTemplate("java/application-service.ftl", entity, fields);
    }

    /**
     * 生成 Java Controller 草稿。
     */
    public String generateJavaController(LcEntity entity, List<LcField> fields) {
        return renderTemplate("java/controller.ftl", entity, fields);
    }

    /**
     * 生成 Vue API.ts 草稿。
     */
    public String generateVueApi(LcEntity entity, List<LcField> fields) {
        return renderTemplate("vue/api.ts.ftl", entity, fields);
    }

    /**
     * 生成 Vue 列表页草稿。
     */
    public String generateVueList(LcEntity entity, List<LcField> fields) {
        return renderTemplate("vue/list.vue.ftl", entity, fields);
    }

    /**
     * 生成 Vue 表单页草稿。
     */
    public String generateVueForm(LcEntity entity, List<LcField> fields) {
        return renderTemplate("vue/form.vue.ftl", entity, fields);
    }

    /**
     * 生成 Vue 详情页草稿。
     */
    public String generateVueDetail(LcEntity entity, List<LcField> fields) {
        return renderTemplate("vue/detail.vue.ftl", entity, fields);
    }

    /**
     * 生成 Uniapp 简表草稿。
     */
    public String generateUniapp(LcEntity entity, List<LcField> fields) {
        return renderTemplate("uniapp/list.vue.ftl", entity, fields);
    }

    /**
     * 生成 Uniapp 详情页草稿。
     */
    public String generateUniappDetail(LcEntity entity, List<LcField> fields) {
        return renderTemplate("uniapp/detail.vue.ftl", entity, fields);
    }

    /**
     * 生成 OpenAPI 片段草稿。
     */
    public String generateOpenApi(LcEntity entity, List<LcField> fields) {
        return renderTemplate("openapi/path.yaml.ftl", entity, fields);
    }

    /**
     * 按 scope 批量生成多文件。65 号文档要求生成任务产出多个文件 (entity/mapper/repository/service/controller/dto)。
     * 返回 (path, content) 列表，由 GeneratorTaskApplicationService 计算 diff。
     */
    public List<GeneratedArtifact> generateByScope(String scope, LcEntity entity, List<LcField> fields) {
        List<GeneratedArtifact> artifacts = new ArrayList<>();
        String className = toPascalCase(entity.getEntityCode());
        String kebab = toKebabCase(entity.getEntityCode());
        switch (scope) {
            case "DDL" -> artifacts.add(new GeneratedArtifact(
                    "database/migrations/V__lc_" + entity.getTableName() + ".sql",
                    generateDdl(entity, fields)));
            case "JAVA" -> {
                String pkgPath = "backend/generated/" + (entity.getModuleCode() != null ? entity.getModuleCode() : "lowcode") + "/";
                artifacts.add(new GeneratedArtifact(pkgPath + className + ".java", generateJavaEntity(entity, fields)));
                artifacts.add(new GeneratedArtifact(pkgPath + "dto/" + className + "Request.java", generateJavaDtoRequest(entity, fields)));
                artifacts.add(new GeneratedArtifact(pkgPath + "dto/" + className + "Response.java", generateJavaDtoResponse(entity, fields)));
                artifacts.add(new GeneratedArtifact(pkgPath + "mapper/" + className + "Mapper.java", generateJavaMapper(entity, fields)));
                artifacts.add(new GeneratedArtifact(pkgPath + "repository/" + className + "Repository.java", generateJavaRepository(entity, fields)));
                artifacts.add(new GeneratedArtifact(pkgPath + "service/" + className + "ApplicationService.java", generateJavaApplicationService(entity, fields)));
                artifacts.add(new GeneratedArtifact(pkgPath + "controller/" + className + "Controller.java", generateJavaController(entity, fields)));
            }
            case "VUE" -> {
                artifacts.add(new GeneratedArtifact("web-admin/src/api/generated/" + kebab + "-api.ts", generateVueApi(entity, fields)));
                artifacts.add(new GeneratedArtifact("web-admin/src/views/generated/" + kebab + "-list.vue", generateVueList(entity, fields)));
                artifacts.add(new GeneratedArtifact("web-admin/src/views/generated/" + kebab + "-form.vue", generateVueForm(entity, fields)));
                artifacts.add(new GeneratedArtifact("web-admin/src/views/generated/" + kebab + "-detail.vue", generateVueDetail(entity, fields)));
            }
            case "UNIAPP" -> {
                artifacts.add(new GeneratedArtifact("mobile-uniapp/src/pages/generated/" + kebab + "-list.vue", generateUniapp(entity, fields)));
                artifacts.add(new GeneratedArtifact("mobile-uniapp/src/pages/generated/" + kebab + "-detail.vue", generateUniappDetail(entity, fields)));
            }
            case "OPENAPI" -> artifacts.add(new GeneratedArtifact(
                    "openapi/fragments/" + kebab + ".yaml", generateOpenApi(entity, fields)));
            default -> throw new BusinessConflictException("不支持的 target_scope: " + scope);
        }
        return artifacts;
    }

    /**
     * 渲染 FreeMarker 模板。统一构建模板变量 model。
     */
    private String renderTemplate(String templatePath, LcEntity entity, List<LcField> fields) {
        try {
            Template template = freemarkerConfig.getTemplate(templatePath);
            Map<String, Object> model = buildModel(entity, fields);
            StringWriter writer = new StringWriter();
            template.process(model, writer);
            return writer.toString();
        } catch (IOException e) {
            throw new BusinessConflictException("模板加载失败: " + templatePath + " - " + e.getMessage());
        } catch (TemplateException e) {
            throw new BusinessConflictException("模板渲染失败: " + templatePath + " - " + e.getMessage());
        }
    }

    /**
     * 构建模板变量 model。65 号文档要求变量: entityCode/entityName/className/moduleCode/tableName/
     * fields/relations/actions/permissions/apiPrefix
     */
    private Map<String, Object> buildModel(LcEntity entity, List<LcField> fields) {
        Map<String, Object> model = new HashMap<>();
        TemplateEntity templateEntity = new TemplateEntity(
                entity.getEntityCode(),
                entity.getEntityName(),
                entity.getTableName(),
                entity.getModuleCode() != null ? entity.getModuleCode() : "lowcode",
                entity.getVersionNo(),
                entity.getSchemaVersion(),
                entity.getConfigHash(),
                entity.getStatus()
        );
        model.put("entity", templateEntity);
        model.put("className", toPascalCase(entity.getEntityCode()));
        model.put("apiPrefix", API_PREFIX);
        model.put("permissions", new ArrayList<String>());
        model.put("actions", new ArrayList<>());
        model.put("relations", new ArrayList<>());

        List<TemplateField> templateFields = new ArrayList<>();
        if (fields != null) {
            for (LcField f : fields) {
                templateFields.add(new TemplateField(
                        f.getFieldCode(),
                        toCamelCase(f.getFieldCode()),
                        f.getFieldName(),
                        f.getDbColumn(),
                        f.getDataType(),
                        toSqlType(f),
                        toJavaType(f),
                        toTsType(f),
                        toOpenApiType(f),
                        f.getLengthValue(),
                        f.getPrecisionValue(),
                        f.getScaleValue(),
                        f.getNullable(),
                        f.getDefaultValue(),
                        f.getDictType(),
                        f.getPrimaryFlag(),
                        f.getUniqueFlag(),
                        f.getIndexFlag(),
                        f.getSortNo()
                ));
            }
        }
        model.put("fields", templateFields);
        return model;
    }

    /**
     * 生成产物 (路径 + 内容)。
     */
    public record GeneratedArtifact(String path, String content) {}

    /** 模板变量: 实体。使用 getter 类而非 record，确保 FreeMarker 按属性名 (getModuleCode→moduleCode) 解析。 */
    public static class TemplateEntity {
        private final String entityCode;
        private final String entityName;
        private final String tableName;
        private final String moduleCode;
        private final Integer versionNo;
        private final String schemaVersion;
        private final String configHash;
        private final String status;

        public TemplateEntity(String entityCode, String entityName, String tableName, String moduleCode,
                              Integer versionNo, String schemaVersion, String configHash, String status) {
            this.entityCode = entityCode;
            this.entityName = entityName;
            this.tableName = tableName;
            this.moduleCode = moduleCode;
            this.versionNo = versionNo;
            this.schemaVersion = schemaVersion;
            this.configHash = configHash;
            this.status = status;
        }

        public String getEntityCode() { return entityCode; }
        public String getEntityName() { return entityName; }
        public String getTableName() { return tableName; }
        public String getModuleCode() { return moduleCode; }
        public Integer getVersionNo() { return versionNo; }
        public String getSchemaVersion() { return schemaVersion; }
        public String getConfigHash() { return configHash; }
        public String getStatus() { return status; }
    }

    /** 模板变量: 字段 (含预计算的 sqlType/javaType/tsType/openapiType)。getter 类以适配 FreeMarker 属性解析。 */
    public static class TemplateField {
        private final String fieldCode;
        private final String fieldCodeCamel;
        private final String fieldName;
        private final String dbColumn;
        private final String dataType;
        private final String sqlType;
        private final String javaType;
        private final String tsType;
        private final String openapiType;
        private final Integer lengthValue;
        private final Integer precisionValue;
        private final Integer scaleValue;
        private final Boolean nullable;
        private final String defaultValue;
        private final String dictType;
        private final Boolean primaryFlag;
        private final Boolean uniqueFlag;
        private final Boolean indexFlag;
        private final Integer sortNo;

        public TemplateField(String fieldCode, String fieldCodeCamel, String fieldName, String dbColumn,
                             String dataType, String sqlType, String javaType, String tsType, String openapiType,
                             Integer lengthValue, Integer precisionValue, Integer scaleValue,
                             Boolean nullable, String defaultValue, String dictType,
                             Boolean primaryFlag, Boolean uniqueFlag, Boolean indexFlag, Integer sortNo) {
            this.fieldCode = fieldCode;
            this.fieldCodeCamel = fieldCodeCamel;
            this.fieldName = fieldName;
            this.dbColumn = dbColumn;
            this.dataType = dataType;
            this.sqlType = sqlType;
            this.javaType = javaType;
            this.tsType = tsType;
            this.openapiType = openapiType;
            this.lengthValue = lengthValue;
            this.precisionValue = precisionValue;
            this.scaleValue = scaleValue;
            this.nullable = nullable;
            this.defaultValue = defaultValue;
            this.dictType = dictType;
            this.primaryFlag = primaryFlag;
            this.uniqueFlag = uniqueFlag;
            this.indexFlag = indexFlag;
            this.sortNo = sortNo;
        }

        public String getFieldCode() { return fieldCode; }
        public String getFieldCodeCamel() { return fieldCodeCamel; }
        public String getFieldName() { return fieldName; }
        public String getDbColumn() { return dbColumn; }
        public String getDataType() { return dataType; }
        public String getSqlType() { return sqlType; }
        public String getJavaType() { return javaType; }
        public String getTsType() { return tsType; }
        public String getOpenapiType() { return openapiType; }
        public Integer getLengthValue() { return lengthValue; }
        public Integer getPrecisionValue() { return precisionValue; }
        public Integer getScaleValue() { return scaleValue; }
        public Boolean getNullable() { return nullable; }
        public String getDefaultValue() { return defaultValue; }
        public String getDictType() { return dictType; }
        public Boolean getPrimaryFlag() { return primaryFlag; }
        public Boolean getUniqueFlag() { return uniqueFlag; }
        public Boolean getIndexFlag() { return indexFlag; }
        public Integer getSortNo() { return sortNo; }
    }

    // ===== 类型映射辅助方法 =====

    private String toSqlType(LcField f) {
        String type = f.getDataType();
        if (type == null) return "varchar(256)";
        return switch (type) {
            case "STRING" -> "varchar(" + (f.getLengthValue() != null ? f.getLengthValue() : 256) + ")";
            case "DECIMAL" -> "numeric(" + (f.getPrecisionValue() != null ? f.getPrecisionValue() : 18)
                    + "," + (f.getScaleValue() != null ? f.getScaleValue() : 2) + ")";
            case "DATE" -> "timestamptz";
            case "DICT" -> "varchar(64)";
            case "FILE" -> "varchar(32)";
            case "JSON" -> "jsonb";
            default -> "varchar(256)";
        };
    }

    private String toJavaType(LcField f) {
        String type = f.getDataType();
        if (type == null) return "String";
        return switch (type) {
            case "STRING", "DICT", "FILE" -> "String";
            case "DECIMAL" -> "BigDecimal";
            case "DATE" -> "OffsetDateTime";
            case "JSON" -> "String";
            default -> "String";
        };
    }

    private String toTsType(LcField f) {
        String type = f.getDataType();
        if (type == null) return "string";
        return switch (type) {
            case "STRING", "DICT", "FILE" -> "string";
            case "DECIMAL" -> "string"; // 金额统一用 string (65 号文档要求)
            case "DATE" -> "string";
            case "JSON" -> "Record<string, unknown>";
            default -> "string";
        };
    }

    private String toOpenApiType(LcField f) {
        String type = f.getDataType();
        if (type == null) return "string";
        return switch (type) {
            case "STRING", "DICT", "FILE", "DECIMAL" -> "string"; // 金额用 string (65 号文档要求)
            case "DATE" -> "string";
            case "JSON" -> "object";
            default -> "string";
        };
    }

    private String toPascalCase(String code) {
        if (code == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String part : code.split("[_-]")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    private String toCamelCase(String code) {
        String pascal = toPascalCase(code);
        if (pascal.isEmpty()) return "";
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    private String toKebabCase(String code) {
        if (code == null) return "";
        return code.toLowerCase().replace('_', '-');
    }
}
