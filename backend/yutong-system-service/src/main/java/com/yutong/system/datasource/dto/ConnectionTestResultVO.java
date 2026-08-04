package com.yutong.system.datasource.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 连接测试结果 VO。GA2-46 v1.5。
 * <p>
 * 安全约束 (46 号文档 line 128): 连接测试失败不得泄露数据库地址、用户名、密码或完整驱动异常。
 * 只返回连通性状态和脱敏摘要。
 */
@Data
public class ConnectionTestResultVO {

    /** 是否连通 */
    private Boolean connected;

    /** 健康状态: UP/DOWN */
    private String healthStatus;

    /** 数据库产品名 (如 PostgreSQL) */
    private String databaseProductName;

    /** 数据库版本 (脱敏, 不泄露具体补丁版本) */
    private String databaseProductVersion;

    /** 测试耗时 (毫秒) */
    private Long latencyMs;

    /** 测试时间 */
    private OffsetDateTime testTime;

    /** 脱敏错误摘要 (不泄露数据库地址/用户名/密码/完整驱动异常) */
    private String errorMessage;

    public static ConnectionTestResultVO success(String productName, String productVersion, Long latencyMs) {
        ConnectionTestResultVO vo = new ConnectionTestResultVO();
        vo.setConnected(true);
        vo.setHealthStatus("UP");
        vo.setDatabaseProductName(productName);
        vo.setDatabaseProductVersion(productVersion);
        vo.setLatencyMs(latencyMs);
        vo.setTestTime(OffsetDateTime.now());
        return vo;
    }

    public static ConnectionTestResultVO failure(String maskedError, Long latencyMs) {
        ConnectionTestResultVO vo = new ConnectionTestResultVO();
        vo.setConnected(false);
        vo.setHealthStatus("DOWN");
        vo.setLatencyMs(latencyMs);
        vo.setTestTime(OffsetDateTime.now());
        vo.setErrorMessage(maskedError);
        return vo;
    }
}
