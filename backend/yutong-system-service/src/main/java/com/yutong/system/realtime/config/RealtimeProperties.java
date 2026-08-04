package com.yutong.system.realtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 实时推送配置。设计来源: 44-实时通信与消息推送设计
 *
 * <p>由 realtime.push.enabled 渐进启用，关闭或故障时必须无损回退到 REST 拉取（44 号文档 GA 基线）。
 *
 * <p>关键约束:
 * <ul>
 *   <li>心跳: 30s PING/PONG，3 次失败断开</li>
 *   <li>背压: 单连接队列上限 1000，超限丢弃非关键；关键消息保留站内信补偿</li>
 *   <li>租户隔离: 订阅频道由服务端从登录上下文生成，客户端不得自定义跨租户频道</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "yutong.realtime.push")
public class RealtimeProperties {

    /** 实时推送总开关。默认 true 启用，关闭时端侧仅使用 REST 拉取（GA 基线无损失）。 */
    private boolean enabled = true;

    /** WebSocket 端点路径。对齐 44 号文档 boot 模式 /ws/v1，不经过 /api/v1 前缀。 */
    private String endpoint = "/ws/v1";

    /** 单连接发送队列上限（背压）。超过则丢弃非关键消息。对齐 44 号文档 line 127。 */
    private int queueSize = 1000;

    /** 心跳间隔秒数。对齐 44 号文档 line 121（30s PING/PONG）。 */
    private int heartbeatSeconds = 30;

    /** 心跳失败容忍次数。超过即断开连接。对齐 44 号文档 line 121（3 次失败断开）。 */
    private int heartbeatFailThreshold = 3;

    /** Redis PubSub 频道名。cloud 模式多实例广播用。boot 单实例也启用，便于后续平滑扩展。 */
    private String redisChannel = "yutong:realtime:push";

    /** 允许的订阅频道前缀。user: 仅订阅本人；tenant: 订阅租户广播。 */
    private boolean allowTenantSubscribe = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public int getQueueSize() { return queueSize; }
    public void setQueueSize(int queueSize) { this.queueSize = queueSize; }

    public int getHeartbeatSeconds() { return heartbeatSeconds; }
    public void setHeartbeatSeconds(int heartbeatSeconds) { this.heartbeatSeconds = heartbeatSeconds; }

    public int getHeartbeatFailThreshold() { return heartbeatFailThreshold; }
    public void setHeartbeatFailThreshold(int heartbeatFailThreshold) { this.heartbeatFailThreshold = heartbeatFailThreshold; }

    public String getRedisChannel() { return redisChannel; }
    public void setRedisChannel(String redisChannel) { this.redisChannel = redisChannel; }

    public boolean isAllowTenantSubscribe() { return allowTenantSubscribe; }
    public void setAllowTenantSubscribe(boolean allowTenantSubscribe) { this.allowTenantSubscribe = allowTenantSubscribe; }
}
