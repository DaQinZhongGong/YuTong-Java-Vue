package com.yutong.system.idempotency.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import com.yutong.infra.persistence.JsonbTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 幂等记录实体。设计来源: 57-完整DDL清单 sys_idempotency_record、98-后端实现蓝图幂等实现模板
 *
 * <p>唯一键: tenant_id + resource_type + COALESCE(resource_id,'') + action + idempotency_key
 * (数据库已建 partial unique index uk_idempotency_scope WHERE deleted=false)。
 *
 * <p>状态机: PROCESSING → SUCCESS / FAILED。FAILED + retryOnFailed=true 时可回退到 PROCESSING。
 *
 * <p>{@code response_snapshot} 为 jsonb 字段，使用 {@link JsonbTypeHandler} 写入，
 * 只保存响应摘要 (code/message/data 摘要)，不保存敏感字段、附件内容、AI Prompt 或大对象
 * (98 号文档阻断清单: response_snapshot 存敏感内容)。
 */
@Getter
@Setter
@TableName(value = "sys_idempotency_record", autoResultMap = true)
public class IdempotencyRecord extends BaseEntity {

    /** 资源类型，如 biz:request / sys:file */
    private String resourceType;

    /** 资源 ID，CREATE 场景可为 null (DB COALESCE 为 '') */
    private String resourceId;

    /** 动作，如 CREATE / UPDATE / APPROVE */
    private String action;

    /** 幂等键，来自 Idempotency-Key 请求头，客户端生成 ULID/UUID */
    private String idempotencyKey;

    /** 请求体 hash (SHA-256 截断 128 字符)，用于检测同 key 不同请求体的冲突 */
    private String requestHash;

    /** 业务响应码 (0=成功，SYS-xxx/AUTH-xxx 等错误码) */
    private String responseCode;

    /** 响应快照 JSON (已脱敏)，使用 JsonbTypeHandler 写入 jsonb 列 */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String responseSnapshot;

    /** 状态: PROCESSING / SUCCESS / FAILED */
    private String status;

    /** Redis 短锁到期时间 (锁持有期间其他请求应返回 SYS-409002) */
    private OffsetDateTime lockedUntil;

    /** 记录过期时间 (到期后可由补偿任务清理， expire_time NOT NULL) */
    private OffsetDateTime expireTime;

    /** 链路 ID，来自 TraceContext */
    private String traceId;
}
