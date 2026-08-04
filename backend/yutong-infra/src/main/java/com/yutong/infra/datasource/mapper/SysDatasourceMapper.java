package com.yutong.infra.datasource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.infra.datasource.domain.SysDatasource;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据源元数据 Mapper。GA2-46 v1.5。
 */
@Mapper
public interface SysDatasourceMapper extends BaseMapper<SysDatasource> {
}
