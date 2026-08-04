package com.yutong.lowcode.generator.service;

import com.yutong.lowcode.meta.domain.LcEntity;
import com.yutong.lowcode.meta.domain.LcField;
import freemarker.template.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-3 低代码代码生成字段映射契约测试。
 *
 * <p>验证 application-service.ftl 在 create / update / toResponse 三个方法中，
 * 基于 {@code fields} 元数据做了真实遍历自动映射（而非遗留 TODO 占位），
 * 且对集合类型 (List) 做了防御性拷贝、标量类型直接赋值 (天然 null 安全)。
 *
 * <p>说明: 生成代码引用了 com.yutong.generated.* 等运行期类，无法在单测中独立编译，
 * 故本测试以结构断言 (映射语句存在、无 TODO 残留、集合分支存在) 验证模板正确性，
 * 配合 docker maven 全量构建保证生成器模块本身可编译。
 */
@DisplayName("低代码 ApplicationService 模板字段映射")
class CodeTemplateServiceApplicationServiceTest {

    private CodeTemplateService service;

    @BeforeEach
    void setUp() throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setClassForTemplateLoading(CodeTemplateService.class, "/templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        service = new CodeTemplateService(cfg);
    }

    private LcEntity sampleEntity() {
        LcEntity e = new LcEntity();
        e.setEntityCode("order_info");
        e.setEntityName("订单");
        e.setTableName("t_order");
        e.setModuleCode("lowcode");
        return e;
    }

    private List<LcField> sampleFields() {
        List<LcField> fields = new ArrayList<>();

        LcField id = new LcField();
        id.setFieldCode("id");
        id.setFieldName("主键");
        id.setDataType(LcField.TYPE_STRING);
        id.setPrimaryFlag(true);
        fields.add(id);

        LcField orderNo = new LcField();
        orderNo.setFieldCode("order_no");
        orderNo.setFieldName("订单号");
        orderNo.setDataType(LcField.TYPE_STRING);
        orderNo.setNullable(false);
        fields.add(orderNo);

        LcField amount = new LcField();
        amount.setFieldCode("amount");
        amount.setFieldName("金额");
        amount.setDataType(LcField.TYPE_DECIMAL);
        amount.setPrecisionValue(18);
        amount.setScaleValue(2);
        fields.add(amount);

        LcField createdAt = new LcField();
        createdAt.setFieldCode("created_at");
        createdAt.setFieldName("创建时间");
        createdAt.setDataType(LcField.TYPE_DATE);
        fields.add(createdAt);

        LcField extInfo = new LcField();
        extInfo.setFieldCode("ext_info");
        extInfo.setFieldName("扩展信息");
        extInfo.setDataType(LcField.TYPE_JSON);
        fields.add(extInfo);

        return fields;
    }

    @Test
    @DisplayName("生成代码类名正确且无 TODO 残留")
    void generate_noTodoResidue() {
        String code = service.generateJavaApplicationService(sampleEntity(), sampleFields());

        assertTrue(code.contains("public class OrderInfoApplicationService"),
                "应生成 OrderInfoApplicationService 类");
        assertFalse(code.contains("TODO"),
                "create/update/toResponse 不应存在 TODO 占位");
    }

    @Test
    @DisplayName("create 方法对全部非主键字段做真实遍历映射")
    void create_mapsAllNonPrimaryKeyFields() {
        String code = service.generateJavaApplicationService(sampleEntity(), sampleFields());

        int createIdx = code.indexOf("public OrderInfoResponse create(");
        int updateIdx = code.indexOf("public OrderInfoResponse update(");
        assertTrue(createIdx >= 0 && updateIdx > createIdx, "create 方法应位于 update 之前");
        String createBody = code.substring(createIdx, updateIdx);

        // 标量字段直接赋值 (类型一致, 天然 null 安全)
        assertTrue(createBody.contains("entity.setOrderNo(request.getOrderNo());"),
                "STRING 字段应被映射");
        assertTrue(createBody.contains("entity.setAmount(request.getAmount());"),
                "DECIMAL(BigDecimal) 字段应被映射");
        assertTrue(createBody.contains("entity.setCreatedAt(request.getCreatedAt());"),
                "DATE(OffsetDateTime) 字段应被映射");
        assertTrue(createBody.contains("entity.setExtInfo(request.getExtInfo());"),
                "JSON(String) 字段应被映射");
        assertFalse(createBody.contains("entity.setId(request.getId())"),
                "主键 id 不应出现在请求映射中");
    }

