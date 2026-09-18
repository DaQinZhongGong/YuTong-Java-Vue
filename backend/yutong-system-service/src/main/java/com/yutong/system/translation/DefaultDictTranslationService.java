package com.yutong.system.translation;

import com.yutong.common.translation.TranslationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 默认字典翻译服务 — 查 sys_dict_item 表
 * 设计来源: ADR 0004 P2-A 批次 2-B
 *
 * 落点:84-字典/用户/部门显示名自动化详设
 *
 * 工作原理:
 *   - extension 参数 = dictType (如 "sys_user_status")
 *   - id 参数 = 字典值 (如 "ENABLED")
 *   - 返回 item_label (如 "启用")
 *
 * 失效:dictType 为空 / 查不到 → 静默返回 null
 */
@Service
public class DefaultDictTranslationService implements TranslationService {

    private static final String SQL =
            "SELECT item_label FROM sys_dict_item " +
            " WHERE dict_type = ? AND item_value = ? AND status = '1' LIMIT 1";

    private final JdbcTemplate jdbc;

    public DefaultDictTranslationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String translate(String id, String dictType) {
        if (id == null || id.isBlank() || dictType == null || dictType.isBlank()) {
            return null;
        }
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(SQL, dictType, id);
            if (rows.isEmpty()) return null;
            Object label = rows.get(0).get("item_label");
            return label == null ? null : String.valueOf(label);
        } catch (Exception e) {
            return null;
        }
    }
}
