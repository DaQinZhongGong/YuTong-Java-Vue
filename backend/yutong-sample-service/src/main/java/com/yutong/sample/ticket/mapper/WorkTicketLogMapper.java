package com.yutong.sample.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.sample.ticket.domain.WorkTicketLog;
import org.apache.ibatis.annotations.Mapper;

/** 工单处理记录 Mapper */
@Mapper
public interface WorkTicketLogMapper extends BaseMapper<WorkTicketLog> {
}
