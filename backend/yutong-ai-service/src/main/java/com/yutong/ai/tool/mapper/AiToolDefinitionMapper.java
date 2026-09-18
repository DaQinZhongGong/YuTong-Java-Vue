package com.yutong.ai.tool.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.ai.tool.domain.AiToolDefinition;
import org.apache.ibatis.annotations.Mapper;

/** 本地工具定义 Mapper。设计来源: V038 ai_tool_definition */
@Mapper
public interface AiToolDefinitionMapper extends BaseMapper<AiToolDefinition> {
}
