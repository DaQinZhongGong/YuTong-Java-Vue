-- V004: 低代码元模型表
-- 设计来源: 14-低代码平台设计、57-完整DDL清单与数据字典详设

-- ===== 实体 =====
CREATE TABLE lc_entity (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    entity_code     varchar(64)   NOT NULL,
    entity_name     varchar(128)  NOT NULL,
    table_name      varchar(64),
    module_code     varchar(64),
    version_no      int           NOT NULL DEFAULT 1,
    schema_version  varchar(16)   NOT NULL DEFAULT '1.0',
    config_hash     varchar(128),
    status          varchar(16)   NOT NULL DEFAULT 'DRAFT',
    owner_user_id   varchar(64),
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_lc_entity PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_lc_entity_code ON lc_entity (tenant_id, entity_code) WHERE deleted = false;
CREATE UNIQUE INDEX uk_lc_entity_table ON lc_entity (tenant_id, table_name) WHERE deleted = false AND table_name IS NOT NULL;
COMMENT ON TABLE lc_entity IS '低代码实体';

-- ===== 字段 =====
CREATE TABLE lc_field (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    entity_id       varchar(32)   NOT NULL,
    field_code      varchar(64)   NOT NULL,
    field_name      varchar(128)  NOT NULL,
    field_name_i18n jsonb,
    db_column       varchar(64),
    data_type       varchar(32)   NOT NULL,
    length_value    int,
    precision_value int,
    scale_value     int,
    nullable        boolean       NOT NULL DEFAULT true,
    default_value   varchar(256),
    dict_type       varchar(64),
    primary_flag    boolean       NOT NULL DEFAULT false,
    unique_flag     boolean       NOT NULL DEFAULT false,
    index_flag      boolean       NOT NULL DEFAULT false,
    sort_no         int           NOT NULL DEFAULT 0,
    old_field_code  varchar(64),
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_lc_field PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_lc_field_code ON lc_field (tenant_id, entity_id, field_code) WHERE deleted = false;
COMMENT ON TABLE lc_field IS '低代码字段';

-- ===== 关系 =====
CREATE TABLE lc_relation (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    source_entity_id    varchar(32)   NOT NULL,
    target_entity_id    varchar(32)   NOT NULL,
    relation_type       varchar(32)   NOT NULL,
    source_field_code   varchar(64),
    target_field_code   varchar(64),
    cascade_policy      varchar(32),
    required            boolean       NOT NULL DEFAULT false,
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_lc_relation PRIMARY KEY (id)
);
COMMENT ON TABLE lc_relation IS '低代码关系';

-- ===== 页面 =====
CREATE TABLE lc_page (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    page_code           varchar(64)   NOT NULL,
    page_name           varchar(128)  NOT NULL,
    entity_id           varchar(32),
    page_type           varchar(32)   NOT NULL,
    layout_json         jsonb,
    layout_schema_version varchar(16) NOT NULL DEFAULT '1.0',
    version_no          int           NOT NULL DEFAULT 1,
    status              varchar(16)   NOT NULL DEFAULT 'DRAFT',
    published_time      timestamptz,
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_lc_page PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_lc_page_code ON lc_page (tenant_id, page_code) WHERE deleted = false;
COMMENT ON TABLE lc_page IS '低代码页面';

-- ===== 组件 =====
CREATE TABLE lc_component (
    id                  varchar(32)   NOT NULL,
    tenant_id           varchar(32)   NOT NULL,
    page_id             varchar(32)   NOT NULL,
    component_code      varchar(64)   NOT NULL,
    component_type      varchar(32)   NOT NULL,
    props_json          jsonb,
    rules_json          jsonb,
    events_json         jsonb,
    props_schema_version varchar(16)  NOT NULL DEFAULT '1.0',
    parent_component_id varchar(32),
    sort_no             int           NOT NULL DEFAULT 0,
    created_by          varchar(64),
    created_time        timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean       NOT NULL DEFAULT false,
    version             int           NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_lc_component PRIMARY KEY (id)
);
COMMENT ON TABLE lc_component IS '低代码组件';

-- ===== 动作 =====
CREATE TABLE lc_action (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    page_id         varchar(32)   NOT NULL,
    action_code     varchar(64)   NOT NULL,
    action_name     varchar(128)  NOT NULL,
    action_type     varchar(32)   NOT NULL,
    permission_code varchar(128),
    confirm_required boolean      NOT NULL DEFAULT false,
    api_method      varchar(16),
    api_path        varchar(256),
    payload_mapping jsonb,
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_lc_action PRIMARY KEY (id)
);
COMMENT ON TABLE lc_action IS '低代码动作';

-- ===== 生成任务 =====
CREATE TABLE lc_generator_task (
    id              varchar(32)   NOT NULL,
    tenant_id       varchar(32)   NOT NULL,
    task_no         varchar(64)   NOT NULL,
    entity_id       varchar(32),
    page_id         varchar(32),
    template_version varchar(32)  NOT NULL,
    target_scope    varchar(32)   NOT NULL,
    diff_json       jsonb,
    conflict_count  int           NOT NULL DEFAULT 0,
    status          varchar(16)   NOT NULL DEFAULT 'PENDING',
    result_file_id  varchar(32),
    error_message   varchar(1024),
    started_time    timestamptz,
    finished_time   timestamptz,
    created_by      varchar(64),
    created_time    timestamptz   NOT NULL DEFAULT now(),
    updated_by      varchar(64),
    updated_time    timestamptz,
    deleted         boolean       NOT NULL DEFAULT false,
    version         int           NOT NULL DEFAULT 0,
    remark          varchar(512),
    CONSTRAINT pk_lc_generator_task PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_lc_generator_task_no ON lc_generator_task (tenant_id, task_no) WHERE deleted = false;
COMMENT ON TABLE lc_generator_task IS '低代码生成任务';
