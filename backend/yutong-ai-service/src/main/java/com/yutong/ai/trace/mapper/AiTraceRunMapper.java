package com.yutong.ai.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.ai.trace.domain.AiTraceRun;
import com.yutong.ai.trace.dto.AiTraceDashboardVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 链路追踪主表 Mapper。设计来源: V043 ai_trace_run
 * <p>M-3 大盘聚合: SQL 侧 GROUP BY, 避免全量拉行 (日期按 DB 时区 DATE() 截断, 见 service 注释)。
 */
@Mapper
public interface AiTraceRunMapper extends BaseMapper<AiTraceRun> {

    /**
     * 按日×状态聚合。
     *
     * @param createdBy 数据权限收敛到本人时传 userId, 否则传 null (不过滤)
     */
    @Select("<script>SELECT DATE(created_time) AS day, status, COUNT(*) AS cnt, " +
            "AVG(latency_ms) AS avgLatency, SUM(latency_ms) AS sumLatency, " +
            "COUNT(latency_ms) AS latencyRuns, MAX(latency_ms) AS maxLatency " +
            "FROM ai_trace_run " +
            "WHERE tenant_id = #{tenantId} " +
            "AND created_time &gt;= #{start} AND created_time &lt; #{end} " +
            "AND deleted = false " +
            "<if test='traceType != null'>AND trace_type = #{traceType}</if>" +
            "<if test='createdBy != null'>AND created_by = #{createdBy}</if>" +
            "GROUP BY DATE(created_time), status " +
            "ORDER BY day</script>")
    List<AiTraceDashboardVO.DailyAggRow> statDaily(@Param("tenantId") String tenantId,
                                                   @Param("start") OffsetDateTime start,
                                                   @Param("end") OffsetDateTime end,
                                                   @Param("traceType") String traceType,
                                                   @Param("createdBy") String createdBy);

    /**
     * 按链路类型聚合。
     */
    @Select("<script>SELECT trace_type AS traceType, COUNT(*) AS total, " +
            "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed, " +
            "AVG(latency_ms) AS avgLatency " +
            "FROM ai_trace_run " +
            "WHERE tenant_id = #{tenantId} " +
            "AND created_time &gt;= #{start} AND created_time &lt; #{end} " +
            "AND deleted = false " +
            "<if test='traceType != null'>AND trace_type = #{traceType}</if>" +
            "<if test='createdBy != null'>AND created_by = #{createdBy}</if>" +
            "GROUP BY trace_type " +
            "ORDER BY total DESC</script>")
    List<AiTraceDashboardVO.TypeAggRow> statByType(@Param("tenantId") String tenantId,
                                                   @Param("start") OffsetDateTime start,
                                                   @Param("end") OffsetDateTime end,
                                                   @Param("traceType") String traceType,
                                                   @Param("createdBy") String createdBy);

    /**
     * 近期失败 (错误表)。
     */
    @Select("<script>SELECT id, trace_type AS traceType, error_message AS errorMessage, " +
            "created_time AS createdTime " +
            "FROM ai_trace_run " +
            "WHERE tenant_id = #{tenantId} " +
            "AND status = 'FAILED' AND deleted = false " +
            "<if test='traceType != null'>AND trace_type = #{traceType}</if>" +
            "<if test='createdBy != null'>AND created_by = #{createdBy}</if>" +
            "ORDER BY created_time DESC " +
            "LIMIT #{limit}</script>")
    List<AiTraceDashboardVO.ErrorItem> recentErrors(@Param("tenantId") String tenantId,
                                                    @Param("traceType") String traceType,
                                                    @Param("createdBy") String createdBy,
                                                    @Param("limit") int limit);
}
