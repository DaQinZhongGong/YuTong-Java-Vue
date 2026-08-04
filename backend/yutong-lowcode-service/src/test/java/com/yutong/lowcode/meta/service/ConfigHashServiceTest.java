package com.yutong.lowcode.meta.service;

import com.yutong.lowcode.meta.domain.LcField;
import com.yutong.lowcode.meta.domain.LcRelation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 元模型 config_hash 计算服务单元测试。设计来源: 14-低代码平台设计 版本与发布
 * 约束: SHA-256 摘要 + 字段排序保证同一配置生成的 hash 稳定。
 */
@DisplayName("元模型 config_hash 计算服务")
class ConfigHashServiceTest {

    private final ConfigHashService hashService = new ConfigHashService();

    private LcField field(String code, String type, String column, boolean pk) {
        LcField f = new LcField();
        f.setFieldCode(code);
        f.setDataType(type);
        f.setDbColumn(column);
        f.setPrimaryFlag(pk);
        return f;
    }

    private LcRelation relation(String target, String type, String source) {
        LcRelation r = new LcRelation();
        r.setTargetEntityId(target);
        r.setRelationType(type);
        r.setSourceFieldCode(source);
        return r;
    }

    @Nested
    @DisplayName("实体 hash 稳定性")
    class EntityHashStability {

        @Test
        @DisplayName("相同输入产生相同 hash")
        void sameInputSameHash() {
            String hash1 = hashService.computeEntityHash("customer", "biz_customer",
                    List.of(field("id", "STRING", "id", true), field("name", "STRING", "name", false)),
                    List.of());
            String hash2 = hashService.computeEntityHash("customer", "biz_customer",
                    List.of(field("id", "STRING", "id", true), field("name", "STRING", "name", false)),
                    List.of());
            assertEquals(hash1, hash2);
        }

        @Test
        @DisplayName("字段顺序不同产生相同 hash (排序保证稳定)")
        void fieldOrderDoesNotMatter() {
            String hash1 = hashService.computeEntityHash("customer", "biz_customer",
                    List.of(field("id", "STRING", "id", true), field("name", "STRING", "name", false)),
                    List.of());
            String hash2 = hashService.computeEntityHash("customer", "biz_customer",
                    List.of(field("name", "STRING", "name", false), field("id", "STRING", "id", true)),
                    List.of());
            assertEquals(hash1, hash2);
        }

        @Test
        @DisplayName("字段类型不同 hash 不同")
        void differentDataTypeDifferentHash() {
            String hash1 = hashService.computeEntityHash("customer", "biz_customer",
                    List.of(field("amount", "DECIMAL", "amount", false)), List.of());
            String hash2 = hashService.computeEntityHash("customer", "biz_customer",
                    List.of(field("amount", "STRING", "amount", false)), List.of());
            assertNotEquals(hash1, hash2);
        }

        @Test
        @DisplayName("entityCode 不同 hash 不同")
        void differentEntityCodeDifferentHash() {
            String hash1 = hashService.computeEntityHash("customer", "biz_customer",
                    List.of(field("id", "STRING", "id", true)), List.of());
            String hash2 = hashService.computeEntityHash("product", "biz_customer",
                    List.of(field("id", "STRING", "id", true)), List.of());
            assertNotEquals(hash1, hash2);
        }

        @Test
        @DisplayName("空 fields 和 relations 也能生成 hash")
        void emptyFieldsAndRelations() {
            String hash = hashService.computeEntityHash("customer", "biz_customer",
                    List.of(), List.of());
            assertNotNull(hash);
            assertFalse(hash.isBlank());
            assertEquals(64, hash.length(), "SHA-256 hex 应为 64 字符");
        }

        @Test
        @DisplayName("null fields 和 relations 不抛异常")
        void nullFieldsAndRelations() {
            String hash = hashService.computeEntityHash("customer", "biz_customer", null, null);
            assertNotNull(hash);
            assertEquals(64, hash.length());
        }

        @Test
        @DisplayName("主键标记不同 hash 不同")
        void primaryFlagChangesHash() {
            String hash1 = hashService.computeEntityHash("customer", "biz_customer",
                    List.of(field("id", "STRING", "id", true)), List.of());
            String hash2 = hashService.computeEntityHash("customer", "biz_customer",
                    List.of(field("id", "STRING", "id", false)), List.of());
            assertNotEquals(hash1, hash2);
        }
    }

    @Nested
    @DisplayName("关系 hash 稳定性")
    class RelationHashStability {

        @Test
        @DisplayName("关系相同 hash 相同")
        void sameRelationsSameHash() {
            String hash1 = hashService.computeEntityHash("order", "biz_order",
                    List.of(),
                    List.of(relation("E001", "MANY_TO_ONE", "customer_id")));
            String hash2 = hashService.computeEntityHash("order", "biz_order",
                    List.of(),
                    List.of(relation("E001", "MANY_TO_ONE", "customer_id")));
            assertEquals(hash1, hash2);
        }

        @Test
        @DisplayName("关系类型不同 hash 不同")
        void differentRelationTypeDifferentHash() {
            String hash1 = hashService.computeEntityHash("order", "biz_order",
                    List.of(),
                    List.of(relation("E001", "MANY_TO_ONE", "customer_id")));
            String hash2 = hashService.computeEntityHash("order", "biz_order",
                    List.of(),
                    List.of(relation("E001", "ONE_TO_ONE", "customer_id")));
            assertNotEquals(hash1, hash2);
        }
    }

    @Nested
    @DisplayName("页面 hash 稳定性")
    class PageHashStability {

        @Test
        @DisplayName("相同 pageCode + pageType + layout 产生相同 hash")
        void samePageInputSameHash() {
            String hash1 = hashService.computePageHash("customer_list", "LIST",
                    "{\"cols\":[\"id\"]}", List.of(), List.of());
            String hash2 = hashService.computePageHash("customer_list", "LIST",
                    "{\"cols\":[\"id\"]}", List.of(), List.of());
            assertEquals(hash1, hash2);
        }

        @Test
        @DisplayName("layout 不同 hash 不同")
        void differentLayoutDifferentHash() {
            String hash1 = hashService.computePageHash("customer_list", "LIST",
                    "{\"cols\":[\"id\"]}", List.of(), List.of());
            String hash2 = hashService.computePageHash("customer_list", "LIST",
                    "{\"cols\":[\"name\"]}", List.of(), List.of());
            assertNotEquals(hash1, hash2);
        }

        @Test
        @DisplayName("null components 和 actions 不抛异常")
        void nullComponentsAndActions() {
            String hash = hashService.computePageHash("page", "FORM", "{}", null, null);
            assertNotNull(hash);
            assertEquals(64, hash.length());
        }
    }
}
