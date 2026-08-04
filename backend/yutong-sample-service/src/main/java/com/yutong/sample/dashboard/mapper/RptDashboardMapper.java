package com.yutong.sample.dashboard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.sample.dashboard.domain.RptDashboard;
import org.apache.ibatis.annotations.Mapper;

/**
 * 大屏定义 Mapper。设计来源: 42-报表与大屏可视化设计。
 */
@Mapper
public interface RptDashboardMapper extends BaseMapper<RptDashboard> {
}
