package com.yutong.system.license.runner;

import com.yutong.api.dto.LicenseInfo;
import com.yutong.api.facade.LicenseService;
import com.yutong.system.license.service.LocalLicenseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * License 启动校验 Runner。设计来源: 70-商业授权与版本能力裁剪详设「授权校验流程 - 启动校验」。
 *
 * <p>触发时机: 应用启动、License 加载。
 * 校验内容: 签名（v1.1+）、到期时间、deploymentId（v1.1+）、版本。
 * 失败处理: 启动告警，禁用商业模块或进入只读模式（70 号文档「失败与降级策略」）。
 *
 * <p>第一版落地范围（GA2-L174）:
 * <ul>
 *   <li>启动时调用 {@link LicenseService#current()} 加载 License</li>
 *   <li>校验到期时间：已过期则记录 WARN 并降级 Community（{@link LocalLicenseService#current()} 已自动降级）</li>
 *   <li>记录审计 action=LOAD，结果 SUCCESS（正常加载）/ WARN（降级模式）/ FAILURE（异常）</li>
 *   <li>启动日志输出当前版本/主体/模块清单/降级模式</li>
 *   <li>启动校验失败不阻断应用启动（与降级策略一致，保证系统可用性）</li>
 * </ul>
 *
 * <p>v1.1+ 待落地: 签名验证（RSA 公钥）+ deploymentId 匹配校验 + License Server 远程拉取。
 *
 * <p>装配规则:
 * <ul>
 *   <li>{@code @Profile("!cloud")} — 与 LocalLicenseService 一致，cloud 模式由 FeignLicenseService 接管</li>
 *   <li>{@code @Order(LATE_ORDER)} — 在所有 Bean 初始化后执行，避免影响启动流程</li>
 *   <li>{@code @Component} — Spring 自动扫描注册</li>
 * </ul>
 *
 * <p>GA2-L174 落地: 70 号文档「授权校验流程」4 时机之「启动校验」补齐。
 * 关闭 writeAudit action=LOAD 从未触发的问题（原仅 DENY/QUOTA_EXCEEDED 被调用）。
 */
@Component
@Profile("!cloud")
@Order(LicenseStartupRunner.LATE_ORDER)
public class LicenseStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LicenseStartupRunner.class);

    /** 延迟执行顺序，确保在所有 Bean 初始化后执行 */
    public static final int LATE_ORDER = 100;

    /** 到期预警阈值（剩余天数 < 30 天时 WARN） */
    private static final long EXPIRY_WARNING_DAYS = 30L;

    private final LicenseService licenseService;
    private final LocalLicenseService localLicenseService;

    public LicenseStartupRunner(LicenseService licenseService,
                                 LocalLicenseService localLicenseService) {
        this.licenseService = licenseService;
        this.localLicenseService = localLicenseService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            LicenseInfo info = licenseService.current();
            if (info.fallbackCommunity()) {
                // 无 ACTIVE License 记录，降级 Community 模式
                log.warn("[License 启动校验] 降级到 Community 模式: 无 ACTIVE License 记录，商业模块（ai/report/workflow/datasource/plugin）将被拦截");
                localLicenseService.writeAudit("LOAD", null, null, "WARN", null,
                        "{\"reason\":\"NO_ACTIVE_LICENSE\",\"edition\":\"Community\",\"fallback\":true}");
            } else {
                // 正常加载 License，校验到期时间
                String expiryDetail = buildExpiryDetail(info);
                boolean expired = isExpired(info);
                boolean nearExpiry = isNearExpiry(info);

                if (expired) {
                    // 已过期（理论上 current() 已降级，但防御性检查）
                    log.error("[License 启动校验] License 已过期: licenseId={} edition={} expireTime={} - 商业模块将被拦截",
                            info.licenseId(), info.edition(), info.expireTime());
                    localLicenseService.writeAudit("LOAD", null, null, "FAILURE", "LIC-403001",
                            "{\"licenseId\":\"" + info.licenseId() + "\",\"reason\":\"EXPIRED\",\"expireTime\":\"" + info.expireTime() + "\"}");
                } else if (nearExpiry) {
                    long daysLeft = daysUntilExpiry(info);
                    log.warn("[License 启动校验] License 即将到期: licenseId={} edition={} 剩余 {} 天，请及时续期",
                            info.licenseId(), info.edition(), daysLeft);
                    localLicenseService.writeAudit("LOAD", null, null, "WARN", null, expiryDetail);
                } else {
                    log.info("[License 启动校验] License 加载成功: licenseId={} edition={} subject={} modules={} expireTime={}",
                            info.licenseId(), info.edition(), info.subject(), info.modules(), info.expireTime());
                    localLicenseService.writeAudit("LOAD", null, null, "SUCCESS", null, expiryDetail);
                }
            }
        } catch (Exception e) {
            // 启动校验异常不阻断应用启动，降级 Community 模式
            log.error("[License 启动校验] 启动校验异常，降级 Community 模式 - {}", e.getMessage(), e);
            try {
                localLicenseService.writeAudit("LOAD", null, null, "FAILURE", "LIC-500001",
                        "{\"reason\":\"STARTUP_EXCEPTION\",\"error\":\"" + escape(e.getMessage()) + "\"}");
            } catch (Exception ignored) {
                // 审计写入失败也忽略，保证启动不中断
            }
        }
    }

    /** 判断 License 是否已过期 */
    private boolean isExpired(LicenseInfo info) {
        return info.expireTime() != null && info.expireTime().isBefore(OffsetDateTime.now());
    }

    /** 判断 License 是否即将到期（剩余天数 < 30 天） */
    private boolean isNearExpiry(LicenseInfo info) {
        if (info.expireTime() == null) {
            return false;
        }
        long daysLeft = daysUntilExpiry(info);
        return daysLeft >= 0 && daysLeft < EXPIRY_WARNING_DAYS;
    }

    /** 计算剩余到期天数 */
    private long daysUntilExpiry(LicenseInfo info) {
        if (info.expireTime() == null) {
            return Long.MAX_VALUE;
        }
        return OffsetDateTime.now().until(info.expireTime(), ChronoUnit.DAYS);
    }

    /** 构建到期详情 JSON（供审计日志 detailJson 字段） */
    private String buildExpiryDetail(LicenseInfo info) {
        long daysLeft = daysUntilExpiry(info);
        return String.format(
                "{\"licenseId\":\"%s\",\"edition\":\"%s\",\"subject\":\"%s\",\"modules\":%s,\"expireTime\":\"%s\",\"daysLeft\":%d,\"fallback\":false}",
                info.licenseId(),
                info.edition(),
                escape(info.subject()),
                toJsonArray(info.modules()),
                info.expireTime(),
                daysLeft == Long.MAX_VALUE ? -1 : daysLeft);
    }

    /** 将 List<String> 转换为合法 JSON 数组字符串（每个元素加双引号，避免 List.toString() 产生非法 JSON） */
    private static String toJsonArray(java.util.List<String> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(escape(items.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    /** 简单 JSON 字符串转义（避免引入完整 JSON 库） */
    private static String escape(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
