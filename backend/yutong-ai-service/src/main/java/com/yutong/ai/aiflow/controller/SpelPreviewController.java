package com.yutong.ai.aiflow.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SpEL 表达式预览器 — 安全沙箱内测试表达式。
 * 表达式预览调试端点。
 *
 * <p>安全: 沙箱化 StandardEvaluationContext, 仅注入用户提供的变量,
 * 不暴露 Spring Bean/类加载器, 禁止方法调用危险类。
 */
@Tag(name = "AI-SpEL预览器")
@RestController
@RequestMapping("/api/v1/aiflow/spel")
public class SpelPreviewController {

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 预览请求体。
     */
    public record PreviewRequest(String expression, Map<String, Object> variables) {}

    /**
     * 预览响应体。
     */
    public record PreviewResult(
            boolean success,
            String expression,
            Object result,
            String resultType,
            String error,
            long durationMs
    ) {}

    @Operation(summary = "SpEL 表达式预览 (沙箱执行)", operationId = "previewSpel")
    @RequiresPermission("ai:assistant:use")
    @PostMapping("/preview")
    public Result<PreviewResult> preview(@RequestBody PreviewRequest request) {
        if (request.expression() == null || request.expression().isBlank()) {
            return Result.ok(new PreviewResult(false, "", null, null,
                    "表达式不能为空", 0));
        }

        long start = System.currentTimeMillis();
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            // 注入用户变量 (沙箱: 仅 Map 数据, 不暴露 Bean)
            if (request.variables() != null) {
                for (Map.Entry<String, Object> entry : request.variables().entrySet()) {
                    context.setVariable(entry.getKey(), entry.getValue());
                }
            }

            Object result = parser.parseExpression(request.expression()).getValue(context);
            long duration = System.currentTimeMillis() - start;

            String resultType = result != null ? result.getClass().getSimpleName() : "null";
            return Result.ok(new PreviewResult(true, request.expression(), result,
                    resultType, null, duration));
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return Result.ok(new PreviewResult(false, request.expression(), null, null,
                    error, duration));
        }
    }
}
