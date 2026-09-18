package com.yutong.ai.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yutong.ai.gateway.domain.AiConversation;
import com.yutong.ai.gateway.domain.AiMessage;
import com.yutong.ai.gateway.mapper.AiConversationMapper;
import com.yutong.ai.gateway.mapper.AiMessageMapper;
import com.yutong.ai.chat.service.llm.LlmMessage;
import com.yutong.ai.chat.service.llm.LlmProviderSelector;
import com.yutong.ai.chat.service.llm.LlmRequest;
import com.yutong.ai.chat.service.llm.LlmResponse;
import com.yutong.ai.memory.service.AiMemoryService;
import com.yutong.common.auth.CurrentUserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 智能体记忆服务。
 * 设计来源: V039 ai_conversation.memory_config_json / summary / memory_window
 * 职责:
 * - MessageWindowChatMemory: 滑动窗口取最近 N 条消息 (默认 10)
 * - summarizedContext: 超阈值时对早期消息用已启用 LLM 做摘要，每 20 条触发一次
 * - buildContext: 摘要 + 窗口消息拼接为 LLM 上下文
 */
@Service
public class AgentMemoryService {

    private static final Logger log = LoggerFactory.getLogger(AgentMemoryService.class);

    public static final int DEFAULT_WINDOW_SIZE = 10;
    public static final int DEFAULT_SUMMARIZE_THRESHOLD = 20;

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final AiMemoryService persistentMemoryService;
    private final LlmProviderSelector providerSelector;

