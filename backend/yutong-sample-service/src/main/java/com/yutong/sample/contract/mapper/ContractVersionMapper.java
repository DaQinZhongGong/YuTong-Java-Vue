package com.yutong.sample.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.sample.contract.domain.ContractVersion;
import org.apache.ibatis.annotations.Mapper;

/** 合同版本 Mapper */
@Mapper
public interface ContractVersionMapper extends BaseMapper<ContractVersion> {
}
