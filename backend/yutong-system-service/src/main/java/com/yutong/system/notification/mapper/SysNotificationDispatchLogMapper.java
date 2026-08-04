package com.yutong.system.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.system.notification.domain.SysNotificationDispatchLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知分发日志 Mapper。GA2-40 实时通知 P2 落地。
 */
@Mapper
public interface SysNotificationDispatchLogMapper extends BaseMapper<SysNotificationDispatchLog> {
}
