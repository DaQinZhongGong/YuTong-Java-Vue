package com.yutong.ai.harness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.ai.harness.domain.HarnessRun;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiHarnessRunMapper extends BaseMapper<HarnessRun> {
}
