package com.yutong.ai.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.agent.domain.AiAgent;
import com.yutong.ai.agent.domain.AiAgentRun;
import com.yutong.ai.agent.mapper.AiAgentMapper;
import com.yutong.ai.agent.mapper.AiAgentRunMapper;
import com.yutong.ai.chat.service.llm.LlmMessage;
import com.yutong.ai.chat.service.llm.LlmProviderSelector;
import com.yutong.ai.chat.service.llm.LlmRequest;
import com.yutong.ai.chat.service.llm.LlmResponse;
import com.yutong.ai.search.WebSearchClient;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.id.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;

/**
 * Supervisor 智能路由 — LLM 决策分派到专职子 Agent。
 * Supervisor 智能路由与多子 Agent 编排。
 *
 * <p>与既有 {@link SupervisorAgent#orchestrateSequential} 的区别:
 * <ul>
 *   <li>顺序编排: 按 subAgentIds 固定顺序逐个执行 (既有能力, 不动)</li>
 *   <li>智能路由: LLM 根据用户意图选择最匹配的子 Agent 执行 (本类新增)</li>
 * </ul>
 *
 * <p>内置子 Agent (无需预先在 DB 创建):
 * <ul>
 *   <li>web_search — 联网搜索 (调用 WebSearchClient)</li>
 *   <li>sql_query — 数据库问数 (只读 SELECT)</li>
 *   <li>chitchat — 闲聊兜底 (直接 LLM 回复)</li>
 * </ul>
 *
 * <p>安全: 失败关闭 — LLM 决策失败 → 回落 chitchat; SQL 仅允许 SELECT; 无 Key 时 web_search 报错。
 */
@Service
public class SmartSupervisorAgent {

    private static final Logger log = LoggerFactory.getLogger(SmartSupervisorAgent.class);

    public static final String TYPE_WEB_SEARCH = "web_search";
    public static final String TYPE_SQL_QUERY = "sql_query";
    public static final String TYPE_CHITCHAT = "chitchat";
    public static final String TYPE_CHART = "chart";

    private static final String ROUTER_SYSTEM_PROMPT = """
            你是一个智能路由调度器。根据用户的问题，选择最合适的处理方式。
            
            可选路由:
            - web_search: 需要联网获取最新信息、实时数据、新闻、天气、股价等
            - sql_query: 需要查询数据库中的业务数据、统计、列表等
            - chart: 需要将数据可视化为图表（柱状图/折线图/饼图等）
            - chitchat: 闲聊、问候、通用知识问答、创意写作等
            
            必须只返回一个 JSON 对象，格式: {"route":"web_search|sql_query|chart|chitchat","reason":"简短理由"}
            不要返回其他任何内容。
            """;

    private final AiAgentRunMapper runMapper;
    private final LlmProviderSelector providerSelector;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    private WebSearchClient searchClient;

    @Autowired(required = false)
    public void setSearchClient(WebSearchClient searchClient) {
        this.searchClient = searchClient;
    }

