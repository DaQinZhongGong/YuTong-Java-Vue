package com.yutong.system.mail;

/**
 * 邮件发送接口。设计来源: 业界同类实现 MailUtils + ADR 0005 P3。
 *
 * <p>实现: SmtpMailService (需 spring-boot-starter-mail 依赖)。
 * 失败关闭: 未配置 SMTP 时抛异常, 不静默丢弃。
 */
public interface MailService {

    /**
     * 发送简单文本邮件。
     *
     * @param to      收件人
     * @param subject 主题
     * @param content 正文 (纯文本)
     */
    void sendText(String to, String subject, String content);

    /**
     * 发送 HTML 邮件。
     *
     * @param to      收件人
     * @param subject 主题
     * @param html    HTML 正文
     */
    void sendHtml(String to, String subject, String html);

    /**
     * 检查邮件服务是否可用。
     */
    boolean isAvailable();
}
