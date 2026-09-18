package com.yutong.sample.drama.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.sample.drama.domain.*;
import com.yutong.sample.drama.dto.*;
import com.yutong.sample.drama.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 短剧资产服务 — 角色/形象/场景地/音频/分镜 CRUD。
 * 设计来源: V043 drama_character 等5表，归属 drama_script，前端可视化实时生效
 * 约束: 租户隔离 + dramaId 归属校验 + 外键 String id，不碰 drama_script/scene 原有逻辑
 */
@Service
public class DramaAssetService {

    private static final Set<String> STORYBOARD_STATUS = Set.of(
            DramaStoryboard.STATUS_DRAFT, DramaStoryboard.STATUS_GENERATING,
            DramaStoryboard.STATUS_SUCCESS, DramaStoryboard.STATUS_FAILED);

    private final DramaScriptMapper scriptMapper;
    private final DramaSceneMapper sceneMapper;
    private final DramaCharacterMapper characterMapper;
    private final DramaCharacterAppearanceMapper appearanceMapper;
    private final DramaLocationMapper locationMapper;
    private final DramaAudioMapper audioMapper;
    private final DramaStoryboardMapper storyboardMapper;

    public DramaAssetService(DramaScriptMapper scriptMapper,
                             DramaSceneMapper sceneMapper,
                             DramaCharacterMapper characterMapper,
                             DramaCharacterAppearanceMapper appearanceMapper,
                             DramaLocationMapper locationMapper,
                             DramaAudioMapper audioMapper,
                             DramaStoryboardMapper storyboardMapper) {
        this.scriptMapper = scriptMapper;
        this.sceneMapper = sceneMapper;
        this.characterMapper = characterMapper;
        this.appearanceMapper = appearanceMapper;
        this.locationMapper = locationMapper;
        this.audioMapper = audioMapper;
        this.storyboardMapper = storyboardMapper;
    }

    // ---- helpers ----
    private DramaScript requireDrama(String dramaId) {
        DramaScript s = scriptMapper.selectById(dramaId);
        if (s == null) throw new ResourceNotFoundException("剧本不存在: " + dramaId);
        return s;
    }

    private DramaCharacter requireCharacter(String dramaId, String characterId) {
        DramaCharacter c = characterMapper.selectById(characterId);
        if (c == null || !c.getDramaId().equals(dramaId)) {
            throw new ResourceNotFoundException("角色不存在: " + characterId);
        }
        return c;
    }

    private void assertTenant(DramaScript drama) {
        // tenantId 校验由 MetaObjectHandler 注入，跨租户访问时 drama.tenantId 与当前上下文应一致
        // 此处仅做归属存在性校验，不强制抛 AUTH，交由上层 DataScope；保持与 DramaService 一致的轻校验
    }

    // ---- Character ----
    public List<DramaCharacter> listCharacters(String dramaId) {
        requireDrama(dramaId);
        return characterMapper.selectList(new LambdaQueryWrapper<DramaCharacter>()
                .eq(DramaCharacter::getDramaId, dramaId)
                .orderByDesc(DramaCharacter::getCreatedTime));
    }

    @Transactional
    public DramaCharacter createCharacter(String dramaId, SaveCharacterRequest req) {
        DramaScript drama = requireDrama(dramaId);
        DramaCharacter e = new DramaCharacter();
        e.setDramaId(dramaId);
        e.setName(req.getName());
        e.setRole(req.getRole());
        e.setDescription(req.getDescription());
        e.setRemark(req.getRemark());
        if (drama.getTenantId() != null) e.setTenantId(drama.getTenantId());
        characterMapper.insert(e);
        return e;
    }

    @Transactional
    public DramaCharacter updateCharacter(String dramaId, String id, SaveCharacterRequest req) {
        requireDrama(dramaId);
        DramaCharacter e = requireCharacter(dramaId, id);
        e.setName(req.getName());
        e.setRole(req.getRole());
        e.setDescription(req.getDescription());
        e.setRemark(req.getRemark());
        characterMapper.updateById(e);
        return e;
    }

