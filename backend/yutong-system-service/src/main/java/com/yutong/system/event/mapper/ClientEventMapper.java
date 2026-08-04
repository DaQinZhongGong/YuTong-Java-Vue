package com.yutong.system.event.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.system.event.domain.ClientEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 端侧埋点事件 Mapper。设计来源: 94-端侧埋点与体验监控详设
 */
@Mapper
public interface ClientEventMapper extends BaseMapper<ClientEvent> {
}
