package com.yutong.ai.chat.service.llm;

import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 对话消息。对齐 OpenAI ChatCompletion message 结构。
 * <p>
 * {@code content} 既可以是 {@link String}（纯文本，对齐 OpenAI 文本消息），
 * 也可以是 {@code List<Map<String, Object>>}（多模态 parts，对齐 OpenAI
 * {@code content: [{type:"text",text:"..."},{type:"image_url",image_url:{url:"..."}}]} 协议）。
 * <p>
 * 设计来源: 13-AI能力设计、P6-02 免费 LLM 供应商集成、P1-7 多模态视觉。
 */
public record LlmMessage(String role, Object content) {

    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    /** OpenAI 多模态 part：纯文本片段 */
    public static final String PART_TYPE_TEXT = "text";
    /** OpenAI 多模态 part：图片 URL（支持 https URL 或 base64 data URI） */
    public static final String PART_TYPE_IMAGE_URL = "image_url";
    /** OpenAI 多模态 part：音频（input_audio，仅 gpt-4o-audio-preview 等支持） */
    public static final String PART_TYPE_INPUT_AUDIO = "input_audio";

    public LlmMessage {
        if (role == null || role.isBlank()) {
            throw new BusinessException(ErrorCode.AI_PROMPT_INVALID, "role 不能为空");
        }
        if (content == null) {
            throw new BusinessException(ErrorCode.AI_PROMPT_INVALID, "content 不能为空");
        }
        if (content instanceof String s && s.isBlank()) {
            throw new BusinessException(ErrorCode.AI_PROMPT_INVALID, "content 不能为空字符串");
        }
    }

    public static LlmMessage system(String content) {
        return new LlmMessage(ROLE_SYSTEM, content);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage(ROLE_USER, content);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage(ROLE_ASSISTANT, content);
    }

    /**
     * 构造多模态用户消息：先给一段文本，再附一组图片 URL。
     * 图片 URL 必须是 https URL 或 data:image/...;base64,... 形式。
     *
     * @param text      用户提问文本；为空时仍可仅发图片
     * @param imageUrls 图片 URL 列表，允许为空
     */
    public static LlmMessage userMultimodal(String text, List<String> imageUrls) {
        List<Map<String, Object>> parts = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            parts.add(textPart(text));
        }
        if (imageUrls != null) {
            for (String url : imageUrls) {
                if (url == null || url.isBlank()) continue;
                parts.add(imageUrlPart(url));
            }
        }
        if (parts.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_PROMPT_INVALID, "多模态消息至少需要一个文本或图片 part");
        }
        return new LlmMessage(ROLE_USER, parts);
    }

    /** 构造文本 part（OpenAI 多模态协议）。 */
    public static Map<String, Object> textPart(String text) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("type", PART_TYPE_TEXT);
        part.put("text", text);
        return part;
    }

    /** 构造 image_url part（OpenAI 多模态协议）。 */
    public static Map<String, Object> imageUrlPart(String url) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("url", url);
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("type", PART_TYPE_IMAGE_URL);
        part.put("image_url", image);
        return part;
    }

    /** 构造 input_audio part（OpenAI 多模态协议，data + format）。 */
    public static Map<String, Object> inputAudioPart(String base64Data, String format) {
        Map<String, Object> audio = new LinkedHashMap<>();
        audio.put("data", base64Data);
        audio.put("format", format == null || format.isBlank() ? "wav" : format);
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("type", PART_TYPE_INPUT_AUDIO);
        part.put("input_audio", audio);
        return part;
    }

    /**
     * @return content 是否为多模态 part 列表
     */
    public boolean isMultimodal() {
        return content instanceof List;
    }

    /**
     * @return 文本形式的 content（多模态时只返回拼接的 text part，纯文本直接返回）。
     *         用于日志 / 审计 / Citation 关联等仅看文本的场景。
     */
    public String textContent() {
        if (content instanceof String s) return s;
        if (content instanceof List<?> parts) {
            StringBuilder sb = new StringBuilder();
            for (Object o : parts) {
                if (o instanceof Map<?, ?> m) {
                    Object type = m.get("type");
                    if (PART_TYPE_TEXT.equals(type)) {
                        Object t = m.get("text");
                        if (t != null) sb.append(t);
                    }
                }
            }
            return sb.toString();
        }
        return content.toString();
    }
}
