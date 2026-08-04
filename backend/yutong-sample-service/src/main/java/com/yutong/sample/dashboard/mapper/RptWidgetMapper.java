package com.yutong.sample.dashboard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.sample.dashboard.domain.RptWidget;
import org.apache.ibatis.annotations.Mapper;

/**
 * Widget 组件 Mapper。设计来源: 42-报表与大屏可视化设计。
 */
@Mapper
public interface RptWidgetMapper extends BaseMapper<RptWidget> {
}
