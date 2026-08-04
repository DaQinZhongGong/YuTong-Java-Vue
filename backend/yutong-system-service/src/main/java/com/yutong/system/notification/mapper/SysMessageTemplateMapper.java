package com.yutong.system.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.system.notification.domain.SysMessageTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息模板 Mapper。GA2-40 实时通知 P2 落地。
 */
@Mapper
public interface SysMessageTemplateMapper extends BaseMapper<SysMessageTemplate> {
}
