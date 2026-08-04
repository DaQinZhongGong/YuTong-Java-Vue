package com.yutong.lowcode.rule.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.lowcode.rule.dto.EvaluateRulesRequest;
import com.yutong.lowcode.rule.dto.EvaluateRulesResponse;
import com.yutong.lowcode.rule.dto.ValidateExpressionRequest;
import com.yutong.lowcode.rule.dto.ValidateExpressionResponse;
import com.yutong.lowcode.rule.engine.ExpressionEngine;
import com.yutong.lowcode.rule.service.RuleEvalResult;
import com.yutong.lowcode.rule.service.RuleEvaluator;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 低代码规则与表达式接口。设计来源: 36-低代码高级能力设计 (GA2-L191 子任务 A)。
 *
 * <p>端点:
 * <ul>
 *   <li>POST /api/v1/lowcode/rules/evaluate           求值规则集合</li>
 *   <li>POST /api/v1/lowcode/rules/validate-expression 校验表达式语法</li>
 * </ul>
 */
@Tag(name = "低代码-规则")
@RestController
@RequestMapping("/api/v1/lowcode/rules")
public class RuleController {

    private final RuleEvaluator evaluator;
    private final ExpressionEngine engine;

    public RuleController(RuleEvaluator evaluator, ExpressionEngine engine) {
        this.evaluator = evaluator;
        this.engine = engine;
    }

    @Operation(summary = "求值规则集合", operationId = "evaluateLowcodeRules")
    @RequiresPermission("lc:rule:eval")
    @Auditable(operationType = "EVALUATE", module = "lowcode", bizType = "rule",
            content = "求值低代码规则集合")
    @PostMapping("/evaluate")
    public Result<EvaluateRulesResponse> evaluate(@Valid @RequestBody EvaluateRulesRequest request) {
        RuleEvalResult result = evaluator.evaluate(request.rules(), request.context());
        return Result.ok(new EvaluateRulesResponse(result), TraceContext.getTraceId());
    }

    @Operation(summary = "校验表达式语法", operationId = "validateLowcodeExpression")
    @RequiresPermission("lc:rule:eval")
    @Auditable(operationType = "VALIDATE", module = "lowcode", bizType = "rule",
            content = "校验低代码表达式语法")
    @PostMapping("/validate-expression")
    public Result<ValidateExpressionResponse> validateExpression(
            @Valid @RequestBody ValidateExpressionRequest request) {
        ExpressionEngine.ValidationResult vr = engine.validate(request.expression());
        return Result.ok(new ValidateExpressionResponse(vr.valid(), vr.error()),
                TraceContext.getTraceId());
    }
}