    public SmartSupervisorAgent(AiAgentRunMapper runMapper,
                                 LlmProviderSelector providerSelector,
                                 ObjectMapper objectMapper,
                                 JdbcTemplate jdbcTemplate) {
        this.runMapper = runMapper;
        this.providerSelector = providerSelector;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record RouteDecision(String route, String reason) {}

    public record SupervisorResult(
            String route, String reason, String answer,
            String subAgentCode, String runId, boolean success, String error
    ) {}

    /**
     * 智能路由执行: LLM 决策 → 分派子 Agent → 返回结果。
     */
    public SupervisorResult routeAndExecute(AiAgent supervisor, String query, String conversationId,
                                             Consumer<ReActEngine.ReActStep> stepConsumer) {
        RouteDecision decision;
        try {
            decision = decideRoute(query);
        } catch (Exception e) {
            log.warn("[Supervisor] route decision failed, fallback to chitchat: {}", e.getMessage());
            decision = new RouteDecision(TYPE_CHITCHAT, "路由决策失败, 回落闲聊: " + e.getMessage());
        }

        stepConsumer.accept(new ReActEngine.ReActStep(1, "Thought",
                "Supervisor 路由决策: " + decision.route() + " (" + decision.reason() + ")", null, null));

        return switch (decision.route()) {
            case TYPE_WEB_SEARCH -> executeWebSearch(supervisor, query, conversationId, decision, stepConsumer);
            case TYPE_SQL_QUERY -> executeSqlQuery(supervisor, query, conversationId, decision, stepConsumer);
            case TYPE_CHART -> executeChartAgent(supervisor, query, conversationId, decision, stepConsumer);
            default -> executeChitChat(supervisor, query, conversationId, decision, stepConsumer);
        };
    }

    /**
     * LLM 路由决策。
     */
    RouteDecision decideRoute(String query) {
        var runtimeOpt = providerSelector.selectEnabledProvider();
        if (runtimeOpt.isEmpty()) {
            return new RouteDecision(TYPE_CHITCHAT, "无可用 LLM 供应商");
        }
        var runtime = runtimeOpt.get();
        LlmResponse resp = runtime.adapter().chat(new LlmRequest(
                runtime.defaultModel(),
                List.of(LlmMessage.system(ROUTER_SYSTEM_PROMPT), LlmMessage.user(query)),
                0.1, 100, false, "AGENT_SUPERVISOR_ROUTER"));

        if (resp.isError() || resp.content() == null || resp.content().isBlank()) {
            return new RouteDecision(TYPE_CHITCHAT, "LLM 未返回路由决策");
        }

        String json = extractJson(resp.content());
        try {
            JsonNode node = objectMapper.readTree(json);
            String route = node.path("route").asText(TYPE_CHITCHAT);
            String reason = node.path("reason").asText("");
            if (!TYPE_WEB_SEARCH.equals(route) && !TYPE_SQL_QUERY.equals(route) && !TYPE_CHART.equals(route)) {
                route = TYPE_CHITCHAT;
            }
            return new RouteDecision(route, reason);
        } catch (Exception e) {
            return new RouteDecision(TYPE_CHITCHAT, "路由 JSON 解析失败: " + e.getMessage());
        }
    }

    // ==================== 子 Agent 实现 ====================

    private SupervisorResult executeWebSearch(AiAgent supervisor, String query, String conversationId,
                                               RouteDecision decision, Consumer<ReActEngine.ReActStep> stepConsumer) {
        String runId = IdGenerator.nextId();
        stepConsumer.accept(new ReActEngine.ReActStep(2, "Action",
                "调用联网搜索: " + query, "web_search", null));

        if (searchClient == null) {
            String err = "Web Search 客户端未配置";
            stepConsumer.accept(new ReActEngine.ReActStep(3, "Observation", err, null, null));
            saveRun(supervisor, runId, conversationId, query, err, "FAILED");
            return new SupervisorResult(TYPE_WEB_SEARCH, decision.reason(), err, "web_search", runId, false, err);
        }

        try {
            var response = searchClient.search(query, 5);
            StringBuilder sb = new StringBuilder("搜索结果:\n");
            for (int i = 0; i < response.results().size(); i++) {
                var r = response.results().get(i);
                sb.append(i + 1).append(". ").append(r.title()).append("\n")
                  .append("   ").append(r.content()).append("\n")
                  .append("   来源: ").append(r.link()).append("\n\n");
            }
            String answer = sb.toString().trim();
            stepConsumer.accept(new ReActEngine.ReActStep(3, "Observation",
                    "搜索到 " + response.count() + " 条结果", null, answer));
            saveRun(supervisor, runId, conversationId, query, answer, "SUCCESS");
            return new SupervisorResult(TYPE_WEB_SEARCH, decision.reason(), answer, "web_search", runId, true, null);
        } catch (Exception e) {
            String err = "联网搜索失败: " + e.getMessage();
            stepConsumer.accept(new ReActEngine.ReActStep(3, "Observation", err, null, null));
            saveRun(supervisor, runId, conversationId, query, err, "FAILED");
            return new SupervisorResult(TYPE_WEB_SEARCH, decision.reason(), err, "web_search", runId, false, err);
        }
    }

    private SupervisorResult executeSqlQuery(AiAgent supervisor, String query, String conversationId,
                                              RouteDecision decision, Consumer<ReActEngine.ReActStep> stepConsumer) {
        String runId = IdGenerator.nextId();
        stepConsumer.accept(new ReActEngine.ReActStep(2, "Action",
                "数据库问数: " + query, "sql_query", null));

        try {
            String sql = generateSql(query);
            if (sql == null || sql.isBlank()) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "LLM 未能生成有效 SQL");
            }
            String normalized = sql.trim().toUpperCase();
            if (!normalized.startsWith("SELECT") && !normalized.startsWith("WITH")) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "仅允许 SELECT 查询");
            }
            if (normalized.contains(";") || normalized.contains("--") || normalized.contains("/*")) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "SQL 包含非法字符");
            }

            stepConsumer.accept(new ReActEngine.ReActStep(3, "Action", "执行 SQL: " + sql, "sql_execute", null));

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            String answer = formatSqlResult(rows);
            stepConsumer.accept(new ReActEngine.ReActStep(4, "Observation",
                    "查询返回 " + rows.size() + " 行", null, answer));
            saveRun(supervisor, runId, conversationId, query, answer, "SUCCESS");
            return new SupervisorResult(TYPE_SQL_QUERY, decision.reason(), answer, "sql_query", runId, true, null);
        } catch (Exception e) {
            String err = "数据库问数失败: " + e.getMessage();
            stepConsumer.accept(new ReActEngine.ReActStep(3, "Observation", err, null, null));
            saveRun(supervisor, runId, conversationId, query, err, "FAILED");
            return new SupervisorResult(TYPE_SQL_QUERY, decision.reason(), err, "sql_query", runId, false, err);
        }
    }

    /**
     * Chart 子 Agent — SQL 查询 + ECharts 配置生成。
     * 返回 JSON: {type, title, categories, series, echartsOption}
     */
    private SupervisorResult executeChartAgent(AiAgent supervisor, String query, String conversationId,
                                                RouteDecision decision, Consumer<ReActEngine.ReActStep> stepConsumer) {
        String runId = IdGenerator.nextId();
        stepConsumer.accept(new ReActEngine.ReActStep(2, "Action",
                "数据可视化: " + query, "chart", null));

        try {
            // 1. 生成 SQL
            String sql = generateSql(query);
            if (sql == null || sql.isBlank()) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "LLM 未能生成有效 SQL");
            }
            String normalized = sql.trim().toUpperCase();
            if (!normalized.startsWith("SELECT") && !normalized.startsWith("WITH")) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "仅允许 SELECT 查询");
            }

            stepConsumer.accept(new ReActEngine.ReActStep(3, "Action", "执行 SQL: " + sql, "sql_execute", null));

            // 2. 执行查询
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            if (rows.isEmpty()) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "查询结果为空, 无法生成图表");
            }

            // 3. LLM 生成 ECharts 配置
            stepConsumer.accept(new ReActEngine.ReActStep(4, "Action", "生成图表配置", "echarts_gen", null));
            String chartConfig = generateChartConfig(query, rows);

            stepConsumer.accept(new ReActEngine.ReActStep(5, "Observation",
                    "图表配置生成完成 (" + rows.size() + " 行数据)", null, chartConfig));
            saveRun(supervisor, runId, conversationId, query, chartConfig, "SUCCESS");
            return new SupervisorResult(TYPE_CHART, decision.reason(), chartConfig, "chart", runId, true, null);
        } catch (Exception e) {
            String err = "数据可视化失败: " + e.getMessage();
            stepConsumer.accept(new ReActEngine.ReActStep(3, "Observation", err, null, null));
            saveRun(supervisor, runId, conversationId, query, err, "FAILED");
            return new SupervisorResult(TYPE_CHART, decision.reason(), err, "chart", runId, false, err);
        }
    }

    /**
     * LLM 生成 ECharts 配置。
     */
    private String generateChartConfig(String query, List<Map<String, Object>> rows) {
        // 取前 20 行数据作为样本
        String dataSample = formatSqlResult(rows.size() > 20 ? rows.subList(0, 20) : rows);
        String systemPrompt = """
                你是一个 ECharts 图表配置专家。根据用户问题和数据，生成 ECharts option JSON。
                
                数据:
                %s
                
                规则:
                - 返回完整的 ECharts option JSON 对象
                - 选择合适的图表类型 (bar/line/pie)
                - 包含 title、tooltip、legend、xAxis、yAxis、series
                - 只返回 JSON，不要解释
                """.formatted(dataSample);

        var runtimeOpt = providerSelector.selectEnabledProvider();
        if (runtimeOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "无可用 LLM 供应商");
        }
        var runtime = runtimeOpt.get();
        LlmResponse resp = runtime.adapter().chat(new LlmRequest(
                runtime.defaultModel(),
                List.of(LlmMessage.system(systemPrompt), LlmMessage.user(query)),
                0.2, 1500, false, "AGENT_SUPERVISOR_CHART"));

        String content = resp.isError() || resp.content() == null ? null : resp.content().trim();
        if (content == null) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "LLM 未返回图表配置");
        }
        // 提取 JSON
        if (content.startsWith("```json")) content = content.substring(7);
        else if (content.startsWith("```")) content = content.substring(3);
        if (content.endsWith("```")) content = content.substring(0, content.length() - 3);
        return content.trim();
    }

    private SupervisorResult executeChitChat(AiAgent supervisor, String query, String conversationId,
                                              RouteDecision decision, Consumer<ReActEngine.ReActStep> stepConsumer) {
        String runId = IdGenerator.nextId();
        stepConsumer.accept(new ReActEngine.ReActStep(2, "Action", "闲聊回复", "chitchat", null));

        try {
            var runtimeOpt = providerSelector.selectEnabledProvider();
            if (runtimeOpt.isEmpty()) {
                throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "无可用 LLM 供应商");
            }
            var runtime = runtimeOpt.get();
            String system = supervisor.getSystemPrompt() != null && !supervisor.getSystemPrompt().isBlank()
                    ? supervisor.getSystemPrompt()
                    : "你是一个友善的 AI 助手，请简洁回答用户问题。";

            LlmResponse resp = runtime.adapter().chat(new LlmRequest(
                    runtime.defaultModel(),
                    List.of(LlmMessage.system(system), LlmMessage.user(query)),
                    0.7, 1000, false, "AGENT_SUPERVISOR_CHITCHAT"));

            String answer = (!resp.isError() && resp.content() != null) ? resp.content()
                    : "抱歉，我无法回答这个问题。";
            stepConsumer.accept(new ReActEngine.ReActStep(3, "Observation", "回复完成", null, answer));
            saveRun(supervisor, runId, conversationId, query, answer, "SUCCESS");
            return new SupervisorResult(TYPE_CHITCHAT, decision.reason(), answer, "chitchat", runId, true, null);
        } catch (Exception e) {
            String err = "闲聊回复失败: " + e.getMessage();
            stepConsumer.accept(new ReActEngine.ReActStep(3, "Observation", err, null, null));
            saveRun(supervisor, runId, conversationId, query, err, "FAILED");
            return new SupervisorResult(TYPE_CHITCHAT, decision.reason(), err, "chitchat", runId, false, err);
        }
    }

    // ==================== 内部方法 ====================

    private String generateSql(String query) {
        String schema = fetchTableSchemaSummary();
        String systemPrompt = """
                你是一个 PostgreSQL SQL 专家。根据用户问题生成只读 SELECT 语句。
                
                数据库表结构摘要:
                %s
                
                规则:
                - 只生成 SELECT 语句，禁止 INSERT/UPDATE/DELETE/DROP/ALTER
                - 使用 PostgreSQL 语法
                - 表名和列名使用双引号包裹
                - 只返回 SQL 本身，不要解释
                """.formatted(schema);

        var runtimeOpt = providerSelector.selectEnabledProvider();
        if (runtimeOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "无可用 LLM 供应商");
        }
        var runtime = runtimeOpt.get();
        LlmResponse resp = runtime.adapter().chat(new LlmRequest(
                runtime.defaultModel(),
                List.of(LlmMessage.system(systemPrompt), LlmMessage.user(query)),
                0.1, 500, false, "AGENT_SUPERVISOR_SQL"));

        String content = resp.isError() || resp.content() == null ? null : resp.content().trim();
        if (content == null) return null;
        if (content.startsWith("```sql")) content = content.substring(6);
        else if (content.startsWith("```")) content = content.substring(3);
        if (content.endsWith("```")) content = content.substring(0, content.length() - 3);
        return content.trim();
    }

    private String fetchTableSchemaSummary() {
        try {
            List<Map<String, Object>> tables = jdbcTemplate.queryForList("""
                    SELECT table_name FROM information_schema.tables 
                    WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                      AND table_name NOT LIKE 'pg_%' AND table_name NOT LIKE 'sql_%'
                    ORDER BY table_name LIMIT 20
                    """);
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> t : tables) {
                sb.append("- ").append(t.get("table_name")).append("\n");
            }
            return sb.isEmpty() ? "(无可用表)" : sb.toString();
        } catch (Exception e) {
            return "(表结构获取失败)";
        }
    }

    private String formatSqlResult(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return "查询结果为空";
        StringBuilder sb = new StringBuilder();
        Set<String> cols = rows.get(0).keySet();
        sb.append(String.join(" | ", cols)).append("\n");
        sb.append("-".repeat(Math.min(80, cols.size() * 15))).append("\n");
        int limit = Math.min(rows.size(), 20);
        for (int i = 0; i < limit; i++) {
            Map<String, Object> row = rows.get(i);
            List<String> vals = new ArrayList<>();
            for (String col : cols) {
                Object v = row.get(col);
                vals.add(v == null ? "NULL" : String.valueOf(v));
            }
            sb.append(String.join(" | ", vals)).append("\n");
        }
        if (rows.size() > limit) {
            sb.append("... 共 ").append(rows.size()).append(" 行");
        }
        return sb.toString().trim();
    }

    private void saveRun(AiAgent supervisor, String runId, String conversationId,
                          String input, String output, String status) {
        try {
            AiAgentRun run = new AiAgentRun();
            run.setId(runId);
            run.setTenantId(supervisor.getTenantId());
            run.setAgentId(supervisor.getId());
            run.setConversationId(conversationId);
            run.setStatus(status);
            run.setInputJson(toJson(Map.of("query", input != null ? input : "")));
            run.setOutputJson(toJson(Map.of("answer", output != null ? output : "")));
            run.setTraceJson("[]");
            runMapper.insert(run);
        } catch (Exception e) {
            log.warn("[Supervisor] saveRun failed: {}", e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String extractJson(String text) {
        if (text == null) return "{}";
        text = text.trim();
        if (text.startsWith("```json")) text = text.substring(7);
        else if (text.startsWith("```")) text = text.substring(3);
        if (text.endsWith("```")) text = text.substring(0, text.length() - 3);
        text = text.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
