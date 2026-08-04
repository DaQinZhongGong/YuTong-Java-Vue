package com.yutong.sample.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.sample.report.domain.RptDataset;
import org.apache.ibatis.annotations.Mapper;

/**
 * 报表数据集 Mapper。设计来源: 42-报表与大屏可视化设计。
 * <p>
 * GA2-36 数据集查询走 DatasetEngine 执行 SQL，本 Mapper 仅用于数据集元数据 CRUD。
 */
@Mapper
public interface RptDatasetMapper extends BaseMapper<RptDataset> {
}
