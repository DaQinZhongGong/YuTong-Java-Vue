package com.yutong.lowcode.rule.engine;

import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则表达式求值引擎。设计来源: 36-低代码高级能力设计 (line 59-72)。
 *
 * <p>自研轻量表达式引擎 (不使用 SpEL/OGNL/JEXL，避免代码注入)。
 * 复用 {@code yutong-workflow-service ExpressionEvaluator} 的安全思路，并扩展白名单函数调用。
 *
 * <p>支持语法:
 * <ul>
 *   <li>变量引用: {@code ${form.amount}} / {@code ${user.name}} (上下文 form/row/user/tenant/dict/params，支持点号路径)</li>
 *   <li>裸变量: {@code amount} (在上下文中查找，找不到返回 null)</li>
 *   <li>比较运算: {@code > < >= <= == !=}</li>
 *   <li>逻辑运算: {@code && || !}</li>
 *   <li>字面量: 数字 / 字符串 (单双引号) / {@code true} / {@code false} / {@code null}</li>
 *   <li>白名单函数: {@code func(arg1, arg2)} 仅允许 {@link WhiteListFunctions} 声明的方法</li>
 *   <li>括号分组: {@code (expr)}</li>
 * </ul>
 *
 * <p>不支持 (解析期即拒绝): 属性访问 {@code a.b} (裸)、方法链、集合操作、{@code new}、反射。
 *
 * <p>安全:
 * <ul>
 *   <li>长度校验: 单条表达式 ≤ {@value #MAX_LENGTH} 字符</li>
 *   <li>危险关键字黑名单: Runtime/Process/Class.forName/exec/system/exit/反射/SQL/HTTP 等</li>
 *   <li>超时熔断: 单次执行默认 {@value #DEFAULT_TIMEOUT_MS}ms，基于 {@code Future.get(timeout)} 实现</li>
 * </ul>
 */
@Component
public class ExpressionEngine {

    /** 单条表达式最大长度。 */
    static final int MAX_LENGTH = 1000;

    /** 单次执行默认超时 (毫秒)。 */
    static final long DEFAULT_TIMEOUT_MS = 50L;

    /** 表达式侧函数名 -> WhiteListFunctions 方法名 别名映射 (规避 Java 关键字 if)。 */
    private static final Map<String, String> FN_ALIAS = Map.of("if", "iif");

    /**
     * 危险关键字黑名单 (case-insensitive)。
     * 覆盖: 反射 / 类加载 / 系统命令 / 退出 / HTTP 外联 / JDBC / new / import。
     * 使用词边界匹配以减少对普通字段名的误伤 (如 systemStatus 不会命中 system)。
     */
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile(
            // 反射 / 类加载
            "(?i)\\b(runtime|process|processbuilder|forname|getclass|getclassloader|loadclass|reflect)\\b"
            // 系统命令 / 退出
            + "|(?i)\\b(exec|system|exit)\\b"
            // Class.forName (允许中间空白)
            + "|(?i)class\\s*\\.\\s*forname"
            // 反射/命令式方法调用 .invoke( / .exec( / .forName( / .load( / .getClass(
            + "|(?i)\\.(exec|invoke|forname|getclass|load)\\s*\\("
            // HTTP / 网络外联 (URL 字面量与连接 API)
            + "|(?i)https?://"
            + "|(?i)\\b(urlconnection|httpclient|openconnection|openstream|socket|serversocket)\\b"
            // JDBC / SQL 执行 API
            + "|(?i)\\b(drivermanager|preparedstatement|createstatement|executequery|executeupdate)\\b"
            // new / import 关键字 (引擎不支持实例化)
            + "|(?i)\\b(new|import)\\b");

    private static final String COMP_OPS = "> < >= <= == !=";

    private final WhiteListFunctions functions;
    private final ExecutorService executor;
    private final Map<String, Method> methodCache = new HashMap<>();
    private volatile long timeoutMs = DEFAULT_TIMEOUT_MS;

    public ExpressionEngine(WhiteListFunctions functions) {
        this.functions = functions;
        // 虚拟线程: 单次求值超时熔断用的轻量线程，daemon，创建/销毁开销极低
        this.executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        cacheFunctions();
    }

    private void cacheFunctions() {
        for (Method m : WhiteListFunctions.class.getDeclaredMethods()) {
            int mod = m.getModifiers();
            if (Modifier.isStatic(mod) || Modifier.isPrivate(mod) || m.isSynthetic()) {
                continue;
            }
            // key = 方法名:参数个数
            methodCache.put(m.getName() + ":" + m.getParameterCount(), m);
        }
    }

    /** 设置超时阈值 (主要供测试使用)。 */
    void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    // ==================== 公开 API ====================

    /**
     * 求值表达式，返回原始结果 (可能是 Boolean/Number/String/null 或函数返回值)。
     */
    public Object evaluate(String expr, Map<String, Object> context) {
        String checked = preCheck(expr);
        final Map<String, Object> ctx = context == null ? Map.of() : context;
        Future<Object> future = executor.submit(() -> {
            WhiteListFunctions.setContext(ctx);
            try {
                List<Token> tokens = tokenize(checked);
                Expr ast = new Parser(tokens).parse();
                return eval(ast, ctx);
            } finally {
                WhiteListFunctions.clearContext();
            }
        });
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "表达式执行超时 (>" + timeoutMs + "ms)");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof BusinessException be) {
                throw be;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "表达式执行失败: " + (cause == null ? "" : cause.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "表达式执行被中断");
        }
    }

    /**
     * 求值条件表达式，返回布尔结果 (用于 VALIDATION/VISIBILITY/READONLY 规则)。
     */
    public boolean evaluateCondition(String expr, Map<String, Object> context) {
        return toBool(evaluate(expr, context));
    }

    /**
     * 校验表达式语法 (含长度/黑名单/词法/语法)，不执行求值。
     */
    public ValidationResult validate(String expr) {
        try {
            String checked = preCheck(expr);
            List<Token> tokens = tokenize(checked);
            new Parser(tokens).parse();
            return new ValidationResult(true, null);
        } catch (BusinessException e) {
            return new ValidationResult(false, messageOf(e));
        } catch (Exception e) {
            return new ValidationResult(false, e.getMessage());
        }
    }

    /** 语法校验结果。 */
    public record ValidationResult(boolean valid, String error) {
    }

    // ==================== 预检: 长度 + 黑名单 ====================

    private String preCheck(String expr) {
        if (expr == null) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "表达式为空");
        }
        String e = expr.trim();
        if (e.isEmpty()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "表达式为空");
        }
        if (e.length() > MAX_LENGTH) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "表达式长度超限 (>" + MAX_LENGTH + " 字符)");
        }
        Matcher m = DANGEROUS_PATTERN.matcher(e);
        if (m.find()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "表达式含危险关键字: " + m.group());
        }
        return e;
    }

    private static String messageOf(BusinessException e) {
        return e.customMessage() != null ? e.customMessage() : e.getMessage();
    }

    // ==================== AST ====================

    private sealed interface Expr permits Lit, Var, Name, Call, Not, Bin {
    }

    private record Lit(Object value) implements Expr {
    }

    private record Var(String path) implements Expr {
    }

    private record Name(String name) implements Expr {
    }

    private record Call(String fn, List<Expr> args) implements Expr {
    }

    private record Not(Expr e) implements Expr {
    }

    private record Bin(String op, Expr left, Expr right) implements Expr {
    }

    // ==================== 求值 ====================

    private Object eval(Expr node, Map<String, Object> ctx) {
        return switch (node) {
            case Lit lit -> lit.value;
            case Var var -> resolveVar(var.path, ctx);
            case Name name -> ctx.get(name.name);
            case Not not -> !toBool(eval(not.e, ctx));
            case Call call -> invokeFunction(call.fn, call.args, ctx);
            case Bin bin -> evalBin(bin, ctx);
        };
    }

    private Object evalBin(Bin bin, Map<String, Object> ctx) {
        // 逻辑短路
        if ("&&".equals(bin.op)) {
            return toBool(eval(bin.left, ctx)) && toBool(eval(bin.right, ctx));
        }
        if ("||".equals(bin.op)) {
            return toBool(eval(bin.left, ctx)) || toBool(eval(bin.right, ctx));
        }
        // 比较
        Object lv = eval(bin.left, ctx);
        Object rv = eval(bin.right, ctx);
        return compare(lv, rv, bin.op);
    }

    @SuppressWarnings("unchecked")
    private Object resolveVar(String path, Map<String, Object> ctx) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        String[] parts = path.split("\\.");
        Object cur = ctx;
        for (String p : parts) {
            if (cur instanceof Map<?, ?> m) {
                cur = m.get(p);
            } else {
                return null;
            }
        }
        return cur;
    }

    private Object compare(Object l, Object r, String op) {
        if (isNumeric(l) && isNumeric(r)) {
            double lv = toDouble(l);
            double rv = toDouble(r);
            return switch (op) {
                case ">" -> lv > rv;
                case "<" -> lv < rv;
                case ">=" -> lv >= rv;
                case "<=" -> lv <= rv;
                case "==" -> lv == rv;
                case "!=" -> lv != rv;
                default -> throw syntaxError("未知比较运算符: " + op);
            };
        }
        if ("==".equals(op)) {
            return Objects.equals(l, r);
        }
        if ("!=".equals(op)) {
            return !Objects.equals(l, r);
        }
        // 非数字按字符串字典序比较
        String ls = l == null ? "" : l.toString();
        String rs = r == null ? "" : r.toString();
        return switch (op) {
            case ">" -> ls.compareTo(rs) > 0;
            case "<" -> ls.compareTo(rs) < 0;
            case ">=" -> ls.compareTo(rs) >= 0;
            case "<=" -> ls.compareTo(rs) <= 0;
            default -> throw syntaxError("未知比较运算符: " + op);
        };
    }

    private Object invokeFunction(String name, List<Expr> argExprs, Map<String, Object> ctx) {
        String methodName = FN_ALIAS.getOrDefault(name, name);
        Object[] args = new Object[argExprs.size()];
        for (int i = 0; i < argExprs.size(); i++) {
            args[i] = eval(argExprs.get(i), ctx);
        }
        Method m = methodCache.get(methodName + ":" + args.length);
        if (m == null) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "未知或参数数量不符的白名单函数: " + name + "(" + args.length + " 参数)");
        }
        try {
            return m.invoke(functions, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof BusinessException be) {
                throw be;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "函数执行失败: " + name + " (" + (cause == null ? "" : cause.getMessage()) + ")");
        } catch (IllegalAccessException e) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "函数不可访问: " + name);
        }
    }

    private static boolean isNumeric(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Number) {
            return true;
        }
        if (v instanceof String s) {
            return s.matches("-?\\d+(\\.\\d+)?");
        }
        return false;
    }

    private static double toDouble(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s) {
            return Double.parseDouble(s);
        }
        throw syntaxError("无法转为数字: " + v);
    }

    private static boolean toBool(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.doubleValue() != 0d;
        }
        if (v instanceof String s) {
            return Boolean.parseBoolean(s.trim());
        }
        return true;
    }

    private static BusinessException syntaxError(String msg) {
        return new BusinessException(ErrorCode.SYS_PARAM_INVALID, msg);
    }

    // ==================== 词法分析 ====================

    private enum TokenType {
        NUMBER, STRING, VAR, IDENT, OP, LPAREN, RPAREN, COMMA, EOF
    }

    private static final class Token {
        final TokenType type;
        final String text;
        final Object value;

        Token(TokenType type, String text, Object value) {
            this.type = type;
            this.text = text;
            this.value = value;
        }
    }

    private List<Token> tokenize(String s) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            // 变量引用 ${...}
            if (c == '$' && i + 1 < n && s.charAt(i + 1) == '{') {
                int j = s.indexOf('}', i + 2);
                if (j < 0) {
                    throw syntaxError("未闭合的变量引用 ${...}");
                }
                String inner = s.substring(i + 2, j).trim();
                if (inner.isEmpty()) {
                    throw syntaxError("空的变量引用 ${}");
                }
                out.add(new Token(TokenType.VAR, inner, inner));
                i = j + 1;
                continue;
            }
            // 字符串字面量
            if (c == '\'' || c == '"') {
                char quote = c;
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < n && s.charAt(i) != quote) {
                    char ch = s.charAt(i);
                    if (ch == '\\' && i + 1 < n) {
                        char nx = s.charAt(i + 1);
                        switch (nx) {
                            case 'n' -> sb.append('\n');
                            case 't' -> sb.append('\t');
                            case 'r' -> sb.append('\r');
                            case '\\' -> sb.append('\\');
                            case '\'' -> sb.append('\'');
                            case '"' -> sb.append('"');
                            default -> sb.append(nx);
                        }
                        i += 2;
                    } else {
                        sb.append(ch);
                        i++;
                    }
                }
                if (i >= n) {
                    throw syntaxError("未闭合的字符串字面量");
                }
                i++; // 跳过结束引号
                out.add(new Token(TokenType.STRING, sb.toString(), sb.toString()));
                continue;
            }
            // 数字 (含负号: '-' 后跟数字视为负数，引擎不支持减法运算)
            if (Character.isDigit(c) || (c == '-' && i + 1 < n && Character.isDigit(s.charAt(i + 1)))) {
                int start = i;
                if (c == '-') {
                    i++;
                }
                while (i < n && Character.isDigit(s.charAt(i))) {
                    i++;
                }
                if (i < n && s.charAt(i) == '.') {
                    i++;
                    while (i < n && Character.isDigit(s.charAt(i))) {
                        i++;
                    }
                    String num = s.substring(start, i);
                    out.add(new Token(TokenType.NUMBER, num, Double.parseDouble(num)));
                } else {
                    String num = s.substring(start, i);
                    out.add(new Token(TokenType.NUMBER, num, Long.parseLong(num)));
                }
                continue;
            }
            // 标识符
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) {
                    i++;
                }
                String id = s.substring(start, i);
                out.add(new Token(TokenType.IDENT, id, id));
                continue;
            }
            // 运算符
            if (c == '&' && i + 1 < n && s.charAt(i + 1) == '&') {
                out.add(new Token(TokenType.OP, "&&", "&&"));
                i += 2;
                continue;
            }
            if (c == '|' && i + 1 < n && s.charAt(i + 1) == '|') {
                out.add(new Token(TokenType.OP, "||", "||"));
                i += 2;
                continue;
            }
            if (c == '!') {
                if (i + 1 < n && s.charAt(i + 1) == '=') {
                    out.add(new Token(TokenType.OP, "!=", "!="));
                    i += 2;
                } else {
                    out.add(new Token(TokenType.OP, "!", "!"));
                    i++;
                }
                continue;
            }
            if (c == '>') {
                if (i + 1 < n && s.charAt(i + 1) == '=') {
                    out.add(new Token(TokenType.OP, ">=", ">="));
                    i += 2;
                } else {
                    out.add(new Token(TokenType.OP, ">", ">"));
                    i++;
                }
                continue;
            }
            if (c == '<') {
                if (i + 1 < n && s.charAt(i + 1) == '=') {
                    out.add(new Token(TokenType.OP, "<=", "<="));
                    i += 2;
                } else {
                    out.add(new Token(TokenType.OP, "<", "<"));
                    i++;
                }
                continue;
            }
            if (c == '=' && i + 1 < n && s.charAt(i + 1) == '=') {
                out.add(new Token(TokenType.OP, "==", "=="));
                i += 2;
                continue;
            }
            if (c == '(') {
                out.add(new Token(TokenType.LPAREN, "(", "("));
                i++;
                continue;
            }
            if (c == ')') {
                out.add(new Token(TokenType.RPAREN, ")", ")"));
                i++;
                continue;
            }
            if (c == ',') {
                out.add(new Token(TokenType.COMMA, ",", ","));
                i++;
                continue;
            }
            throw syntaxError("非法字符: '" + c + "'");
        }
        out.add(new Token(TokenType.EOF, "", ""));
        return out;
    }

    // ==================== 语法分析 (递归下降) ====================

    private final class Parser {
        private final List<Token> tokens;
        private int pos;

        Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        Expr parse() {
            Expr e = parseOr();
            if (peek().type != TokenType.EOF) {
                throw syntaxError("多余 token: " + peek().text);
            }
            return e;
        }

        private Expr parseOr() {
            Expr l = parseAnd();
            while (isOp("||")) {
                next();
                l = new Bin("||", l, parseAnd());
            }
            return l;
        }

        private Expr parseAnd() {
            Expr l = parseNot();
            while (isOp("&&")) {
                next();
                l = new Bin("&&", l, parseNot());
            }
            return l;
        }

        private Expr parseNot() {
            if (isOp("!")) {
                next();
                return new Not(parseNot());
            }
            return parseComparison();
        }

        private Expr parseComparison() {
            Expr l = parsePrimary();
            if (isOpAny(COMP_OPS)) {
                String op = next().text;
                Expr r = parsePrimary();
                return new Bin(op, l, r);
            }
            return l;
        }

        private Expr parsePrimary() {
            Token t = peek();
            switch (t.type) {
                case NUMBER, STRING -> {
                    next();
                    return new Lit(t.value);
                }
                case VAR -> {
                    next();
                    return new Var((String) t.value);
                }
                case LPAREN -> {
                    next();
                    Expr e = parseOr();
                    expect(TokenType.RPAREN);
                    return e;
                }
                case IDENT -> {
                    next();
                    if (peek().type == TokenType.LPAREN) {
                        next();
                        List<Expr> args = new ArrayList<>();
                        if (peek().type != TokenType.RPAREN) {
                            args.add(parseOr());
                            while (peek().type == TokenType.COMMA) {
                                next();
                                args.add(parseOr());
                            }
                        }
                        expect(TokenType.RPAREN);
                        return new Call(t.text, args);
                    }
                    if ("true".equals(t.text)) {
                        return new Lit(Boolean.TRUE);
                    }
                    if ("false".equals(t.text)) {
                        return new Lit(Boolean.FALSE);
                    }
                    if ("null".equals(t.text)) {
                        return new Lit(null);
                    }
                    return new Name(t.text);
                }
                default -> throw syntaxError("意外的 token: " + (t.text.isEmpty() ? "<结束>" : t.text));
            }
        }

        private Token peek() {
            return tokens.get(pos);
        }

        private Token next() {
            return tokens.get(pos++);
        }

        private boolean isOp(String op) {
            Token t = peek();
            return t.type == TokenType.OP && t.text.equals(op);
        }

        private boolean isOpAny(String spaceSeparated) {
            Token t = peek();
            if (t.type != TokenType.OP) {
                return false;
            }
            for (String op : spaceSeparated.split(" ")) {
                if (op.equals(t.text)) {
                    return true;
                }
            }
            return false;
        }

        private void expect(TokenType type) {
            if (peek().type != type) {
                throw syntaxError("期望 " + type + " 但遇到 " + (peek().text.isEmpty() ? "<结束>" : peek().text));
            }
            next();
        }
    }
}
