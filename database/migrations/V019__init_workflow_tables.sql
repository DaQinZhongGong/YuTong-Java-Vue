-- V019: 工作流 BPMN 引擎表 (GA2-44, 41 号文档 L1+L2 轻量自研实现)
-- 设计来源: 41-工作流与BPMN引擎设计
-- 验证能力: 流程定义 CRUD + BPMN XML 解析 + 流程实例 + 用户任务 + 状态流转 + 委派转办 + 与样例业务衔接
-- 实现策略: 轻量自研引擎 (LightWorkflowEngine), 不依赖 Flowable, 通过 BPMN XML 解析驱动状态机
-- 注意: SQL 注释中避免使用 $xxx 占位符语法 (Flyway 会解析为变量)

-- ===== 1. 流程定义元数据 =====
-- 41 号文档 wf_process_definition, 增加 form_page_code/deployment_id 等扩展字段
CREATE TABLE wf_process_definition (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    -- 流程唯一标识 (biz_request_approval / contract_approval 等)
    process_key         varchar(64)   NOT NULL,
    process_name        varchar(128)  NOT NULL,
    -- 分类 (GENERAL/CONTRACT/PAYMENT/WORK_TICKET 等)
    category_code       varchar(64),
    -- 绑定业务类型, 如 biz_request / contract / pay_order
    biz_type            varchar(64),
    -- 引擎部署 ID (轻量引擎使用 ULID, 不依赖 Flowable ACT_RE_DEPLOYMENT)
    engine_deployment_id varchar(64),
    -- BPMN XML 快照 (流程定义的内容, 包含 StartEvent/UserTask/ExclusiveGateway/EndEvent/SequenceFlow)
    bpmn_xml            text          NOT NULL,
    -- 版本号 (同 process_key 自增, DRAFT 草稿不占版本号)
    version_no          int           NOT NULL DEFAULT 1,
    -- DRAFT / PUBLISHED / DISABLED
    status              varchar(16)   NOT NULL DEFAULT 'DRAFT',
    -- 关联低代码表单页 page_code (可选)
    form_page_code      varchar(64),
    -- 流程描述
    description         varchar(512),
    -- 服务任务白名单 (JSON 数组, 允许调用的内部能力如 message/send/todo/sync/business-callback)
    service_task_whitelist jsonb,
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    PRIMARY KEY (id)
);

CREATE INDEX idx_wf_pd_tenant ON wf_process_definition (tenant_id);
CREATE INDEX idx_wf_pd_biz_type ON wf_process_definition (biz_type);
CREATE INDEX idx_wf_pd_status ON wf_process_definition (status);
CREATE UNIQUE INDEX uk_wf_pd_tenant_key_version ON wf_process_definition (tenant_id, process_key, version_no) WHERE deleted = false;
CREATE UNIQUE INDEX uk_wf_pd_tenant_key_active ON wf_process_definition (tenant_id, process_key, status) WHERE deleted = false AND status = 'PUBLISHED';

-- ===== 2. 流程实例扩展 =====
-- 41 号文档 wf_process_instance, 引擎实例 ID 由轻量引擎生成
CREATE TABLE wf_process_instance (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    -- 引擎实例 ID (轻量引擎使用 ULID, 与主键一致)
    engine_instance_id  varchar(64),
    -- 关联流程定义
    process_definition_id varchar(32) NOT NULL,
    process_key         varchar(64)   NOT NULL,
    -- 业务类型 + 业务主键 + 业务单号展示
    biz_type            varchar(64),
    biz_id              varchar(32),
    biz_no              varchar(64),
    -- 发起人
    starter_id          varchar(64),
    -- RUNNING / COMPLETED / TERMINATED / SUSPENDED
    instance_status     varchar(16)   NOT NULL DEFAULT 'RUNNING',
    -- 当前节点名称摘要 (逗号分隔, 多任务并行时多个)
    current_node_names  varchar(512),
    -- 流程变量快照 (JSON, 启动时传入 + 任务办理时更新)
    variables           jsonb,
    -- 业务回调完成的 URL (可选, 流程结束时回调)
    business_callback_url varchar(256),
    -- 终止原因 (TERMINATED 时填写)
    terminate_reason    varchar(256),
    -- 发起时间
    start_time          timestamptz,
    -- 结束时间 (COMPLETED/TERMINATED)
    end_time            timestamptz,
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    PRIMARY KEY (id)
);

CREATE INDEX idx_wf_pi_tenant ON wf_process_instance (tenant_id);
CREATE INDEX idx_wf_pi_biz ON wf_process_instance (biz_type, biz_id);
CREATE INDEX idx_wf_pi_starter ON wf_process_instance (starter_id, created_time);
CREATE INDEX idx_wf_pi_status ON wf_process_instance (instance_status);
CREATE INDEX idx_wf_pi_definition ON wf_process_instance (process_definition_id);

