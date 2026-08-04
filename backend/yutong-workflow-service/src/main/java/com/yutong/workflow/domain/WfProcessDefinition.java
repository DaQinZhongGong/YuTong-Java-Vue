package com.yutong.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 工作流流程定义元数据。设计来源: 41-工作流与BPMN引擎设计。
 *
 * <p>GA2-44 L1+L2 轻量自研引擎实现: 不依赖 Flowable, 通过 BPMN XML 解析驱动状态机。
 * 状态机: DRAFT -> DEPLOYED -> PUBLISHED -> DISABLED (PUBLISHED 可重新 DISABLED, DRAFT 可部署/发布)。
 * DEPLOYED: 已通过 BPMN 合法性 + 服务任务白名单校验，可发布为 PUBLISHED。
 *
 * <p>版本化规则: 同 process_key 可有多个版本, 但只能有 1 个 PUBLISHED 状态 (uk_wf_pd_tenant_key_active 唯一约束)。
 */
@Getter
@Setter
@TableName("wf_process_definition")
public class WfProcessDefinition extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_DEPLOYED = "DEPLOYED";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_DISABLED = "DISABLED";

    /** 流程唯一标识 (biz_request_approval / contract_approval 等) */
    private String processKey;

    private String processName;

    /** 分类 (GENERAL/CONTRACT/PAYMENT/WORK_TICKET 等) */
    private String categoryCode;

    /** 绑定业务类型, 如 biz_request / contract / pay_order */
    private String bizType;

    /** 引擎部署 ID (轻量引擎使用 ULID) */
    private String engineDeploymentId;

    /** BPMN XML 快照 (流程定义的内容) */
    private String bpmnXml;

    /** 版本号 (同 process_key 自增, DRAFT 不占版本号) */
    private Integer versionNo;

    /** DRAFT / PUBLISHED / DISABLED */
    private String status;

    /** 关联低代码表单页 page_code (可选) */
    private String formPageCode;

    private String description;

    /**
     * 服务任务白名单 (JSON 数组, 允许调用的内部能力如 message/send/todo/sync/business-callback)。
     * 数据库类型 jsonb, MyBatis-Plus 默认按 String 处理, 这里使用 String 接收 + 业务层 JSON 解析。
     */
    private String serviceTaskWhitelist;
}
