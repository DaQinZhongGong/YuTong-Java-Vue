-- ============================================================
-- V025__init_lowcode_component_registry.sql
-- 低代码组件协议元数据注册表（36 号文档「低代码高级能力设计」GA2-L191 子任务 B）
-- 对齐 36 号文档 line 87-99 组件元数据 + 45 号文档 line 50-61 plugin.yaml
-- 表 lc_component_registry 保存组件协议元数据: 分类/平台/兼容性/props_schema/event_schema 等
-- 设计器组件库通过 listPublished 拉取已发布组件清单，渲染物料区与属性面板
-- 主键采用 varchar(32) 字符串主键（ULID），与平台约定一致
-- ============================================================

CREATE TABLE IF NOT EXISTS lc_component_registry (
    id                   varchar(32)   NOT NULL,
    tenant_id            varchar(32)   NOT NULL DEFAULT 'default',
    component_code       varchar(64)   NOT NULL,
    component_name       varchar(128)  NOT NULL,
    component_type       varchar(32)   NOT NULL,
    display_name         varchar(128)  NOT NULL,
    platform             varchar(16)   NOT NULL DEFAULT 'BOTH',
    category             varchar(32)   NOT NULL,
    compatibility_grade  varchar(16)   NOT NULL DEFAULT 'STABLE',
    props_schema         text,
    event_schema         text,
    data_binding         text,
    permission_support   boolean       NOT NULL DEFAULT false,
    validation_support   boolean       NOT NULL DEFAULT false,
    permission_code      varchar(128),
    component_version    varchar(16)   NOT NULL DEFAULT '1.0.0',
    min_platform_version varchar(16),
    max_platform_version varchar(16),
    description          varchar(512),
    icon                 varchar(64),
    status               varchar(16)   NOT NULL DEFAULT 'PUBLISHED',
    deprecated           boolean       NOT NULL DEFAULT false,
    deprecated_message   varchar(256),
    sort_no              int           NOT NULL DEFAULT 0,
    -- 通用字段（BaseEntity 约束）
    created_by           varchar(32),
    created_time         timestamptz   NOT NULL DEFAULT now(),
    updated_by           varchar(32),
    updated_time         timestamptz   NOT NULL DEFAULT now(),
    deleted              boolean       NOT NULL DEFAULT false,
    version              int           NOT NULL DEFAULT 0,
    remark               varchar(256),
    CONSTRAINT pk_lc_component_registry PRIMARY KEY (id)
);

-- 部分唯一索引：保证 (tenant_id, component_code) 在未删除记录中唯一（PostgreSQL 不支持 UNIQUE 约束带 WHERE）
CREATE UNIQUE INDEX IF NOT EXISTS uk_lc_component_registry_code     ON lc_component_registry (tenant_id, component_code) WHERE deleted = false;
CREATE INDEX        IF NOT EXISTS idx_lc_component_registry_type     ON lc_component_registry (tenant_id, component_type) WHERE deleted = false;
CREATE INDEX        IF NOT EXISTS idx_lc_component_registry_category ON lc_component_registry (tenant_id, category)       WHERE deleted = false;
CREATE INDEX        IF NOT EXISTS idx_lc_component_registry_status   ON lc_component_registry (tenant_id, status)         WHERE deleted = false;

