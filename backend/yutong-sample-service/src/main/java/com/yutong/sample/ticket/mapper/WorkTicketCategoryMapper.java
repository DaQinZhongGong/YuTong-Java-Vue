package com.yutong.sample.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.sample.ticket.domain.WorkTicketCategory;
import org.apache.ibatis.annotations.Mapper;

/** 工单分类 Mapper */
@Mapper
public interface WorkTicketCategoryMapper extends BaseMapper<WorkTicketCategory> {
}
