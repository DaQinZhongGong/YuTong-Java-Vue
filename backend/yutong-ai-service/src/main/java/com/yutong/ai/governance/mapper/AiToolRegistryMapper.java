package com.yutong.ai.governance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.ai.governance.domain.AiToolRegistry;
import org.apache.ibatis.annotations.Mapper;

/** AI 工具注册表 Mapper。设计来源: 37-AI治理与评测设计 */
@Mapper
public interface AiToolRegistryMapper extends BaseMapper<AiToolRegistry> {
}
