package com.yutong.infra.persistence;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * PostgreSQL jsonb 字段 TypeHandler。
 * 设计来源: 98-后端实现蓝图、57-完整DDL清单（ai_message.citation_json 等 jsonb 字段）
 * 修复 API-ISSUE-005: MyBatis-Plus 默认 setString 写入 jsonb 列报类型不匹配。
 * 使用 setObject(parameter, Types.OTHER) 让 PostgreSQL JDBC 驱动以无类型传入，
 * 由服务端按列定义（jsonb）解析，避免 "is of type jsonb but expression is of type character varying"。
 */
@MappedJdbcTypes(JdbcType.OTHER)
@MappedTypes(String.class)
public class JsonbTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setObject(i, parameter, Types.OTHER);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }
}
