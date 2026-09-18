package com.yutong.ai.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.ai.trace.domain.AiTraceNode;
import org.apache.ibatis.annotations.Mapper;

/** 链路节点 Mapper。设计来源: V043 ai_trace_node */
@Mapper
public interface AiTraceNodeMapper extends BaseMapper<AiTraceNode> {
}
