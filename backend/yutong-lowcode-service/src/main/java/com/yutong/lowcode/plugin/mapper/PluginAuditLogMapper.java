package com.yutong.lowcode.plugin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.lowcode.plugin.domain.PluginAuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PluginAuditLogMapper extends BaseMapper<PluginAuditLog> {
}
