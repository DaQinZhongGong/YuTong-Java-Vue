package com.yutong.sample.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.sample.contract.domain.ContractTag;
import org.apache.ibatis.annotations.Mapper;

/** 合同标签 Mapper */
@Mapper
public interface ContractTagMapper extends BaseMapper<ContractTag> {
}
