package com.yutong.sample.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.sample.report.domain.RptReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 报表定义 Mapper。设计来源: 42-报表与大屏可视化设计。
 */
@Mapper
public interface RptReportMapper extends BaseMapper<RptReport> {
}
