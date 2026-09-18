package com.yutong.system.translation;

import com.yutong.common.translation.TranslationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 默认部门翻译服务 — 查 sys_dept 表,返回 dept_name
 * 设计来源: ADR 0004 P2-A 批次 2-B
 */
@Service
public class DefaultDeptTranslationService implements TranslationService {

    private static final String SQL =
            "SELECT dept_name FROM sys_dept WHERE id = ? AND status = '1' LIMIT 1";

    private final JdbcTemplate jdbc;

    public DefaultDeptTranslationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String translate(String id, String extension) {
        if (id == null || id.isBlank()) return null;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(SQL, id);
            if (rows.isEmpty()) return null;
            Object name = rows.get(0).get("dept_name");
            return name == null ? null : String.valueOf(name);
        } catch (Exception e) {
            return null;
        }
    }
}
