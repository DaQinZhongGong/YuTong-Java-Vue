package com.yutong.lowcode.rule.engine;

import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;

/**
 * 白名单函数集合。设计来源: 36-低代码高级能力设计 (line 72)。
 *
 * <p>所有方法签名统一为 {@code Object} 入参并在内部做类型转换，便于
 * {@link ExpressionEngine} 通过反射按名调用，避免重载/装箱歧义。
 *
 * <p>函数清单:
 * <ul>
 *   <li>数学: abs / round / ceil / floor / min / max</li>
 *   <li>字符串: length / upper / lower / trim / concat / substring / contains / indexOf / startsWith / endsWith / replace</li>
 *   <li>日期: now / formatDate / dateAdd / dateDiff (java.time, yyyy-MM-dd / yyyy-MM-dd HH:mm:ss)</li>
 *   <li>字典: dict(key) 从上下文 dict Map 取值</li>
 *   <li>空值: isNull / notNull / isEmpty / isNotEmpty</li>
 *   <li>集合: size</li>
 *   <li>逻辑: if(cond, a, b) 三元 (方法名 iif，由引擎别名映射)</li>
 * </ul>
 *
 * <p>非 final、方法非 final，允许测试子类化覆盖以构造超时场景。
 */
@Component
public class WhiteListFunctions {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 求值上下文 ThreadLocal，供 dict() 等需要访问上下文的函数使用。由 ExpressionEngine 设置。 */
    private static final ThreadLocal<Map<String, Object>> CONTEXT = new ThreadLocal<>();

    static void setContext(Map<String, Object> ctx) {
        CONTEXT.set(ctx);
    }

    static void clearContext() {
        CONTEXT.remove();
    }

    // ==================== 数学 ====================

    public Object abs(Object n) {
        return Math.abs(toDouble(n));
    }

    public Object round(Object n, Object scale) {
        int s = toInt(scale);
        double v = toDouble(n);
        return BigDecimal.valueOf(v).setScale(s, RoundingMode.HALF_UP).doubleValue();
    }

    public Object ceil(Object n) {
        return Math.ceil(toDouble(n));
    }

    public Object floor(Object n) {
        return Math.floor(toDouble(n));
    }

    public Object min(Object a, Object b) {
        return Math.min(toDouble(a), toDouble(b));
    }

    public Object max(Object a, Object b) {
        return Math.max(toDouble(a), toDouble(b));
    }

    // ==================== 字符串 ====================

    public Object length(Object s) {
        return toStr(s).length();
    }

    public Object upper(Object s) {
        return toStr(s).toUpperCase();
    }

    public Object lower(Object s) {
        return toStr(s).toLowerCase();
    }

    public Object trim(Object s) {
        return toStr(s).trim();
    }

    public Object concat(Object a, Object b) {
        return toStr(a) + toStr(b);
    }

    public Object substring(Object s, Object begin, Object end) {
        String str = toStr(s);
        return str.substring(toInt(begin), toInt(end));
    }

    public Object contains(Object s, Object sub) {
        return toStr(s).contains(toStr(sub));
    }

    public Object indexOf(Object s, Object sub) {
        return toStr(s).indexOf(toStr(sub));
    }

    public Object startsWith(Object s, Object prefix) {
        return toStr(s).startsWith(toStr(prefix));
    }

    public Object endsWith(Object s, Object suffix) {
        return toStr(s).endsWith(toStr(suffix));
    }

    public Object replace(Object s, Object old, Object neo) {
        return toStr(s).replace(toStr(old), toStr(neo));
    }

    // ==================== 日期 ====================

    public Object now() {
        return LocalDateTime.now().format(DATETIME_FMT);
    }

    public Object formatDate(Object dateStr, Object pattern) {
        LocalDateTime dt = parseDate(toStr(dateStr));
        return dt.format(DateTimeFormatter.ofPattern(toStr(pattern)));
    }

    public Object dateAdd(Object dateStr, Object days) {
        LocalDate d = parseDate(toStr(dateStr)).toLocalDate();
        return d.plusDays(toLong(days)).format(DATE_FMT);
    }

    public Object dateDiff(Object d1, Object d2) {
        LocalDate a = parseDate(toStr(d1)).toLocalDate();
        LocalDate b = parseDate(toStr(d2)).toLocalDate();
        return ChronoUnit.DAYS.between(b, a);
    }

    // ==================== 字典 ====================

    public Object dict(Object key) {
        Map<String, Object> ctx = CONTEXT.get();
        if (ctx == null) {
            return null;
        }
        Object dictMap = ctx.get("dict");
        if (dictMap instanceof Map<?, ?> m) {
            return m.get(key);
        }
        return null;
    }

    // ==================== 空值 ====================

    public Object isNull(Object v) {
        return v == null;
    }

    public Object notNull(Object v) {
        return v != null;
    }

    public Object isEmpty(Object v) {
        if (v == null) {
            return true;
        }
        if (v instanceof CharSequence c) {
            return c.isEmpty();
        }
        if (v instanceof Collection<?> c) {
            return c.isEmpty();
        }
        if (v instanceof Map<?, ?> m) {
            return m.isEmpty();
        }
        return false;
    }

    public Object isNotEmpty(Object v) {
        return !(Boolean) isEmpty(v);
    }

    // ==================== 集合 ====================

    public Object size(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Collection<?> c) {
            return c.size();
        }
        if (v instanceof Map<?, ?> m) {
            return m.size();
        }
        if (v instanceof CharSequence c) {
            return c.length();
        }
        throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "size() 仅支持集合/Map/字符串");
    }

    // ==================== 逻辑 ====================

    /** 三元函数 if(cond, a, b)。表达式侧函数名为 if，由引擎别名映射到本方法。 */
    public Object iif(Object cond, Object a, Object b) {
        return toBool(cond) ? a : b;
    }

    // ==================== 类型转换工具 ====================

    private static double toDouble(Object o) {
        if (o == null) {
            return 0d;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o instanceof Boolean b) {
            return b ? 1d : 0d;
        }
        if (o instanceof String s) {
            if (s.isBlank()) {
                return 0d;
            }
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "无法转为数字: " + s);
            }
        }
        throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "无法转为数字: " + o);
    }

    private static int toInt(Object o) {
        return (int) toDouble(o);
    }

    private static long toLong(Object o) {
        return (long) toDouble(o);
    }

    private static String toStr(Object o) {
        return o == null ? "" : o.toString();
    }

    private static boolean toBool(Object o) {
        if (o == null) {
            return false;
        }
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof Number n) {
            return n.doubleValue() != 0d;
        }
        if (o instanceof String s) {
            return Boolean.parseBoolean(s.trim());
        }
        return true;
    }

    private static LocalDateTime parseDate(String s) {
        if (s == null || s.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "日期参数为空");
        }
        String t = s.trim();
        try {
            if (t.length() > 10) {
                return LocalDateTime.parse(t, DATETIME_FMT);
            }
            return LocalDate.parse(t, DATE_FMT).atStartOfDay();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "日期格式无效 (需 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss): " + s);
        }
    }
}
