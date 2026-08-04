-- V015: 知识库运营表 (GA2-39)
-- 设计来源: 35-样例业务矩阵扩展设计 P2 知识库运营
-- 验证能力: 文档导入 + 分块和向量化 + 权限过滤 + 问答引用 + 命中率统计 + 低置信度拒答
-- 复用: ai_knowledge_base / ai_document / ai_document_chunk / ai_embedding (V005 已建)
-- 本迁移仅新增运营层表: 问答日志 + 日统计
-- 注意: SQL 注释中避免使用 $xxx 占位符语法 (Flyway 会解析为变量)

-- ===== 问答日志 =====
-- 一次问答产生一条记录, 包含问题/答案/引用/命中分数/是否拒答
CREATE TABLE kb_conversation_log (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    conversation_no     varchar(64)   NOT NULL,
    -- 关联知识库 (可空, 用于全局问答场景)
    kb_id               varchar(32),
    -- 提问用户
    user_id             varchar(64),
    -- 用户问题 (截断到 4KB)
    question            text          NOT NULL,
    -- 系统答案 (截断到 16KB, 拒答时为拒答提示语)
    answer              text,
    -- 命中分块数量
    hit_chunk_count     int           NOT NULL DEFAULT 0,
    -- 最高/最低/平均相关性分数 (0.0~1.0)
    max_score           numeric(6,4),
    min_score           numeric(6,4),
    avg_score           numeric(6,4),
    -- 是否拒答 (无命中/低置信度/权限不足时为 true)
    is_refused          boolean       NOT NULL DEFAULT false,
    -- 拒答原因: NO_HITS / LOW_CONFIDENCE / KB_DISABLED / ACL_DENIED
    refuse_reason       varchar(32),
    -- 引用文档列表 (JSON 数组: [{"docId":"...","docTitle":"...","chunkId":"...","sectionPath":"...","score":0.85}])
    cited_documents     jsonb,
    -- 命中分块 ID 列表 (JSON 字符串数组, 便于追溯)
    cited_chunk_ids     jsonb,
    -- 问答耗时 (毫秒)
    latency_ms          bigint,
    -- 关联 AI 会话 ID (可选, 若走 AI Gateway 流式对话则关联 ai_conversation.id)
    ai_conversation_id  varchar(32),
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    PRIMARY KEY (id)
);

CREATE INDEX idx_kb_conv_log_tenant ON kb_conversation_log (tenant_id);
CREATE INDEX idx_kb_conv_log_kb_time ON kb_conversation_log (kb_id, created_time);
CREATE INDEX idx_kb_conv_log_user ON kb_conversation_log (user_id, created_time);
CREATE INDEX idx_kb_conv_log_refused ON kb_conversation_log (is_refused, created_time);
CREATE UNIQUE INDEX uk_kb_conv_log_no ON kb_conversation_log (tenant_id, conversation_no) WHERE deleted = false;

-- ===== 日统计表 =====
-- 每个知识库每日一条统计记录, 由定时任务或手动触发汇总
CREATE TABLE kb_stats_daily (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    -- 统计日期 (北京时间)
    stat_date           date          NOT NULL,
    kb_id               varchar(32)   NOT NULL,
    -- 文档总数 (ACTIVE 状态)
    document_count      int           NOT NULL DEFAULT 0,
    -- 分块总数 (含向量)
    chunk_count         int           NOT NULL DEFAULT 0,
    -- 向量总数
    embedding_count     int           NOT NULL DEFAULT 0,
    -- 当日问答总数
    conversation_count  int           NOT NULL DEFAULT 0,
    -- 当日命中问答数 (hit_chunk_count > 0 且未拒答)
    hit_count           int           NOT NULL DEFAULT 0,
    -- 当日拒答数
    refused_count       int           NOT NULL DEFAULT 0,
    -- 当日平均最高分数 (0.0~1.0)
    avg_max_score       numeric(6,4),
    -- 当日平均延时 (毫秒)
    avg_latency_ms      bigint,
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    PRIMARY KEY (id)
);

CREATE INDEX idx_kb_stats_daily_tenant ON kb_stats_daily (tenant_id);
CREATE INDEX idx_kb_stats_daily_kb_date ON kb_stats_daily (kb_id, stat_date);
CREATE UNIQUE INDEX uk_kb_stats_daily_kb_date ON kb_stats_daily (tenant_id, kb_id, stat_date) WHERE deleted = false;

-- ===== 种子数据 =====
-- tenant_id 使用 'default' 与 MockAuthAdapter.DEFAULT_TENANT 对齐
-- 注意: 种子数据中避免使用 $ 符号 (Flyway 占位符陷阱)

-- 1. 知识库种子 (复用 V005 的 ai_knowledge_base 表, 但 V005 未灌种子, 这里补一条)
INSERT INTO ai_knowledge_base (id, tenant_id, kb_code, kb_name, description, embedding_model, visibility, permission_code, sensitivity_level, status, owner_user_id, version)
VALUES ('01K8KB0OPS0MOCK0000000000001', 'default', 'kb-ops-mock', 'GA2-39 知识库运营样例', 'GA2-39 P2 样例: 验证文档导入/分块向量化/权限过滤/问答引用/命中率统计/低置信度拒答 6 项能力',
 'local-hash-bow-1536', 'PUBLIC', NULL, 'INTERNAL', 'ACTIVE', 'mock-admin', 0)
