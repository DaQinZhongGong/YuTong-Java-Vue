package com.yutong.ai.chat.service.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LlmMessage 多模态内容单测。
 * 设计来源: P1-7 多模态视觉 — 验证 text / image_url / input_audio parts 序列化 + 防御。
 */
class LlmMessageMultimodalTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("纯文本消息：保持向后兼容")
    void textMessageBackwardsCompatible() throws Exception {
        LlmMessage m = LlmMessage.user("你好");
        assertEquals("user", m.role());
        assertEquals("你好", m.content());
        assertFalse(m.isMultimodal());
        // 序列化等于字符串
        assertEquals("\"你好\"", objectMapper.writeValueAsString(m.content()));
    }

    @Test
    @DisplayName("多模态消息：text + image_url parts 序列化为 OpenAI 协议")
    void multimodalMessageSerialize() throws Exception {
        LlmMessage m = LlmMessage.userMultimodal(
                "请看这张图",
                List.of("https://example.com/a.png", "data:image/png;base64,xxxx")
        );
        assertTrue(m.isMultimodal());
        assertEquals("user", m.role());
        // 序列化后必须是 JSON 数组，包含 type=text 和 type=image_url
        String json = objectMapper.writeValueAsString(m.content());
        assertTrue(json.startsWith("["), "多模态 content 必须是 JSON 数组，实际: " + json);
        assertTrue(json.contains("\"type\":\"text\""));
        assertTrue(json.contains("\"text\":\"请看这张图\""));
        assertTrue(json.contains("\"type\":\"image_url\""));
        assertTrue(json.contains("\"url\":\"https://example.com/a.png\""));
        assertTrue(json.contains("\"url\":\"data:image/png;base64,xxxx\""));
    }

    @Test
    @DisplayName("textContent() 折叠多模态 parts：仅返回拼接的 text part")
    void textContentFoldsMultimodal() {
        LlmMessage m = LlmMessage.userMultimodal(
                "问题",
                List.of("https://example.com/a.png")
        );
        assertEquals("问题", m.textContent());
    }

    @Test
    @DisplayName("仅图片无文本：仍可构造")
    void multimodalImagesOnly() {
        LlmMessage m = LlmMessage.userMultimodal(null, List.of("https://example.com/a.png"));
        assertTrue(m.isMultimodal());
        assertEquals("", m.textContent());
    }

    @Test
    @DisplayName("空图片列表 + null 文本：构造抛错")
    void multimodalEmptyThrows() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> LlmMessage.userMultimodal(null, null));
        assertTrue(ex.getMessage().contains("多模态"));
    }

    @Test
    @DisplayName("textPart / imageUrlPart / inputAudioPart 结构对齐 OpenAI 协议")
    void partHelpers() {
        Map<String, Object> text = LlmMessage.textPart("hi");
        assertEquals("text", text.get("type"));
        assertEquals("hi", text.get("text"));

        Map<String, Object> img = LlmMessage.imageUrlPart("https://x");
        assertEquals("image_url", img.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> image = (Map<String, Object>) img.get("image_url");
        assertEquals("https://x", image.get("url"));

        Map<String, Object> audio = LlmMessage.inputAudioPart("base64==", "mp3");
        assertEquals("input_audio", audio.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) audio.get("input_audio");
        assertEquals("base64==", inner.get("data"));
        assertEquals("mp3", inner.get("format"));
    }

    @Test
    @DisplayName("空字符串 content 仍抛错（向后兼容）")
    void emptyStringContentThrows() {
        assertThrows(BusinessException.class, () -> LlmMessage.user(""));
        assertThrows(BusinessException.class, () -> LlmMessage.user("   "));
    }
}
