package com.yutong.sample.drama.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.sample.drama.domain.DramaScene;
import com.yutong.sample.drama.domain.DramaScript;
import com.yutong.sample.drama.dto.DramaDetailVO;
import com.yutong.sample.drama.dto.SaveDramaRequest;
import com.yutong.sample.drama.dto.SaveSceneRequest;
import com.yutong.sample.drama.mapper.DramaSceneMapper;
import com.yutong.sample.drama.mapper.DramaScriptMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 短剧垂类服务 — 最小可用 CRUD + scenes 列表。
 * 设计来源: V041 drama_script/drama_scene, Phase 7 垂类占位短剧垂类最小闭环
 */
@Service
public class DramaService {

    private static final Set<String> ALLOWED_STATUS = Set.of(
            DramaScript.STATUS_DRAFT, DramaScript.STATUS_PUBLISHED, DramaScript.STATUS_ARCHIVED);

    private final DramaScriptMapper scriptMapper;
    private final DramaSceneMapper sceneMapper;

    public DramaService(DramaScriptMapper scriptMapper, DramaSceneMapper sceneMapper) {
        this.scriptMapper = scriptMapper;
        this.sceneMapper = sceneMapper;
    }

    public PageResult<DramaScript> pageScripts(PageRequest request, String title, String status) {
        String tenantId = CurrentUserContext.getTenantId();
        LambdaQueryWrapper<DramaScript> wrapper = new LambdaQueryWrapper<DramaScript>()
                .eq(tenantId != null, DramaScript::getTenantId, tenantId)
                .like(title != null && !title.isBlank(), DramaScript::getTitle, title)
                .eq(status != null && !status.isBlank(), DramaScript::getStatus, status)
                .orderByDesc(DramaScript::getCreatedTime);
        Page<DramaScript> page = scriptMapper.selectPage(new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    public DramaDetailVO getDetail(String id) {
        DramaScript script = scriptMapper.selectById(id);
        if (script == null) throw new ResourceNotFoundException("剧本不存在: " + id);
        List<DramaScene> scenes = sceneMapper.selectList(new LambdaQueryWrapper<DramaScene>()
                .eq(DramaScene::getDramaId, id)
                .orderByAsc(DramaScene::getSceneNo));
        return DramaDetailVO.of(script, scenes);
    }

    public List<DramaScene> listScenes(String dramaId) {
        DramaScript script = scriptMapper.selectById(dramaId);
        if (script == null) throw new ResourceNotFoundException("剧本不存在: " + dramaId);
        return sceneMapper.selectList(new LambdaQueryWrapper<DramaScene>()
                .eq(DramaScene::getDramaId, dramaId)
                .orderByAsc(DramaScene::getSceneNo));
    }

    @Transactional
    public DramaScript createScript(SaveDramaRequest request) {
        DramaScript script = new DramaScript();
        script.setTitle(request.getTitle());
        script.setSynopsis(request.getSynopsis());
        script.setStatus(normalizeStatus(request.getStatus()));
        script.setRemark(request.getRemark());
        scriptMapper.insert(script);
        return script;
    }

    @Transactional
    public DramaScript updateScript(String id, SaveDramaRequest request) {
        DramaScript script = scriptMapper.selectById(id);
        if (script == null) throw new ResourceNotFoundException("剧本不存在: " + id);
        script.setTitle(request.getTitle());
        script.setSynopsis(request.getSynopsis());
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            script.setStatus(normalizeStatus(request.getStatus()));
        }
        script.setRemark(request.getRemark());
        scriptMapper.updateById(script);
        return script;
    }

    @Transactional
    public void deleteScript(String id) {
        DramaScript script = scriptMapper.selectById(id);
        if (script == null) throw new ResourceNotFoundException("剧本不存在: " + id);
        // 软删剧本及其场景 (BaseEntity deleted 逻辑删)
        scriptMapper.deleteById(id);
        // 场景逻辑删
        List<DramaScene> scenes = sceneMapper.selectList(new LambdaQueryWrapper<DramaScene>()
                .eq(DramaScene::getDramaId, id));
        for (DramaScene s : scenes) {
            sceneMapper.deleteById(s.getId());
        }
    }

    @Transactional
    public DramaScene createScene(String dramaId, SaveSceneRequest request) {
        DramaScript script = scriptMapper.selectById(dramaId);
        if (script == null) throw new ResourceNotFoundException("剧本不存在: " + dramaId);
        // 校验 scene_no 唯一
        Long cnt = sceneMapper.selectCount(new LambdaQueryWrapper<DramaScene>()
                .eq(DramaScene::getDramaId, dramaId)
                .eq(DramaScene::getSceneNo, request.getSceneNo()));
        if (cnt != null && cnt > 0) {
            throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT, "场次已存在: sceneNo=" + request.getSceneNo());
        }
        DramaScene scene = new DramaScene();
        scene.setDramaId(dramaId);
        scene.setSceneNo(request.getSceneNo());
        scene.setDescription(request.getDescription());
        scene.setCharacterConsistencyJson(request.getCharacterConsistencyJson());
        scene.setRemark(request.getRemark());
        // tenantId 由 MetaObjectHandler 注入; 显式设置为脚本租户以防上下文缺失
        if (script.getTenantId() != null) scene.setTenantId(script.getTenantId());
        sceneMapper.insert(scene);
        return scene;
    }

    @Transactional
    public DramaScene updateScene(String dramaId, String sceneId, SaveSceneRequest request) {
        DramaScene scene = sceneMapper.selectById(sceneId);
        if (scene == null || !scene.getDramaId().equals(dramaId)) {
            throw new ResourceNotFoundException("场景不存在: " + sceneId);
        }
        // 若修改 sceneNo 需查重
        if (!scene.getSceneNo().equals(request.getSceneNo())) {
            Long cnt = sceneMapper.selectCount(new LambdaQueryWrapper<DramaScene>()
                    .eq(DramaScene::getDramaId, dramaId)
                    .eq(DramaScene::getSceneNo, request.getSceneNo()));
            if (cnt != null && cnt > 0) {
                throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT, "场次已存在: sceneNo=" + request.getSceneNo());
            }
        }
        scene.setSceneNo(request.getSceneNo());
        scene.setDescription(request.getDescription());
        scene.setCharacterConsistencyJson(request.getCharacterConsistencyJson());
        scene.setRemark(request.getRemark());
        sceneMapper.updateById(scene);
        return scene;
    }

    @Transactional
    public void deleteScene(String dramaId, String sceneId) {
        DramaScene scene = sceneMapper.selectById(sceneId);
        if (scene == null || !scene.getDramaId().equals(dramaId)) {
            throw new ResourceNotFoundException("场景不存在: " + sceneId);
        }
        sceneMapper.deleteById(sceneId);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return DramaScript.STATUS_DRAFT;
        String s = status.toUpperCase();
        if (!ALLOWED_STATUS.contains(s)) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "不支持的 status: " + status);
        }
        return s;
    }
}