COMMENT ON TABLE  lc_component_registry IS '低代码组件协议元数据注册表（36 号文档组件协议）保存组件分类/平台/兼容性/props_schema/event_schema 等元数据';
COMMENT ON COLUMN lc_component_registry.component_code      IS '组件编码，租户内唯一，设计器引用键';
COMMENT ON COLUMN lc_component_registry.component_type      IS '组件类型，对齐 lc_component.component_type';
COMMENT ON COLUMN lc_component_registry.platform            IS '适用平台 WEB/MOBILE/BOTH';
COMMENT ON COLUMN lc_component_registry.category            IS '组件分类 INPUT/DISPLAY/CONTAINER/TABLE/BUSINESS/MOBILE/CHART';
COMMENT ON COLUMN lc_component_registry.compatibility_grade  IS '兼容性等级 STABLE/EXPERIMENTAL/INTERNAL';
COMMENT ON COLUMN lc_component_registry.props_schema         IS '组件属性 JSON Schema，属性面板渲染依据';
COMMENT ON COLUMN lc_component_registry.event_schema         IS '组件事件契约 JSON，如 {"change":{"description":"值变更"}}';
COMMENT ON COLUMN lc_component_registry.data_binding         IS '数据绑定契约 JSON，描述支持的字段绑定方式';
COMMENT ON COLUMN lc_component_registry.permission_support   IS '是否支持权限码绑定';
COMMENT ON COLUMN lc_component_registry.validation_support   IS '是否支持校验规则';
COMMENT ON COLUMN lc_component_registry.permission_code      IS '默认权限码（permission_support=true 时生效）';
COMMENT ON COLUMN lc_component_registry.component_version    IS '组件协议版本，semver';
COMMENT ON COLUMN lc_component_registry.min_platform_version IS '最低平台版本兼容约束';
COMMENT ON COLUMN lc_component_registry.max_platform_version IS '最高平台版本兼容约束';
COMMENT ON COLUMN lc_component_registry.status               IS '生命周期 DRAFT/PUBLISHED/DISABLED';
COMMENT ON COLUMN lc_component_registry.deprecated           IS '是否已废弃';
COMMENT ON COLUMN lc_component_registry.deprecated_message   IS '废弃提示信息';

-- ============================================================
-- 种子数据: 7 分类基线组件（36 号文档 line 102-111）
-- platform: WEB/MOBILE/BOTH；compatibility_grade: STABLE；status: PUBLISHED
-- ON CONFLICT 保证可重复执行
-- ============================================================

-- ===== 输入 INPUT (platform=WEB) =====
INSERT INTO lc_component_registry (id, tenant_id, component_code, component_name, component_type, display_name, platform, category, compatibility_grade, props_schema, event_schema, permission_support, validation_support, permission_code, component_version, status, sort_no, created_by)
VALUES ('01LCREG0000000000000000001', 'default', 'TextInput', '文本输入', 'INPUT', '单行文本', 'WEB', 'INPUT', 'STABLE',
        '{"type":"object","properties":{"value":{"type":"string"},"placeholder":{"type":"string"},"maxLength":{"type":"number"},"disabled":{"type":"boolean"}}}',
        '{"change":{"description":"值变更"},"blur":{"description":"失焦"},"focus":{"description":"获焦"}}',
        false, true, NULL, '1.0.0', 'PUBLISHED', 1, 'system')
ON CONFLICT (tenant_id, component_code) WHERE deleted = false DO NOTHING;

INSERT INTO lc_component_registry (id, tenant_id, component_code, component_name, component_type, display_name, platform, category, compatibility_grade, props_schema, event_schema, permission_support, validation_support, permission_code, component_version, status, sort_no, created_by)
VALUES ('01LCREG0000000000000000002', 'default', 'NumberInput', '数字输入', 'INPUT', '数字', 'WEB', 'INPUT', 'STABLE',
        '{"type":"object","properties":{"value":{"type":"number"},"min":{"type":"number"},"max":{"type":"number"},"step":{"type":"number"},"precision":{"type":"number"}}}',
        '{"change":{"description":"值变更"},"blur":{"description":"失焦"}}',
        false, true, NULL, '1.0.0', 'PUBLISHED', 2, 'system')
ON CONFLICT (tenant_id, component_code) WHERE deleted = false DO NOTHING;

INSERT INTO lc_component_registry (id, tenant_id, component_code, component_name, component_type, display_name, platform, category, compatibility_grade, props_schema, event_schema, permission_support, validation_support, permission_code, component_version, status, sort_no, created_by)
VALUES ('01LCREG0000000000000000003', 'default', 'DatePicker', '日期选择', 'INPUT', '日期', 'WEB', 'INPUT', 'STABLE',
        '{"type":"object","properties":{"value":{"type":"string"},"format":{"type":"string"},"mode":{"type":"string"}}}',
        '{"change":{"description":"值变更"}}',
        false, true, NULL, '1.0.0', 'PUBLISHED', 3, 'system')
