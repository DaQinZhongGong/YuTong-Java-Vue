package com.yutong.ai.harness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.ai.harness.domain.HarnessEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiHarnessEventMapper extends BaseMapper<HarnessEvent> {

    @Select("SELECT COALESCE(MAX(sequence_no), 0) FROM ai_harness_event WHERE run_id = #{runId}")
    long selectMaxSequence(@Param("runId") String runId);
}
