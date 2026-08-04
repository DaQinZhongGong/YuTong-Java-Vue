package com.yutong.system.idempotency.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.system.idempotency.domain.IdempotencyRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 幂等记录 Mapper。设计来源: 57-完整DDL清单 sys_idempotency_record、98-后端实现蓝图
 *
 * <p>唯一键冲突依赖 DB partial unique index uk_idempotency_scope:
 * (tenant_id, resource_type, COALESCE(resource_id,''), action, idempotency_key) WHERE deleted=false。
 * 插入冲突时由 service 层 catch DuplicateKeyException 后转查询分支 (98 号文档第 4 步)。
 */
@Mapper
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecord> {
}