ON CONFLICT (tenant_id, component_code) WHERE deleted = false DO NOTHING;

INSERT INTO lc_component_registry (id, tenant_id, component_code, component_name, component_type, display_name, platform, category, compatibility_grade, props_schema, event_schema, permission_support, validation_support, permission_code, component_version, status, sort_no, created_by)
VALUES ('01LCREG0000000000000000004', 'default', 'DictSelect', '字典下拉', 'INPUT', '字典选择', 'WEB', 'INPUT', 'STABLE',
        '{"type":"object","properties":{"value":{"type":"string"},"dictType":{"type":"string"},"multiple":{"type":"boolean"},"placeholder":{"type":"string"}}}',
        '{"change":{"description":"值变更"}}',
        false, true, NULL, '1.0.0', 'PUBLISHED', 4, 'system')
ON CONFLICT (tenant_id, component_code) WHERE deleted = false DO NOTHING;

-- ===== 展示 DISPLAY (platform=WEB) =====
INSERT INTO lc_component_registry (id, tenant_id, component_code, component_name, component_type, display_name, platform, category, compatibility_grade, props_schema, event_schema, permission_support, validation_support, permission_code, component_version, status, sort_no, created_by)
VALUES ('01LCREG0000000000000000005', 'default', 'Text', '文本展示', 'DISPLAY', '文本', 'WEB', 'DISPLAY', 'STABLE',
        '{"type":"object","properties":{"text":{"type":"string"},"color":{"type":"string"},"fontSize":{"type":"number"}}}',
        '{}',
        false, false, NULL, '1.0.0', 'PUBLISHED', 1, 'system')
ON CONFLICT (tenant_id, component_code) WHERE deleted = false DO NOTHING;

INSERT INTO lc_component_registry (id, tenant_id, component_code, component_name, component_type, display_name, platform, category, compatibility_grade, props_schema, event_schema, permission_support, validation_support, permission_code, component_version, status, sort_no, created_by)
VALUES ('01LCREG0000000000000000006', 'default', 'StatusTag', '状态标签', 'DISPLAY', '状态', 'WEB', 'DISPLAY', 'STABLE',
        '{"type":"object","properties":{"value":{"type":"string"},"statusType":{"type":"string"}}}',
        '{}',
        false, false, NULL, '1.0.0', 'PUBLISHED', 2, 'system')
ON CONFLICT (tenant_id, component_code) WHERE deleted = false DO NOTHING;

INSERT INTO lc_component_registry (id, tenant_id, component_code, component_name, component_type, display_name, platform, category, compatibility_grade, props_schema, event_schema, permission_support, validation_support, permission_code, component_version, status, sort_no, created_by)
VALUES ('01LCREG0000000000000000007', 'default', 'ImagePreview', '图片预览', 'DISPLAY', '图片', 'WEB', 'DISPLAY', 'STABLE',
        '{"type":"object","properties":{"src":{"type":"string"},"alt":{"type":"string"},"width":{"type":"number"}}}',
        '{}',
        false, false, NULL, '1.0.0', 'PUBLISHED', 3, 'system')
ON CONFLICT (tenant_id, component_code) WHERE deleted = false DO NOTHING;

INSERT INTO lc_component_registry (id, tenant_id, component_code, component_name, component_type, display_name, platform, category, compatibility_grade, props_schema, event_schema, permission_support, validation_support, permission_code, component_version, status, sort_no, created_by)
VALUES ('01LCREG0000000000000000008', 'default', 'FileList', '文件列表', 'DISPLAY', '文件', 'WEB', 'DISPLAY', 'STABLE',
        '{"type":"object","properties":{"files":{"type":"array"},"downloadable":{"type":"boolean"}}}',
        '{"download":{"description":"下载文件"}}',
        true, false, 'lowcode:component:FileList:download', '1.0.0', 'PUBLISHED', 4, 'system')