    @Transactional
    public void deleteCharacter(String dramaId, String id) {
        requireDrama(dramaId);
        DramaCharacter e = requireCharacter(dramaId, id);
        // 级联软删形象
        List<DramaCharacterAppearance> apps = appearanceMapper.selectList(
                new LambdaQueryWrapper<DramaCharacterAppearance>().eq(DramaCharacterAppearance::getCharacterId, id));
        for (DramaCharacterAppearance a : apps) {
            appearanceMapper.deleteById(a.getId());
        }
        characterMapper.deleteById(e.getId());
    }

    // ---- CharacterAppearance ----
    public List<DramaCharacterAppearance> listAppearances(String dramaId, String characterId) {
        requireDrama(dramaId);
        requireCharacter(dramaId, characterId);
        return appearanceMapper.selectList(new LambdaQueryWrapper<DramaCharacterAppearance>()
                .eq(DramaCharacterAppearance::getCharacterId, characterId)
                .orderByDesc(DramaCharacterAppearance::getCreatedTime));
    }

    @Transactional
    public DramaCharacterAppearance createAppearance(String dramaId, String characterId, SaveCharacterAppearanceRequest req) {
        DramaScript drama = requireDrama(dramaId);
        requireCharacter(dramaId, characterId);
        DramaCharacterAppearance e = new DramaCharacterAppearance();
        e.setCharacterId(characterId);
        e.setAppearanceJson(req.getAppearanceJson());
        e.setImageUrl(req.getImageUrl());
        e.setIsSelected(Boolean.TRUE.equals(req.getIsSelected()));
        e.setRemark(req.getRemark());
        if (drama.getTenantId() != null) e.setTenantId(drama.getTenantId());
        appearanceMapper.insert(e);
        if (Boolean.TRUE.equals(e.getIsSelected())) {
            clearOtherSelected(characterId, e.getId());
        }
        return e;
    }

    @Transactional
    public DramaCharacterAppearance updateAppearance(String dramaId, String characterId, String id, SaveCharacterAppearanceRequest req) {
        requireDrama(dramaId);
        requireCharacter(dramaId, characterId);
        DramaCharacterAppearance e = appearanceMapper.selectById(id);
        if (e == null || !e.getCharacterId().equals(characterId)) {
            throw new ResourceNotFoundException("形象不存在: " + id);
        }
        e.setAppearanceJson(req.getAppearanceJson());
        e.setImageUrl(req.getImageUrl());
        e.setIsSelected(req.getIsSelected());
        e.setRemark(req.getRemark());
        appearanceMapper.updateById(e);
        if (Boolean.TRUE.equals(e.getIsSelected())) {
            clearOtherSelected(characterId, e.getId());
        }
        return e;
    }

    @Transactional
    public void deleteAppearance(String dramaId, String characterId, String id) {
        requireDrama(dramaId);
        requireCharacter(dramaId, characterId);
        DramaCharacterAppearance e = appearanceMapper.selectById(id);
        if (e == null || !e.getCharacterId().equals(characterId)) {
            throw new ResourceNotFoundException("形象不存在: " + id);
        }
        appearanceMapper.deleteById(id);
    }

    /**
     * 确认角色形象 — 选中并锁定 (人机协同: 生成 → 确认 → 锁定)。
     * 确认后同角色其他形象自动取消选中。
     */
    @Transactional
    public DramaCharacterAppearance confirmAppearance(String dramaId, String characterId, String id) {
        requireDrama(dramaId);
        requireCharacter(dramaId, characterId);
        DramaCharacterAppearance e = appearanceMapper.selectById(id);
        if (e == null || !e.getCharacterId().equals(characterId)) {
            throw new ResourceNotFoundException("形象不存在: " + id);
        }
        e.setIsSelected(true);
        appearanceMapper.updateById(e);
        clearOtherSelected(characterId, id);
        return e;
    }

    /**
     * 撤销角色形象确认 — 回到可重选状态。
     */
    @Transactional
    public DramaCharacterAppearance undoAppearance(String dramaId, String characterId, String id) {
        requireDrama(dramaId);
        requireCharacter(dramaId, characterId);
        DramaCharacterAppearance e = appearanceMapper.selectById(id);
        if (e == null || !e.getCharacterId().equals(characterId)) {
            throw new ResourceNotFoundException("形象不存在: " + id);
        }
        e.setIsSelected(false);
        appearanceMapper.updateById(e);
        return e;
    }

