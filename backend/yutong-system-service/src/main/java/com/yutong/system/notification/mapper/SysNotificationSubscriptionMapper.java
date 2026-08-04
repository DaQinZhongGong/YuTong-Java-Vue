package com.yutong.system.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.system.notification.domain.SysNotificationSubscription;
import org.apache.ibatis.annotations.Mapper;

/**
 * 移动端订阅 Mapper。GA2-40 实时通知 P2 落地。
 */
@Mapper
public interface SysNotificationSubscriptionMapper extends BaseMapper<SysNotificationSubscription> {
}
