package com.yutong.lowcode.copilot.service;

import com.yutong.lowcode.copilot.dto.CopilotGenerateRequest;
import com.yutong.lowcode.copilot.dto.CopilotGenerateResponse;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.util.*;

/**
 * 编程助手 Copilot 占位服务 — 基于 prompt 模板生成 form/page 草稿。
 * 设计来源: Phase 7 低代码 copilot mock, 复用 FreeMarker 现有配置
 * 首版无外部 AI 调用，纯本地模板规则生成，可视化预览实时生效。
 */
@Service
public class CopilotService {

    private static final Logger log = LoggerFactory.getLogger(CopilotService.class);

    private final Configuration freemarker;

    public CopilotService(Configuration freemarker) {
        this.freemarker = freemarker;
    }

    public CopilotGenerateResponse generate(CopilotGenerateRequest request) {
        String prompt = request.getPrompt() == null ? "" : request.getPrompt().trim();
        String targetType = normalizeTarget(request.getTargetType());
        String lower = prompt.toLowerCase(Locale.ROOT);

        // 启发式字段推断 (mock)
        List<Map<String, Object>> fields = inferFields(lower, prompt);

        String suggestedCode = suggestCode(prompt, targetType);
        String suggestedName = suggestName(prompt);

        // 尝试用 FreeMarker 渲染 form.vue 模板作 layoutJson 示例 (若模板存在)
        String layoutJson = buildLayoutJson(targetType, suggestedCode, suggestedName, fields, prompt);

        CopilotGenerateResponse resp = new CopilotGenerateResponse();
        resp.setTargetType(targetType);
        resp.setSuggestedCode(suggestedCode);
        resp.setSuggestedName(suggestedName);
        resp.setFields(fields);
        resp.setLayoutJson(layoutJson);
        resp.setMock(true);
        resp.setMessage("Mock 生成完成 — 草稿预览，非直接落库 (Phase 7 占位)");
        return resp;
    }

    private String normalizeTarget(String t) {
        if (t == null || t.isBlank()) return "form";
        String v = t.toLowerCase(Locale.ROOT);
        if (Set.of("form", "page", "entity", "list", "detail").contains(v)) {
            if ("list".equals(v)) return "page";
            if ("detail".equals(v)) return "page";
            return v;
        }
        return "form";
    }

    private List<Map<String, Object>> inferFields(String lower, String original) {
        List<Map<String, Object>> fields = new ArrayList<>();
        // 通用启发式
        if (lower.contains("客户") || lower.contains("customer")) {
            fields.add(field("customerName", "客户名称", "input", true));
            fields.add(field("customerPhone", "联系电话", "input", false));
            fields.add(field("customerEmail", "邮箱", "input", false));
        }
        if (lower.contains("订单") || lower.contains("order")) {
            fields.add(field("orderNo", "订单号", "input", true));
            fields.add(field("orderAmount", "订单金额", "number", true));
            fields.add(field("orderStatus", "订单状态", "select", false));
        }
        if (lower.contains("合同") || lower.contains("contract")) {
            fields.add(field("contractNo", "合同编号", "input", true));
            fields.add(field("contractAmount", "合同金额", "number", true));
            fields.add(field("signDate", "签订日期", "date", false));
        }
        if (lower.contains("审批") || lower.contains("approv")) {
            fields.add(field("approvalOpinion", "审批意见", "textarea", false));
            fields.add(field("approvalResult", "审批结果", "select", true));
        }
        if (lower.contains("短剧") || lower.contains("drama") || lower.contains("剧本")) {
            fields.add(field("title", "剧名", "input", true));
            fields.add(field("synopsis", "梗概", "textarea", false));
            fields.add(field("sceneNo", "场次", "number", true));
        }
        if (fields.isEmpty()) {
            // 通用兜底 — 从 prompt 抽 2-3 个字段
            fields.add(field("title", "标题", "input", true));
            fields.add(field("description", "描述", "textarea", false));
            fields.add(field("status", "状态", "select", false));
            fields.add(field("remark", "备注", "textarea", false));
        }
        // 追加 trace: 标记来源为 copilot mock
        return fields;
    }

    private Map<String, Object> field(String code, String name, String component, boolean required) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fieldCode", code);
        m.put("fieldName", name);
        m.put("component", component);
        m.put("required", required);
        m.put("source", "copilot-mock");
        return m;
    }

    private String suggestCode(String prompt, String targetType) {
        String base = prompt.replaceAll("[^a-zA-Z0-9\u4e00-\u9fa5]", " ").trim().split("\\s+")[0];
        if (base == null || base.isBlank()) base = "mock";
        // 简单拼音/英文规范化
        String code = base.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        if (code.isBlank()) code = "mock";
        if (code.length() > 20) code = code.substring(0, 20);
        return targetType + "_" + code;
    }

    private String suggestName(String prompt) {
        String s = prompt.trim();
        if (s.length() > 30) s = s.substring(0, 30) + "...";
        return s;
    }

    private String buildLayoutJson(String targetType, String code, String name,
                                   List<Map<String, Object>> fields, String prompt) {
        // 优先尝试 FreeMarker 轻量渲染; 失败则回落 JSON 手写
        try {
            // 使用内联模板字符串渲染示例 (不依赖外部 ftl 文件，避免路径问题)
            String templateStr = """
                    {"code":"${code}","name":"${name}","targetType":"${targetType}","prompt":"${prompt}","fields":[<#list fields as f>{"fieldCode":"${f.fieldCode}","fieldName":"${f.fieldName}","component":"${f.component}","required":${f.required?c}}<#if f_has_next>,</#if></#list>]}
                    """;
            Template tpl = new Template("copilot-inline", templateStr, freemarker);
            Map<String, Object> model = new HashMap<>();
            model.put("code", code);
            model.put("name", name);
            model.put("targetType", targetType);
            model.put("prompt", prompt.replace("\"", "'"));
            model.put("fields", fields);
            StringWriter out = new StringWriter();
            tpl.process(model, out);
            return out.toString();
        } catch (Exception e) {
            log.debug("copilot freemarker inline render fallback", e);
            // fallback 手写 JSON
            StringBuilder sb = new StringBuilder();
            sb.append("{\"code\":\"").append(code).append("\",\"name\":\"").append(name.replace("\"","'"))
              .append("\",\"targetType\":\"").append(targetType).append("\",\"fields\":[");
            for (int i = 0; i < fields.size(); i++) {
                Map<String, Object> f = fields.get(i);
                sb.append("{\"fieldCode\":\"").append(f.get("fieldCode")).append("\",\"fieldName\":\"").append(f.get("fieldName")).append("\"}");
                if (i < fields.size() - 1) sb.append(",");
            }
            sb.append("]}");
            return sb.toString();
        }
    }
}
