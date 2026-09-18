package com.yutong.ai.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.ai.agent.domain.AiAgent;
import org.apache.ibatis.annotations.Mapper;

/** AiAgent Mapper。设计来源: V039 ai_agent */
@Mapper
public interface AiAgentMapper extends BaseMapper<AiAgent> {
}
