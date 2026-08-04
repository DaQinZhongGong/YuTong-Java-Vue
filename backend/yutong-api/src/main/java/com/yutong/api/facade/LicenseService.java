package com.yutong.api.facade;

import com.yutong.api.dto.LicenseInfo;

/**
 * 授权校验 Facade — 跨模块调用商业授权校验的统一契约。
 *
 * <p>设计来源: 70-商业授权与版本能力裁剪详设「授权校验接口」、
 * 98-后端实现蓝图与代码骨架详设（boot/cloud Facade 装配规范）。
 *
 * <p>装配规则:
 * <ul>
 *   <li>boot 模式: {@code LocalLicenseService} (@Profile("!cloud")) 本地 Bean，
 *       直接查询 sys_license 表 + 读取 yutong.modules 配置</li>
 *   <li>cloud 模式: {@code FeignLicenseService} (@Profile("cloud")) 通过 Feign 调用 license-service (v1.1+)</li>
 * </ul>
 *
 * <p>使用方: yutong-lowcode-service/ai-service/report-service/workflow-service 等业务模块
 * 在受控模块入口调用 {@link #hasModule(String)} 判断模块是否授权，
 * 在额度消耗前调用 {@link #checkQuota(String, long)} 校验额度。
 *
 * <p>规则（70 号文档）:
 * <ul>
 *   <li>模块开关只影响菜单、路由、接口访问和初始化数据，不允许在业务核心代码中散落 if/else</li>
 *   <li>额度扣减必须先检查再扣减；扣减失败不得执行业务动作</li>
 *   <li>所有直连 API 拒绝必须写授权审计，包含 moduleCode、edition、licenseId、userIdHash、tenantIdHash、traceId</li>
 * </ul>
 *
 * <p>容错: 第一版未实现 License Server 时，{@link #current()} 返回降级 Community 实例，
 * 保证基础能力可用，仅商业模块被拦截；审计日志仍正常写入。
 */
public interface LicenseService {

    /**
     * 获取当前授权信息。
     *
     * <p>查询 sys_license 表当前租户的 ACTIVE License 记录；
     * 若无记录（第一版未实现 License Server），返回降级 Community 实例
     * （{@code fallbackCommunity=true}，modules 为基础模块集，limits 为 Community 默认额度）。
     *
     * @return 授权信息（永不为 null，降级时返回 Community 实例）
     */
    LicenseInfo current();

    /**
     * 判断指定模块是否已授权且开关开启。
     *
     * <p>同时满足两个条件才返回 true:
     * <ol>
     *   <li>当前 License 的 modules 列表包含该 moduleCode</li>
     *   <li>application.yml 中 {@code yutong.modules.{moduleCode}} 开关为 true（默认 true）</li>
     * </ol>
     *
     * <p>规则（70 号文档）: 模块开关只影响菜单、路由、接口访问和初始化数据，
     * 不允许在业务核心代码中散落 if/else。本方法供拦截器/Starter/FeatureFlag 调用。
     *
     * @param moduleCode 模块编码（lowcode/ai/report/workflow/datasource/plugin）
     * @return true 表示模块已授权且开关开启
     */
    boolean hasModule(String moduleCode);

    /**
     * 校验当前周期内指定额度是否还可消耗指定数量。
     *
     * <p>查询 sys_license_usage 表当前租户 + quotaCode + usagePeriod 的已使用量，
     * 加上本次 amount 后是否超过 License limits 中对应的上限。
     *
     * <p>规则（70 号文档）:
     * <ul>
     *   <li>额度扣减必须先检查再扣减；扣减失败不得执行业务动作</li>
     *   <li>对高并发额度使用，优先使用 Redis 原子计数 + PostgreSQL 定期落账</li>
     *   <li>AI 流式调用在开始时预占额度，在结束后按实际 token 结算，多退少补</li>
     *   <li>额度统计周期以租户时区为准，默认 Asia/Shanghai</li>
     * </ul>
     *
     * @param quotaCode 额度编码（如 ai.monthly.tokens / lowcode.generate.count / report.export.count）
     * @param amount    本次拟消耗数量（必须 &gt;= 0）
     * @throws com.yutong.common.exception.BusinessException 当额度不足时抛出 {@code LIC-429001} (license.error.quotaExceeded)
     */
    void checkQuota(String quotaCode, long amount);

    /**
     * 记录额度使用量（扣减额度）。
     *
     * <p>在业务动作执行成功后调用，将本次消耗量累加到 sys_license_usage 表。
     * 与 {@link #checkQuota(String, long)} 配合使用: 先 checkQuota 校验，业务成功后 recordQuotaUsage 扣减。
     *
     * <p>规则（70 号文档「额度扣减规则」）:
     * <ul>
     *   <li>额度扣减必须先检查再扣减；扣减失败不得执行业务动作</li>
     *   <li>AI 流式调用在开始时预占额度，在结束后按实际 token 结算，多退少补</li>
     *   <li>额度统计周期以租户时区为准，默认 Asia/Shanghai</li>
     * </ul>
     *
     * <p>容错: 记录失败不阻断业务流程（仅记录 WARN 日志），避免额度统计故障影响核心业务。
     *
     * @param quotaCode 额度编码（如 ai.monthly.tokens / lowcode.generate.count / report.export.count）
     * @param amount    本次实际消耗数量（必须 &gt;= 0）
     */
    void recordQuotaUsage(String quotaCode, long amount);
}
