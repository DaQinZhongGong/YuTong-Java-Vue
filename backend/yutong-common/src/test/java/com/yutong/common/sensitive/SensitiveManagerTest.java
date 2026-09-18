package com.yutong.common.sensitive;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 脱敏管理器单元测试
 * 设计来源: ADR 0004 P2-A common-sensitive
 *
 * 覆盖:
 *  - 7 种内置策略的 mask 函数
 *  - SensitiveManager 反射批量 mask
 *  - CUSTOM 策略 + 自定义 service
 *  - 边界: null/empty/超短字符串
 *  - 异常安全: service 抛错不传播
 */
class SensitiveManagerTest {

    // ============ 测试 VO ============

    static class UserVO {
        @Sensitive(strategy = SensitiveStrategy.MOBILE)
        private String phone;
        @Sensitive(strategy = SensitiveStrategy.EMAIL)
        private String email;
        @Sensitive(strategy = SensitiveStrategy.ID_CARD)
        private String idCard;
        @Sensitive(strategy = SensitiveStrategy.BANK_CARD)
        private String bankCard;
        @Sensitive(strategy = SensitiveStrategy.CHINESE_NAME)
        private String name;
        @Sensitive(strategy = SensitiveStrategy.ADDRESS)
        private String address;
        @Sensitive(strategy = SensitiveStrategy.FIXED_PHONE)
        private String fixedPhone;
        // 不带 @Sensitive, 应保持原值
        private String nickname;
        public String getPhone() { return phone; }
        public String getEmail() { return email; }
        public String getIdCard() { return idCard; }
        public String getBankCard() { return bankCard; }
        public String getName() { return name; }
        public String getAddress() { return address; }
        public String getFixedPhone() { return fixedPhone; }
        public String getNickname() { return nickname; }
    }

    static class CustomVO {
        @Sensitive(strategy = SensitiveStrategy.CUSTOM, service = "testMasker")
        private String value;
        public String getValue() { return value; }
    }

    // ============ 7 种策略单测 ============

    @Test
    void mobile_masks11Digits() {
        assertEquals("138****1234", SensitiveHandlers.mobile("13800001234"));
    }

    @Test
    void mobile_stripsNonDigits() {
        // 138 0000 1234 格式
        assertEquals("138****1234", SensitiveHandlers.mobile("138 0000 1234"));
        assertEquals("138****1234", SensitiveHandlers.mobile("138-0000-1234"));
    }

    @Test
    void mobile_tooShort_returnsAsIs() {
        assertEquals("12345", SensitiveHandlers.mobile("12345"));
    }

    @Test
    void email_masksLocalPart() {
        assertEquals("a***@example.com", SensitiveHandlers.email("alice@example.com"));
        assertEquals("t***@test.cn", SensitiveHandlers.email("test@test.cn"));
    }

    @Test
    void email_noAt_returnsAsIs() {
        assertEquals("not-an-email", SensitiveHandlers.email("not-an-email"));
    }

    @Test
    void idCard_masksMiddle() {
        assertEquals("110101********1234", SensitiveHandlers.idCard("110101199001011234"));
    }

    @Test
    void idCard_tooShort_returnsAsIs() {
        assertEquals("12345", SensitiveHandlers.idCard("12345"));
    }

    @Test
    void bankCard_masksMiddle() {
        assertEquals("6222 **** **** 1234", SensitiveHandlers.bankCard("6222021234561234"));
    }

    @Test
    void chineseName_masksCorrectly() {
        assertEquals("*", SensitiveHandlers.chineseName("张"));
        assertEquals("张*", SensitiveHandlers.chineseName("张三"));
        assertEquals("张*丰", SensitiveHandlers.chineseName("张三丰"));
        assertEquals("欧**娜", SensitiveHandlers.chineseName("欧阳娜娜"));
    }

    @Test
    void address_keepsFirst6Chars() {
        // substring(0, 6) 取前 6 个 char, 中文字符 1 char=1 位
        assertEquals("北京市朝阳区****", SensitiveHandlers.address("北京市朝阳区某某街道100号"));
        // 短地址: 长度 <= 6 时保留全部
        assertEquals("北京市****", SensitiveHandlers.address("北京市"));
        assertEquals("北京市朝阳区****", SensitiveHandlers.address("北京市朝阳区"));
    }

    @Test
    void fixedPhone_masksMiddle() {
        // stripNonDigits = "01012345678" (10 char), 保留前 4 后 4, 中间 ****
        assertEquals("0101****5678", SensitiveHandlers.fixedPhone("010-12345678"));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        // 清空 SensitiveManager 静态注册表, 避免 test 间污染
        try {
            java.lang.reflect.Field f = SensitiveManager.class.getDeclaredField("SERVICES");
            f.setAccessible(true);
            ((java.util.Map<?, ?>) f.get(null)).clear();
        } catch (Exception e) {
            // ignore
        }
    }

    @Test
    void custom_usesLambda() {
        String masked = SensitiveHandlers.custom("secret123", s -> "***" + s.length() + "***");
        assertEquals("***9***", masked);
    }

