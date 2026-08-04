package com.yutong.ai.governance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.ai.governance.domain.AiCostQuotaUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/** AI 成本额度日用量 Mapper。设计来源: GA2-45 成本治理闭环 */
@Mapper
public interface AiCostQuotaUsageMapper extends BaseMapper<AiCostQuotaUsage> {

    /**
     * 统计指定维度当日已用 token 数。
     *
     * @param modelCode 模型编码，为 null 时统计全部模型
     */
    @Select("<script>SELECT COALESCE(SUM(token_used), 0) FROM ai_cost_quota_usage " +
            "WHERE tenant_id = #{tenantId} " +
            "AND quota_scope = #{quotaScope} " +
            "AND scope_key = #{scopeKey} " +
            "AND usage_date = #{usageDate, jdbcType=DATE} " +
            "AND deleted = false " +
            "<if test='modelCode != null'>AND model_code = #{modelCode}</if>" +
            "</script>")
    Long sumTokenUsed(@Param("tenantId") String tenantId,
                      @Param("quotaScope") String quotaScope,
                      @Param("scopeKey") String scopeKey,
                      @Param("modelCode") String modelCode,
                      @Param("usageDate") LocalDate usageDate);
}