    public AgentMemoryService(AiConversationMapper conversationMapper,
                              AiMessageMapper messageMapper,
                              AiMemoryService persistentMemoryService,
                              LlmProviderSelector providerSelector) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.persistentMemoryService = persistentMemoryService;
        this.providerSelector = providerSelector;
    }

    /**
     * 获取记忆窗口大小 (会话级覆盖 > 默认 10)。
     */
    public int resolveWindowSize(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return DEFAULT_WINDOW_SIZE;
        }
        AiConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) return DEFAULT_WINDOW_SIZE;
        if (conv.getMemoryWindow() != null && conv.getMemoryWindow() > 0) {
            return conv.getMemoryWindow();
        }
        // 尝试从 memory_config_json 解析 windowSize
        Integer fromJson = parseWindowFromJson(conv.getMemoryConfigJson());
        return fromJson != null ? fromJson : DEFAULT_WINDOW_SIZE;
    }

    /**
     * 获取 summarize 阈值 (默认 20)。
     */
    public int resolveThreshold(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return DEFAULT_SUMMARIZE_THRESHOLD;
        AiConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) return DEFAULT_SUMMARIZE_THRESHOLD;
        Integer fromJson = parseThresholdFromJson(conv.getMemoryConfigJson());
        return fromJson != null ? fromJson : DEFAULT_SUMMARIZE_THRESHOLD;
    }

    /**
     * MessageWindowChatMemory: 取最近 windowSize 条消息。
     */
    public List<AiMessage> windowMessages(String conversationId, int windowSize) {
        if (conversationId == null || conversationId.isBlank()) return List.of();
        // 取最新 windowSize 条，按时间正序返回
        LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .orderByDesc(AiMessage::getCreatedTime)
                .last("LIMIT " + Math.max(1, windowSize));
        List<AiMessage> desc = messageMapper.selectList(wrapper);
        // 反转成时间正序
        return desc.stream().sorted((a, b) -> {
            if (a.getCreatedTime() == null || b.getCreatedTime() == null) return 0;
            return a.getCreatedTime().compareTo(b.getCreatedTime());
        }).toList();
    }

    /**
     * 构建上下文: 摘要(若有) + 窗口消息文本拼接。
     */
    public String buildContext(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return "";
        AiConversation conv = conversationMapper.selectById(conversationId);
        int windowSize = resolveWindowSize(conversationId);
        List<AiMessage> window = windowMessages(conversationId, windowSize);
        StringBuilder sb = new StringBuilder();
        try {
            String persistent = persistentMemoryService.recallForChat(
                    conv != null ? conv.getUserId() : CurrentUserContext.getUserId());
            if (persistent != null && !persistent.isBlank()) {
                sb.append(persistent).append("\n");
            }
        } catch (Exception e) {
            log.debug("persistent memory recall skipped: {}", e.getMessage());
        }
        if (conv != null && conv.getSummary() != null && !conv.getSummary().isBlank()) {
            sb.append("[Summary of earlier messages]\n").append(conv.getSummary()).append("\n\n");
        }
        sb.append("[Recent ").append(window.size()).append(" messages]\n");
        for (AiMessage m : window) {
            String content = m.getContentSummary() != null ? m.getContentSummary() : "";
            sb.append(m.getRole()).append(": ").append(truncate(content, 500)).append("\n");
        }
        return sb.toString();
    }

    /**
     * summarizedContext: 若消息总数超过阈值，则对超出窗口的早期消息用 LLM 摘要。
     * 策略: count = selectCount; 若 count >= threshold 且 count > windowSize，则对 [0, count-windowSize) 做摘要并写回 conversation.summary。
     * 返回最新的摘要文本 (可能未变更)。
     */
    public String maybeSummarize(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return "";
        AiConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) return "";
        int threshold = resolveThreshold(conversationId);
        int windowSize = resolveWindowSize(conversationId);
        long total = messageMapper.selectCount(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId));
        if (total < threshold || total <= windowSize) {
            return conv.getSummary() != null ? conv.getSummary() : "";
        }
        // 需要摘要的早期消息数
        int earlyCount = (int) (total - windowSize);
        if (earlyCount <= 0) return conv.getSummary() != null ? conv.getSummary() : "";
        // 取最早的 earlyCount 条
        LambdaQueryWrapper<AiMessage> earlyWrapper = new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .orderByAsc(AiMessage::getCreatedTime)
                .last("LIMIT " + earlyCount);
        List<AiMessage> early = messageMapper.selectList(earlyWrapper);
        String newSummary = llmSummarize(early);
        conv.setSummary(newSummary);
        conversationMapper.updateById(conv);
        log.info("AgentMemory summarized: conversationId={} total={} window={} summaryLen={}", conversationId, total, windowSize, newSummary.length());
        return newSummary;
    }

    private String llmSummarize(List<AiMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder corpus = new StringBuilder();
        for (AiMessage m : messages) {
            String s = m.getContentSummary() != null ? m.getContentSummary() : "";
            s = s.replaceAll("\\s+", " ").trim();
            corpus.append("[").append(m.getRole()).append("] ").append(truncate(s, 200)).append('\n');
            if (corpus.length() > 4000) {
                break;
            }
        }
        var runtimeOpt = providerSelector.selectEnabledProvider();
        if (runtimeOpt.isEmpty()) {
            log.warn("memory summarize skipped: no LLM provider");
            return truncate(corpus.toString(), 1000);
        }
        LlmResponse resp = runtimeOpt.get().adapter().chat(new LlmRequest(
                runtimeOpt.get().defaultModel(),
                List.of(
                        LlmMessage.system("用中文把对话历史压缩成不超过 400 字的要点摘要，不要编造未出现的事实。"),
                        LlmMessage.user(corpus.toString())),
                0.2, 600, false, "AGENT_MEMORY"));
        if (resp.isError() || resp.content() == null || resp.content().isBlank()) {
            log.warn("memory summarize LLM failed: {}", resp.error() == null ? "empty" : resp.error().getMessage());
            return truncate(corpus.toString(), 1000);
        }
        return truncate(resp.content().trim(), 1000);
    }

    private Integer parseWindowFromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            // 轻量解析: 查找 "windowSize":<num>
            String key = "\"windowSize\"";
            int idx = json.indexOf(key);
            if (idx < 0) return null;
            int colon = json.indexOf(':', idx);
            if (colon < 0) return null;
            int start = colon + 1;
            while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) start++;
            int end = start;
            while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
            if (start >= end) return null;
            return Integer.parseInt(json.substring(start, end));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseThresholdFromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            String key = "\"summarizeThreshold\"";
            int idx = json.indexOf(key);
            if (idx < 0) return null;
            int colon = json.indexOf(':', idx);
            if (colon < 0) return null;
            int start = colon + 1;
            while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) start++;
            int end = start;
            while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
            if (start >= end) return null;
            return Integer.parseInt(json.substring(start, end));
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
