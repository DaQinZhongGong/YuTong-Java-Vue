package com.yutong.ai.gateway.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.ai.gateway.domain.AiToolCallLog;
import org.apache.ibatis.annotations.Mapper;

/** 工具调用审计日志 Mapper。设计来源: 98-后端实现蓝图 MyBatis-Plus BaseMapper */
@Mapper
public interface AiToolCallLogMapper extends BaseMapper<AiToolCallLog> {
}
