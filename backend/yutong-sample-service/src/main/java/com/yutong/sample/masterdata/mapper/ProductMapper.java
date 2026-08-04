package com.yutong.sample.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.sample.masterdata.domain.Product;
import org.apache.ibatis.annotations.Mapper;

/** 商品 Mapper。设计来源: 98-后端实现蓝图 MyBatis-Plus BaseMapper */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
