package com.yutong.system.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.system.log.domain.SysLoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录审计日志 Mapper。设计来源: 67-数据权限与审计日志详设 sys_login_log
 * GA2-L175 落地: 配合 {@link com.yutong.system.log.service.LoginAuditService} 写入登录审计。
 */
@Mapper
public interface SysLoginLogMapper extends BaseMapper<SysLoginLog> {
}
