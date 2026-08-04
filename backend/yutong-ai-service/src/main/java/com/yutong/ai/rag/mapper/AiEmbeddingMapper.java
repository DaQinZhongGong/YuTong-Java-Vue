package com.yutong.ai.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.ai.rag.domain.AiEmbedding;
import org.apache.ibatis.annotations.Mapper;

/** 向量嵌入 Mapper。设计来源: 98-后端实现蓝图 MyBatis-Plus BaseMapper
 * 注意: vector(1536) 字段不在实体映射中，向量读写由原生 SQL 处理。 */
@Mapper
public interface AiEmbeddingMapper extends BaseMapper<AiEmbedding> {
}