ON CONFLICT (tenant_id, component_code) WHERE deleted = false DO NOTHING;

-- ===== 容器 CONTAINER (platform=WEB) =====
INSERT INTO lc_component_registry (id, tenant_id, component_code, component_name, component_type, display_name, platform, category, compatibility_grade, props_schema, event_schema, permission_support, validation_support, permission_code, component_version, status, sort_no, created_by)
VALUES ('01LCREG0000000000000000009', 'default', 'Card', '卡片容器', 'CONTAINER', '卡片', 'WEB', 'CONTAINER', 'STABLE',
        '{"type":"object","properties":{"title":{"type":"string"},"bordered":{"type":"boolean"}}}',
        '{}',
        false, false, NULL, '1.0.0', 'PUBLISHED', 1, 'system')
ON CONFLICT (tenant_id, component_code) WHERE deleted = false DO NOTHING;

INSERT INTO lc_component_registry (id, tenant_id, component_code, component_name, component_type, display_name, platform, category, compatibility_grade, props_schema, event_schema, permission_support, validation_support, permission_code, component_version, status, sort_no, created_by)
VALUES ('01LCREG0000000000000000010', 'default', 'Tabs', '标签页', 'CONTAINER', '标签页', 'WEB', 'CONTAINER', 'STABLE',
        '{"type":"object","properties":{"activeKey":{"type":"string"},"items":{"type":"array"}}}',
        '{"change":{"description":"切换标签"}}',
        false, false, NULL, '1.0.0', 'PUBLISHED', 2, 'system')
ON CONFLICT (tenant_id, component_code) WHERE deleted = false DO NOTHING;

INSERT INTO lc_component_registry (id, tenant_id, component_code, component_name, component_type, display_name, platform, category, compatibility_grade, props_schema, event_schema, permission_support, validation_support, permission_code, component_version, status, sort_no, created_by)
VALUES ('01LCREG0000000000000000011', 'default', 'Collapse', '折叠面板', 'CONTAINER', '折叠面板', 'WEB', 'CONTAINER', 'STABLE',
        '{"type":"object","properties":{"activeKey":{"type":"array"},"items":{"type":"array"}}}',
        '{"change":{"description":"展开折叠"}}',
        false, false, NULL, '1.0.0', 'PUBLISHED', 3, 'system')
ON CONFLICT (tenant_id, component_code) WHERE deleted = false DO NOTHING;

INSERT INTO lc_component_registry (id, tenant_id, component_code, component_name, component_type, display_name, platform, category, compatibility_grade, props_schema, event_schema, permission_support, validation_support, permission_code, component_version, status, sort_no, created_by)
VALUES ('01LCREG0000000000000000012', 'default', 'Grid', '栅格布局', 'CONTAINER', '栅格', 'WEB', 'CONTAINER', 'STABLE',
        '{"type":"object","properties":{"columns":{"type":"number"},"gutter":{"type":"number"}}}',
        '{}',
        false, false, NULL, '1.0.0', 'PUBLISHED', 4, 'system')
ON CONFLICT (tenant_id, component_code) WHERE deleted = false DO NOTHING;

-- ===== 表格 TABLE (platform=WEB) =====
INSERT INTO lc_component_registry (id, tenant_id, component_code, component_name, component_type, display_name, platform, category, compatibility_grade, props_schema, event_schema, permission_support, validation_support, permission_code, component_version, status, sort_no, created_by)
VALUES ('01LCREG0000000000000000013', 'default', 'DataTable', '数据表格', 'TABLE', '数据表', 'WEB', 'TABLE', 'STABLE',
        '{"type":"object","properties":{"columns":{"type":"array"},"data":{"type":"array"},"rowKey":{"type":"string"},"pagination":{"type":"object"}}}',
        '{"rowClick":{"description":"行点击"},"pageChange":{"description":"分页变更"},"sort":{"description":"排序变更"}}',
        true, false, 'lowcode:component:DataTable:view', '1.0.0', 'PUBLISHED', 1, 'system')
