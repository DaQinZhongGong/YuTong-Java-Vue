package com.yutong.common.translation;

/**
 * 字典翻译服务 SPI — 业务方实现此接口提供自定义翻译
 * 设计来源: platform-common-translation + ADR 0004 P2-A
 *
 * 落点:84-字典/用户/部门显示名自动化详设
 *
 * 用法:
 *   @Component("myDictTranslator")
 *   public class MyDictTranslator implements TranslationService {
 *       public String translate(String id) { ... }
 *   }
 *
 *   @Translation(type = TranslationType.CUSTOM, ref = "foo", translator = "myDictTranslator")
 *   private String fooLabel;
 *
 * 内置默认实现:
 *  - DictTranslationService  (DICT 类型, 查 sys_dict_data 表)
 *  - UserTranslationService  (USER 类型, 查 sys_user 表)
 *  - DeptTranslationService  (DEPT 类型, 查 sys_dept 表)
 *  - PostTranslationService  (POST 类型, 查 sys_post 表)
 *  - RoleTranslationService  (ROLE 类型, 查 sys_role 表)
 */
public interface TranslationService {

    /**
     * 根据 ID 翻译为显示名
     * @param id 待翻译的 ID/字典值 (可能为 null/空)
     * @param extension 翻译扩展参数, DICT 类型时为 dictType, 其他类型通常为空
     * @return 显示名; 查不到返回 null
     */
    String translate(String id, String extension);
}
