package com.yutong.infra.datasource.safety;

import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.WithItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * SQL 安全沙箱: AST 静态分析禁止危险 SQL。
 * <p>
 * GA2-46 v1.5 设计来源: 46-多数据源与数据集设计 line 95-103。
 * <p>
 * 校验规则:
 * <ul>
 *   <li>仅允许单条 SELECT 或受控只读 VIEW 查询</li>
 *   <li>禁止多语句 (分号分隔)</li>
 *   <li>禁止注释绕过 (-- 行注释 和 /* 块注释)</li>
 *   <li>禁止 DDL: DROP/CREATE/ALTER/TRUNCATE</li>
 *   <li>禁止写操作: INSERT/UPDATE/DELETE/MERGE</li>
 *   <li>禁止 COPY (PostgreSQL 大批量数据导入导出)</li>
 *   <li>禁止 dblink_* 外部函数</li>
 *   <li>禁止 SELECT ... FOR UPDATE</li>
 *   <li>禁止临时表 (TEMP/TEMPORARY 关键字)</li>
 *   <li>禁止不可控 CTE 写入 (WITH ... INSERT/UPDATE/DELETE, 仅允许 WITH ... SELECT)</li>
 * </ul>
 */
@Component
public class SqlSafetyChecker {

    private static final Logger log = LoggerFactory.getLogger(SqlSafetyChecker.class);

    /** 禁止的 SQL 关键字 (大小写不敏感) */
    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "DROP", "CREATE", "ALTER", "TRUNCATE", "GRANT", "REVOKE",
            "INSERT", "UPDATE", "DELETE", "MERGE",
            "COPY", "VACUUM", "ANALYZE", "REINDEX", "CLUSTER",
            "CALL", "DO", "EXECUTE",
            "TEMP", "TEMPORARY",
            "DBLINK", "DBLINK_EXEC", "DBLINK_QUERY", "DBLINK_SEND_QUERY",
            "PG_READ_FILE", "PG_READ_BINARY_FILE", "PG_SLEEP",
            "LO_IMPORT", "LO_EXPORT",
            "CONVERT_FROM", "PG_LS_DIR"
    );

    /** 检测 FOR UPDATE 模式 (大小写不敏感, 允许中间空白) */
    private static final Pattern FOR_UPDATE_PATTERN = Pattern.compile(
            "\\bFOR\\s+UPDATE\\b", Pattern.CASE_INSENSITIVE);

    /** 检测分号 (多语句分隔符) */
    private static final Pattern SEMICOLON_PATTERN = Pattern.compile(";");

    /** 检测行注释 -- */
    private static final Pattern LINE_COMMENT_PATTERN = Pattern.compile("--");

    /** 检测块注释 /* */
    private static final Pattern BLOCK_COMMENT_PATTERN = Pattern.compile("/\\*");

    /**
     * 校验 SQL 安全性。
     *
     * @param sql 待校验的 SQL
     * @throws BusinessException DS-400002 仅允许只读 SELECT 或已批准 VIEW
     */
    public void check(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BusinessException(ErrorCode.DS_PARAMETER_INVALID, "SQL 不能为空");
        }
        String trimmed = sql.trim();
        // 移除末尾分号 (允许单条语句末尾加分号, 但禁止中间分号)
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        // 1. 多语句检查 (分号分隔)
        if (SEMICOLON_PATTERN.matcher(trimmed).find()) {
            throw new BusinessException(ErrorCode.DS_READ_ONLY_REQUIRED,
                    "SQL 安全检查失败: 禁止多语句 (分号分隔)");
        }
        // 2. 注释绕过检查
        if (LINE_COMMENT_PATTERN.matcher(trimmed).find()) {
            throw new BusinessException(ErrorCode.DS_READ_ONLY_REQUIRED,
                    "SQL 安全检查失败: 禁止行注释 (--)");
        }
        if (BLOCK_COMMENT_PATTERN.matcher(trimmed).find()) {
            throw new BusinessException(ErrorCode.DS_READ_ONLY_REQUIRED,
                    "SQL 安全检查失败: 禁止块注释 (/* */)");
        }
        // 3. FOR UPDATE 检查
        if (FOR_UPDATE_PATTERN.matcher(trimmed).find()) {
            throw new BusinessException(ErrorCode.DS_READ_ONLY_REQUIRED,
                    "SQL 安全检查失败: 禁止 SELECT ... FOR UPDATE");
        }
        // 4. AST 解析 (深度校验)
        Statement stmt;
        try {
            stmt = CCJSqlParserUtil.parse(trimmed);
        } catch (JSQLParserException e) {
            log.warn("SQL 解析失败 (可能是方言不支持, 视为不安全): {}", e.getMessage());
            throw new BusinessException(ErrorCode.DS_READ_ONLY_REQUIRED,
                    "SQL 安全检查失败: 解析失败 (" + e.getMessage() + ")");
        }
        // 5. 必须是 Select 语句
        if (!(stmt instanceof Select)) {
            throw new BusinessException(ErrorCode.DS_READ_ONLY_REQUIRED,
                    "SQL 安全检查失败: 仅允许 SELECT 语句, 实际类型=" + stmt.getClass().getSimpleName());
        }
        Select select = (Select) stmt;
        // 6. 检查 WITH 子句 (CTE): 仅允许 WITH ... SELECT, 禁止 WITH ... INSERT/UPDATE/DELETE
        // jsqlparser 4.x/5.x 跨版本兼容: 不直接调用 getSelectItem()/getSubSelect(),
        // 而是通过 toString() 包含 INSERT/UPDATE/DELETE 关键字判断 (后续第 7 步禁止关键字检查也会兜底)
        if (select.getWithItemsList() != null) {
            for (WithItem withItem : select.getWithItemsList()) {
                String withItemStr = withItem.toString().toUpperCase();
                if (withItemStr.contains("INSERT") || withItemStr.contains("UPDATE")
                        || withItemStr.contains("DELETE") || withItemStr.contains("MERGE")) {
                    throw new BusinessException(ErrorCode.DS_READ_ONLY_REQUIRED,
                            "SQL 安全检查失败: CTE 不允许写操作 (WITH ... INSERT/UPDATE/DELETE/MERGE)");
                }
            }
        }
        // 7. 禁止关键字检查 (在原始 SQL 中查找, 大小写不敏感)
        String upperSql = trimmed.toUpperCase();
        for (String kw : FORBIDDEN_KEYWORDS) {
            if (containsKeyword(upperSql, kw)) {
                throw new BusinessException(ErrorCode.DS_READ_ONLY_REQUIRED,
                        "SQL 安全检查失败: 禁止关键字 " + kw);
            }
        }
        // 通过所有检查
        if (log.isDebugEnabled()) {
            log.debug("SQL 安全检查通过: {}", trimmed.substring(0, Math.min(80, trimmed.length())));
        }
    }

    /**
     * 检查 SQL 中是否包含禁止关键字 (单词边界匹配, 避免误报如 column_name 包含 "drop")。
     */
    private boolean containsKeyword(String upperSql, String keyword) {
        // 使用单词边界匹配
        String pattern = "\\b" + keyword + "\\b";
        return Pattern.compile(pattern).matcher(upperSql).find();
    }
}
