package com.yutong.ai.copilot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.chat.service.llm.LlmMessage;
import com.yutong.ai.chat.service.llm.LlmProviderSelector;
import com.yutong.ai.chat.service.llm.LlmRequest;
import com.yutong.ai.chat.service.llm.LlmResponse;
import com.yutong.ai.copilot.domain.AiCopilotRun;
import com.yutong.ai.copilot.dto.AiCopilotDraftRequest;
import com.yutong.ai.copilot.dto.AiCopilotDraftResponse;
import com.yutong.ai.copilot.mapper.AiCopilotRunMapper;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 低代码草稿生成：调用已启用 LLM，返回可预览 JSON，不直接落库。
 */
@Service
public class AiCopilotDraftService {

    private final LlmProviderSelector providerSelector;
    private final ObjectMapper objectMapper;
    private final AiCopilotRunMapper runMapper;

    public AiCopilotDraftService(LlmProviderSelector providerSelector,
                                 ObjectMapper objectMapper,
                                 AiCopilotRunMapper runMapper) {
        this.providerSelector = providerSelector;
        this.objectMapper = objectMapper;
        this.runMapper = runMapper;
    }

    public AiCopilotDraftResponse generate(AiCopilotDraftRequest request) {
        String target = normalize(request.getTargetType());
        var runtime = providerSelector.selectEnabledProvider()
                .orElseThrow(() -> new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                        "未启用可用 LLM，无法生成 Copilot 草稿"));
        String system = """
                你是 YuTong 低代码助手。只输出 JSON，不要 Markdown。
                结构: {"suggestedCode":"snake_or_camel","suggestedName":"中文名","fields":[{"fieldCode":"","fieldName":"","component":"input|textarea|number|select|date","required":true}]}
                component 仅允许 input/textarea/number/select/date。
                """;
        String user = "目标类型=" + target + "\n需求=" + request.getPrompt()
                + (request.getEntityCode() == null ? "" : "\n实体编码=" + request.getEntityCode());
        JsonNode root = null;
        int loops = 0;
        String lastError = null;
        while (loops < 3 && root == null) {
            loops++;
            LlmResponse resp = runtime.adapter().chat(new LlmRequest(
                    runtime.defaultModel(),
                    List.of(LlmMessage.system(system), LlmMessage.user(user + (loops > 1 ? "\n上次输出不是合法 JSON，请只输出 JSON。" : "")))));
            if (resp == null || resp.isError() || resp.content() == null || resp.content().isBlank()) {
                lastError = "Copilot LLM 无有效输出";
                continue;
            }
            try {
                root = parseJson(resp.content());
            } catch (BusinessException e) {
                lastError = e.getMessage();
            }
        }
        if (root == null) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, lastError == null ? "Copilot LLM 无有效输出" : lastError);
        }
        AiCopilotDraftResponse out = new AiCopilotDraftResponse();
        out.setTargetType(target);
        out.setSuggestedCode(root.path("suggestedCode").asText("draft_" + target));
        out.setSuggestedName(root.path("suggestedName").asText(request.getPrompt()));
        out.setFields(parseFields(root.get("fields")));
        out.setLayoutJson(toJson(Map.of(
                "targetType", target,
                "code", out.getSuggestedCode(),
                "name", out.getSuggestedName(),
                "fields", out.getFields()
        )));
        out.setProviderCode(runtime.provider().getProviderCode());
        out.setModelCode(runtime.defaultModel());
        out.setMock(false);
        AiCopilotRun run = persistRun(request.getPrompt(), target, out, AiCopilotRun.STATUS_DRAFT, null, loops);
        out.setRunId(run.getId());
        out.setStatus(run.getStatus());
        out.setMessage("LLM 草稿已生成并进入待审（含 Plan），批准后写入 Outbox，不自动落低代码库");
        return out;
    }

    public PageResult<AiCopilotRun> page(PageRequest request, String status) {
        String tenantId = CurrentUserContext.getTenantId();
        LambdaQueryWrapper<AiCopilotRun> wrapper = new LambdaQueryWrapper<AiCopilotRun>()
                .eq(AiCopilotRun::getTenantId, tenantId)
                .eq(status != null && !status.isBlank(), AiCopilotRun::getStatus, status)
                .orderByDesc(AiCopilotRun::getCreatedTime);
        Page<AiCopilotRun> page = runMapper.selectPage(new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    @Transactional
    public AiCopilotRun approve(String id) {
        return transit(id, AiCopilotRun.STATUS_DRAFT, AiCopilotRun.STATUS_APPROVED);
    }

    @Transactional
    public AiCopilotRun reject(String id) {
        return transit(id, AiCopilotRun.STATUS_DRAFT, AiCopilotRun.STATUS_REJECTED);
    }

    public List<AiCopilotRun> pendingOutbox() {
        String tenantId = CurrentUserContext.getTenantId();
        return runMapper.selectList(new LambdaQueryWrapper<AiCopilotRun>()
                .eq(AiCopilotRun::getTenantId, tenantId)
                .eq(AiCopilotRun::getStatus, AiCopilotRun.STATUS_APPROVED)
                .isNotNull(AiCopilotRun::getOutboxJson)
                .orderByAsc(AiCopilotRun::getUpdatedTime)
                .last("LIMIT 50"))
                .stream()
                .filter(this::outboxUnacked)
                .toList();
    }

    @Transactional
    public AiCopilotRun ackOutbox(String id) {
        AiCopilotRun run = runMapper.selectById(id);
        if (run == null) {
            throw new ResourceNotFoundException("Copilot Run 不存在: " + id);
        }
        if (!AiCopilotRun.STATUS_APPROVED.equals(run.getStatus())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "仅 APPROVED Run 可确认 Outbox");
        }
        Map<String, Object> box = new LinkedHashMap<>();
        if (run.getOutboxJson() != null && !run.getOutboxJson().isBlank()) {
            try {
                JsonNode n = objectMapper.readTree(run.getOutboxJson());
                if (n.isObject()) {
                    box.putAll(objectMapper.convertValue(n, new TypeReference<Map<String, Object>>() {}));
                }
            } catch (Exception ignored) {
                // keep empty and overwrite with ack
            }
        }
        box.put("acked", true);
        box.put("ackedAt", java.time.OffsetDateTime.now().toString());
        box.put("ackedBy", CurrentUserContext.getUserId());
        run.setOutboxJson(toJson(box));
        run.setUpdatedBy(CurrentUserContext.getUserId());
        runMapper.updateById(run);
        return run;
    }

    private boolean outboxUnacked(AiCopilotRun run) {
        String json = run.getOutboxJson();
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            JsonNode n = objectMapper.readTree(json);
            return !n.path("acked").asBoolean(false);
        } catch (Exception e) {
            return true;
        }
    }

    private AiCopilotRun transit(String id, String from, String to) {
        AiCopilotRun run = runMapper.selectById(id);
        if (run == null) {
            throw new ResourceNotFoundException("Copilot Run 不存在: " + id);
        }
        if (!from.equals(run.getStatus())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "仅 " + from + " 可流转到 " + to + "，当前=" + run.getStatus());
        }
        run.setStatus(to);
        run.setUpdatedBy(CurrentUserContext.getUserId());
        if (AiCopilotRun.STATUS_APPROVED.equals(to)) {
            run.setOutboxJson(toJson(Map.of(
                    "eventType", "COPILOT_DRAFT_APPROVED",
                    "runId", run.getId(),
                    "targetType", run.getTargetType(),
                    "published", true,
                    "publishedAt", java.time.OffsetDateTime.now().toString()
            )));
        }
        runMapper.updateById(run);
        return run;
    }

    private AiCopilotRun persistRun(String prompt, String target, AiCopilotDraftResponse out, String status, String error, int loops) {
        AiCopilotRun run = new AiCopilotRun();
        run.setId(IdGenerator.nextId());
        run.setTenantId(CurrentUserContext.getTenantId());
        run.setCreatedBy(CurrentUserContext.getUserId());
        run.setTargetType(target);
        run.setPrompt(prompt);
        run.setStatus(status);
        run.setProviderCode(out.getProviderCode());
        run.setModelCode(out.getModelCode());
        run.setOutputJson(error == null ? out.getLayoutJson() : toJson(Map.of("error", error)));
        run.setPlanJson(toJson(List.of(
                Map.of("step", 1, "name", "parse_intent", "status", "DONE"),
                Map.of("step", 2, "name", "generate_fields", "status", "DONE"),
                Map.of("step", 3, "name", "await_approval", "status", "PENDING")
        )));
        run.setLoopCount(Math.max(1, loops));
        run.setVersion(0);
        runMapper.insert(run);
        return run;
    }

    private JsonNode parseJson(String content) {
        String json = content.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "Copilot 输出不是合法 JSON");
        }
    }

    private List<Map<String, Object>> parseFields(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of(field("title", "标题", "input", true));
        }
        try {
            List<Map<String, Object>> raw = objectMapper.convertValue(node, new TypeReference<>() {});
            List<Map<String, Object>> cleaned = new ArrayList<>();
            for (Map<String, Object> item : raw) {
                if (item == null) continue;
                String code = String.valueOf(item.getOrDefault("fieldCode", "field")).replaceAll("[^a-zA-Z0-9_]", "");
                if (code.isBlank()) code = "field";
                String name = String.valueOf(item.getOrDefault("fieldName", code));
                String component = String.valueOf(item.getOrDefault("component", "input")).toLowerCase(Locale.ROOT);
                if (!List.of("input", "textarea", "number", "select", "date").contains(component)) {
                    component = "input";
                }
                boolean required = Boolean.parseBoolean(String.valueOf(item.getOrDefault("required", false)));
                cleaned.add(field(code, name, component, required));
            }
            return cleaned.isEmpty() ? List.of(field("title", "标题", "input", true)) : cleaned;
        } catch (Exception e) {
            return List.of(field("title", "标题", "input", true));
        }
    }

    private Map<String, Object> field(String code, String name, String component, boolean required) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fieldCode", code);
        m.put("fieldName", name);
        m.put("component", component);
        m.put("required", required);
        return m;
    }

    private String normalize(String t) {
        if (t == null || t.isBlank()) return "form";
        String v = t.toLowerCase(Locale.ROOT);
        return switch (v) {
            case "form", "page", "entity" -> v;
            case "list", "detail" -> "page";
            default -> "form";
        };
    }

    private String toJson(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (Exception e) {
            return "{}";
        }
    }
}
