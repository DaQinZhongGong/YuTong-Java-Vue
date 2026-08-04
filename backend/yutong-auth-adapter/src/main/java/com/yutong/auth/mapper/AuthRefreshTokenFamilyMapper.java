package com.yutong.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.auth.domain.AuthRefreshTokenFamily;
import org.apache.ibatis.annotations.Mapper;

/**
 * Refresh Token 家族 Mapper。设计来源: 32-企业级权限与租户接入方案 auth_refresh_token_family
 */
@Mapper
public interface AuthRefreshTokenFamilyMapper extends BaseMapper<AuthRefreshTokenFamily> {
}
