package com.yutong.lowcode.meta.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.lowcode.meta.domain.*;
import com.yutong.lowcode.meta.dto.LcEntityDetailVO;
import com.yutong.lowcode.meta.dto.SaveLcEntityRequest;
import com.yutong.lowcode.meta.mapper.*;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 低代码实体应用服务。设计来源: 14-低代码平台设计、52-后端服务分工
 * 事务编排: 草稿保存、发布（不可变快照 + config_hash）、禁用、重新启用。
 *
 * <p>GA2-DS: pageEntities 接入 DataScope 数据权限过滤。
 * 默认 TENANT (租户隔离由 BaseEntity 保证)，对非 admin 角色追加 owner 过滤。
 * LcEntity 实体有 owner_user_id 字段，使用 ownerUserId 作为 owner 字段。
 */
@Service
public class LcEntityApplicationService {

    /** 低代码实体资源编码，对齐 lc 命名空间。 */
    public static final String RESOURCE_CODE = "lc:entity";

    private final LcEntityMapper entityMapper;
    private final LcFieldMapper fieldMapper;
    private final LcRelationMapper relationMapper;
    private final LcDomainService domainService;
    private final ConfigHashService configHashService;
    private final DataScopeResolver dataScopeResolver;

    public LcEntityApplicationService(LcEntityMapper entityMapper,
                                      LcFieldMapper fieldMapper,
                                      LcRelationMapper relationMapper,
                                      LcDomainService domainService,
                                      ConfigHashService configHashService,
                                      DataScopeResolver dataScopeResolver) {
        this.entityMapper = entityMapper;
        this.fieldMapper = fieldMapper;
        this.relationMapper = relationMapper;
        this.domainService = domainService;
        this.configHashService = configHashService;
        this.dataScopeResolver = dataScopeResolver;
    }

