package com.yutong.infra.datasource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.infra.datasource.domain.SysDatasourceColumnAcl;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据源列级 ACL Mapper。GA2-46 v1.5。
 */
@Mapper
public interface SysDatasourceColumnAclMapper extends BaseMapper<SysDatasourceColumnAcl> {
}
