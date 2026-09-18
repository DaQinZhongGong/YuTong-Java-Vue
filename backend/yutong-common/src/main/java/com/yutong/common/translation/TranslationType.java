package com.yutong.common.translation;

/**
 * 字典翻译类型 — @Translation 注解 type 字段枚举
 * 设计来源: platform-common-translation + ADR 0004 P2-A 中间件补强
 *
 * 落点:50-设计系统 + 84-字典/用户/部门显示名自动化详设
 *
 * 复用方式:VO 字段上加 @Translation(type = "USER", ref = "createBy") 自动注入显示名
 *   private String createBy;       // 用户 ID
 *   @Translation(type = "USER", ref = "createBy")
 *   private String createByName;   // 序列化时自动填入用户名
 */
public enum TranslationType {
    /** sys_dict_data 字典项:dictType+dictValue -> dictLabel */
    DICT,
    /** sys_user 用户:userId -> username / nickName */
    USER,
    /** sys_dept 部门:deptId -> deptName */
    DEPT,
    /** sys_post 岗位:postId -> postName */
    POST,
    /** sys_role 角色:roleId -> roleName */
    ROLE,
    /** 自定义:由 SPI 实现 (TranslationService 接口) */
    CUSTOM
}
