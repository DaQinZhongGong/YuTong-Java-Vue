package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-getPlatformHealth 契约测试 (operationId: getPlatformHealth)。
 *
 * <p>契约来源: routes.yaml operationIds: [getPlatformHealth], GET /api/v1/monitor/health
 * <p>控制器: MonitorController.health() - @RequiresPermission("monitor:health:view")
 * <p>权限码: monitor:health:view (仅 admin 通配 * 拥有, biz/approver/viewer 均无)
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功获取平台健康 - admin (200, code=0, status/platform/version/components)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 monitor:health:view, 403 AUTH-403001)</li>
 *   <li>CT-4 traceId 透传 (响应头 + 响应体)</li>
 *   <li>CT-5 组件结构 (App/PostgreSQL/Redis/MinIO/Flyway/RabbitMQ/Nacos/AI Provider)</li>
 *   <li>CT-6 boot profile 下 RabbitMQ/Nacos 为 SKIPPED</li>
 * </ol>
 */
@DisplayName("CT-getPlatformHealth: GET /api/v1/monitor/health 契约测试")
class CTGetPlatformHealthTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/monitor/health";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";

    // ===== CT-1: 成功获取平台健康 - admin =====

    @Test
    @DisplayName("CT-1: 成功获取平台健康 - admin (200, code=0, status/platform/version/components)")
    void testGetPlatformHealthAdmin() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("status"), "应返回 status");
        assertNotNull(data.get("platform"), "应返回 platform");
        assertNotNull(data.get("version"), "应返回 version");
        assertNotNull(data.get("profile"), "应返回 profile");
        assertNotNull(data.get("checkedAt"), "应返回 checkedAt");
        assertTrue(data.get("components").isArray(), "components 应为数组");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testGetPlatformHealthUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 monitor:health:view, 403 AUTH-403001)")
    void testGetPlatformHealthForbidden() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无监控权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: traceId 透传 =====

    @Test
    @DisplayName("CT-4: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)")
    void testGetPlatformHealthTraceIdPropagation() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }

    // ===== CT-5: 组件结构 =====

    @Test
    @DisplayName("CT-5: 组件结构 (App/PostgreSQL/Redis/MinIO/Flyway/RabbitMQ/Nacos/AI Provider)")
    void testGetPlatformHealthComponents() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        ArrayNode components = (ArrayNode) data.get("components");
        assertNotNull(components, "components 不应为 null");
        assertTrue(components.size() >= 8, "应至少包含 8 个组件 (App/PG/Redis/MinIO/Flyway/RabbitMQ/Nacos/AI)");

        // 验证每个组件包含必要字段
        for (JsonNode comp : components) {
            assertNotNull(comp.get("name"), "组件应包含 name");
            assertNotNull(comp.get("status"), "组件应包含 status");
            assertNotNull(comp.get("checkedAt"), "组件应包含 checkedAt");
        }

        // 验证关键组件存在
        java.util.Set<String> names = new java.util.HashSet<>();
        for (JsonNode comp : components) {
            names.add(comp.get("name").asText());
        }
        assertTrue(names.contains("App"), "应包含 App 组件");
        assertTrue(names.contains("PostgreSQL"), "应包含 PostgreSQL 组件");
        assertTrue(names.contains("Redis"), "应包含 Redis 组件");
        assertTrue(names.contains("MinIO"), "应包含 MinIO 组件");
    }

    // ===== CT-6: boot profile 下 RabbitMQ/Nacos 为 SKIPPED =====

    @Test
    @DisplayName("CT-6: boot profile 下 RabbitMQ/Nacos 为 SKIPPED")
    void testGetPlatformHealthOptionalComponentsSkipped() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        // test profile 为 boot 模式, runMode=boot
        assertEquals("boot", data.get("runMode").asText(), "test profile 应为 boot runMode");

        ArrayNode components = (ArrayNode) data.get("components");
        for (JsonNode comp : components) {
            String name = comp.get("name").asText();
            if ("RabbitMQ".equals(name) || "Nacos".equals(name)) {
                assertEquals("SKIPPED", comp.get("status").asText(),
                        "boot profile 下 " + name + " 应为 SKIPPED");
            }
        }
    }
}
