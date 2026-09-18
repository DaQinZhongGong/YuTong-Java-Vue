-- ============================================================
-- V041__media_drama_copilot_parity.sql
-- Phase 7: 多模态 /media/* + 垂类(短剧+编程助手占位) parity
-- 设计来源:
--   - 多模态媒体生成 — 对齐 enterprise-ai / 本项目 ai_media_job
--   - 短剧垂类 — 对齐 drama vertical (drama_script / drama_scene)
--   - 编程助手 Copilot — 低代码占位 (生成 form/page 模板，无需独立表，复用 lc_* 元数据)
-- 约束:
--   - 主键 varchar(32) ULID (AGENTS.md 硬约束)
--   - Flyway placeholder-replacement=false 兼容，禁止 ${}
--   - 幂等 CREATE TABLE IF NOT EXISTS + 索引/约束兼容
--   - jsonb 字段前端可视化配置实时生效
-- ============================================================

-- ------------------------------------------------------------
-- 1. ai_media_job — 多模态媒体生成任务 (image/video/audio/ppt)
-- 参考: drama vertical / media 模块, 本项目 yutong-ai-service MediaService
-- 媒体类型: image / video / audio / ppt
-- 状态: PENDING / RUNNING / SUCCESS / FAILED
-- 前端路径: /api/v1/media/{type} (MediaController)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_media_job (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    media_type          varchar(32)     NOT NULL,
    prompt              text,
    provider_code       varchar(64),
    model_code          varchar(64),
    status              varchar(16)     NOT NULL DEFAULT 'PENDING',
    input_json          jsonb,
    output_url          varchar(1024),
    output_json         jsonb,
    cost                numeric(12,4)   DEFAULT 0,
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_ai_media_job PRIMARY KEY (id)
);

ALTER TABLE ai_media_job DROP CONSTRAINT IF EXISTS chk_ai_media_job_type;
ALTER TABLE ai_media_job ADD CONSTRAINT chk_ai_media_job_type
    CHECK (media_type IN ('image','video','audio','ppt'));

ALTER TABLE ai_media_job DROP CONSTRAINT IF EXISTS chk_ai_media_job_status;
ALTER TABLE ai_media_job ADD CONSTRAINT chk_ai_media_job_status
    CHECK (status IN ('PENDING','RUNNING','SUCCESS','FAILED'));

CREATE INDEX IF NOT EXISTS idx_ai_media_job_tenant_type ON ai_media_job (tenant_id, media_type) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_media_job_tenant_status ON ai_media_job (tenant_id, status) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_media_job_tenant_created ON ai_media_job (tenant_id, created_time DESC) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_media_job_input_gin ON ai_media_job USING GIN (input_json);
CREATE INDEX IF NOT EXISTS idx_ai_media_job_output_gin ON ai_media_job USING GIN (output_json);

COMMENT ON TABLE ai_media_job IS '多模态媒体任务 — V041 Phase 7: media_type image/video/audio/ppt, status PENDING/RUNNING/SUCCESS/FAILED, mock provider 无外部调用';
COMMENT ON COLUMN ai_media_job.media_type IS '媒体类型: image/video/audio/ppt';
COMMENT ON COLUMN ai_media_job.prompt IS '生成提示词';
COMMENT ON COLUMN ai_media_job.provider_code IS '提供方编码 (mock/local/第三方)';
COMMENT ON COLUMN ai_media_job.model_code IS '模型编码';
COMMENT ON COLUMN ai_media_job.status IS '任务状态: PENDING/RUNNING/SUCCESS/FAILED';
COMMENT ON COLUMN ai_media_job.input_json IS '输入 JSON: {prompt, params, style, size, ...}';
COMMENT ON COLUMN ai_media_job.output_url IS '输出资源 URL (mock 返回 fake url)';
COMMENT ON COLUMN ai_media_job.output_json IS '输出 JSON: {url, meta, duration, ...}';
COMMENT ON COLUMN ai_media_job.cost IS '本次任务成本 (计费预留)';

-- ------------------------------------------------------------
-- 2. drama_script — 短剧剧本 (垂类占位, yutong-sample-service)
-- 参考: drama vertical drama_script 最小可用字段
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS drama_script (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    title               varchar(256)    NOT NULL,
    synopsis            text,
    status              varchar(16)     NOT NULL DEFAULT 'DRAFT',
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_drama_script PRIMARY KEY (id)
);

ALTER TABLE drama_script DROP CONSTRAINT IF EXISTS chk_drama_script_status;
ALTER TABLE drama_script ADD CONSTRAINT chk_drama_script_status
    CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED'));

CREATE INDEX IF NOT EXISTS idx_drama_script_tenant_status ON drama_script (tenant_id, status) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_drama_script_tenant_created ON drama_script (tenant_id, created_time DESC) WHERE deleted = false;

COMMENT ON TABLE drama_script IS '短剧剧本 — V041 Phase 7: 垂类占位 yutong-sample-service, 对齐 drama vertical drama_script (标题/梗概/状态)';
COMMENT ON COLUMN drama_script.title IS '剧本标题';
COMMENT ON COLUMN drama_script.synopsis IS '梗概';
COMMENT ON COLUMN drama_script.status IS '状态: DRAFT/PUBLISHED/ARCHIVED';

-- ------------------------------------------------------------
-- 3. drama_scene — 短剧分镜/场景 (归属剧本)
-- 参考: drama vertical drama_scene 最小可用字段
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS drama_scene (
    id                              varchar(32)     NOT NULL,
    tenant_id                       varchar(32)     NOT NULL,
    drama_id                        varchar(32)     NOT NULL,
    scene_no                        int             NOT NULL,
    description                     text,
    character_consistency_json      jsonb,
    created_by                      varchar(64),
    created_time                    timestamptz     NOT NULL DEFAULT now(),
    updated_by                      varchar(64),
    updated_time                    timestamptz,
    deleted                         boolean         NOT NULL DEFAULT false,
    version                         int             NOT NULL DEFAULT 0,
    remark                          varchar(512),
    CONSTRAINT pk_drama_scene PRIMARY KEY (id),
    CONSTRAINT fk_drama_scene_drama FOREIGN KEY (drama_id) REFERENCES drama_script(id)
);

ALTER TABLE drama_scene DROP CONSTRAINT IF EXISTS chk_drama_scene_no;
ALTER TABLE drama_scene ADD CONSTRAINT chk_drama_scene_no CHECK (scene_no >= 1);

CREATE UNIQUE INDEX IF NOT EXISTS uk_drama_scene_drama_no
    ON drama_scene (drama_id, scene_no) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_drama_scene_tenant_drama ON drama_scene (tenant_id, drama_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_drama_scene_character_gin ON drama_scene USING GIN (character_consistency_json);

COMMENT ON TABLE drama_scene IS '短剧场景 — V041 Phase 7: 归属 drama_script, scene_no 剧内唯一, character_consistency_json 角色一致性配置';
COMMENT ON COLUMN drama_scene.drama_id IS '所属剧本 ID (drama_script.id)';
COMMENT ON COLUMN drama_scene.scene_no IS '场次序号 (剧内唯一，>=1)';
COMMENT ON COLUMN drama_scene.description IS '场景描述/台词/分镜';
COMMENT ON COLUMN drama_scene.character_consistency_json IS '角色一致性 JSON: {characters:[{name, avatar, voiceId}], style, ...} 可视化配置实时生效';

