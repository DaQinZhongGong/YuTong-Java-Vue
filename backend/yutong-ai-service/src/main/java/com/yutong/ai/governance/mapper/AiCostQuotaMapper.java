package com.yutong.ai.governance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.ai.governance.domain.AiCostQuota;
import org.apache.ibatis.annotations.Mapper;

/** AI 成本额度配置 Mapper。设计来源: 37-AI治理与评测设计 成本治理章节 */
@Mapper
public interface AiCostQuotaMapper extends BaseMapper<AiCostQuota> {
}
