package com.yutong.ai.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.ai.rag.domain.AiKnowledgeGraph;
import org.apache.ibatis.annotations.Mapper;

/** 知识图谱 Mapper。设计来源: V043 ai_knowledge_graph */
@Mapper
public interface AiKnowledgeGraphMapper extends BaseMapper<AiKnowledgeGraph> {
}
