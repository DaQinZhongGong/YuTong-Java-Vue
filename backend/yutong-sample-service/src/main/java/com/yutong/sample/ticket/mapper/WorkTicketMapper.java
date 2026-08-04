package com.yutong.sample.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.sample.ticket.domain.WorkTicket;
import org.apache.ibatis.annotations.Mapper;

/** 工单 Mapper */
@Mapper
public interface WorkTicketMapper extends BaseMapper<WorkTicket> {
}
