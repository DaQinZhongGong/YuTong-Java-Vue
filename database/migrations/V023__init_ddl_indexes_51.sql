-- V023: 数据库物理模型索引补齐 (51-数据库物理模型与DDL详设)
-- 设计来源: 51-数据库物理模型与DDL详设 (GA2-49)
-- 内容:
--   1. 修复 8 处唯一索引不合规 (检查 5): 7 处添加 WHERE deleted=false, 1 处添加 tenant_id
--   2. 补齐 58 处逻辑外键缺失索引 (检查 4)
-- 索引规范: 多租户复合索引 (tenant_id, _id, ...)，部分含 WHERE deleted=false

-- ===== 1. 修复唯一索引不合规 (检查 5) =====
-- 说明: 部分唯一索引由 CREATE TABLE CONSTRAINT 创建，需先 DROP CONSTRAINT 再重建索引。
--       对每个索引同时执行 DROP CONSTRAINT 和 DROP INDEX，兼容两种创建方式。

-- 1.1 sys_datasource: 添加 WHERE deleted = false
ALTER TABLE sys_datasource DROP CONSTRAINT IF EXISTS uk_sys_datasource_code;
DROP INDEX IF EXISTS uk_sys_datasource_code;
CREATE UNIQUE INDEX uk_sys_datasource_code
    ON sys_datasource (tenant_id, datasource_code) WHERE deleted = false;

-- 1.2 rpt_dataset: 添加 WHERE deleted = false
ALTER TABLE rpt_dataset DROP CONSTRAINT IF EXISTS uk_rpt_dataset_tenant_code;
DROP INDEX IF EXISTS uk_rpt_dataset_tenant_code;
CREATE UNIQUE INDEX uk_rpt_dataset_tenant_code
    ON rpt_dataset (tenant_id, dataset_code) WHERE deleted = false;

-- 1.3 rpt_report: 添加 WHERE deleted = false
ALTER TABLE rpt_report DROP CONSTRAINT IF EXISTS uk_rpt_report_tenant_code;
DROP INDEX IF EXISTS uk_rpt_report_tenant_code;
CREATE UNIQUE INDEX uk_rpt_report_tenant_code
    ON rpt_report (tenant_id, report_code) WHERE deleted = false;

-- 1.4 inv_stock_transaction: 添加 WHERE deleted = false
ALTER TABLE inv_stock_transaction DROP CONSTRAINT IF EXISTS uk_inv_transaction_tenant_no;
DROP INDEX IF EXISTS uk_inv_transaction_tenant_no;
CREATE UNIQUE INDEX uk_inv_transaction_tenant_no
    ON inv_stock_transaction (tenant_id, transaction_no) WHERE deleted = false;

-- 1.5 sys_notification_subscription: 添加 WHERE deleted = false
ALTER TABLE sys_notification_subscription DROP CONSTRAINT IF EXISTS sys_notification_subscription_tenant_id_user_id_topic_chann_key;
DROP INDEX IF EXISTS sys_notification_subscription_tenant_id_user_id_topic_chann_key;
CREATE UNIQUE INDEX sys_notification_subscription_tenant_id_user_id_topic_chann_key
    ON sys_notification_subscription (tenant_id, user_id, topic, channel) WHERE deleted = false;

-- 1.6 sys_datasource_table_acl: 添加 WHERE deleted = false
ALTER TABLE sys_datasource_table_acl DROP CONSTRAINT IF EXISTS uk_ds_table_acl;
DROP INDEX IF EXISTS uk_ds_table_acl;
CREATE UNIQUE INDEX uk_ds_table_acl
    ON sys_datasource_table_acl (tenant_id, datasource_id, table_name) WHERE deleted = false;

-- 1.7 sys_datasource_column_acl: 添加 WHERE deleted = false
ALTER TABLE sys_datasource_column_acl DROP CONSTRAINT IF EXISTS uk_ds_column_acl;
DROP INDEX IF EXISTS uk_ds_column_acl;
CREATE UNIQUE INDEX uk_ds_column_acl
    ON sys_datasource_column_acl (tenant_id, datasource_id, table_name, column_name) WHERE deleted = false;

-- 1.8 ext_sync_error: 添加 tenant_id 到唯一索引
ALTER TABLE ext_sync_error DROP CONSTRAINT IF EXISTS uk_ext_sync_error_task_biz_unresolved;
DROP INDEX IF EXISTS uk_ext_sync_error_task_biz_unresolved;
CREATE UNIQUE INDEX uk_ext_sync_error_task_biz_unresolved
    ON ext_sync_error (tenant_id, task_id, business_key)
    WHERE status IN ('PENDING', 'RETRYING') AND deleted = false;