ON CONFLICT (tenant_id, component_code) WHERE deleted = false DO NOTHING;

INSERT INTO lc_component_registry (id, tenant_id, component_code, component_name, component_type, display_name, platform, category, compatibility_grade, props_schema, event_schema, permission_support, validation_support, permission_code, component_version, status, sort_no, created_by)
VALUES ('01LCREG0000000000000000014', 'default', 'EditableTable', '可编辑表格', 'TABLE', '可编辑表', 'WEB', 'TABLE', 'STABLE',
        '{"type":"object","properties":{"columns":{"type":"array"},"data":{"type":"array"},"rowKey":{"type":"string"}}}',
        '{"rowChange":{"description":"行变更"},"add":{"description":"新增行"},"delete":{"description":"删除行"}}',
        true, true, 'lowcode:component:EditableTable:edit', '1.0.0', 'PUBLISHED', 2, 'system')
ON CONFLICT (tenant_id, component_code) WHERE deleted = false DO NOTHING;

-- ===== 业务 BUSINESS (platform=BOTH) =====
INSERT INTO lc_component_registry (id, tenant_id, component_code, component_name, component_type, display_name, platform, category, compatibility_grade, props_schema, event_schema, permission_support, validation_support, permission_code, component_version, status, sort_no, created_by)
VALUES ('01LCREG0000000000000000015', 'default', 'UserSelect', '用户选择', 'BUSINESS', '用户选择', 'BOTH', 'BUSINESS', 'STABLE',
        '{"type":"object","properties":{"value":{"type":"string"},"multiple":{"type":"boolean"},"deptScope":{"type":"string"}}}',
        '{"change":{"description":"值变更"}}',
        true, false, 'lowcode:component:UserSelect:view', '1.0.0', 'PUBLISHED', 1, 'system')
ON CONFLICT (tenant_id, component_code) WHERE deleted = false DO NOTHING;

INSERT INTO lc_component_registry (id, tenant_id, component_code, component_name, component_type, display_name, platform, category, compatibility_grade, props_schema, event_schema, permission_support, validation_support, permission_code, component_version, status, sort_no, created_by)
VALUES ('01LCREG0000000000000000016', 'default', 'DeptSelect', '部门选择', 'BUSINESS', '部门选择', 'BOTH', 'BUSINESS', 'STABLE',
        '{"type":"object","properties":{"value":{"type":"string"},"multiple":{"type":"boolean"},"rootDeptId":{"type":"string"}}}',
        '{"change":{"description":"值变更"}}',
        true, false, 'lowcode:component:DeptSelect:view', '1.0.0', 'PUBLISHED', 2, 'system')
ON CONFLICT (tenant_id, component_code) WHERE deleted = false DO NOTHING;

INSERT INTO lc_component_registry (id, tenant_id, component_code, component_name, component_type, display_name, platform, category, compatibility_grade, props_schema, event_schema, permission_support, validation_support, permission_code, component_version, status, sort_no, created_by)
VALUES ('01LCREG0000000000000000017', 'default', 'FileUploader', '文件上传', 'BUSINESS', '文件上传', 'BOTH', 'BUSINESS', 'STABLE',
        '{"type":"object","properties":{"files":{"type":"array"},"accept":{"type":"string"},"maxSize":{"type":"number"},"maxCount":{"type":"number"}}}',
        '{"change":{"description":"文件变更"},"success":{"description":"上传成功"},"error":{"description":"上传失败"}}',
        true, false, 'lowcode:component:FileUploader:upload', '1.0.0', 'PUBLISHED', 3, 'system')
ON CONFLICT (tenant_id, component_code) WHERE deleted = false DO NOTHING;

