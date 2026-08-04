package com.yutong.system.license.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 额度使用统计实体。设计来源: 70-商业授权与版本能力裁剪详设「授权运行数据模型」、V024__init_license_tables.sql
 *
 * <p>记录 AI token、低代码生成、报表导出等额度按租户/周期统计。
 * 高并发场景由 Redis 原子计数，定期落账到本表。
 *
 * <p>GA2-L170 落地: 关闭 DEV-L170-002 偏差。
 */
@Getter
@Setter
@TableName("sys_license_usage")
public class SysLicenseUsage extends BaseEntity {

    /** 额度编码，如 ai.monthly.tokens / lowcode.generate.count */
    private String quotaCode;

    /** 统计周期，例如 2026-07（按租户时区，默认 Asia/Shanghai） */
    private String usagePeriod;

    /** 已使用量（高并发场景由 Redis 原子计数，定期落账） */
    private BigDecimal usedAmount;

    /** 授权上限（来自 sys_license.limits_json） */
    private BigDecimal limitAmount;

    /** 最近使用时间 */
    private OffsetDateTime lastUsedTime;
}