-- ===== 2. 补齐逻辑外键缺失索引 (检查 4) =====

-- ----- AI 模块 (10) -----
CREATE INDEX IF NOT EXISTS idx_ai_cost_log_conversation ON ai_cost_log (tenant_id, conversation_id);
CREATE INDEX IF NOT EXISTS idx_ai_cost_log_user ON ai_cost_log (tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_ai_document_file ON ai_document (tenant_id, file_id);
CREATE INDEX IF NOT EXISTS idx_ai_document_chunk_kb ON ai_document_chunk (tenant_id, knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_ai_eval_result_trace ON ai_eval_result (tenant_id, trace_id);
CREATE INDEX IF NOT EXISTS idx_ai_feedback_conversation ON ai_feedback (tenant_id, conversation_id);
CREATE INDEX IF NOT EXISTS idx_ai_feedback_message ON ai_feedback (tenant_id, message_id);
CREATE INDEX IF NOT EXISTS idx_ai_feedback_trace ON ai_feedback (tenant_id, trace_id);
CREATE INDEX IF NOT EXISTS idx_ai_kb_owner ON ai_knowledge_base (tenant_id, owner_user_id);
CREATE INDEX IF NOT EXISTS idx_ai_tool_registry_owner ON ai_tool_registry (tenant_id, owner_user_id);

-- ----- 业务样例模块 (2) -----
CREATE INDEX IF NOT EXISTS idx_biz_approval_record_operator ON biz_approval_record (tenant_id, operator_id, operated_time);
CREATE INDEX IF NOT EXISTS idx_biz_request_applicant ON biz_request (tenant_id, applicant_id, created_time);

-- ----- 合同模块 (3) -----
CREATE INDEX IF NOT EXISTS idx_contract_dept ON contract (tenant_id, owner_dept_id);
CREATE INDEX IF NOT EXISTS idx_contract_approval_approver ON contract_approval (tenant_id, approver_id);
CREATE INDEX IF NOT EXISTS idx_contract_version_file ON contract_version (tenant_id, file_id);

-- ----- 外部同步模块 (1) -----
CREATE INDEX IF NOT EXISTS idx_ext_sync_record_system ON ext_sync_record (tenant_id, system_id);

-- ----- 库存模块 (4) -----
CREATE INDEX IF NOT EXISTS idx_inv_inbound_balance ON inv_inbound_order (tenant_id, balance_id);
CREATE INDEX IF NOT EXISTS idx_inv_outbound_balance ON inv_outbound_order (tenant_id, balance_id);
CREATE INDEX IF NOT EXISTS idx_inv_stock_txn_warehouse ON inv_stock_transaction (tenant_id, warehouse_id);
CREATE INDEX IF NOT EXISTS idx_inv_warehouse_manager ON inv_warehouse (tenant_id, manager_user_id);

-- ----- 知识库模块 (1) -----
CREATE INDEX IF NOT EXISTS idx_kb_conversation_log_ai_conv ON kb_conversation_log (tenant_id, ai_conversation_id);

-- ----- 低代码模块 (9) -----
CREATE INDEX IF NOT EXISTS idx_lc_action_page ON lc_action (tenant_id, page_id);
CREATE INDEX IF NOT EXISTS idx_lc_component_parent ON lc_component (tenant_id, parent_component_id);
CREATE INDEX IF NOT EXISTS idx_lc_entity_owner ON lc_entity (tenant_id, owner_user_id);
CREATE INDEX IF NOT EXISTS idx_lc_generator_task_entity ON lc_generator_task (tenant_id, entity_id);
CREATE INDEX IF NOT EXISTS idx_lc_generator_task_page ON lc_generator_task (tenant_id, page_id);
CREATE INDEX IF NOT EXISTS idx_lc_generator_task_result_file ON lc_generator_task (tenant_id, result_file_id);
CREATE INDEX IF NOT EXISTS idx_lc_page_entity ON lc_page (tenant_id, entity_id);
CREATE INDEX IF NOT EXISTS idx_lc_relation_source ON lc_relation (tenant_id, source_entity_id);
CREATE INDEX IF NOT EXISTS idx_lc_relation_target ON lc_relation (tenant_id, target_entity_id);

-- ----- 支付模块 (1) -----
CREATE INDEX IF NOT EXISTS idx_pay_refund_order_operator ON pay_refund_order (tenant_id, operator_id);

-- ----- 报表模块 (1) -----
CREATE INDEX IF NOT EXISTS idx_rpt_dataset_owner ON rpt_dataset (tenant_id, owner_user_id);

-- ----- 系统模块 (15) -----
CREATE INDEX IF NOT EXISTS idx_sys_client_event_event ON sys_client_event (tenant_id, event_id);
CREATE INDEX IF NOT EXISTS idx_sys_idempotency_resource ON sys_idempotency_record (tenant_id, resource_id);
CREATE INDEX IF NOT EXISTS idx_sys_idempotency_trace ON sys_idempotency_record (tenant_id, trace_id);
CREATE INDEX IF NOT EXISTS idx_sys_import_export_error_file ON sys_import_export_task (tenant_id, error_file_id);
CREATE INDEX IF NOT EXISTS idx_sys_import_export_file ON sys_import_export_task (tenant_id, file_id);
CREATE INDEX IF NOT EXISTS idx_sys_job_log_biz ON sys_job_log (tenant_id, biz_id);
CREATE INDEX IF NOT EXISTS idx_sys_job_log_trace ON sys_job_log (tenant_id, trace_id);
CREATE INDEX IF NOT EXISTS idx_sys_login_log_token ON sys_login_log (tenant_id, token_id);
CREATE INDEX IF NOT EXISTS idx_sys_message_route ON sys_message (tenant_id, target_route_id);
CREATE INDEX IF NOT EXISTS idx_sys_message_template_route ON sys_message_template (tenant_id, target_route_id);
CREATE INDEX IF NOT EXISTS idx_sys_outbox_actor ON sys_outbox_event (tenant_id, actor_id);
CREATE INDEX IF NOT EXISTS idx_sys_outbox_causation ON sys_outbox_event (tenant_id, causation_id);
CREATE INDEX IF NOT EXISTS idx_sys_outbox_correlation ON sys_outbox_event (tenant_id, correlation_id);
CREATE INDEX IF NOT EXISTS idx_sys_outbox_trace ON sys_outbox_event (tenant_id, trace_id);
CREATE INDEX IF NOT EXISTS idx_sys_todo_source_event ON sys_todo_task (tenant_id, source_event_id);

-- ----- 工作流模块 (6) -----
CREATE INDEX IF NOT EXISTS idx_wf_process_def_deployment ON wf_process_definition (tenant_id, engine_deployment_id);
CREATE INDEX IF NOT EXISTS idx_wf_process_inst_engine ON wf_process_instance (tenant_id, engine_instance_id);
CREATE INDEX IF NOT EXISTS idx_wf_task_ext_actual_handler ON wf_task_ext (tenant_id, actual_handler_id);
CREATE INDEX IF NOT EXISTS idx_wf_task_ext_delegate_to ON wf_task_ext (tenant_id, delegate_to_user_id);
CREATE INDEX IF NOT EXISTS idx_wf_task_ext_engine_task ON wf_task_ext (tenant_id, engine_task_id);
CREATE INDEX IF NOT EXISTS idx_wf_task_ext_node ON wf_task_ext (tenant_id, node_id);

-- ----- 工单模块 (5) -----
CREATE INDEX IF NOT EXISTS idx_work_ticket_category ON work_ticket (tenant_id, category_id);
CREATE INDEX IF NOT EXISTS idx_work_ticket_dept ON work_ticket (tenant_id, owner_dept_id);
CREATE INDEX IF NOT EXISTS idx_work_ticket_owner ON work_ticket (tenant_id, owner_user_id);
CREATE INDEX IF NOT EXISTS idx_work_ticket_reporter ON work_ticket (tenant_id, reporter_id);
CREATE INDEX IF NOT EXISTS idx_work_ticket_log_operator ON work_ticket_log (tenant_id, operator_id);

-- ===== 索引注释 (关键索引说明) =====
COMMENT ON INDEX uk_sys_datasource_code IS '51 号文档: 数据源唯一索引补 WHERE deleted=false';
COMMENT ON INDEX uk_ext_sync_error_task_biz_unresolved IS '51 号文档: 外部同步错误唯一索引补 tenant_id';
COMMENT ON INDEX idx_lc_action_page IS '51 号文档: 低代码页面动作逻辑外键索引';
COMMENT ON INDEX idx_lc_page_entity IS '51 号文档: 低代码页面关联实体逻辑外键索引';
COMMENT ON INDEX idx_lc_relation_source IS '51 号文档: 低代码关系源实体逻辑外键索引';
COMMENT ON INDEX idx_lc_relation_target IS '51 号文档: 低代码关系目标实体逻辑外键索引';