-- ===== 移动 MOBILE (platform=MOBILE) =====
INSERT INTO lc_component_registry (id, tenant_id, component_code, component_name, component_type, display_name, platform, category, compatibility_grade, props_schema, event_schema, permission_support, validation_support, permission_code, component_version, status, sort_no, created_by)
VALUES ('01LCREG0000000000000000018', 'default', 'MobileCameraUpload', '移动拍照上传', 'MOBILE', '拍照上传', 'MOBILE', 'MOBILE', 'STABLE',
        '{"type":"object","properties":{"files":{"type":"array"},"maxSize":{"type":"number"},"quality":{"type":"string"}}}',
        '{"change":{"description":"文件变更"},"success":{"description":"上传成功"}}',
        true, false, 'lowcode:component:MobileCameraUpload:upload', '1.0.0', 'PUBLISHED', 1, 'system')
ON CONFLICT (tenant_id, component_code) WHERE deleted = false DO NOTHING;

INSERT INTO lc_component_registry (id, tenant_id, component_code, component_name, component_type, display_name, platform, category, compatibility_grade, props_schema, event_schema, permission_support, validation_support, permission_code, component_version, status, sort_no, created_by)
VALUES ('01LCREG0000000000000000019', 'default', 'MobileScanInput', '移动扫码输入', 'MOBILE', '扫码输入', 'MOBILE', 'MOBILE', 'STABLE',
        '{"type":"object","properties":{"value":{"type":"string"},"scanType":{"type":"string"}}}',
        '{"change":{"description":"值变更"},"scan":{"description":"扫码完成"}}',
        false, true, NULL, '1.0.0', 'PUBLISHED', 2, 'system')
ON CONFLICT (tenant_id, component_code) WHERE deleted = false DO NOTHING;

-- ===== 图表 CHART (platform=BOTH) =====
INSERT INTO lc_component_registry (id, tenant_id, component_code, component_name, component_type, display_name, platform, category, compatibility_grade, props_schema, event_schema, permission_support, validation_support, permission_code, component_version, status, sort_no, created_by)
VALUES ('01LCREG0000000000000000020', 'default', 'LineChart', '折线图', 'CHART', '折线图', 'BOTH', 'CHART', 'STABLE',
        '{"type":"object","properties":{"data":{"type":"array"},"xField":{"type":"string"},"yField":{"type":"string"},"title":{"type":"string"}}}',
        '{}',
        false, false, NULL, '1.0.0', 'PUBLISHED', 1, 'system')
ON CONFLICT (tenant_id, component_code) WHERE deleted = false DO NOTHING;

INSERT INTO lc_component_registry (id, tenant_id, component_code, component_name, component_type, display_name, platform, category, compatibility_grade, props_schema, event_schema, permission_support, validation_support, permission_code, component_version, status, sort_no, created_by)
VALUES ('01LCREG0000000000000000021', 'default', 'BarChart', '柱状图', 'CHART', '柱状图', 'BOTH', 'CHART', 'STABLE',
        '{"type":"object","properties":{"data":{"type":"array"},"xField":{"type":"string"},"yField":{"type":"string"},"title":{"type":"string"}}}',
        '{}',
        false, false, NULL, '1.0.0', 'PUBLISHED', 2, 'system')
ON CONFLICT (tenant_id, component_code) WHERE deleted = false DO NOTHING;

INSERT INTO lc_component_registry (id, tenant_id, component_code, component_name, component_type, display_name, platform, category, compatibility_grade, props_schema, event_schema, permission_support, validation_support, permission_code, component_version, status, sort_no, created_by)
VALUES ('01LCREG0000000000000000022', 'default', 'PieChart', '饼图', 'CHART', '饼图', 'BOTH', 'CHART', 'STABLE',
        '{"type":"object","properties":{"data":{"type":"array"},"nameField":{"type":"string"},"valueField":{"type":"string"},"title":{"type":"string"}}}',
        '{}',
        false, false, NULL, '1.0.0', 'PUBLISHED', 3, 'system')
ON CONFLICT (tenant_id, component_code) WHERE deleted = false DO NOTHING;
