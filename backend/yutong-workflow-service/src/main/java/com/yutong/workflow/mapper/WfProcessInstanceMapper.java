package com.yutong.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.workflow.domain.WfProcessInstance;
import org.apache.ibatis.annotations.Mapper;

/** 工作流流程实例 Mapper */
@Mapper
public interface WfProcessInstanceMapper extends BaseMapper<WfProcessInstance> {
}
