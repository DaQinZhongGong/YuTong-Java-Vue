package com.yutong.infra.datasource.router;

import java.util.function.Supplier;

/**
 * 动态数据源路由器。设计来源: 46-多数据源与数据集设计 DynamicDataSourceRouter。
 * <p>
 * GA2-46 v1.5 落地能力:
 * <ul>
 *   <li>基于 ThreadLocal 的动态路由 (route 方法在 action 执行期间切换数据源)</li>
 *   <li>事务内禁止切换数据源 (46 号文档 line 67 硬约束, 进入事务前必须确定数据源)</li>
 *   <li>从库延迟感知 (lag_threshold_ms 超过则可切回主库, 由调用方判断)</li>
 *   <li>连接池按数据源独立 HikariCP (46 号文档 line 69, 不与主库共用池)</li>
 *   <li>数据源上下文必须使用 try/finally 清理 ThreadLocal (46 号文档 line 70)</li>
 * </ul>
 */
public interface DataSourceRouter {

    /**
     * 在指定数据源上下文中执行 Runnable (无返回值)。
     *
     * @param datasourceCode 数据源编码 (如 primary / report_ro)
     * @param action         要执行的操作
     */
    void route(String datasourceCode, Runnable action);

    /**
     * 在指定数据源上下文中执行 Supplier (有返回值)。
     *
     * @param datasourceCode 数据源编码
     * @param action         要执行的操作
     * @param <T>            返回类型
     * @return action 的返回值
     */
    <T> T route(String datasourceCode, Supplier<T> action);

    /**
     * 获取当前 ThreadLocal 中的数据源编码 (供 JdbcTemplate 等使用)。
     *
     * @return 当前数据源编码, null 表示使用默认主库
     */
    String currentDatasourceCode();
}
