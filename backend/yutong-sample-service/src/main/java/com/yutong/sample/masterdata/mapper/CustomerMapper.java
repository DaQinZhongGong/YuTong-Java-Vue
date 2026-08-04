package com.yutong.sample.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.sample.masterdata.domain.Customer;
import org.apache.ibatis.annotations.Mapper;

/** 客户 Mapper。设计来源: 98-后端实现蓝图 MyBatis-Plus BaseMapper */
@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
}
