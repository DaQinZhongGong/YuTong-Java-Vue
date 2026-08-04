package com.yutong.ai.governance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.ai.governance.domain.AiEvalResult;
import org.apache.ibatis.annotations.Mapper;

/** AI 评测样本结果 Mapper。设计来源: 37-AI治理与评测设计 */
@Mapper
public interface AiEvalResultMapper extends BaseMapper<AiEvalResult> {
}
