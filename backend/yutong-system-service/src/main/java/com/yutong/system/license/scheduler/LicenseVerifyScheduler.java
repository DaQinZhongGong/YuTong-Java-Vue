package com.yutong.system.license.scheduler;

import com.yutong.system.license.service.LocalLicenseService;
import com.yutong.system.license.service.LocalLicenseService.LicenseVerifyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * License 定时校验任务。设计来源: 70-商业授权与版本能力裁剪详设「授权校验流程 - 定时校验」。
 *
 * <p>触发时机: 每天 02:00（避开业务高峰期）。
 * 校验内容: 到期时间、降级模式、即将到期预警。
 * v1.1+ 待落地: 签名验证（RSA 公钥）+ deploymentId 匹配校验 + License Server 远程校验。
 *
 * <p>GA2-L175 落地: 补齐 70 号文档「授权校验流程」4 时机中最后 1 个未落地的「定时校验」时机。
 * 与 {@link com.yutong.system.license.runner.LicenseStartupRunner}（启动校验）、
 * {@link com.yutong.system.license.interceptor.LicenseInterceptor}（请求校验）、
 * {@code LocalLicenseService.refreshLicense()}（手动刷新）共同构成完整 4 时机。
 *
 * <p>装配规则:
 * <ul>
 *   <li>{@code @Profile("!cloud")} — 与 LocalLicenseService 一致，cloud 模式由 License Server 远程校验</li>
 *   <li>{@code @Component} — Spring 自动扫描注册</li>
 *   <li>{@code @Scheduled(cron = "0 0 2 * * *")} — 每天 02:00:00 执行（@EnableScheduling 已在 YutongApplication 启用）</li>
 * </ul>
 *
 * <p>容错策略: 校验异常不抛出，仅记录 ERROR 日志（与 SlaScanScheduler 一致策略），
 * 避免定时任务异常导致 Spring 调度器停止。
 */
@Component
@Profile("!cloud")
public class LicenseVerifyScheduler {

    private static final Logger log = LoggerFactory.getLogger(LicenseVerifyScheduler.class);

    private final LocalLicenseService localLicenseService;

    public LicenseVerifyScheduler(LocalLicenseService localLicenseService) {
        this.localLicenseService = localLicenseService;
    }

    /**
     * 每天 02:00 执行 License 定时校验。
     * cron: 0 0 2 * * *（秒 0 / 分 0 / 时 2 / 日 * / 月 * / 周 *）
     *
     * <p>校验结果状态:
     * <ul>
     *   <li>OK - 校验通过</li>
     *   <li>NEAR_EXPIRY - 即将到期（剩余 &lt; 30 天），WARN</li>
     *   <li>EXPIRED - 已过期，状态已改为 EXPIRED，商业模块将被拦截</li>
     *   <li>NO_LICENSE - 无 ACTIVE License，降级 Community 模式</li>
     *   <li>ERROR - 校验异常</li>
     * </ul>
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void verifyLicense() {
        long startMs = System.currentTimeMillis();
        try {
            log.info("[License 定时校验] 开始执行 (cron: 0 0 2 * * *)");
            LicenseVerifyResult result = localLicenseService.verifyLicense();
            long costMs = System.currentTimeMillis() - startMs;
            log.info("[License 定时校验] 执行完成 status={} licenseId={} edition={} daysLeft={} costMs={}",
                    result.status(), result.licenseId(), result.edition(), result.daysLeft(), costMs);
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startMs;
            // 定时任务异常不抛出，避免影响 Spring 调度器
            log.error("[License 定时校验] 执行异常 costMs={} - {}", costMs, e.getMessage(), e);
        }
    }
}
