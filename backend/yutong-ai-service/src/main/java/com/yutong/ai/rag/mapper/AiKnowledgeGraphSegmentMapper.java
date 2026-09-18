package com.yutong.ai.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.ai.rag.domain.AiKnowledgeGraphSegment;
import org.apache.ibatis.annotations.Mapper;

/** 知识图谱分段 Mapper。设计来源: V043 ai_knowledge_graph_segment */
@Mapper
public interface AiKnowledgeGraphSegmentMapper extends BaseMapper<AiKnowledgeGraphSegment> {
}
