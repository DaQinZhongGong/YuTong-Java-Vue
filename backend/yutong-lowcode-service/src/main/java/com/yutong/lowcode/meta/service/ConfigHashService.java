package com.yutong.lowcode.meta.service;

import com.yutong.lowcode.meta.domain.LcAction;
import com.yutong.lowcode.meta.domain.LcComponent;
import com.yutong.lowcode.meta.domain.LcField;
import com.yutong.lowcode.meta.domain.LcRelation;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 元模型 config_hash 计算服务。设计来源: 14-低代码平台设计 版本与发布
 * 发布时生成不可变快照，hash 用于 diff 比对和版本一致性校验。
 * SHA-256 摘要 + 字段排序保证同一配置生成的 hash 稳定。
 */
@Service
public class ConfigHashService {

    /**
     * 计算实体配置 hash（entityCode + tableName + fields + relations）
     */
    public String computeEntityHash(String entityCode, String tableName,
                                    List<LcField> fields, List<LcRelation> relations) {
        StringBuilder sb = new StringBuilder();
        sb.append("entity=").append(nullToEmpty(entityCode)).append('|');
        sb.append("table=").append(nullToEmpty(tableName)).append('|');
        if (fields != null) {
            fields.stream()
                    .sorted(this::compareField)
                    .forEach(f -> sb.append(fieldSignature(f)).append(';'));
        }
        sb.append('|');
        if (relations != null) {
            relations.stream()
                    .sorted(this::compareRelation)
                    .forEach(r -> sb.append(relationSignature(r)).append(';'));
        }
        return sha256(sb.toString());
    }

    /**
     * 计算页面配置 hash（pageCode + pageType + layout + components + actions）
     */
    public String computePageHash(String pageCode, String pageType, String layoutJson,
                                  List<LcComponent> components, List<LcAction> actions) {
        StringBuilder sb = new StringBuilder();
        sb.append("page=").append(nullToEmpty(pageCode)).append('|');
        sb.append("type=").append(nullToEmpty(pageType)).append('|');
        sb.append("layout=").append(nullToEmpty(layoutJson)).append('|');
        if (components != null) {
            components.stream()
                    .sorted(this::compareComponent)
                    .forEach(c -> sb.append(componentSignature(c)).append(';'));
        }
        sb.append('|');
        if (actions != null) {
            actions.stream()
                    .sorted(this::compareAction)
                    .forEach(a -> sb.append(actionSignature(a)).append(';'));
        }
        return sha256(sb.toString());
    }

    private String fieldSignature(LcField f) {
        return nullToEmpty(f.getFieldCode()) + ':'
                + nullToEmpty(f.getDataType()) + ':'
                + nullToEmpty(f.getDbColumn()) + ':'
                + (Boolean.TRUE.equals(f.getPrimaryFlag()) ? "PK" : "NP");
    }

    private String relationSignature(LcRelation r) {
        return nullToEmpty(r.getTargetEntityId()) + ':'
                + nullToEmpty(r.getRelationType()) + ':'
                + nullToEmpty(r.getSourceFieldCode());
    }

    private String componentSignature(LcComponent c) {
        return nullToEmpty(c.getComponentCode()) + ':'
                + nullToEmpty(c.getComponentType()) + ':'
                + nullToEmpty(c.getPropsJson());
    }

    private String actionSignature(LcAction a) {
        return nullToEmpty(a.getActionCode()) + ':'
                + nullToEmpty(a.getActionType()) + ':'
                + nullToEmpty(a.getApiMethod()) + ':'
                + nullToEmpty(a.getApiPath());
    }

    private int compareField(LcField a, LcField b) {
        return nullToEmpty(a.getFieldCode()).compareTo(nullToEmpty(b.getFieldCode()));
    }

    private int compareRelation(LcRelation a, LcRelation b) {
        return nullToEmpty(a.getSourceFieldCode()).compareTo(nullToEmpty(b.getSourceFieldCode()));
    }

    private int compareComponent(LcComponent a, LcComponent b) {
        return nullToEmpty(a.getComponentCode()).compareTo(nullToEmpty(b.getComponentCode()));
    }

    private int compareAction(LcAction a, LcAction b) {
        return nullToEmpty(a.getActionCode()).compareTo(nullToEmpty(b.getActionCode()));
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
