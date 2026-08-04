package com.yutong.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.auth.domain.AuthIdentityProvider;
import org.apache.ibatis.annotations.Mapper;

/**
 * 身份提供商配置 Mapper。设计来源: 32-企业级权限与租户接入方案 auth_identity_provider
 */
@Mapper
public interface AuthIdentityProviderMapper extends BaseMapper<AuthIdentityProvider> {
}
