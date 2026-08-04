package com.yutong.system.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 移动推送适配器 (Mock 实现)。设计来源: 35-样例业务矩阵扩展设计 P2 实时通知 + 44-实时通信与消息推送设计
 * <p>
 * GA2-40 落地: 模拟第三方移动推送 SDK（如 APNs/FCM/华为推送/小米推送），用于验证移动端订阅消息能力。
 * <p>
 * Mock 行为：
 * <ul>
 *   <li>deviceToken="mock-token-fail-xxx" → 模拟推送失败，用于测试消息重试</li>
 *   <li>deviceToken="mock-token-admin-001" / "mock-token-biz-001" → 模拟推送成功</li>
 *   <li>所有调用都记录到日志，便于冒烟测试验证</li>
 * </ul>
 * <p>
 * 生产环境替换为真实 SDK 集成时，仅替换本类实现，上层 NotificationOpsApplicationService 无需改动。
 */
@Component
public class MockMobilePushAdapter {

    private static final Logger log = LoggerFactory.getLogger(MockMobilePushAdapter.class);

    /** 失败 token 前缀 (冒烟测试用) */
    public static final String FAIL_TOKEN_PREFIX = "mock-token-fail-";

    private final ObjectMapper objectMapper;

    public MockMobilePushAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 推送消息到移动设备。
     *
     * @param deviceToken 设备 token
     * @param title       标题
     * @param content     内容
     * @param bizType     业务类型 (可选)
     * @param bizId       业务 ID (可选)
     * @return true=推送成功, false=推送失败 (调用方根据返回值更新 dispatch_log 状态)
     */
    public boolean push(String deviceToken, String title, String content, String bizType, String bizId) {
        if (deviceToken == null || deviceToken.isBlank()) {
            log.warn("MockMobilePush: deviceToken 为空, 推送失败 title={}", title);
            return false;
        }

        // 模拟失败场景
        if (deviceToken.startsWith(FAIL_TOKEN_PREFIX)) {
            log.warn("MockMobilePush: 模拟推送失败 deviceToken={} title={}", deviceToken, title);
            return false;
        }

        // 模拟成功场景：构造 payload 并"发送"
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("to", deviceToken);
            payload.put("notification", Map.of(
                    "title", title != null ? title : "",
                    "body", content != null ? content : ""));
            payload.put("data", Map.of(
                    "bizType", bizType != null ? bizType : "",
                    "bizId", bizId != null ? bizId : "",
                    "timestamp", System.currentTimeMillis()));
            String payloadJson = objectMapper.writeValueAsString(payload);
            log.info("MockMobilePush: 推送成功 deviceToken={} title={} payload={}",
                    deviceToken, title, payloadJson);
            return true;
        } catch (Exception e) {
            log.error("MockMobilePush: payload 序列化失败 deviceToken={} - {}", deviceToken, e.getMessage());
            return false;
        }
    }
}
