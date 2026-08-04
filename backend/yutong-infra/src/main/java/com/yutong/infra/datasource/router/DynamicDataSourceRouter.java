package com.yutong.infra.datasource.router;

import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.function.Supplier;

/**
 * 动态数据源路由器实现。设计来源: 46-多数据源与数据集设计 DynamicDataSourceRouter。
 * <p>
 * GA2-46 v1.5 实现细节:
 * <ul>
 *   <li>基于 ThreadLocal 切换数据源编码, route 方法在 action 执行期间设置/清理</li>
 *   <li>事务内禁止切换数据源 (46 号文档 line 67 硬约束)</li>
 *   <li>try/finally 清理 ThreadLocal (46 号文档 line 70)</li>
 *   <li>通过 DataSourceManager.getDataSource 获取真实 DataSource</li>
 * </ul>
 */
@Component
public class DynamicDataSourceRouter implements DataSourceRouter {

    private static final Logger log = LoggerFactory.getLogger(DynamicDataSourceRouter.class);

    private static final ThreadLocal<String> CURRENT_DS = new ThreadLocal<>();

    private final DataSourceManager dataSourceManager;

    public DynamicDataSourceRouter(DataSourceManager dataSourceManager) {
        this.dataSourceManager = dataSourceManager;
    }

    @Override
    public void route(String datasourceCode, Runnable action) {
        route(datasourceCode, () -> {
            action.run();
            return null;
        });
    }

    @Override
    public <T> T route(String datasourceCode, Supplier<T> action) {
        // 46 号文档 line 67: 事务内禁止切换数据源
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            String current = CURRENT_DS.get();
            if (current != null && !current.equals(datasourceCode)) {
                throw new BusinessException(ErrorCode.DS_PARAMETER_INVALID,
                        "事务内禁止切换数据源 (当前=" + current + ", 目标=" + datasourceCode + ")");
            }
        }
        String previous = CURRENT_DS.get();
        CURRENT_DS.set(datasourceCode);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT_DS.remove();
            } else {
                CURRENT_DS.set(previous);
            }
        }
    }

    @Override
    public String currentDatasourceCode() {
        String code = CURRENT_DS.get();
        return code != null ? code : DataSourceManager.PRIMARY_CODE;
    }

    /**
     * 获取当前 ThreadLocal 数据源编码对应的 DataSource。
     * 若 ThreadLocal 为空, 返回主库 DataSource。
     *
     * @return 当前数据源
     */
    public DataSource currentDataSource() {
        return dataSourceManager.getDataSource(currentDatasourceCode());
    }
}
