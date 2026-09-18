package com.yutong.ai.skill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.ai.skill.domain.AiSkill;
import com.yutong.ai.skill.mapper.AiSkillMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Skill 注册表。
 * 设计来源: V038 ai_skill / Phase 3 Skill
 * 职责:
 * - CRUD + 分页 (DataScope)
 * - DRAFT → PUBLISHED → DISABLED 状态机
 * - activate_skill: 加载 SKILL.md、解析、按 tenant 缓存
 */
@Service
public class SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);

    public static final String RESOURCE_CODE = "ai:skill";

    private final AiSkillMapper skillMapper;
    private final DataScopeResolver dataScopeResolver;

    /** per-tenant 缓存: key = tenantId:skillCode → latest PUBLISHED AiSkill */
    private final Map<String, AiSkill> activatedCache = new ConcurrentHashMap<>();

    public SkillRegistry(AiSkillMapper skillMapper, DataScopeResolver dataScopeResolver) {
        this.skillMapper = skillMapper;
        this.dataScopeResolver = dataScopeResolver;
    }

    public PageResult<AiSkill> pageSkills(PageRequest request, String skillCode, String skillType, String status) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        LambdaQueryWrapper<AiSkill> wrapper = new LambdaQueryWrapper<AiSkill>()
                .eq(AiSkill::getTenantId, CurrentUserContext.getTenantId())
                .like(skillCode != null && !skillCode.isBlank(), AiSkill::getSkillCode, skillCode)
                .eq(skillType != null && !skillType.isBlank(), AiSkill::getSkillType, skillType)
                .eq(status != null && !status.isBlank(), AiSkill::getStatus, status)
                .orderByDesc(AiSkill::getCreatedTime);
        applyDataScope(wrapper, scope);
        Page<AiSkill> page = skillMapper.selectPage(new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    public AiSkill getSkill(String id) {
        AiSkill skill = skillMapper.selectById(id);
        if (skill == null) {
            throw new ResourceNotFoundException("Skill 不存在: " + id);
        }
        return skill;
    }

    @Transactional
    public AiSkill saveDraft(AiSkill skill) {
        if (skill.getSkillCode() == null || skill.getSkillCode().isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "skillCode 不能为空");
        }
        if (skill.getSkillName() == null || skill.getSkillName().isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "skillName 不能为空");
        }
        if (skill.getSkillType() == null || skill.getSkillType().isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "skillType 不能为空");
        }
        if (skill.getConfigJson() != null && skill.getConfigJson().isBlank()) {
            skill.setConfigJson(null);
        }
        if (skill.getSkillMd() != null && skill.getSkillMd().isBlank()) {
            skill.setSkillMd(null);
        }
        if (skill.getId() == null || skill.getId().isBlank()) {
            skill.setId(IdGenerator.nextId());
            skill.setTenantId(CurrentUserContext.getTenantId());
            skill.setCreatedBy(CurrentUserContext.getUserId());
            if (skill.getVersionNo() == null) skill.setVersionNo(1);
            skill.setStatus(AiSkill.STATUS_DRAFT);
            skill.setVersion(0);
            try {
                skillMapper.insert(skill);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "Skill 编码+版本已存在: " + skill.getSkillCode() + " v" + skill.getVersionNo());
            }
            log.info("skill draft created: id={} code={} v{}", skill.getId(), skill.getSkillCode(), skill.getVersionNo());
            return skill;
        }
        AiSkill existing = getSkill(skill.getId());
        checkVersion(skill.getVersion(), existing.getVersion());
        if (!AiSkill.STATUS_DRAFT.equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "仅 DRAFT 状态可编辑，当前状态=" + existing.getStatus());
        }
        skill.setTenantId(existing.getTenantId());
        skill.setStatus(AiSkill.STATUS_DRAFT);
        skill.setUpdatedBy(CurrentUserContext.getUserId());
        int rows = skillMapper.updateById(skill);
        if (rows == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        return skill;
    }

    @Transactional
    public AiSkill publishSkill(String id, Integer version) {
        AiSkill skill = getSkill(id);
        checkVersion(version, skill.getVersion());
        if (AiSkill.STATUS_PUBLISHED.equals(skill.getStatus())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "Skill 已发布，不可重复发布");
        }
        if (!AiSkill.STATUS_DRAFT.equals(skill.getStatus())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "仅 DRAFT 可发布，当前状态=" + skill.getStatus());
        }
        skill.setStatus(AiSkill.STATUS_PUBLISHED);
        skill.setUpdatedBy(CurrentUserContext.getUserId());
        int rows = skillMapper.updateById(skill);
        if (rows == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        // 发布后自动激活缓存
        cacheActivated(skill);
        log.info("skill published: id={} code={} v{}", id, skill.getSkillCode(), skill.getVersionNo());
        return skill;
    }

    @Transactional
    public AiSkill disableSkill(String id, Integer version) {
        AiSkill skill = getSkill(id);
        checkVersion(version, skill.getVersion());
        if (AiSkill.STATUS_DISABLED.equals(skill.getStatus())) {
            return skill;
        }
        skill.setStatus(AiSkill.STATUS_DISABLED);
        skill.setUpdatedBy(CurrentUserContext.getUserId());
        int rows = skillMapper.updateById(skill);
        if (rows == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        // 禁用后清除缓存
        activatedCache.remove(cacheKey(skill.getTenantId(), skill.getSkillCode()));
        log.info("skill disabled: id={} code={}", id, skill.getSkillCode());
        return skill;
    }

    /**
     * 激活 Skill: 加载 SKILL.md、解析并按 tenant 缓存。
     * 仅 PUBLISHED 状态可激活。
     * 解析逻辑首版为 mock: 校验 skillMd 非空，提取首行作为描述摘要。
     * 后续可替换为真实 Markdown 解析 + source_path 文件读取。
     */
    public AiSkill activateSkill(String id) {
        AiSkill skill = getSkill(id);
        if (!AiSkill.STATUS_PUBLISHED.equals(skill.getStatus())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "仅 PUBLISHED 状态可激活，当前状态=" + skill.getStatus());
        }
        // 校验 SKILL.md
        if (skill.getSkillMd() == null || skill.getSkillMd().isBlank()) {
            String loaded = loadSkillMd(skill.getSourcePath());
            if (loaded != null && !loaded.isBlank()) {
                skill.setSkillMd(loaded);
            } else {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "Skill 未配置 SKILL.md 内容，无法激活");
            }
        }
        // 简易解析: 提取首个非空行作为摘要写入 remark (不持久化，仅缓存)
        String summary = parseSkillMdSummary(skill.getSkillMd());
        log.info("skill activate: id={} code={} summary={}", id, skill.getSkillCode(), summary);
        cacheActivated(skill);
        return skill;
    }

    /**
     * 获取已激活的 Skill 缓存 (按 tenant + skillCode)。
     * 若缓存未命中，尝试从 DB 加载最新 PUBLISHED 版本并缓存。
     */
    public AiSkill getActivated(String skillCode) {
        String tenantId = CurrentUserContext.getTenantId();
        String key = cacheKey(tenantId, skillCode);
        AiSkill cached = activatedCache.get(key);
        if (cached != null) {
            return cached;
        }
        // DB 回源: 取该 skillCode 最新 PUBLISHED 版本
        LambdaQueryWrapper<AiSkill> wrapper = new LambdaQueryWrapper<AiSkill>()
                .eq(AiSkill::getTenantId, tenantId)
                .eq(AiSkill::getSkillCode, skillCode)
                .eq(AiSkill::getStatus, AiSkill.STATUS_PUBLISHED)
                .orderByDesc(AiSkill::getVersionNo)
                .last("LIMIT 1");
        AiSkill latest = skillMapper.selectOne(wrapper);
        if (latest != null) {
            activatedCache.put(key, latest);
        }
        return latest;
    }

    public void evictCache(String skillCode) {
        String tenantId = CurrentUserContext.getTenantId();
        activatedCache.remove(cacheKey(tenantId, skillCode));
    }

    /**
     * 从市场创建 Skill — V043 P0 业界同类实现 平价补齐占位。
     * 设计来源: V043 ai_skill.market_id / script_ref / skill_type 扩展
     * <p>幂等：同一 marketId 已有 DRAFT 则直接返回；真实联动需查询 ai_mcp_market 复制 name/code/config_json。
     * 当前占位仅生成最小可编译 Draft，scriptRef 为空需后续绑定。
     */
    @Transactional
    public AiSkill fromMarket(String marketId) {
        if (marketId == null || marketId.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "marketId 不能为空");
        }
        String tenantId = CurrentUserContext.getTenantId();
        // 幂等：同一市场已生成 DRAFT 则直接返回
        LambdaQueryWrapper<AiSkill> existWrapper = new LambdaQueryWrapper<AiSkill>()
                .eq(AiSkill::getTenantId, tenantId)
                .eq(AiSkill::getMarketId, marketId)
                .eq(AiSkill::getStatus, AiSkill.STATUS_DRAFT)
                .last("LIMIT 1");
        AiSkill existing = skillMapper.selectOne(existWrapper);
        if (existing != null) {
            log.info("skill fromMarket idempotent hit: marketId={} existingId={}", marketId, existing.getId());
            return existing;
        }
        AiSkill skill = new AiSkill();
        skill.setId(IdGenerator.nextId());
        skill.setTenantId(tenantId);
        skill.setCreatedBy(CurrentUserContext.getUserId());
        String suffix = marketId.length() <= 8 ? marketId.toLowerCase() : marketId.substring(0, 8).toLowerCase();
        skill.setSkillCode("market-" + suffix);
        skill.setSkillName("Market Skill " + suffix);
        skill.setSkillType(AiSkill.TYPE_CUSTOM);
        skill.setMarketId(marketId);
        // scriptRef 占位为空，标识需后续绑定脚本路径或对象存储 key
        skill.setScriptRef(null);
        skill.setStatus(AiSkill.STATUS_DRAFT);
        if (skill.getVersionNo() == null) skill.setVersionNo(1);
        skill.setVersion(0);
        try {
            skillMapper.insert(skill);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发幂等：再次查询返回
            AiSkill dup = skillMapper.selectOne(existWrapper);
            if (dup != null) return dup;
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "Skill 编码已存在: " + skill.getSkillCode());
        }
        log.info("skill fromMarket created: id={} marketId={} code={}", skill.getId(), marketId, skill.getSkillCode());
        return skill;
    }

    private String loadSkillMd(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return null;
        }
        String path = sourcePath.trim();
        if (path.startsWith("classpath:")) {
            path = path.substring("classpath:".length());
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            try (var in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
                if (in == null) {
                    log.warn("skill md classpath miss: {}", path);
                    return null;
                }
                return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("skill md classpath read failed: {}", path, e);
                return null;
            }
        }
        log.warn("skill activate: unsupported sourcePath scheme: {}", sourcePath);
        return null;
    }

    private void cacheActivated(AiSkill skill) {
        activatedCache.put(cacheKey(skill.getTenantId(), skill.getSkillCode()), skill);
    }

    private String cacheKey(String tenantId, String skillCode) {
        return tenantId + ":" + skillCode;
    }

    private String parseSkillMdSummary(String skillMd) {
        if (skillMd == null) return "";
        for (String line : skillMd.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#") && !t.startsWith(">")) {
                return t.length() > 120 ? t.substring(0, 120) : t;
            }
        }
        // fallback: 首个非空行
        for (String line : skillMd.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty()) return t.length() > 120 ? t.substring(0, 120) : t;
        }
        return "";
    }

    private void applyDataScope(LambdaQueryWrapper<AiSkill> wrapper, DataScope scope) {
        if (scope == null) return;
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) return;
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.eq(AiSkill::getCreatedBy, userId);
    }

    private void checkVersion(Integer requestVersion, Integer currentVersion) {
        if (requestVersion == null || !requestVersion.equals(currentVersion)) {
            throw new BusinessConflictException("版本号不匹配，请求版本=" + requestVersion + ", 当前版本=" + currentVersion);
        }
    }
}
