package com.yutong.ai.tool.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * MCP 服务注册实体。设计来源: 13-AI能力设计、V038 ai_mcp_server
 * 约束: 租户内 server_code 唯一; transport in sse/stdio/http; config不落明文密钥。
 */
@Getter
@Setter
@TableName("ai_mcp_server")
public class AiMcpServer extends BaseEntity {

    public static final String TRANSPORT_SSE = "sse";
    public static final String TRANSPORT_STDIO = "stdio";
    public static final String TRANSPORT_HTTP = "http";

    public static final String STATUS_UNKNOWN = "UNKNOWN";
    public static final String STATUS_HEALTHY = "HEALTHY";
    public static final String STATUS_UNHEALTHY = "UNHEALTHY";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String STATUS_CONNECTING = "CONNECTING";

    /** 服务编码，租户内唯一 */
    private String serverCode;

    /** 服务名称 */
    private String serverName;

    /** 传输类型: sse/stdio/http */
    private String transport;

    /** 服务端点 URL (sse/http 必填，stdio 可为空) */
    private String endpoint;

    /** 传输配置 JSON: sse={headers, timeoutMs}, stdio={command, args, env} */
    @TableField("config_json")
    private String configJson;

    /** 是否启用 */
    private Boolean enabled;

    /** 运行状态: UNKNOWN/HEALTHY/UNHEALTHY/DISABLED/CONNECTING */
    private String status;
}
