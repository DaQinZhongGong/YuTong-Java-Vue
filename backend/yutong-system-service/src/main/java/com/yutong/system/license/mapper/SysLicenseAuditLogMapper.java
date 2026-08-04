package com.yutong.system.license.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.system.license.domain.SysLicenseAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 授权审计日志 Mapper（不可变审计表，仅 insert + select，禁止 update/delete）。
 * 设计来源: 70-商业授权与版本能力裁剪详设。
 * GA2-L170 落地: 关闭 DEV-L170-002 偏差。
 *
 * <p>注意: 因 {@link SysLicenseAuditLog} 未继承 {@link com.yutong.infra.persistence.BaseEntity}，
 * BaseMapper 的 updateById/deleteById 仍可调用但会因无 deleted/version 列报错，
 * 业务层应仅调用 {@link #insert} 与 select* 方法以保证审计日志不可变。
 */
@Mapper
public interface SysLicenseAuditLogMapper extends BaseMapper<SysLicenseAuditLog> {
}
