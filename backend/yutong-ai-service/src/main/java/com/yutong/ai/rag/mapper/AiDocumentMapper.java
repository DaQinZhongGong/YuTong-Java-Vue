package com.yutong.ai.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.ai.rag.domain.AiDocument;
import org.apache.ibatis.annotations.Mapper;

/** 文档 Mapper。设计来源: 98-后端实现蓝图 MyBatis-Plus BaseMapper */
@Mapper
public interface AiDocumentMapper extends BaseMapper<AiDocument> {
}
