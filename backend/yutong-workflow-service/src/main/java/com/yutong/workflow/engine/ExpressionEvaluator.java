package com.yutong.workflow.engine;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 流程变量表达式求值器。设计来源: 41-工作流与BPMN引擎设计。
 *
 * <p>GA2-44 L1+L2 轻量自研引擎实现: 支持 BPMN 表达式语法:
 * <ul>
 *   <li>变量引用: ${variableName}</li>
 *   <li>比较运算: &gt;, &lt;, &gt;=, &lt;=, ==, !=</li>
 *   <li>逻辑运算: &amp;&amp;, ||, !</li>
 *   <li>字面量: 数字 (整数/小数), 字符串 (单引号/双引号), true/false</li>
 * </ul>
 *
 * <p>不支持: 方法调用, 属性访问, 集合操作 (SpEL 留待后续版本)。
 * <p>安全: 不使用 SpEL/OGNL/JEXL 等通用表达式引擎, 避免代码注入风险。
 */
public class ExpressionEvaluator {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * 解析变量引用 ${variableName} 字面量为变量值。
     * <p>例如: ${starter_manager} -> 流程变量 starter_manager 的值。
     */
    public static String resolveVariable(String expr, Map<String, Object> variables) {
        if (expr == null) return null;
        Matcher matcher = VARIABLE_PATTERN.matcher(expr.trim());
        if (matcher.matches()) {
            String varName = matcher.group(1).trim();
            Object value = variables.get(varName);
            return value == null ? null : value.toString();
        }
        return expr;
    }

    /**
     * 求值条件表达式 (用于 ExclusiveGateway 出边选择)。
     * <p>支持: ${totalAmount > 10000}, ${status == 'APPROVED'}, ${amount > 100 && type == 'VIP'}
     *
     * @param expression 条件表达式 (含 ${...} 包裹或裸表达式)
     * @param variables  流程变量
     * @return 表达式求值结果
     */
    public static boolean evaluateCondition(String expression, Map<String, Object> variables) {
        if (expression == null || expression.isBlank()) {
            // 无条件表达式 = 默认流 (Else 分支)
            return true;
        }

        String expr = expression.trim();
        // 去掉 ${...} 包裹
        Matcher matcher = VARIABLE_PATTERN.matcher(expr);
        if (matcher.matches()) {
            expr = matcher.group(1).trim();
        }

        // 逻辑与 (&&): 拆分多个子表达式
        if (expr.contains("&&")) {
            String[] parts = expr.split("&&");
            for (String part : parts) {
                if (!evaluateSingleCondition(part.trim(), variables)) {
                    return false;
                }
            }
            return true;
        }

        // 逻辑或 (||): 拆分多个子表达式
        if (expr.contains("||")) {
            String[] parts = expr.split("\\|\\|");
            for (String part : parts) {
                if (evaluateSingleCondition(part.trim(), variables)) {
                    return true;
                }
            }
            return false;
        }

        // 逻辑非 (!): 取反
        if (expr.startsWith("!")) {
            return !evaluateSingleCondition(expr.substring(1).trim(), variables);
        }

        return evaluateSingleCondition(expr, variables);
    }

    /**
     * 求值单个条件表达式。
     * <p>支持: &gt;, &lt;, &gt;=, &lt;=, ==, !=
     */
    private static boolean evaluateSingleCondition(String expr, Map<String, Object> variables) {
        expr = expr.trim();

        // 处理 XML 实体转义
        expr = expr.replace("&gt;", ">").replace("&lt;", "<").replace("&amp;", "&");

        // 查找比较运算符 (注意顺序: >=, <=, ==, !=, >, <)
        String[] operators = {">=", "<=", "==", "!=", ">", "<"};
        for (String op : operators) {
            int idx = expr.indexOf(op);
            if (idx > 0) {
                String left = expr.substring(0, idx).trim();
                String right = expr.substring(idx + op.length()).trim();
                return compareValues(left, right, op, variables);
            }
        }

        // 无运算符: 视为布尔变量引用 (变量值为 true/false)
        Object value = resolveValue(expr, variables);
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return value != null;
    }

    @SuppressWarnings("unchecked")
    private static boolean compareValues(String left, String right, String op, Map<String, Object> variables) {
        Object leftValue = resolveValue(left, variables);
        Object rightValue = resolveValue(right, variables);

        // 数字比较
        if (isNumeric(leftValue) && isNumeric(rightValue)) {
            double lv = toDouble(leftValue);
            double rv = toDouble(rightValue);
            return switch (op) {
                case ">" -> lv > rv;
                case "<" -> lv < rv;
                case ">=" -> lv >= rv;
                case "<=" -> lv <= rv;
                case "==" -> lv == rv;
                case "!=" -> lv != rv;
                default -> false;
            };
        }

        // 字符串比较
        String ls = leftValue == null ? "" : leftValue.toString();
        String rs = rightValue == null ? "" : rightValue.toString();
        return switch (op) {
            case "==" -> ls.equals(rs);
            case "!=" -> !ls.equals(rs);
            case ">" -> ls.compareTo(rs) > 0;
            case "<" -> ls.compareTo(rs) < 0;
            case ">=" -> ls.compareTo(rs) >= 0;
            case "<=" -> ls.compareTo(rs) <= 0;
            default -> false;
        };
    }

    private static Object resolveValue(String token, Map<String, Object> variables) {
        if (token == null) return null;
        token = token.trim();

        // 字符串字面量 (单引号或双引号)
        if ((token.startsWith("'") && token.endsWith("'")) ||
                (token.startsWith("\"") && token.endsWith("\""))) {
            return token.substring(1, token.length() - 1);
        }

        // 数字字面量
        if (token.matches("-?\\d+")) {
            return Long.parseLong(token);
        }
        if (token.matches("-?\\d+\\.\\d+")) {
            return Double.parseDouble(token);
        }

        // 布尔字面量
        if ("true".equals(token)) return true;
        if ("false".equals(token)) return false;

        // 变量引用
        return variables.get(token);
    }

    private static boolean isNumeric(Object value) {
        if (value == null) return false;
        if (value instanceof Number) return true;
        if (value instanceof String s) {
            return s.matches("-?\\d+(\\.\\d+)?");
        }
        return false;
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) return Double.parseDouble(s);
        throw new IllegalArgumentException("Cannot convert " + value + " to double");
    }
}
