package com.yutong.infra.persistence;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.id.IdGenerator;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * MyBatis-Plus 自动填充。
 * 设计来源: 98-后端实现蓝图与代码骨架详设
 * 统一填充 id、tenantId、createdBy、createdTime、updatedBy、updatedTime、deleted、version。
 * 业务代码不得手动生成主键或绕过自动填充。
 */
@Component
public class YutongMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        OffsetDateTime now = OffsetDateTime.now();
        this.strictInsertFill(metaObject, "id", String.class, IdGenerator.nextId());
        this.strictInsertFill(metaObject, "tenantId", String.class, currentTenantId());
        this.strictInsertFill(metaObject, "createdBy", String.class, currentUserId());
        this.strictInsertFill(metaObject, "createdTime", OffsetDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedBy", String.class, currentUserId());
        this.strictInsertFill(metaObject, "updatedTime", OffsetDateTime.class, now);
        this.strictInsertFill(metaObject, "deleted", Boolean.class, false);
        this.strictInsertFill(metaObject, "version", Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedBy", String.class, currentUserId());
        this.strictUpdateFill(metaObject, "updatedTime", OffsetDateTime.class, OffsetDateTime.now());
    }

    private String currentTenantId() {
        String tenantId = CurrentUserContext.getTenantId();
        return tenantId != null ? tenantId : "default";
    }

    private String currentUserId() {
        String userId = CurrentUserContext.getUserId();
        return userId != null ? userId : "system";
    }
}
