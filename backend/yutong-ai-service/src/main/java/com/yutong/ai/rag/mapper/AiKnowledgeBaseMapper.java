package com.yutong.ai.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.ai.rag.domain.AiKnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

/** 知识库 Mapper。设计来源: 98-后端实现蓝图 MyBatis-Plus BaseMapper */
@Mapper
public interface AiKnowledgeBaseMapper extends BaseMapper<AiKnowledgeBase> {
}
