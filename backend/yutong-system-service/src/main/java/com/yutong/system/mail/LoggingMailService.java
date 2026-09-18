package com.yutong.system.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 邮件发送服务 (降级实现)。
 * 落点: 业界同类实现 MailUtils + ADR 0005 P3。
 *
 * <p>当前为日志降级实现: 记录邮件内容但不真正发送。
 * 引入 spring-boot-starter-mail 后可替换为 SmtpMailService 实现。
 * 失败关闭: enabled=false 时发送抛异常。
 */
@Service
public class LoggingMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailService.class);

    @Value("${yutong.mail.enabled:false}")
    private boolean enabled;

    @Override
    public void sendText(String to, String subject, String content) {
        if (!enabled) {
            throw new IllegalStateException("邮件服务未启用 (yutong.mail.enabled=false)");
        }
        // 降级: 仅日志记录, 不真正发送
        log.info("[Mail] (降级模式) 发送文本邮件 to={}, subject={}, contentLength={}",
                to, subject, content != null ? content.length() : 0);
    }

    @Override
    public void sendHtml(String to, String subject, String html) {
        if (!enabled) {
            throw new IllegalStateException("邮件服务未启用 (yutong.mail.enabled=false)");
        }
        log.info("[Mail] (降级模式) 发送 HTML 邮件 to={}, subject={}, htmlLength={}",
                to, subject, html != null ? html.length() : 0);
    }

    @Override
    public boolean isAvailable() {
        return enabled;
    }
}
