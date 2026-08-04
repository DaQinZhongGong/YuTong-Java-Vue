package com.yutong.system.license.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.system.license.domain.SysLicense;
import org.apache.ibatis.annotations.Mapper;

/**
 * 授权主表 Mapper。设计来源: 70-商业授权与版本能力裁剪详设。
 * GA2-L170 落地: 关闭 DEV-L170-002 偏差。
 */
@Mapper
public interface SysLicenseMapper extends BaseMapper<SysLicense> {
}
