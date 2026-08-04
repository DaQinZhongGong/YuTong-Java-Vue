package com.yutong.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.auth.domain.AuthLoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录日志 Mapper。设计来源: 32-企业级权限与租户接入方案 auth_login_log
 */
@Mapper
public interface AuthLoginLogMapper extends BaseMapper<AuthLoginLog> {
}
