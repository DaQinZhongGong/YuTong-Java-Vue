package com.yutong.ai.memory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.ai.memory.domain.AiMemory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiMemoryMapper extends BaseMapper<AiMemory> {
}