-- ===== 3. 用户任务扩展 =====
-- 41 号文档 wf_task_ext, 引擎任务 ID 由轻量引擎生成
CREATE TABLE wf_task_ext (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    -- 引擎任务 ID (轻量引擎使用 ULID, 与主键一致)
    engine_task_id      varchar(64),
    -- 关联流程实例
    instance_id         varchar(32)   NOT NULL,
    process_key         varchar(64)   NOT NULL,
    biz_type            varchar(64),
    biz_id              varchar(32),
    biz_no              varchar(64),
    -- BPMN 节点 ID (XML 中的 task id)
    node_id             varchar(64)   NOT NULL,
    -- 任务名称 (XML 中的 task name)
    task_name           varchar(128),
    -- 办理人 (单人或多人逗号分隔, 候选组办理后写入实际办理人)
    assignee_id         varchar(256),
    -- 候选组 (角色 code 或部门 code, 多个逗号分隔)
    candidate_group     varchar(256),
    -- PENDING / COMPLETED / REJECTED / DELEGATED / TRANSFERRED / CANCELLED
    task_status         varchar(16)   NOT NULL DEFAULT 'PENDING',
    -- 任务类型 (APPROVAL/NOTIFICATION/FILL_FORM)
    task_type           varchar(32)   DEFAULT 'APPROVAL',
    -- 截止时间 (SLA)
    due_time            timestamptz,
    -- 办理表单快照 (JSON, 任务办理时填写的表单数据)
    form_data_json      jsonb,
    -- 办理意见
    opinion             varchar(1024),
    -- 委派/转办标记 (DELEGATED: 委派, 任务回到原办理人; TRANSFERRED: 转办, 任务转到新人)
    delegate_type       varchar(16),
    -- 委派/转办目标人
    delegate_to_user_id varchar(64),
    -- 创建时间 (任务到达该节点时)
    create_time         timestamptz   NOT NULL DEFAULT now(),
    -- 办理时间 (任务完成/驳回/委派/转办时)
    complete_time       timestamptz,
    -- 实际办理人 (与 assignee_id 区分: 委派场景下 assignee_id 仍是原办理人, actual_handler 是被委派人)
    actual_handler_id   varchar(64),
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    PRIMARY KEY (id)
);

CREATE INDEX idx_wf_te_tenant ON wf_task_ext (tenant_id);
CREATE INDEX idx_wf_te_instance ON wf_task_ext (instance_id);
CREATE INDEX idx_wf_te_assignee_status ON wf_task_ext (assignee_id, task_status);
CREATE INDEX idx_wf_te_candidate ON wf_task_ext (candidate_group, task_status);
CREATE INDEX idx_wf_te_biz ON wf_task_ext (biz_type, biz_id);
CREATE INDEX idx_wf_te_due ON wf_task_ext (due_time) WHERE task_status = 'PENDING';

-- ===== 4. 流程变量映射 =====
-- 41 号文档 wf_variable_mapping (低代码表单页字段与流程变量映射)
CREATE TABLE wf_variable_mapping (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    process_key         varchar(64)   NOT NULL,
    node_id             varchar(64),
    -- 流程变量名 (BPMN XML 表达式中引用)
    variable_name       varchar(64)   NOT NULL,
    -- 业务实体字段路径 (如 totalAmount / customerId / items[0].productId)
    entity_field_path   varchar(128)  NOT NULL,
    -- 映射方向 (IN: 业务→流程变量; OUT: 流程变量→业务实体)
    direction           varchar(8)    NOT NULL DEFAULT 'IN',
    -- 默认值 (业务实体字段为空时使用)
    default_value       varchar(256),
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    PRIMARY KEY (id)
);

CREATE INDEX idx_wf_vm_tenant ON wf_variable_mapping (tenant_id);
CREATE INDEX idx_wf_vm_process ON wf_variable_mapping (process_key, node_id);
CREATE UNIQUE INDEX uk_wf_vm_process_var ON wf_variable_mapping (tenant_id, process_key, node_id, variable_name) WHERE deleted = false;

-- ===== 种子数据 =====
-- tenant_id 使用 'default' 与 MockAuthAdapter.DEFAULT_TENANT 对齐

