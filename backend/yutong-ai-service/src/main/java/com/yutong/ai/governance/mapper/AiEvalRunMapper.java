package com.yutong.ai.governance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.ai.governance.domain.AiEvalRun;
import org.apache.ibatis.annotations.Mapper;

/** AI 评测运行批次 Mapper。设计来源: 37-AI治理与评测设计 AI 发布门禁章节 */
@Mapper
public interface AiEvalRunMapper extends BaseMapper<AiEvalRun> {
}
