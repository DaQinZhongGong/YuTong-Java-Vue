package com.yutong.ai.harness.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.ai.harness.domain.HarnessSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiHarnessSessionMapper extends BaseMapper<HarnessSession> {

    /**
     * 逻辑删恢复：绕过 @TableLogic（更新条件含 deleted=true）。
     */
    @Update("""
            UPDATE ai_harness_session
               SET deleted = false,
                   updated_time = now(),
                   updated_by = #{updatedBy}
             WHERE id = #{id}
               AND tenant_id = #{tenantId}
               AND deleted = true
            """)
    int restoreById(@Param("id") String id,
                    @Param("tenantId") String tenantId,
                    @Param("updatedBy") String updatedBy);
}