    @Test
    void mask_dispatchByStrategy() {
        assertEquals("138****1234", SensitiveHandlers.mask("13800001234", SensitiveStrategy.MOBILE));
        assertEquals("a***@x.com", SensitiveHandlers.mask("a@x.com", SensitiveStrategy.EMAIL));
    }

    // ============ 边界 ============

    @Test
    void mask_nullOrEmpty_returnsAsIs() {
        assertNull(SensitiveHandlers.mask(null, SensitiveStrategy.MOBILE));
        assertEquals("", SensitiveHandlers.mask("", SensitiveStrategy.MOBILE));
        assertNull(SensitiveHandlers.mask(null, null));
    }

    @Test
    void allHandlers_nullSafe() {
        assertNull(SensitiveHandlers.mobile(null));
        assertNull(SensitiveHandlers.email(null));
        assertNull(SensitiveHandlers.idCard(null));
        assertNull(SensitiveHandlers.bankCard(null));
        assertNull(SensitiveHandlers.chineseName(null));
        assertNull(SensitiveHandlers.address(null));
        assertNull(SensitiveHandlers.fixedPhone(null));
    }

    @Test
    void allHandlers_emptyReturnsEmpty() {
        assertEquals("", SensitiveHandlers.mobile(""));
        assertEquals("", SensitiveHandlers.email(""));
        assertEquals("", SensitiveHandlers.idCard(""));
    }

    // ============ SensitiveManager 反射批量 ============

    @Test
    void manager_masksAllAnnotatedFields() {
        UserVO vo = new UserVO();
        vo.phone = "13800001234";
        vo.email = "alice@example.com";
        vo.idCard = "110101199001011234";
        vo.bankCard = "6222021234561234";
        vo.name = "张三丰";
        vo.address = "北京市朝阳区某某街道100号";
        vo.fixedPhone = "010-12345678";
        vo.nickname = "管理员";

        SensitiveManager.mask(vo);

        assertEquals("138****1234", vo.getPhone());
        assertEquals("a***@example.com", vo.getEmail());
        assertEquals("110101********1234", vo.getIdCard());
        assertEquals("6222 **** **** 1234", vo.getBankCard());
        assertEquals("张*丰", vo.getName());
        assertEquals("北京市朝阳区****", vo.getAddress());
        assertEquals("0101****5678", vo.getFixedPhone());
        assertEquals("管理员", vo.getNickname(), "非 @Sensitive 字段保持原值");
    }

    @Test
    void manager_masksBatch() {
        UserVO vo1 = new UserVO();
        vo1.phone = "13800001111";
        UserVO vo2 = new UserVO();
        vo2.phone = "13800002222";
        List<UserVO> vos = SensitiveManager.maskAll(Arrays.asList(vo1, vo2));
        assertEquals("138****1111", vo1.getPhone());
        assertEquals("138****2222", vo2.getPhone());
    }

    @Test
    void manager_nullInput_returnsNull() {
        assertNull(SensitiveManager.mask(null));
    }

    @Test
    void manager_emptyBatch_returnsEmpty() {
        java.util.List<UserVO> empty = java.util.Collections.emptyList();
        assertTrue(SensitiveManager.maskAll(empty).isEmpty());
    }

    @Test
    void manager_voWithoutSensitive_unchanged() {
        class EmptyVO { String data = "untouched"; String getData() { return data; } }
        EmptyVO vo = new EmptyVO();
        SensitiveManager.mask(vo);
        assertEquals("untouched", vo.getData());
    }

    @Test
    void manager_customStrategy_usesRegisteredService() {
        SensitiveManager.register("testMasker", value -> "<" + value + ">");
        CustomVO vo = new CustomVO();
        vo.value = "secret";
        SensitiveManager.mask(vo);
        assertEquals("<secret>", vo.getValue());
    }

    @Test
    void manager_customStrategy_unregisteredService_keepsOriginal() {
        // CUSTOM 策略但 service 未注册 -> 静默保留原值
        CustomVO vo = new CustomVO();
        vo.value = "secret";
        SensitiveManager.mask(vo);
        assertEquals("secret", vo.getValue());
    }

    @Test
    void manager_serviceThrows_silentlyKeepsOriginal() {
        SensitiveManager.register("thrower", value -> {
            throw new RuntimeException("boom");
        });
        class ThrowVO {
            @Sensitive(strategy = SensitiveStrategy.CUSTOM, service = "thrower")
            String v = "data";
            String getV() { return v; }
        }
        ThrowVO vo = new ThrowVO();
        SensitiveManager.mask(vo);
        // service 抛错 -> 静默, 字段保留原值
        assertEquals("data", vo.getV());
    }

    @Test
    void manager_inheritanceInheritedFieldsAlsoMasked() {
        class Parent {
            @Sensitive(strategy = SensitiveStrategy.MOBILE) String parentPhone;
        }
        class Child extends Parent {
            @Sensitive(strategy = SensitiveStrategy.EMAIL) String childEmail;
        }
        Child c = new Child();
        c.parentPhone = "13800001111";
        c.childEmail = "kid@example.com";
        SensitiveManager.mask(c);
        assertEquals("138****1111", c.parentPhone);
        assertEquals("k***@example.com", c.childEmail);
    }
}