ON CONFLICT DO NOTHING;

-- 2. 文档种子 (复用 V005 的 ai_document 表)
INSERT INTO ai_document (id, tenant_id, kb_id, doc_title, source_type, source_uri, visibility, sensitivity_level, document_status, chunk_count, indexed_time, created_by, version) VALUES
('01K8KB0DOC0MOCK0000000000001', 'default', '01K8KB0OPS0MOCK0000000000001',
 'YuTong 技术底座产品白皮书', 'DESIGN_DOC', 'mock://docs/whitepaper.md',
 'PUBLIC', 'INTERNAL', 'ACTIVE', 3, now(), 'mock-admin', 0),
('01K8KB0DOC0MOCK0000000000002', 'default', '01K8KB0OPS0MOCK0000000000001',
 'API 集成指南', 'FAQ', 'mock://docs/api-guide.md',
 'PUBLIC', 'INTERNAL', 'ACTIVE', 2, now(), 'mock-admin', 0)
ON CONFLICT DO NOTHING;

-- 3. 分块种子 (复用 V005 的 ai_document_chunk 表)
INSERT INTO ai_document_chunk (id, tenant_id, knowledge_base_id, document_id, chunk_no, chunk_text, chunk_hash, token_count, section_path, permission_code, sensitivity_level, created_by, version) VALUES
('01K8KB0CHK0MOCK0000000000001', 'default', '01K8KB0OPS0MOCK0000000000001', '01K8KB0DOC0MOCK0000000000001', 1,
 'YuTong 技术底座是基于 Spring Boot 3 + Vue 3 的企业级开发平台, 提供 RBAC 权限/数据权限/审计日志/低代码/AI 网关/RAG 知识库等核心能力, 帮助企业快速构建业务系统。',
 'sha256-mock-chunk-001', 80, '第一章/产品概述', NULL, 'INTERNAL', 'mock-admin', 0),
('01K8KB0CHK0MOCK0000000000002', 'default', '01K8KB0OPS0MOCK0000000000001', '01K8KB0DOC0MOCK0000000000001', 2,
 'YuTong 平台的核心能力包括: (1) 多租户隔离与 RBAC 权限; (2) DataScope 数据权限 (ALL/TENANT/DEPARTMENT/SELF/CUSTOM/NONE); (3) 审计日志 AOP; (4) 低代码元模型与代码生成; (5) AI 网关与 RAG 检索; (6) 实时推送 WebSocket; (7) 报表大屏 ECharts。',
 'sha256-mock-chunk-002', 120, '第二章/核心能力', NULL, 'INTERNAL', 'mock-admin', 0),
('01K8KB0CHK0MOCK0000000000003', 'default', '01K8KB0OPS0MOCK0000000000001', '01K8KB0DOC0MOCK0000000000001', 3,
 'YuTong 平台的部署架构: 单体应用 + PostgreSQL + Redis + MinIO, 支持 Docker 容器化部署。生产环境建议采用 C2/C3 档位, 含 OIDC/SSO 身份集成与渗透测试。',
 'sha256-mock-chunk-003', 80, '第三章/部署架构', NULL, 'INTERNAL', 'mock-admin', 0),
('01K8KB0CHK0MOCK0000000000004', 'default', '01K8KB0OPS0MOCK0000000000001', '01K8KB0DOC0MOCK0000000000002', 1,
 'API 集成指南: 第三方系统通过 REST API 接入 YuTong 平台, 支持 HMAC-SHA256 签名鉴权。请求头包含 X-Ext-Access-Key/X-Ext-Timestamp/X-Ext-Nonce/X-Ext-Signature 四项。',
 'sha256-mock-chunk-004', 75, 'API 集成/签名鉴权', NULL, 'INTERNAL', 'mock-admin', 0),
('01K8KB0CHK0MOCK0000000000005', 'default', '01K8KB0OPS0MOCK0000000000001', '01K8KB0DOC0MOCK0000000000002', 2,
 'API 集成指南: 失败重试采用指数退避策略, retry_backoff_ms * 2^(attempt-1)。死信队列状态机: PENDING → RETRYING → RESOLVED / DEAD_LETTER。retry_count 达到 max_retry_count 后进入死信。',
 'sha256-mock-chunk-005', 90, 'API 集成/重试与死信', NULL, 'INTERNAL', 'mock-admin', 0)
ON CONFLICT DO NOTHING;

