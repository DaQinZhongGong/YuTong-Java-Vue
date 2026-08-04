package com.yutong.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.workflow.domain.WfVariableMapping;
import org.apache.ibatis.annotations.Mapper;

/** 工作流变量映射 Mapper */
@Mapper
public interface WfVariableMappingMapper extends BaseMapper<WfVariableMapping> {
}