    private void clearOtherSelected(String characterId, String keepId) {
        appearanceMapper.update(null, new LambdaUpdateWrapper<DramaCharacterAppearance>()
                .eq(DramaCharacterAppearance::getCharacterId, characterId)
                .ne(DramaCharacterAppearance::getId, keepId)
                .set(DramaCharacterAppearance::getIsSelected, false));
    }

    // ---- Location ----
    public List<DramaLocation> listLocations(String dramaId) {
        requireDrama(dramaId);
        return locationMapper.selectList(new LambdaQueryWrapper<DramaLocation>()
                .eq(DramaLocation::getDramaId, dramaId)
                .orderByDesc(DramaLocation::getCreatedTime));
    }

    @Transactional
    public DramaLocation createLocation(String dramaId, SaveLocationRequest req) {
        DramaScript drama = requireDrama(dramaId);
        DramaLocation e = new DramaLocation();
        e.setDramaId(dramaId);
        e.setName(req.getName());
        e.setDescription(req.getDescription());
        e.setImageUrl(req.getImageUrl());
        e.setRemark(req.getRemark());
        if (drama.getTenantId() != null) e.setTenantId(drama.getTenantId());
        locationMapper.insert(e);
        return e;
    }

    @Transactional
    public DramaLocation updateLocation(String dramaId, String id, SaveLocationRequest req) {
        requireDrama(dramaId);
        DramaLocation e = locationMapper.selectById(id);
        if (e == null || !e.getDramaId().equals(dramaId)) throw new ResourceNotFoundException("场景地不存在: " + id);
        e.setName(req.getName());
        e.setDescription(req.getDescription());
        e.setImageUrl(req.getImageUrl());
        e.setRemark(req.getRemark());
        locationMapper.updateById(e);
        return e;
    }

    @Transactional
    public void deleteLocation(String dramaId, String id) {
        requireDrama(dramaId);
        DramaLocation e = locationMapper.selectById(id);
        if (e == null || !e.getDramaId().equals(dramaId)) throw new ResourceNotFoundException("场景地不存在: " + id);
        locationMapper.deleteById(id);
    }

    // ---- Audio ----
    public List<DramaAudio> listAudios(String dramaId) {
        requireDrama(dramaId);
        return audioMapper.selectList(new LambdaQueryWrapper<DramaAudio>()
                .eq(DramaAudio::getDramaId, dramaId)
                .orderByDesc(DramaAudio::getCreatedTime));
    }

    @Transactional
    public DramaAudio createAudio(String dramaId, SaveAudioRequest req) {
        DramaScript drama = requireDrama(dramaId);
        DramaAudio e = new DramaAudio();
        e.setDramaId(dramaId);
        e.setName(req.getName());
        e.setAudioUrl(req.getAudioUrl());
        e.setDurationSeconds(req.getDurationSeconds());
        e.setVoiceId(req.getVoiceId());
        e.setRemark(req.getRemark());
        if (drama.getTenantId() != null) e.setTenantId(drama.getTenantId());
        audioMapper.insert(e);
        return e;
    }

    @Transactional
    public DramaAudio updateAudio(String dramaId, String id, SaveAudioRequest req) {
        requireDrama(dramaId);
        DramaAudio e = audioMapper.selectById(id);
        if (e == null || !e.getDramaId().equals(dramaId)) throw new ResourceNotFoundException("音频不存在: " + id);
        e.setName(req.getName());
        e.setAudioUrl(req.getAudioUrl());
        e.setDurationSeconds(req.getDurationSeconds());
        e.setVoiceId(req.getVoiceId());
        e.setRemark(req.getRemark());
        audioMapper.updateById(e);
        return e;
    }

    @Transactional
    public void deleteAudio(String dramaId, String id) {
        requireDrama(dramaId);
        DramaAudio e = audioMapper.selectById(id);
        if (e == null || !e.getDramaId().equals(dramaId)) throw new ResourceNotFoundException("音频不存在: " + id);
        audioMapper.deleteById(id);
    }

    // ---- Storyboard ----
    public List<DramaStoryboard> listStoryboards(String dramaId) {
        requireDrama(dramaId);
        return storyboardMapper.selectList(new LambdaQueryWrapper<DramaStoryboard>()
                .eq(DramaStoryboard::getDramaId, dramaId)
                .orderByAsc(DramaStoryboard::getStoryboardNo));
    }

