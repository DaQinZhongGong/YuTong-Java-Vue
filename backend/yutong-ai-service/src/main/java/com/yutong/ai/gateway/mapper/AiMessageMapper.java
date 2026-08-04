package com.yutong.ai.gateway.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.ai.gateway.domain.AiMessage;
import org.apache.ibatis.annotations.Mapper;

/** AI 会话消息 Mapper。设计来源: 98-后端实现蓝图 MyBatis-Plus BaseMapper */
@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {
}
