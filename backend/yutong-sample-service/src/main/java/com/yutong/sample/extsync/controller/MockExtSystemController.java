package com.yutong.sample.extsync.controller;

import com.yutong.common.response.Result;
import com.yutong.sample.extsync.service.ExtSignatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mock 外部系统 Controller。设计来源: 35-样例业务矩阵扩展设计 P2 外部接口同步。
 *
 * <p>用于 GA2-38 端到端验证: 作为同步目标, 提供 Mock 订单数据 API。
 * <p>验证 HMAC-SHA256 签名鉴权 + 请求头校验 + 响应固定 JSON。
 *
 * <p>预期凭据 (与 V014 种子数据对齐):
 * <ul>
 *   <li>mock-erp: accessKey=mock-access-key-001, secretKey=mock-secret-key-001-very-long (验签通过)</li>
 *   <li>mock-crm: accessKey=mock-access-key-002, secretKey=mock-secret-key-002-wrong (验签失败)</li>
 * </ul>
 *
 * <p>响应格式 (数组, 每条记录含 orderNo 字段作为业务键):
 * <pre>
 * [{"orderNo":"MOCK-001","amount":100.00,"status":"PAID",...}, ...]
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/ext/mock")
@Tag(name = "MockExtSystem", description = "Mock 外部系统 (GA2-38 同步目标)")
public class MockExtSystemController {

    private static final Logger log = LoggerFactory.getLogger(MockExtSystemController.class);

    private static final String EXPECTED_ACCESS_KEY = "mock-access-key-001";
    private static final String EXPECTED_SECRET_KEY = "mock-secret-key-001-very-long";

    private final ExtSignatureService signatureService;

    public MockExtSystemController(ExtSignatureService signatureService) {
        this.signatureService = signatureService;
    }

    /**
     * Mock 订单查询接口 (GET)。
     * 校验 HMAC-SHA256 签名, 通过后返回 5 条 Mock 订单数据。
     */
    @GetMapping("/orders")
    @Operation(summary = "Mock 订单查询 (HMAC-SHA256 签名校验)", operationId = "mockExtListOrders")
    public ResponseEntity<Result<List<Object>>> listOrders(HttpServletRequest request) {
        String accessKey = request.getHeader("X-Ext-Access-Key");
        String timestamp = request.getHeader("X-Ext-Timestamp");
        String nonce = request.getHeader("X-Ext-Nonce");
        String signature = request.getHeader("X-Ext-Signature");

        log.info("mock ext listOrders: accessKey={} timestamp={} nonce={} signatureLen={}",
                accessKey, timestamp, nonce, signature == null ? 0 : signature.length());

        if (accessKey == null || timestamp == null || nonce == null || signature == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.fail("AUTH-401001", "missing signature headers", null));
        }

        boolean verified = signatureService.verify("GET", "/orders", accessKey, timestamp, nonce,
                "", signature, EXPECTED_ACCESS_KEY, EXPECTED_SECRET_KEY);
        if (!verified) {
            log.warn("mock ext signature verify failed: accessKey={}", accessKey);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.fail("AUTH-SIGN-FAIL", "signature verification failed", null));
        }

        return ResponseEntity.ok(Result.ok(buildMockOrders()));
    }

    /**
     * Mock 订单创建接口 (POST)。校验签名后返回成功。
     */
    @PostMapping("/orders")
    @Operation(summary = "Mock 订单创建 (HMAC-SHA256 签名校验)", operationId = "mockExtCreateOrder")
    public ResponseEntity<Result<Object>> createOrder(@RequestBody(required = false) String body,
                                                      HttpServletRequest request) {
        String accessKey = request.getHeader("X-Ext-Access-Key");
        String timestamp = request.getHeader("X-Ext-Timestamp");
        String nonce = request.getHeader("X-Ext-Nonce");
        String signature = request.getHeader("X-Ext-Signature");

        if (accessKey == null || timestamp == null || nonce == null || signature == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.fail("AUTH-401001", "missing signature headers", null));
        }

        boolean verified = signatureService.verify("POST", "/orders", accessKey, timestamp, nonce,
                body == null ? "" : body, signature, EXPECTED_ACCESS_KEY, EXPECTED_SECRET_KEY);
        if (!verified) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.fail("AUTH-SIGN-FAIL", "signature verification failed", null));
        }

        return ResponseEntity.ok(Result.ok(buildMockOrder(UUID.randomUUID().toString())));
    }

    private List<Object> buildMockOrders() {
        List<Object> orders = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 1; i <= 5; i++) {
            orders.add(buildMockOrder("MOCK-" + today.toString().replace("-", "") + "-" + String.format("%03d", i)));
        }
        return orders;
    }

    private Object buildMockOrder(String orderNo) {
        java.util.Map<String, Object> order = new java.util.LinkedHashMap<>();
        order.put("orderNo", orderNo);
        order.put("amount", 100.00 * (orderNo.hashCode() % 100 + 1));
        order.put("currency", "CNY");
        order.put("status", "PAID");
        order.put("createdAt", OffsetDateTime.now().toString());
        order.put("customerName", "Mock 客户 " + orderNo);
        order.put("items", 3);
        return order;
    }
}
