package com.yutong.system.translation;

import com.yutong.common.translation.TranslationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 默认用户翻译服务 — 查 sys_user 表,返回 username
 * 设计来源: ADR 0004 P2-A 批次 2-B
 *
 * 落点:84-字典/用户/部门显示名自动化详设
 *
 * 注:sys_user 表结构由 57 号文档定义,字段:
 *   - id (varchar(32) ULID)
 *   - username (varchar(64) 登录名)
 *   - display_name / real_name (展示名,本服务优先取)
 *
 * 兼容:
 *   - username 字段缺失时回退 display_name
 *   - status='1' (启用) 过滤
 */
@Service
public class DefaultUserTranslationService implements TranslationService {

    private static final String SQL =
            "SELECT username, display_name FROM sys_user " +
            " WHERE id = ? AND status = '1' LIMIT 1";

    private final JdbcTemplate jdbc;

    public DefaultUserTranslationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String translate(String id, String extension) {
        if (id == null || id.isBlank()) return null;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(SQL, id);
            if (rows.isEmpty()) return null;
            Map<String, Object> row = rows.get(0);
            Object display = row.get("display_name");
            if (display != null && !String.valueOf(display).isBlank()) {
                return String.valueOf(display);
            }
            Object username = row.get("username");
            return username == null ? null : String.valueOf(username);
        } catch (Exception e) {
            return null;
        }
    }
}