    @Transactional
    public DramaStoryboard createStoryboard(String dramaId, SaveStoryboardRequest req) {
        DramaScript drama = requireDrama(dramaId);
        // 校验 storyboard_no 唯一
        Long cnt = storyboardMapper.selectCount(new LambdaQueryWrapper<DramaStoryboard>()
                .eq(DramaStoryboard::getDramaId, dramaId)
                .eq(DramaStoryboard::getStoryboardNo, req.getStoryboardNo()));
        if (cnt != null && cnt > 0) {
            throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT, "分镜序号已存在: " + req.getStoryboardNo());
        }
        if (req.getSceneId() != null && !req.getSceneId().isBlank()) {
            DramaScene scene = sceneMapper.selectById(req.getSceneId());
            if (scene == null || !scene.getDramaId().equals(dramaId)) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "sceneId 不归属当前剧本: " + req.getSceneId());
            }
        }
        if (req.getAudioId() != null && !req.getAudioId().isBlank()) {
            DramaAudio audio = audioMapper.selectById(req.getAudioId());
            if (audio == null || !audio.getDramaId().equals(dramaId)) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "audioId 不归属当前剧本: " + req.getAudioId());
            }
        }
        DramaStoryboard e = new DramaStoryboard();
        e.setDramaId(dramaId);
        e.setSceneId(blankToNull(req.getSceneId()));
        e.setStoryboardNo(req.getStoryboardNo());
        e.setPrompt(req.getPrompt());
        e.setImageUrl(req.getImageUrl());
        e.setAudioId(blankToNull(req.getAudioId()));
        e.setStatus(normalizeStoryboardStatus(req.getStatus()));
        e.setRemark(req.getRemark());
        if (drama.getTenantId() != null) e.setTenantId(drama.getTenantId());
        storyboardMapper.insert(e);
        return e;
    }

    @Transactional
    public DramaStoryboard updateStoryboard(String dramaId, String id, SaveStoryboardRequest req) {
        requireDrama(dramaId);
        DramaStoryboard e = storyboardMapper.selectById(id);
        if (e == null || !e.getDramaId().equals(dramaId)) throw new ResourceNotFoundException("分镜不存在: " + id);
        if (!e.getStoryboardNo().equals(req.getStoryboardNo())) {
            Long cnt = storyboardMapper.selectCount(new LambdaQueryWrapper<DramaStoryboard>()
                    .eq(DramaStoryboard::getDramaId, dramaId)
                    .eq(DramaStoryboard::getStoryboardNo, req.getStoryboardNo()));
            if (cnt != null && cnt > 0) throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT, "分镜序号已存在: " + req.getStoryboardNo());
        }
        if (req.getSceneId() != null && !req.getSceneId().isBlank()) {
            DramaScene scene = sceneMapper.selectById(req.getSceneId());
            if (scene == null || !scene.getDramaId().equals(dramaId)) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "sceneId 不归属当前剧本: " + req.getSceneId());
            }
        }
        if (req.getAudioId() != null && !req.getAudioId().isBlank()) {
            DramaAudio audio = audioMapper.selectById(req.getAudioId());
            if (audio == null || !audio.getDramaId().equals(dramaId)) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "audioId 不归属当前剧本: " + req.getAudioId());
            }
        }
        e.setSceneId(blankToNull(req.getSceneId()));
        e.setStoryboardNo(req.getStoryboardNo());
        e.setPrompt(req.getPrompt());
        e.setImageUrl(req.getImageUrl());
        e.setAudioId(blankToNull(req.getAudioId()));
        if (req.getStatus() != null && !req.getStatus().isBlank()) e.setStatus(normalizeStoryboardStatus(req.getStatus()));
        e.setRemark(req.getRemark());
        storyboardMapper.updateById(e);
        return e;
    }

    @Transactional
    public void deleteStoryboard(String dramaId, String id) {
        requireDrama(dramaId);
        DramaStoryboard e = storyboardMapper.selectById(id);
        if (e == null || !e.getDramaId().equals(dramaId)) throw new ResourceNotFoundException("分镜不存在: " + id);
        storyboardMapper.deleteById(id);
    }

    private String normalizeStoryboardStatus(String status) {
        if (status == null || status.isBlank()) return DramaStoryboard.STATUS_DRAFT;
        String s = status.toUpperCase();
        if (!STORYBOARD_STATUS.contains(s)) throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "不支持的 status: " + status);
        return s;
    }

    private String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }
}
