package com.yutong.ai.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.ai.rag.domain.AiDocumentChunk;
import org.apache.ibatis.annotations.Mapper;

/** 文档分块 Mapper。设计来源: 98-后端实现蓝图 MyBatis-Plus BaseMapper */
@Mapper
public interface AiDocumentChunkMapper extends BaseMapper<AiDocumentChunk> {
}
