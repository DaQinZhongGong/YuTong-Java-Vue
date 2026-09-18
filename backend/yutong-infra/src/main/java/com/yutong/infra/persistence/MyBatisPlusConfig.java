package com.yutong.infra.persistence;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 * 设计来源: 98-后端实现蓝图与代码骨架详设
 * 插件顺序: 租户行级隔离(首位, MP 强制要求) -> 分页 -> 乐观锁 -> 防全表攻击
 */
@Configuration
@EnableConfigurationProperties(TenantProperties.class)
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(TenantProperties tenantProperties) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // P2-A: 租户行级隔离必须首位；interceptorEnabled=false 时回到显式过滤模式
        if (tenantProperties.isInterceptorEnabled()) {
            interceptor.addInnerInterceptor(
                    new TenantLineInnerInterceptor(new YutongTenantLineHandler(tenantProperties)));
        }
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }
}
