package com.yutong.sample.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.sample.contract.domain.ContractAttachment;
import org.apache.ibatis.annotations.Mapper;

/** 合同附件 Mapper */
@Mapper
public interface ContractAttachmentMapper extends BaseMapper<ContractAttachment> {
}