-- 4. 向量种子 (复用 V005 的 ai_embedding 表, 向量由应用层 EmbeddingService 生成)
-- 这里只创建占位记录, 实际向量列由后端启动后通过 /api/v1/kb/documents/{id}/reindex 端点触发回填
-- 或由 DocumentIngestApplicationService.ingest 在创建时写入
INSERT INTO ai_embedding (id, tenant_id, chunk_id, embedding_model, embedding_dimension, embedding_hash, created_by, version) VALUES
('01K8KB0EMB0MOCK0000000000001', 'default', '01K8KB0CHK0MOCK0000000000001', 'local-hash-bow-1536', 1536, 'sha256-mock-chunk-001', 'mock-admin', 0),
('01K8KB0EMB0MOCK0000000000002', 'default', '01K8KB0CHK0MOCK0000000000002', 'local-hash-bow-1536', 1536, 'sha256-mock-chunk-002', 'mock-admin', 0),
('01K8KB0EMB0MOCK0000000000003', 'default', '01K8KB0CHK0MOCK0000000000003', 'local-hash-bow-1536', 1536, 'sha256-mock-chunk-003', 'mock-admin', 0),
('01K8KB0EMB0MOCK0000000000004', 'default', '01K8KB0CHK0MOCK0000000000004', 'local-hash-bow-1536', 1536, 'sha256-mock-chunk-004', 'mock-admin', 0),
('01K8KB0EMB0MOCK0000000000005', 'default', '01K8KB0CHK0MOCK0000000000005', 'local-hash-bow-1536', 1536, 'sha256-mock-chunk-005', 'mock-admin', 0)
ON CONFLICT DO NOTHING;

-- 5. 问答日志种子 (用于运营统计展示)
INSERT INTO kb_conversation_log (id, tenant_id, conversation_no, kb_id, user_id, question, answer, hit_chunk_count, max_score, min_score, avg_score, is_refused, refuse_reason, cited_documents, cited_chunk_ids, latency_ms, created_time, version) VALUES
('01K8KB0LOG0MOCK0000000000001', 'default', 'KB-CONV-20260718-0001', '01K8KB0OPS0MOCK0000000000001', 'mock-admin',
 'YuTong 平台有哪些核心能力?',
 'YuTong 平台的核心能力包括: (1) 多租户隔离与 RBAC 权限; (2) DataScope 数据权限; (3) 审计日志 AOP; (4) 低代码元模型与代码生成; (5) AI 网关与 RAG 检索; (6) 实时推送 WebSocket; (7) 报表大屏 ECharts。',
 1, 0.8500, 0.8500, 0.8500, false, NULL,
 '[{"docId":"01K8KB0DOC0MOCK0000000000001","docTitle":"YuTong 技术底座产品白皮书","chunkId":"01K8KB0CHK0MOCK0000000000002","sectionPath":"第二章/核心能力","score":0.85}]'::jsonb,
 '["01K8KB0CHK0MOCK0000000000002"]'::jsonb,
 125, '2026-07-18 10:30:00+08', 0),
('01K8KB0LOG0MOCK0000000000002', 'default', 'KB-CONV-20260718-0002', '01K8KB0OPS0MOCK0000000000001', 'mock-admin',
 'API 集成时签名失败如何处理?',
 'API 集成时若签名失败, 请检查: (1) access_key/secret_key 是否正确; (2) 时间戳是否在 5 分钟有效期内; (3) nonce 是否重复使用; (4) 签名串拼接顺序是否为 method+path+timestamp+nonce+body_hash。失败重试采用指数退避策略, retry_count 达到 max_retry_count 后进入死信队列。',
 2, 0.7800, 0.6500, 0.7150, false, NULL,
 '[{"docId":"01K8KB0DOC0MOCK0000000000002","docTitle":"API 集成指南","chunkId":"01K8KB0CHK0MOCK0000000000004","sectionPath":"API 集成/签名鉴权","score":0.78},{"docId":"01K8KB0DOC0MOCK0000000000002","docTitle":"API 集成指南","chunkId":"01K8KB0CHK0MOCK0000000000005","sectionPath":"API 集成/重试与死信","score":0.65}]'::jsonb,
 '["01K8KB0CHK0MOCK0000000000004","01K8KB0CHK0MOCK0000000000005"]'::jsonb,
 198, '2026-07-18 14:20:00+08', 0),
('01K8KB0LOG0MOCK0000000000003', 'default', 'KB-CONV-20260719-0001', '01K8KB0OPS0MOCK0000000000001', 'mock-admin',
 '今天天气怎么样?',
 '抱歉, 知识库中未找到与您问题相关的内容, 请尝试换一种问法或联系管理员补充文档。',
 0, NULL, NULL, NULL, true, 'LOW_CONFIDENCE',
 '[]'::jsonb, '[]'::jsonb,
 45, '2026-07-19 09:15:00+08', 0)
ON CONFLICT DO NOTHING;

-- 6. 日统计种子 (用于运营看板展示)
INSERT INTO kb_stats_daily (id, tenant_id, stat_date, kb_id, document_count, chunk_count, embedding_count, conversation_count, hit_count, refused_count, avg_max_score, avg_latency_ms, version) VALUES
('01K8KB0STA0MOCK0000000000001', 'default', '2026-07-18', '01K8KB0OPS0MOCK0000000000001', 2, 5, 5, 2, 2, 0, 0.8150, 161, 0),
('01K8KB0STA0MOCK0000000000002', 'default', '2026-07-19', '01K8KB0OPS0MOCK0000000000001', 2, 5, 5, 1, 0, 1, NULL, 45, 0)
ON CONFLICT DO NOTHING;
