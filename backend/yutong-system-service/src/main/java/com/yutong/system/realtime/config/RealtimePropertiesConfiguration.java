package com.yutong.system.realtime.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 实时推送属性注册配置。设计来源: 44-实时通信与消息推送设计
 *
 * <p>GA2-L180 修复: 独立于 {@link WebSocketConfig} 注册 {@link RealtimeProperties}。
 *
 * <p>背景: WebSocketConfig 带 {@code @ConditionalOnProperty(yutong.realtime.push.enabled=true)}，
 * 当 realtime.push.enabled=false（例如 CT 契约测试环境）时 WebSocketConfig 不加载，
 * 进而 {@code @EnableConfigurationProperties(RealtimeProperties.class)} 不生效，
 * 导致 {@link com.yutong.system.realtime.service.PushService}（@Service 无条件注册）注入失败。
 *
 * <p>本配置类无条件加载，仅用于把 RealtimeProperties 注册为 Bean。
 * PushService 内部已在 {@code init()} 与 {@code push()} 中对 {@code enabled=false} 做优雅 no-op 处理，
 * 因此 RealtimeProperties 始终注册不影响业务语义，仍符合「关闭时无损回退 REST 拉取」的设计约束。
 */
@Configuration
@EnableConfigurationProperties(RealtimeProperties.class)
public class RealtimePropertiesConfiguration {
}
