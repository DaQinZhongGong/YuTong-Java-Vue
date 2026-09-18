package com.yutong.ai.skill.service;

import com.yutong.ai.skill.domain.AiSkill;
import com.yutong.ai.skill.mapper.AiSkillMapper;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.id.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 租户首次访问时确保内置技能存在且已发布。
 * 文档技能 docx/pdf/xlsx + 编程助手技能 verification/safe-refactoring/repository-investigation
 * (后三者对标 业界同类实现 coding-harness SKILL, 类型 custom, 走 SkillExecutor 通用兜底: 分析走通用摘要,
 * 创建走 .md 回退, 失败关闭, 不执行外部命令)。
 */
@Service
public class BuiltinSkillCatalog {

    private static final Logger log = LoggerFactory.getLogger(BuiltinSkillCatalog.class);

    private static final List<BuiltinDef> BUILTINS = List.of(
            new BuiltinDef("docx", "Word 文档", AiSkill.TYPE_DOCX, """
                    # docx
                    适用场景：创建、分析 Word 文档。
                    输入要求：content 文本；create 时可提供 title/fileName。
                    操作步骤：analyze 统计字词；create 用 POI 生成 docx 并上传 MinIO。
                    输出规范：analyze 返回摘要；create 返回 downloadUrl。
                    限制条件：不执行外部命令；内容上限 200000 字符。
                    """),
            new BuiltinDef("pdf", "PDF 文档", AiSkill.TYPE_PDF, """
                    # pdf
                    适用场景：创建、分析 PDF。
                    输入要求：content 文本；create 时可提供 title/fileName。
                    操作步骤：analyze 统计行数；create 用 PDFBox 生成 PDF 并上传 MinIO。
                    输出规范：analyze 返回摘要；create 返回 downloadUrl。
                    限制条件：Helvetia 字形仅覆盖 ASCII，中文在 PDF 中以 ? 占位并建议改用 docx。
                    """),
            new BuiltinDef("xlsx", "Excel 表格", AiSkill.TYPE_XLSX, """
                    # xlsx
                    适用场景：创建、分析表格。
                    输入要求：content 按行，可用逗号或制表符分列。
                    操作步骤：analyze 统计行数；create 用 POI 生成 xlsx 并上传 MinIO。
                    输出规范：analyze 返回摘要；create 返回 downloadUrl。
                    限制条件：最多 50000 行 / 32 列。
                    """),
            new BuiltinDef("verification", "交付验证", AiSkill.TYPE_CUSTOM, """
                    # verification
                    适用场景：代码变更后证明任务真正完成，而非仪式化收尾。
                    输入要求：任务验收标准 + 相关仓库命令与测试报告。
                    操作步骤：为每条需求建证据矩阵(可观察行为/最简证伪检查/产物/缺口)；
                    边界用例覆盖解析器转义、幂等并发、状态机非法迁移、资源上下界；
                    完成门把关 6 项(需求/计划/构建测试/纯净 diff/安全持久证据/局限声明)。
                    输出规范：带稳定 ID 或产物哈希的证据记录；未验证项显式声明。
                    限制条件：不执行外部命令；模型自信陈述不算证据。
                    全文见 classpath:skills/verification/SKILL.md。
                    """),
            new BuiltinDef("safe-refactoring", "安全重构", AiSkill.TYPE_CUSTOM, """
                    # safe-refactoring
                    适用场景：多文件重命名、组件抽取、状态管理变更、API 迁移等保行为重构。
                    输入要求：需保留的确切行为 + 有意的变更点(如有) + 调用方/序列化形式清单。
                    操作步骤：先定机械验收(编译/聚焦测试/契约测试/diff 审查)；
                    小步窄 diff, 保持兼容适配器；持久化 schema 变更走版本化。
                    输出规范：行为结果 + 兼容决策 + 关键变更路径 + 实际执行的检查。
                    限制条件：不混入无关格式化；不执行外部命令；未验证不宣称保行为。
                    全文见 classpath:skills/safe-refactoring/SKILL.md。
                    """),
            new BuiltinDef("repository-investigation", "仓库调研", AiSkill.TYPE_CUSTOM, """
                    # repository-investigation
                    适用场景：跨模块任务、架构分析、根因定位前的证据地图构建。
                    输入要求：可观察行为 + 约束 + 期望结果(一句内部目标)。
                    操作步骤：先找仓库说明与构建入口；按标识/路由/事件/配置键搜索；
                    追踪一条完整输入→状态→消费者路径；记录归属文件/数据流/不变量/
                    现有测试/未决假设。
                    输出规范：精简可执行的证据地图；证据不足以决策时给显式计划。
                    限制条件：不做通用仓库摘要；不执行外部命令；证据够用即停。
                    全文见 classpath:skills/repository-investigation/SKILL.md。
                    """)
    );

    private final AiSkillMapper skillMapper;

    public BuiltinSkillCatalog(AiSkillMapper skillMapper) {
        this.skillMapper = skillMapper;
    }

    @Transactional
    public int ensurePublished() {
        String tenantId = CurrentUserContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return 0;
        }
        int created = 0;
        for (BuiltinDef def : BUILTINS) {
            Long count = skillMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiSkill>()
                    .eq(AiSkill::getTenantId, tenantId)
                    .eq(AiSkill::getSkillCode, def.code()));
            if (count != null && count > 0) {
                continue;
            }
            AiSkill skill = new AiSkill();
            skill.setId(IdGenerator.nextId());
            skill.setTenantId(tenantId);
            skill.setCreatedBy(CurrentUserContext.getUserId());
            skill.setSkillCode(def.code());
            skill.setSkillName(def.name());
            skill.setSkillType(def.type());
            skill.setSourcePath("classpath:skills/" + def.code() + "/SKILL.md");
            skill.setSkillMd(def.md());
            skill.setVersionNo(1);
            skill.setStatus(AiSkill.STATUS_PUBLISHED);
            skill.setVersion(0);
            skillMapper.insert(skill);
            created++;
            log.info("builtin skill seeded: tenant={} code={}", tenantId, def.code());
        }
        return created;
    }

    private record BuiltinDef(String code, String name, String type, String md) {}
}
