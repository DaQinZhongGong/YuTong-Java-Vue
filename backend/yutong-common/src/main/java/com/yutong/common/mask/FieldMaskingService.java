package com.yutong.common.mask;

/**
 * 字段脱敏服务。设计来源: 67-数据权限与审计日志详设「字段脱敏」line 81-89
 *
 * <p>5 类脱敏规则 (67 号文档 line 83-89):
 * <ol>
 *   <li>contact_phone: 138****8000 (列表默认脱敏，详情按字段权限决定)</li>
 *   <li>email: a***@example.com (列表默认脱敏)</li>
 *   <li>api_key_ref: 只展示引用名 (永不返回真实 key)</li>
 *   <li>authorization/header/token: 全脱敏 *** (日志、审计、错误信息都不得出现)</li>
 *   <li>password/secret: 全脱敏 ***</li>
 * </ol>
 *
 * <p>本类提供基础脱敏方法，供各业务模块调用。
 * 对于 JSON 深度递归脱敏 (如操作日志 before_json/after_json)，
 * 由调用方 (如 OperationLogService) 使用 Jackson 遍历 JSON 节点后调用本类方法。
 *
 * <p>GA2-L176 落地: 关闭 67 号文档验收标准 line 167
 * "联系电话在列表页默认脱敏，敏感字段不进入日志、AI Prompt 或前端埋点"。
 */
public final class FieldMaskingService {

    private FieldMaskingService() {
    }

    /** 全脱敏占位符 (authorization/header/token/password/secret/api_key) */
    public static final String FULL_MASK = "******";

    // ===== 字段名匹配规则 (小写包含匹配) =====

    /** 需要全脱敏的字段名关键词 (67 号文档 line 88: authorization/header/token 全脱敏) */
    private static final String[] FULL_MASK_KEYWORDS = {
            "password", "secret", "token", "authorization", "authkey",
            "apikey", "api_key", "accesskey", "access_key",
            "privatekey", "private_key", "credential"
    };

    /** 手机号字段名关键词 */
    private static final String[] PHONE_KEYWORDS = {
            "phone", "mobile", "tel", "contactphone", "contact_phone"
    };

    /** 邮箱字段名关键词 */
    private static final String[] EMAIL_KEYWORDS = {
            "email", "mail"
    };

    // ===== 公开脱敏方法 =====

    /**
     * 根据字段名自动判断脱敏规则并返回脱敏值。
     * 字段名匹配优先级: 全脱敏 > 手机号 > 邮箱 > 默认不脱敏。
     *
     * @param fieldName 字段名 (不区分大小写)
     * @param value     原始值
     * @return 脱敏后的值，value 为 null 时返回 null
     */
    public static String maskByFieldName(String fieldName, String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (fieldName == null || fieldName.isBlank()) {
            return value;
        }
        String lowerField = fieldName.toLowerCase();

        if (matchAny(lowerField, FULL_MASK_KEYWORDS)) {
            return FULL_MASK;
        }
        if (matchAny(lowerField, PHONE_KEYWORDS)) {
            return maskPhone(value);
        }
        if (matchAny(lowerField, EMAIL_KEYWORDS)) {
            return maskEmail(value);
        }
        return value;
    }

    /**
     * 手机号脱敏: 138****8000 (保留前 3 后 4，中间 4 位星号)。
     * 长度不足 7 位时回退为全脱敏。
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return phone;
        }
        if (phone.length() < 7) {
            return FULL_MASK;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 邮箱脱敏: a***@example.com (保留首字符 + 域名，本地部分星号)。
     * 无 @ 符号时回退为全脱敏。
     */
    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }
        int atIdx = email.indexOf('@');
        if (atIdx <= 0) {
            return FULL_MASK;
        }
        return email.charAt(0) + "***" + email.substring(atIdx);
    }

    /** API Key / Token 全脱敏: 返回 *** (67 号文档 line 87: 永不返回真实 key) */
    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return apiKey;
        }
        return FULL_MASK;
    }

    /**
     * 判断字段名是否属于需要全脱敏的敏感字段 (password/secret/token/authorization/api_key 等)。
     * 供 JSON 深度递归脱敏时使用。
     */
    public static boolean isFullMaskField(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        return matchAny(fieldName.toLowerCase(), FULL_MASK_KEYWORDS);
    }

    /**
     * 判断字段名是否属于手机号字段。
     */
    public static boolean isPhoneField(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        return matchAny(fieldName.toLowerCase(), PHONE_KEYWORDS);
    }

    /**
     * 判断字段名是否属于邮箱字段。
     */
    public static boolean isEmailField(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        return matchAny(fieldName.toLowerCase(), EMAIL_KEYWORDS);
    }

    // ===== 内部辅助方法 =====

    /** 检查 fieldName 是否包含 keywords 数组中任一关键词 */
    private static boolean matchAny(String fieldName, String[] keywords) {
        for (String kw : keywords) {
            if (fieldName.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
