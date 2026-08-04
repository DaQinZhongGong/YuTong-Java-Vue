package com.yutong.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限注解。Controller 方法必须标注权限码。
 * 设计来源: 98-后端实现蓝图权限注解与菜单权限
 * 资源列表 *:list、详情 *:detail、动作 add/edit/delete/import/export/approve/reject/archive
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    String value();
}
