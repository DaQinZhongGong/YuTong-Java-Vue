package com.yutong.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.auth.domain.AuthEmergencyAdmin;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应急管理员 Mapper。设计来源: 32-企业级权限与租户接入方案 auth_emergency_admin
 */
@Mapper
public interface AuthEmergencyAdminMapper extends BaseMapper<AuthEmergencyAdmin> {
}
