package com.yutong.sample.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.sample.contract.domain.ContractApproval;
import org.apache.ibatis.annotations.Mapper;

/** 合同审批记录 Mapper */
@Mapper
public interface ContractApprovalMapper extends BaseMapper<ContractApproval> {
}
