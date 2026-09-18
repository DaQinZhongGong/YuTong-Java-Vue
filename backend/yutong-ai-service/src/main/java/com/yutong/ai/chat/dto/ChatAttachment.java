package com.yutong.ai.chat.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 对话附件（图片 / 音频），用于多模态视觉（P1-7）。
 * <p>
 * 设计原则：仅携带 URL 引用 + 基本元数据，不在后端重复存储；
 * 实际文件由 {@code /files/upload} 预签名 URL 承载（前端先走 /files/upload 拿到 URL 再下发本字段）。
 *
 * @param type   "image" / "audio" / "file"，对应 OpenAI 多模态 part type
 * @param url    已上传文件的可访问 URL（https URL 或 data:URI）
 * @param name   原始文件名（仅用于审计 / 日志，不入 LLM 请求）
 * @param format 音频格式（wav/mp3），仅 audio 类型有效
 */
@Getter
@Setter
public class ChatAttachment {

    private String type;
    private String url;
    private String name;
    private String format;

    public ChatAttachment() {
    }

    public ChatAttachment(String type, String url, String name, String format) {
        this.type = type;
        this.url = url;
        this.name = name;
        this.format = format;
    }

    public static ChatAttachment image(String url, String name) {
        return new ChatAttachment("image", url, name, null);
    }

    public static ChatAttachment audio(String url, String name, String format) {
        return new ChatAttachment("audio", url, name, format == null ? "wav" : format);
    }

    /**
     * 提取多模态 URL 列表：仅 image 类型参与 OpenAI 多模态 content 构造；
     * audio 暂折叠为文本说明（input_audio 需 gpt-4o-audio-preview 等特定模型）。
     */
    public static List<String> imageUrls(List<ChatAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) return List.of();
        return attachments.stream()
                .filter(a -> a != null && a.getUrl() != null && !a.getUrl().isBlank())
                .filter(a -> a.getType() == null || "image".equalsIgnoreCase(a.getType()))
                .map(ChatAttachment::getUrl)
                .toList();
    }
}
