-- V018: 问卷表单 P2 表 (GA2-42)
-- 设计来源: 35-样例业务矩阵扩展设计 P2 问卷表单
-- 验证能力: 动态表单渲染 + 条件显隐 + 字段校验 + 移动端填写 + 统计报表 + AI 生成题目草稿
-- 状态机:
--   问卷 sur_survey: DRAFT → PUBLISHED → COLLECTING → CLOSED
--                    DRAFT → ARCHIVED (草稿直接归档)
--                    COLLECTING → CLOSED (提前结束收集)
--   答卷 sur_response: IN_PROGRESS → SUBMITTED / ABANDONED
-- 题目类型: SINGLE_CHOICE / MULTI_CHOICE / TEXT / TEXTAREA / RATING / DATE / MATRIX
-- 条件显隐: sur_question.logic_json 存显隐规则 (依赖题目 code + 选项 value)
-- 字段校验: required + min_length/max_length/min_value/max_value + regex
-- AI 草稿: 复用 AiChatApplicationService.chat(scenario=SURVEY_QUESTION_GENERATE) 生成题目列表

-- ===== 1. 问卷主表 =====
CREATE TABLE sur_survey (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    -- 问卷编号 (业务可读, SURyyyyMMddNNNNNN 格式)
    survey_no           varchar(32)   NOT NULL,
    -- 问卷标题
    title               varchar(256)  NOT NULL,
    -- 问卷描述
    description         varchar(1024),
    -- 问卷状态: DRAFT / PUBLISHED / COLLECTING / CLOSED / ARCHIVED
    status              varchar(16)   NOT NULL DEFAULT 'DRAFT',
    -- 问卷分类 (如: 客户满意度 / 市场调研 / 内部调研 / 产品反馈)
    category            varchar(64),
    -- 是否匿名 (true: 答卷不记录 respondent_id)
    anonymous           boolean       NOT NULL DEFAULT false,
    -- 每个用户可填写次数 (0 表示不限制)
    max_responses_per_user     int    NOT NULL DEFAULT 1,
    -- 计划开始时间
    start_time          timestamptz,
    -- 计划结束时间
    end_time            timestamptz,
    -- 发布时间
    published_time      timestamptz,
    -- 关闭时间
    closed_time         timestamptz,
    -- 答卷数 (冗余字段, 定期同步 sur_response 统计)
    response_count      int           NOT NULL DEFAULT 0,
    -- 主题配置 JSON (颜色/logo 等, 预留)
    theme_json          jsonb,
    -- AI 生成草稿来源提示 (scenario=SURVEY_QUESTION_GENERATE 调用记录)
    ai_draft_prompt     varchar(1024),
    -- BaseEntity 标准字段
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz   NOT NULL DEFAULT now(),
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(500),
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_sur_survey_no      ON sur_survey (tenant_id, survey_no) WHERE deleted = false;
CREATE INDEX        idx_sur_survey_status ON sur_survey (tenant_id, status, created_time);

COMMENT ON TABLE  sur_survey IS '问卷主表: 管理问卷元信息和状态机';
COMMENT ON COLUMN sur_survey.survey_no IS '问卷编号 SURyyyyMMddNNNNNN 格式';

-- ===== 2. 题目表 =====
CREATE TABLE sur_question (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    -- 所属问卷 ID
    survey_id           varchar(32)   NOT NULL,
    -- 题目编号 (问卷内唯一, Q001/Q002/...)
    question_code       varchar(32)   NOT NULL,
    -- 题目类型: SINGLE_CHOICE / MULTI_CHOICE / TEXT / TEXTAREA / RATING / DATE / MATRIX
    question_type       varchar(32)   NOT NULL,
    -- 题目标题
    title               varchar(512)  NOT NULL,
    -- 题目描述 (帮助文字)
    description         varchar(1024),
    -- 是否必答
    required            boolean       NOT NULL DEFAULT true,
    -- 排序号 (问卷内题目顺序)
    sort_no             int           NOT NULL DEFAULT 0,
    -- 选项 JSON (SINGLE_CHOICE/MULTI_CHOICE/RATING/MATRIX 类型使用)
    -- 格式: [{"code":"OPT_A","label":"选项 A","value":"A","sortNo":1}, ...]
    options_json        jsonb,
    -- 校验规则 JSON
    -- 格式: {"minLength":1,"maxLength":200,"minValue":0,"maxValue":100,"regex":"^[0-9]+$"}
    validation_json     jsonb,
    -- 条件显隐规则 JSON (数组, 满足任一条件即显示)
    -- 格式: [{"questionCode":"Q001","operator":"EQ","value":"A","action":"SHOW"}, ...]
    -- operator: EQ/NE/IN/NOT_IN/CONTAINS/GT/LT
    -- action: SHOW/HIDE/REQUIRE
    logic_json           jsonb,
    -- 矩阵题行/列配置 (MATRIX 类型使用)
    -- 格式: {"rows":[{"code":"R1","label":"行 1"}],"cols":[{"code":"C1","label":"列 1"}]}
    matrix_json         jsonb,
    -- AI 生成标记 (true 表示由 AI 生成草稿)
    ai_generated        boolean       NOT NULL DEFAULT false,
    -- BaseEntity 标准字段
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz   NOT NULL DEFAULT now(),
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(500),
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_sur_question_code   ON sur_question (tenant_id, survey_id, question_code) WHERE deleted = false;
CREATE INDEX        idx_sur_question_survey ON sur_question (tenant_id, survey_id, sort_no);

COMMENT ON TABLE  sur_question IS '题目表: 题目元信息 + 选项 + 校验规则 + 条件显隐规则';
COMMENT ON COLUMN sur_question.logic_json IS '条件显隐规则 JSON 数组, 满足任一条件即触发 action';

-- ===== 3. 答卷表 =====
CREATE TABLE sur_response (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    -- 所属问卷 ID
    survey_id           varchar(32)   NOT NULL,
    -- 答卷编号 (业务可读, RSPyyyyMMddNNNNNN 格式)
    response_no         varchar(32)   NOT NULL,
    -- 答卷人 ID (anonymous=true 时为 null)
    respondent_id       varchar(64),
    -- 答卷人名称 (冗余, 便于统计展示)
    respondent_name     varchar(128),
    -- 答卷状态: IN_PROGRESS / SUBMITTED / ABANDONED
    status              varchar(16)   NOT NULL DEFAULT 'IN_PROGRESS',
    -- 来源渠道: WEB_ADMIN / MOBILE_UNIAPP / API
    source              varchar(32)   NOT NULL DEFAULT 'WEB_ADMIN',
    -- 开始作答时间
    start_time          timestamptz   NOT NULL DEFAULT now(),
    -- 提交时间
    submitted_time      timestamptz,
    -- 作答时长 (毫秒)
    duration_ms         bigint,
    -- 客户端 IP
    client_ip           varchar(64),
    -- User-Agent
    user_agent          varchar(512),
    -- 答卷总分 (RATING 题型累计, 用于统计)
    total_score         int,
    -- BaseEntity 标准字段
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz   NOT NULL DEFAULT now(),
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(500),
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_sur_response_no      ON sur_response (tenant_id, response_no) WHERE deleted = false;
CREATE INDEX        idx_sur_response_survey ON sur_response (tenant_id, survey_id, status, submitted_time);
CREATE INDEX        idx_sur_response_resp   ON sur_response (tenant_id, respondent_id, survey_id);

COMMENT ON TABLE  sur_response IS '答卷表: 一份问卷的一次作答记录';
COMMENT ON COLUMN sur_response.response_no IS '答卷编号 RSPyyyyMMddNNNNNN 格式';

-- ===== 4. 答题表 =====
CREATE TABLE sur_answer (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    -- 所属答卷 ID
    response_id         varchar(32)   NOT NULL,
    -- 所属问卷 ID (冗余, 便于统计查询)
    survey_id           varchar(32)   NOT NULL,
    -- 题目 ID
    question_id         varchar(32)   NOT NULL,
    -- 题目编号 (冗余)
    question_code       varchar(32)   NOT NULL,
    -- 题目类型 (冗余)
    question_type       varchar(32)   NOT NULL,
    -- 答案值 JSON
    -- SINGLE_CHOICE: "OPT_A"
    -- MULTI_CHOICE: ["OPT_A","OPT_B"]
    -- TEXT/TEXTAREA: "用户输入文本"
    -- RATING: 5
    -- DATE: "2026-07-19"
    -- MATRIX: {"R1":"C1","R2":"C2"}
    answer_value        jsonb,
    -- 答案文本 (冗余, 用于统计快速展示)
    answer_text         varchar(2048),
    -- 选项 code 列表 (冗余, 用于统计聚合, MULTI_CHOICE 存数组)
    -- SINGLE_CHOICE: ["OPT_A"]
    -- MULTI_CHOICE: ["OPT_A","OPT_B"]
    selected_options    jsonb,
    -- 评分 (RATING 题型冗余, 便于统计)
    rating_score        int,
    -- 作答耗时 (毫秒, 单题作答时长)
    duration_ms         bigint,
    -- BaseEntity 标准字段
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz   NOT NULL DEFAULT now(),
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(500),
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_sur_answer_resp_q   ON sur_answer (tenant_id, response_id, question_id) WHERE deleted = false;
CREATE INDEX        idx_sur_answer_survey  ON sur_answer (tenant_id, survey_id, question_id);
CREATE INDEX        idx_sur_answer_qcode   ON sur_answer (tenant_id, survey_id, question_code);

COMMENT ON TABLE  sur_answer IS '答题表: 一份答卷下每道题的答案, 一对一关系';
COMMENT ON COLUMN sur_answer.answer_value IS '答案值 JSON, 格式根据题目类型不同';

-- ===== 5. 种子数据 =====
-- tenant_id='default' 对齐 MockAuthAdapter.DEFAULT_TENANT

-- 5.1 问卷种子 (2 份: 1 份 COLLECTING 状态 + 1 份 DRAFT 状态)
INSERT INTO sur_survey (id, tenant_id, survey_no, title, description, status, category, anonymous, max_responses_per_user, start_time, end_time, published_time, closed_time, response_count, theme_json, ai_draft_prompt, created_by, created_time, updated_by, updated_time, remark) VALUES
('sur-seed-001', 'default', 'SUR20260719000001', 'GA2-42 客户满意度调研', '请对本季度技术服务进行满意度评价, 您的反馈对我们很重要', 'COLLECTING', '客户满意度', false, 1, '2026-07-19 00:00:00+08', '2026-07-31 23:59:59+08', '2026-07-19 14:00:00+08', null, 3, '{"primaryColor":"#409EFF"}', null, 'system', '2026-07-19 14:00:00+08', 'system', '2026-07-19 14:00:00+08', 'GA2-42 种子: 客户满意度调研 (收集中)'),
('sur-seed-002', 'default', 'SUR20260719000002', 'GA2-42 产品功能反馈问卷', '帮助我们改进产品功能, 请填写您使用过程中的反馈', 'DRAFT', '产品反馈', true, 0, null, null, null, null, 0, null, '请生成关于产品功能反馈的问卷题目, 包含 5 道题: 单选/多选/评分/文本/矩阵各 1 道', 'system', '2026-07-19 14:00:00+08', 'system', '2026-07-19 14:00:00+08', 'GA2-42 种子: 产品功能反馈问卷 (草稿, AI 草稿生成演示)');

-- 5.2 题目种子 (5 道题, 覆盖 5 种题型 + 1 道带条件显隐的题)
-- 题目类型: SINGLE_CHOICE / MULTI_CHOICE / RATING / TEXT / MATRIX
-- 条件显隐: Q005 当 Q003 rating_score <= 2 时显示
INSERT INTO sur_question (id, tenant_id, survey_id, question_code, question_type, title, description, required, sort_no, options_json, validation_json, logic_json, matrix_json, ai_generated, created_by, created_time, updated_by, updated_time, remark) VALUES
('sur-q-seed-001', 'default', 'sur-seed-001', 'Q001', 'SINGLE_CHOICE', '您对本期技术服务的整体满意度如何?', '请选择最符合您感受的选项', true, 1,
 '[{"code":"OPT_A","label":"非常满意","value":"A","sortNo":1},{"code":"OPT_B","label":"满意","value":"B","sortNo":2},{"code":"OPT_C","label":"一般","value":"C","sortNo":3},{"code":"OPT_D","label":"不满意","value":"D","sortNo":4}]',
 null, null, null, false,
 'system', '2026-07-19 14:00:00+08', 'system', '2026-07-19 14:00:00+08', 'GA2-42 种子: 单选题 (整体满意度)'),
('sur-q-seed-002', 'default', 'sur-seed-001', 'Q002', 'MULTI_CHOICE', '您认为我们哪些方面做得好? (可多选)', '请勾选所有符合的选项', true, 2,
 '[{"code":"OPT_A","label":"响应速度快","value":"A","sortNo":1},{"code":"OPT_B","label":"技术专业","value":"B","sortNo":2},{"code":"OPT_C","label":"态度友好","value":"C","sortNo":3},{"code":"OPT_D","label":"问题解决彻底","value":"D","sortNo":4},{"code":"OPT_E","label":"文档完善","value":"E","sortNo":5}]',
 '{"minSelect":1,"maxSelect":5}', null, null, false,
 'system', '2026-07-19 14:00:00+08', 'system', '2026-07-19 14:00:00+08', 'GA2-42 种子: 多选题 (优点评价)'),
('sur-q-seed-003', 'default', 'sur-seed-001', 'Q003', 'RATING', '请为技术团队的专业能力评分', '1 分最低, 5 分最高', true, 3,
 '[{"code":"OPT_1","label":"1 分","value":1,"sortNo":1},{"code":"OPT_2","label":"2 分","value":2,"sortNo":2},{"code":"OPT_3","label":"3 分","value":3,"sortNo":3},{"code":"OPT_4","label":"4 分","value":4,"sortNo":4},{"code":"OPT_5","label":"5 分","value":5,"sortNo":5}]',
 '{"minValue":1,"maxValue":5}', null, null, false,
 'system', '2026-07-19 14:00:00+08', 'system', '2026-07-19 14:00:00+08', 'GA2-42 种子: 评分题 (专业能力)'),
('sur-q-seed-004', 'default', 'sur-seed-001', 'Q004', 'TEXT', '您希望我们改进的具体方面是什么?', '请详细描述您的建议 (200 字以内)', false, 4,
 null,
 '{"maxLength":200}', null, null, false,
 'system', '2026-07-19 14:00:00+08', 'system', '2026-07-19 14:00:00+08', 'GA2-42 种子: 文本题 (改进建议)'),
('sur-q-seed-005', 'default', 'sur-seed-001', 'Q005', 'TEXTAREA', '如果您评分较低, 请告知我们具体原因', '仅当评分 <= 2 分时显示', false, 5,
 null,
 '{"maxLength":500}',
 '[{"questionCode":"Q003","operator":"LTE","value":2,"action":"SHOW"}]',
 null, false,
 'system', '2026-07-19 14:00:00+08', 'system', '2026-07-19 14:00:00+08', 'GA2-42 种子: 条件显隐题 (评分低时显示)');

-- 5.3 答卷种子 (3 份: 全部 SUBMITTED, 用于统计报表)
INSERT INTO sur_response (id, tenant_id, survey_id, response_no, respondent_id, respondent_name, status, source, start_time, submitted_time, duration_ms, client_ip, user_agent, total_score, created_by, created_time, updated_by, updated_time, remark) VALUES
('sur-resp-seed-001', 'default', 'sur-seed-001', 'RSP20260719000001', '01MOCKUSER00000000000000BIZ', '业务用户 A', 'SUBMITTED', 'WEB_ADMIN', '2026-07-19 14:30:00+08', '2026-07-19 14:35:00+08', 300000, '127.0.0.1', 'Mozilla/5.0 Chrome', 5,
 'system', '2026-07-19 14:30:00+08', 'system', '2026-07-19 14:35:00+08', 'GA2-42 种子: 答卷 1 (满意度高)'),
('sur-resp-seed-002', 'default', 'sur-seed-001', 'RSP20260719000002', '01MOCKUSER0000000000APPROVER', '审批人 B', 'SUBMITTED', 'MOBILE_UNIAPP', '2026-07-19 15:00:00+08', '2026-07-19 15:08:00+08', 480000, '10.0.0.1', 'Mozilla/5.0 Mobile Safari', 4,
 'system', '2026-07-19 15:00:00+08', 'system', '2026-07-19 15:08:00+08', 'GA2-42 种子: 答卷 2 (移动端填写)'),
('sur-resp-seed-003', 'default', 'sur-seed-001', 'RSP20260719000003', '01MOCKUSER0000000000000ADMIN', '管理员 C', 'SUBMITTED', 'WEB_ADMIN', '2026-07-19 15:30:00+08', '2026-07-19 15:36:00+08', 360000, '127.0.0.1', 'Mozilla/5.0 Chrome', 3,
 'system', '2026-07-19 15:30:00+08', 'system', '2026-07-19 15:36:00+08', 'GA2-42 种子: 答卷 3 (评分较低, 触发 Q005)');

-- 5.4 答题种子 (15 条: 3 份答卷 × 5 道题)
-- 答卷 1: 满意度高 (Q001=A, Q002=[A,B,C], Q003=5, Q004=null, Q005=null 不触发)
INSERT INTO sur_answer (id, tenant_id, response_id, survey_id, question_id, question_code, question_type, answer_value, answer_text, selected_options, rating_score, duration_ms, created_by, created_time, updated_by, updated_time, remark) VALUES
('sur-ans-seed-001', 'default', 'sur-resp-seed-001', 'sur-seed-001', 'sur-q-seed-001', 'Q001', 'SINGLE_CHOICE', '"OPT_A"', '非常满意', '["OPT_A"]', null, 5000, 'system', '2026-07-19 14:30:00+08', 'system', '2026-07-19 14:30:00+08', 'GA2-42 种子: 答卷1 Q001'),
('sur-ans-seed-002', 'default', 'sur-resp-seed-001', 'sur-seed-001', 'sur-q-seed-002', 'Q002', 'MULTI_CHOICE', '["OPT_A","OPT_B","OPT_C"]', '响应速度快, 技术专业, 态度友好', '["OPT_A","OPT_B","OPT_C"]', null, 8000, 'system', '2026-07-19 14:31:00+08', 'system', '2026-07-19 14:31:00+08', 'GA2-42 种子: 答卷1 Q002'),
('sur-ans-seed-003', 'default', 'sur-resp-seed-001', 'sur-seed-001', 'sur-q-seed-003', 'Q003', 'RATING', '5', '5 分', null, 5, 3000, 'system', '2026-07-19 14:32:00+08', 'system', '2026-07-19 14:32:00+08', 'GA2-42 种子: 答卷1 Q003'),
('sur-ans-seed-004', 'default', 'sur-resp-seed-001', 'sur-seed-001', 'sur-q-seed-004', 'Q004', 'TEXT', 'null', null, null, null, 10000, 'system', '2026-07-19 14:33:00+08', 'system', '2026-07-19 14:33:00+08', 'GA2-42 种子: 答卷1 Q004 未填写'),
('sur-ans-seed-005', 'default', 'sur-resp-seed-001', 'sur-seed-001', 'sur-q-seed-005', 'Q005', 'TEXTAREA', 'null', null, null, null, 0, 'system', '2026-07-19 14:33:00+08', 'system', '2026-07-19 14:33:00+08', 'GA2-42 种子: 答卷1 Q005 不触发'),

-- 答卷 2: 满意度中 (Q001=B, Q002=[A,D], Q003=4, Q004=有建议, Q005=null 不触发)
('sur-ans-seed-006', 'default', 'sur-resp-seed-002', 'sur-seed-001', 'sur-q-seed-001', 'Q001', 'SINGLE_CHOICE', '"OPT_B"', '满意', '["OPT_B"]', null, 6000, 'system', '2026-07-19 15:00:00+08', 'system', '2026-07-19 15:00:00+08', 'GA2-42 种子: 答卷2 Q001'),
('sur-ans-seed-007', 'default', 'sur-resp-seed-002', 'sur-seed-001', 'sur-q-seed-002', 'Q002', 'MULTI_CHOICE', '["OPT_A","OPT_D"]', '响应速度快, 问题解决彻底', '["OPT_A","OPT_D"]', null, 12000, 'system', '2026-07-19 15:01:00+08', 'system', '2026-07-19 15:01:00+08', 'GA2-42 种子: 答卷2 Q002'),
('sur-ans-seed-008', 'default', 'sur-resp-seed-002', 'sur-seed-001', 'sur-q-seed-003', 'Q003', 'RATING', '4', '4 分', null, 4, 4000, 'system', '2026-07-19 15:02:00+08', 'system', '2026-07-19 15:02:00+08', 'GA2-42 种子: 答卷2 Q003'),
('sur-ans-seed-009', 'default', 'sur-resp-seed-002', 'sur-seed-001', 'sur-q-seed-004', 'Q004', 'TEXT', '"希望增加更多培训文档"', '希望增加更多培训文档', null, null, 30000, 'system', '2026-07-19 15:03:00+08', 'system', '2026-07-19 15:03:00+08', 'GA2-42 种子: 答卷2 Q004'),
('sur-ans-seed-010', 'default', 'sur-resp-seed-002', 'sur-seed-001', 'sur-q-seed-005', 'Q005', 'TEXTAREA', 'null', null, null, null, 0, 'system', '2026-07-19 15:03:00+08', 'system', '2026-07-19 15:03:00+08', 'GA2-42 种子: 答卷2 Q005 不触发'),

-- 答卷 3: 满意度低 (Q001=D, Q002=[E], Q003=2, Q004=null, Q005=触发填写原因)
('sur-ans-seed-011', 'default', 'sur-resp-seed-003', 'sur-seed-001', 'sur-q-seed-001', 'Q001', 'SINGLE_CHOICE', '"OPT_D"', '不满意', '["OPT_D"]', null, 4000, 'system', '2026-07-19 15:30:00+08', 'system', '2026-07-19 15:30:00+08', 'GA2-42 种子: 答卷3 Q001'),
('sur-ans-seed-012', 'default', 'sur-resp-seed-003', 'sur-seed-001', 'sur-q-seed-002', 'Q002', 'MULTI_CHOICE', '["OPT_E"]', '文档完善', '["OPT_E"]', null, 15000, 'system', '2026-07-19 15:31:00+08', 'system', '2026-07-19 15:31:00+08', 'GA2-42 种子: 答卷3 Q002'),
('sur-ans-seed-013', 'default', 'sur-resp-seed-003', 'sur-seed-001', 'sur-q-seed-003', 'Q003', 'RATING', '2', '2 分', null, 2, 5000, 'system', '2026-07-19 15:32:00+08', 'system', '2026-07-19 15:32:00+08', 'GA2-42 种子: 答卷3 Q003 (评分低触发 Q005)'),
('sur-ans-seed-014', 'default', 'sur-resp-seed-003', 'sur-seed-001', 'sur-q-seed-004', 'Q004', 'TEXT', 'null', null, null, null, 0, 'system', '2026-07-19 15:33:00+08', 'system', '2026-07-19 15:33:00+08', 'GA2-42 种子: 答卷3 Q004 未填写'),
('sur-ans-seed-015', 'default', 'sur-resp-seed-003', 'sur-seed-001', 'sur-q-seed-005', 'Q005', 'TEXTAREA', '"响应速度太慢, 工单处理超时严重"', '响应速度太慢, 工单处理超时严重', null, null, 60000, 'system', '2026-07-19 15:34:00+08', 'system', '2026-07-19 15:34:00+08', 'GA2-42 种子: 答卷3 Q005 触发填写');