-- 1. 流程定义种子: biz_request_approval (申请单审批, 与样例业务衔接)
-- BPMN XML 包含: StartEvent -> UserTask(部门经理审批) -> ExclusiveGateway(金额>10000?) -> UserTask(总经理审批)/直接 -> EndEvent
INSERT INTO wf_process_definition (id, tenant_id, process_key, process_name, category_code, biz_type, engine_deployment_id, bpmn_xml, version_no, status, description, service_task_whitelist, created_by, version)
VALUES ('01K8WFPD0MOCK0000000000001', 'default', 'biz_request_approval', '申请单审批流程', 'GENERAL', 'biz_request', 'deploy-mock-001',
'<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmnDi="http://www.omg.org/spec/BPMN/20100524/DI" id="Definitions_biz_request_approval" targetNamespace="http://yutong.com/bpmn">
  <bpmn:process id="biz_request_approval" name="申请单审批流程" isExecutable="true">
    <bpmn:startEvent id="start" name="提交申请单" />
    <bpmn:sequenceFlow id="flow_start_to_mgr" sourceRef="start" targetRef="mgr_approval" />
    <bpmn:userTask id="mgr_approval" name="部门经理审批" assignee="${starter_manager}" candidateGroups="role_dept_manager">
      <bpmn:documentation>部门经理审批申请单, 金额 &lt;= 10000 元直接通过</bpmn:documentation>
    </bpmn:userTask>
    <bpmn:sequenceFlow id="flow_mgr_to_gw" sourceRef="mgr_approval" targetRef="amount_gateway" />
    <bpmn:exclusiveGateway id="amount_gateway" name="金额判断" />
    <bpmn:sequenceFlow id="flow_gw_to_gm" sourceRef="amount_gateway" targetRef="gm_approval">
      <bpmn:conditionExpression>${totalAmount &gt; 10000}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="flow_gw_to_end" sourceRef="amount_gateway" targetRef="end_approved">
      <bpmn:conditionExpression>${totalAmount &lt;= 10000}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:userTask id="gm_approval" name="总经理审批" assignee="${starter_gm}" candidateGroups="role_gm">
      <bpmn:documentation>总经理审批大额申请单 (金额 &gt; 10000)</bpmn:documentation>
    </bpmn:userTask>
    <bpmn:sequenceFlow id="flow_gm_to_end" sourceRef="gm_approval" targetRef="end_approved" />
    <bpmn:endEvent id="end_approved" name="审批通过" />
  </bpmn:process>
</bpmn:definitions>',
1, 'PUBLISHED', '申请单审批流程: 部门经理审批 -> 金额判断网关 -> (<= 10000) 直接通过 / (> 10000) 总经理审批 -> 通过',
'["message/send","todo/sync","business-callback"]'::jsonb,
'mock-admin', 0)
ON CONFLICT DO NOTHING;

-- 2. 流程实例种子: 1 个 RUNNING 实例 (关联样例 biz_request)
INSERT INTO wf_process_instance (id, tenant_id, engine_instance_id, process_definition_id, process_key, biz_type, biz_id, biz_no, starter_id, instance_status, current_node_names, variables, start_time, created_by, version)
VALUES ('01K8WFPI0MOCK0000000000001', 'default', '01K8WFPI0MOCK0000000000001',
'01K8WFPD0MOCK0000000000001', 'biz_request_approval',
'biz_request', '01K8BIZ0REQ0000000000001', 'BIZ202607180001',
'mock-biz', 'RUNNING', '部门经理审批',
'{"totalAmount": 15000, "customerId": "01K8CUS0MOCK0000000000001", "starter_manager": "mock-approver", "starter_gm": "mock-admin"}'::jsonb,
now(), 'mock-biz', 0)
ON CONFLICT DO NOTHING;

-- 3. 用户任务种子: 1 个 PENDING 任务 (部门经理审批)
INSERT INTO wf_task_ext (id, tenant_id, engine_task_id, instance_id, process_key, biz_type, biz_id, biz_no, node_id, task_name, assignee_id, candidate_group, task_status, task_type, due_time, create_time, created_by, version)
VALUES ('01K8WFTE0MOCK0000000000001', 'default', '01K8WFTE0MOCK0000000000001',
'01K8WFPI0MOCK0000000000001', 'biz_request_approval',
'biz_request', '01K8BIZ0REQ0000000000001', 'BIZ202607180001',
'mgr_approval', '部门经理审批',
'mock-approver', 'role_dept_manager',
'PENDING', 'APPROVAL',
now() + interval '24 hours',
now(), 'mock-biz', 0)
ON CONFLICT DO NOTHING;

-- 4. 变量映射种子: biz_request_approval 流程的变量映射
INSERT INTO wf_variable_mapping (id, tenant_id, process_key, node_id, variable_name, entity_field_path, direction, default_value, created_by, version) VALUES
('01K8WFVM0MOCK0000000000001', 'default', 'biz_request_approval', NULL, 'totalAmount', 'totalAmount', 'IN', '0', 'mock-admin', 0),
('01K8WFVM0MOCK0000000000002', 'default', 'biz_request_approval', NULL, 'customerId', 'customerId', 'IN', NULL, 'mock-admin', 0),
('01K8WFVM0MOCK0000000000003', 'default', 'biz_request_approval', NULL, 'starter_manager', 'starterManager', 'IN', 'mock-approver', 'mock-admin', 0),
('01K8WFVM0MOCK0000000000004', 'default', 'biz_request_approval', NULL, 'starter_gm', 'starterGm', 'IN', 'mock-admin', 'mock-admin', 0)
ON CONFLICT DO NOTHING;
