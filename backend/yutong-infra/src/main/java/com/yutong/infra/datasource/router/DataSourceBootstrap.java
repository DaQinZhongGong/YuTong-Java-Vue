package com.yutong.infra.datasource.router;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yutong.infra.datasource.domain.SysDatasource;
import com.yutong.infra.datasource.mapper.SysDatasourceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 数据源启动加载器。
 * <p>
 * GA2-46 v1.5: 应用启动时从 sys_datasource 加载所有 enabled=true 的数据源, 注册到 DataSourceManager。
 * primary 数据源会复用 Spring 主 DataSource, 其他数据源创建独立 HikariDataSource 连接池。
 * <p>
 * 设计来源: 46-多数据源与数据集设计 line 72 (热加载与故障降级)。
 */
@Component
public class DataSourceBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSourceBootstrap.class);

    private final SysDatasourceMapper datasourceMapper;
    private final DataSourceManager dataSourceManager;

    public DataSourceBootstrap(SysDatasourceMapper datasourceMapper,
                                DataSourceManager dataSourceManager) {
        this.datasourceMapper = datasourceMapper;
        this.dataSourceManager = dataSourceManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        LambdaQueryWrapper<SysDatasource> w = new LambdaQueryWrapper<SysDatasource>()
                .eq(SysDatasource::getEnabled, true);
        List<SysDatasource> list = datasourceMapper.selectList(w);
        log.info("GA2-46 启动加载: 发现 {} 个已启用数据源, 开始注册连接池", list.size());
        int success = 0;
        int failed = 0;
        for (SysDatasource ds : list) {
            try {
                dataSourceManager.register(ds);
                success++;
            } catch (Exception e) {
                failed++;
                log.warn("数据源 [{}] 注册失败: {}", ds.getDatasourceCode(), e.getMessage());
                dataSourceManager.updateHealth(ds.getDatasourceCode(),
                        SysDatasource.HEALTH_DOWN, e.getMessage());
            }
        }
        log.info("GA2-46 启动加载完成: {} 成功, {} 失败", success, failed);
    }
}
