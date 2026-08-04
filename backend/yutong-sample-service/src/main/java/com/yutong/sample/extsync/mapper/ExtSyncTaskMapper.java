package com.yutong.sample.extsync.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.sample.extsync.domain.ExtSyncTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 同步任务 Mapper。设计来源: 35-样例业务矩阵扩展设计 P2 外部接口同步。
 */
@Mapper
public interface ExtSyncTaskMapper extends BaseMapper<ExtSyncTask> {
}