    public PageResult<LcEntity> pageEntities(PageRequest request, String entityCode, String entityName, String status) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        LambdaQueryWrapper<LcEntity> wrapper = new LambdaQueryWrapper<LcEntity>()
                .eq(LcEntity::getTenantId, CurrentUserContext.getTenantId())
                .like(entityCode != null && !entityCode.isBlank(), LcEntity::getEntityCode, entityCode)
                .like(entityName != null && !entityName.isBlank(), LcEntity::getEntityName, entityName)
                .eq(status != null && !status.isBlank(), LcEntity::getStatus, status)
                .orderByDesc(LcEntity::getCreatedTime);
        // GA2-DS: 接入 DataScope 过滤，对非 admin 角色追加 owner 过滤
        applyDataScope(wrapper, scope);
        Page<LcEntity> page = entityMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    /**
     * GA2-DS: 对 LambdaQueryWrapper 追加 DataScope 过滤条件。
     * LcEntity 实体有 owner_user_id 字段，使用 ownerUserId 作为 owner 字段。
     * - ALL/TENANT: 无附加条件 (admin/viewer)
     * - SELF: owner_user_id = currentUserId (biz 用户)
     * - DEPT/DEPT_AND_CHILD/CUSTOM/NONE: 实体无对应 dept 字段，安全降级为 owner_user_id = currentUserId
     */
    private void applyDataScope(LambdaQueryWrapper<LcEntity> wrapper, DataScope scope) {
        if (scope == null) {
            return;
        }
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) {
            return;
        }
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            wrapper.apply("1 = 0");
            return;
        }
        // 非 admin: 追加 owner 过滤 (LcEntity 有 ownerUserId 字段)
        wrapper.eq(LcEntity::getOwnerUserId, userId);
    }

    public LcEntityDetailVO getEntityDetail(String id) {
        LcEntity entity = entityMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("实体不存在: " + id);
        }
        List<LcField> fields = fieldMapper.selectList(
                new LambdaQueryWrapper<LcField>()
                        .eq(LcField::getEntityId, id)
                        .orderByAsc(LcField::getSortNo));
        List<LcRelation> relations = relationMapper.selectList(
                new LambdaQueryWrapper<LcRelation>()
                        .eq(LcRelation::getSourceEntityId, id));

        LcEntityDetailVO vo = new LcEntityDetailVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setFields(fields.stream().map(this::toFieldDTO).toList());
        vo.setRelations(relations.stream().map(this::toRelationDTO).toList());
        return vo;
    }

    @Transactional
    public LcEntity saveDraft(SaveLcEntityRequest request) {
        if (request.getId() == null || request.getId().isBlank()) {
            // 新建
            LcEntity entity = new LcEntity();
            entity.setId(IdGenerator.nextId());
            entity.setTenantId(CurrentUserContext.getTenantId());
            entity.setCreatedBy(CurrentUserContext.getUserId());
            entity.setEntityCode(request.getEntityCode());
            entity.setEntityName(request.getEntityName());
            entity.setTableName(request.getTableName());
            entity.setModuleCode(request.getModuleCode());
            entity.setOwnerUserId(request.getOwnerUserId());
            entity.setVersionNo(1);
            entity.setSchemaVersion("1.0");
            entity.setStatus(LcEntity.STATUS_DRAFT);
            entityMapper.insert(entity);
            saveFields(entity.getId(), request.getFields());
            saveRelations(entity.getId(), request.getRelations());
            return entity;
        }

        // 修改
        LcEntity existing = entityMapper.selectById(request.getId());
        if (existing == null) {
            throw new ResourceNotFoundException("实体不存在: " + request.getId());
        }
        checkVersion(request.getVersion(), existing.getVersion());
        if (!LcEntity.STATUS_DRAFT.equals(existing.getStatus())) {
            throw new BusinessConflictException(
                    "实体当前状态[" + existing.getStatus() + "]不允许编辑，仅 DRAFT 可编辑");
        }
        existing.setEntityName(request.getEntityName());
        existing.setTableName(request.getTableName());
        existing.setModuleCode(request.getModuleCode());
        existing.setOwnerUserId(request.getOwnerUserId());
        existing.setUpdatedBy(CurrentUserContext.getUserId());
        int affected = entityMapper.updateById(existing);
        if (affected == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        saveFields(existing.getId(), request.getFields());
        saveRelations(existing.getId(), request.getRelations());
        return existing;
    }

    /**
     * 发布: DRAFT → PUBLISHED。生成不可变 config_hash，versionNo 自增。
     * 重复发布（已 PUBLISHED）幂等返回当前实体。
     */
    @Transactional
    public LcEntity publish(String id, Integer version) {
        LcEntity entity = entityMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("实体不存在: " + id);
        }
        checkVersion(version, entity.getVersion());
        // 幂等: 已发布直接返回
        if (LcEntity.STATUS_PUBLISHED.equals(entity.getStatus())) {
            return entity;
        }
        domainService.validateEntityTransition(entity.getStatus(), "PUBLISH");

        List<LcField> fields = fieldMapper.selectList(
                new LambdaQueryWrapper<LcField>().eq(LcField::getEntityId, id));
        List<LcRelation> relations = relationMapper.selectList(
                new LambdaQueryWrapper<LcRelation>().eq(LcRelation::getSourceEntityId, id));
        String hash = configHashService.computeEntityHash(
                entity.getEntityCode(), entity.getTableName(), fields, relations);
        entity.setConfigHash(hash);
        entity.setVersionNo(entity.getVersionNo() + 1);
        entity.setStatus(LcEntity.STATUS_PUBLISHED);
        entity.setUpdatedBy(CurrentUserContext.getUserId());
        int affected = entityMapper.updateById(entity);
        if (affected == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        return entity;
    }

    @Transactional
    public LcEntity disable(String id, Integer version) {
        LcEntity entity = entityMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("实体不存在: " + id);
        }
        checkVersion(version, entity.getVersion());
        domainService.validateEntityTransition(entity.getStatus(), "DISABLE");
        entity.setStatus(LcEntity.STATUS_DISABLED);
        entity.setUpdatedBy(CurrentUserContext.getUserId());
        int affected = entityMapper.updateById(entity);
        if (affected == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        return entity;
    }

    @Transactional
    public void delete(String id) {
        LcEntity entity = entityMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("实体不存在: " + id);
        }
        // BaseEntity 已配置 @TableLogic，deleteById 自动逻辑删除
        entityMapper.deleteById(id);
    }

    private void saveFields(String entityId, List<SaveLcEntityRequest.LcFieldDTO> fields) {
        fieldMapper.delete(new LambdaQueryWrapper<LcField>().eq(LcField::getEntityId, entityId));
        if (fields == null || fields.isEmpty()) {
            return;
        }
        String tenantId = CurrentUserContext.getTenantId();
        String userId = CurrentUserContext.getUserId();
        int index = 1;
        for (SaveLcEntityRequest.LcFieldDTO dto : fields) {
            // 主键字段约束校验
            domainService.validatePrimaryKey(dto.getFieldCode(), dto.getDataType(), dto.getPrimaryFlag());
            LcField entity = new LcField();
            entity.setId(IdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setCreatedBy(userId);
            entity.setEntityId(entityId);
            entity.setFieldCode(dto.getFieldCode());
            entity.setFieldName(dto.getFieldName());
            entity.setDbColumn(dto.getDbColumn());
            entity.setDataType(dto.getDataType());
            entity.setLengthValue(dto.getLengthValue());
            entity.setPrecisionValue(dto.getPrecisionValue());
            entity.setScaleValue(dto.getScaleValue());
            entity.setNullable(dto.getNullable());
            entity.setDefaultValue(dto.getDefaultValue());
            entity.setDictType(dto.getDictType());
            entity.setPrimaryFlag(dto.getPrimaryFlag());
            entity.setUniqueFlag(dto.getUniqueFlag());
            entity.setIndexFlag(dto.getIndexFlag());
            entity.setSortNo(dto.getSortNo() != null ? dto.getSortNo() : index);
            fieldMapper.insert(entity);
            index++;
        }
    }

    private void saveRelations(String entityId, List<SaveLcEntityRequest.LcRelationDTO> relations) {
        relationMapper.delete(new LambdaQueryWrapper<LcRelation>().eq(LcRelation::getSourceEntityId, entityId));
        if (relations == null || relations.isEmpty()) {
            return;
        }
        String tenantId = CurrentUserContext.getTenantId();
        String userId = CurrentUserContext.getUserId();
        for (SaveLcEntityRequest.LcRelationDTO dto : relations) {
            LcRelation entity = new LcRelation();
            entity.setId(IdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setCreatedBy(userId);
            entity.setSourceEntityId(entityId);
            entity.setTargetEntityId(dto.getTargetEntityId());
            entity.setRelationType(dto.getRelationType());
            entity.setSourceFieldCode(dto.getSourceFieldCode());
            entity.setTargetFieldCode(dto.getTargetFieldCode());
            entity.setCascadePolicy(dto.getCascadePolicy());
            entity.setRequired(dto.getRequired());
            relationMapper.insert(entity);
        }
    }

    private LcEntityDetailVO.LcFieldDTO toFieldDTO(LcField f) {
        LcEntityDetailVO.LcFieldDTO dto = new LcEntityDetailVO.LcFieldDTO();
        BeanUtils.copyProperties(f, dto);
        return dto;
    }

    private LcEntityDetailVO.LcRelationDTO toRelationDTO(LcRelation r) {
        LcEntityDetailVO.LcRelationDTO dto = new LcEntityDetailVO.LcRelationDTO();
        BeanUtils.copyProperties(r, dto);
        return dto;
    }

    private void checkVersion(Integer requestVersion, Integer currentVersion) {
        if (requestVersion == null || !requestVersion.equals(currentVersion)) {
            throw new BusinessConflictException(
                    "版本号不匹配，请求版本=" + requestVersion + ", 当前版本=" + currentVersion);
        }
    }
}