    @Test
    @DisplayName("update 方法同样基于 fields 遍历映射")
    void update_mapsAllNonPrimaryKeyFields() {
        String code = service.generateJavaApplicationService(sampleEntity(), sampleFields());

        int updateIdx = code.indexOf("public OrderInfoResponse update(");
        int deleteIdx = code.indexOf("public void delete(");
        assertTrue(updateIdx >= 0 && deleteIdx > updateIdx, "update 应位于 delete 之前");
        String updateBody = code.substring(updateIdx, deleteIdx);

        assertTrue(updateBody.contains("entity.setOrderNo(request.getOrderNo());"));
        assertTrue(updateBody.contains("entity.setAmount(request.getAmount());"));
        assertTrue(updateBody.contains("entity.setCreatedAt(request.getCreatedAt());"));
        assertTrue(updateBody.contains("entity.setExtInfo(request.getExtInfo());"));
    }

    @Test
    @DisplayName("toResponse 方法对实体字段反向映射且包含审计字段")
    void toResponse_mapsAllFields() {
        String code = service.generateJavaApplicationService(sampleEntity(), sampleFields());

        int respIdx = code.indexOf("private OrderInfoResponse toResponse(");
        assertTrue(respIdx >= 0, "应存在 toResponse 方法");
        String respBody = code.substring(respIdx);

        assertTrue(respBody.contains("resp.setOrderNo(entity.getOrderNo());"));
        assertTrue(respBody.contains("resp.setAmount(entity.getAmount());"));
        assertTrue(respBody.contains("resp.setCreatedAt(entity.getCreatedAt());"));
        assertTrue(respBody.contains("resp.setExtInfo(entity.getExtInfo());"));
        assertTrue(respBody.contains("resp.setCreatedBy(entity.getCreatedBy());"),
                "应透传审计字段 createdBy");
        assertTrue(respBody.contains("resp.setVersion(entity.getVersion());"),
                "应透传乐观锁版本号");
    }

    @Test
    @DisplayName("集合类型字段生成防御性拷贝且引入 ArrayList")
    void collectionField_defensiveCopy() {
        List<LcField> fields = sampleFields();
        LcField tags = new LcField();
        tags.setFieldCode("tags");
        tags.setFieldName("标签");
        // 当前 toJavaType 不会产出 List，这里直接验证模板对 List 前缀 javaType 的防御性拷贝分支。
        // 通过构造一个 javaType 以 List 开头的字段验证分支代码生成 (生产中以 List 类型字段触发)。
        tags.setDataType(LcField.TYPE_JSON);
        // 借由 buildModel 内部 toJavaType 仍为 String，故额外断言：模板导入 ArrayList 已就位，
        // 且对 List 前缀 javaType 产生的防御性拷贝语法正确。
        fields.add(tags);

        String code = service.generateJavaApplicationService(sampleEntity(), fields);

        assertTrue(code.contains("import java.util.ArrayList;"),
                "模板应统一引入 ArrayList 以支持集合防御性拷贝");
    }

    @Test
    @DisplayName("macro 对 List 前缀 javaType 生成 null 安全防御性拷贝")
    void mapField_macro_listBranch() throws Exception {
        // 直接渲染一段使用 List 前缀 javaType 的字段，验证宏产出防御性拷贝代码。
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setClassForTemplateLoading(CodeTemplateService.class, "/templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);

        freemarker.template.Template tpl = cfg.getTemplate("java/application-service.ftl");
        java.util.Map<String, Object> model = new java.util.HashMap<>();
        model.put("entity", new CodeTemplateService.TemplateEntity(
                "order_info", "订单", "t_order", "lowcode", 1, "v1", "h", "PUBLISHED"));
        model.put("className", "OrderInfo");
        model.put("apiPrefix", "/api/v1");
        model.put("permissions", new ArrayList<String>());
        model.put("actions", new ArrayList<>());
        model.put("relations", new ArrayList<>());
        List<CodeTemplateService.TemplateField> fs = new ArrayList<>();
        fs.add(new CodeTemplateService.TemplateField(
                "tags", "tags", "标签", "tags", "JSON", "jsonb",
                "List<String>", "Record<string,unknown>", "object",
                null, null, null, true, null, null, false, false, false, 1));
        model.put("fields", fs);

        java.io.StringWriter w = new java.io.StringWriter();
        tpl.process(model, w);
        String code = w.toString();

        assertFalse(code.contains("TODO"));
        // create 与 update 均把 request.tags 防御性拷贝到 entity (共 2 处)
        assertTrue(code.contains("if (request.getTags() != null)"),
                "集合字段应先判空再拷贝");
        assertEquals(2, countOccurrences(code, "entity.setTags(new ArrayList<>(request.getTags()));"),
                "create/update 应对 request.tags 做防御性拷贝");
        // toResponse 把 entity.tags 防御性拷贝到 resp (1 处)
        assertEquals(1, countOccurrences(code, "resp.setTags(new ArrayList<>(entity.getTags()));"),
                "toResponse 应对 entity.tags 做防御性拷贝");
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
