package com.yutong.sample.extsync.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.sample.extsync.domain.ExtSyncError;
import org.apache.ibatis.annotations.Mapper;

/**
 * 同步错误明细 Mapper (死信队列)。设计来源: 35-样例业务矩阵扩展设计 P2 外部接口同步。
 */
@Mapper
public interface ExtSyncErrorMapper extends BaseMapper<ExtSyncError> {
}
