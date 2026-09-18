-- ============================================================
-- V060__ai_drama_storyboard.sql
-- 分镜视频生成对标 (业界同类实现 ShortDrama storyboard video)
-- 设计来源: docs/compose/spec/ai-depth-parity.md S2.2
-- 约束: 幂等 / ULID / tenant / 失败关闭 / 中文 COMMENT
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_drama_project (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    title               varchar(256)    NOT NULL,
    synopsis            text,
    art_style           varchar(64),
    style_ref           varchar(1024),
    aspect_ratio        varchar(16)     NOT NULL DEFAULT '16:9',
    compose_status      varchar(16)     NOT NULL DEFAULT 'NONE',
    compose_job_id      varchar(32),
    composed_path       varchar(1024),
    meta_json           jsonb,
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_ai_drama_project PRIMARY KEY (id)
);

ALTER TABLE ai_drama_project DROP CONSTRAINT IF EXISTS chk_ai_drama_project_compose;
ALTER TABLE ai_drama_project ADD CONSTRAINT chk_ai_drama_project_compose
    CHECK (compose_status IN ('NONE','PENDING','RUNNING','SUCCESS','FAILED'));

ALTER TABLE ai_drama_project DROP CONSTRAINT IF EXISTS chk_ai_drama_project_aspect;
ALTER TABLE ai_drama_project ADD CONSTRAINT chk_ai_drama_project_aspect
    CHECK (aspect_ratio IN ('16:9','9:16','1:1','4:3'));

CREATE INDEX IF NOT EXISTS idx_ai_drama_project_tenant
    ON ai_drama_project (tenant_id, created_time DESC) WHERE deleted = false;

COMMENT ON TABLE ai_drama_project IS '短剧项目 — 分镜视频生成与成片合成的聚合根';
COMMENT ON COLUMN ai_drama_project.compose_status IS '成片状态: NONE 未合成 / PENDING 待重算 / RUNNING / SUCCESS / FAILED';

CREATE TABLE IF NOT EXISTS ai_drama_storyboard (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    project_id          varchar(32)     NOT NULL,
    scene_no            int             NOT NULL DEFAULT 1,
    shot_type           varchar(32),
    location_name       varchar(128),
    image_prompt        text,
    video_prompt        text            NOT NULL,
    duration_seconds    numeric(6,2)    NOT NULL DEFAULT 5,
    reference_images    jsonb,
    video_status        varchar(16)     NOT NULL DEFAULT 'PENDING',
    video_id            varchar(128),
    video_url           varchar(1024),
    last_frame_url      varchar(1024),
    media_job_id        varchar(32),
    error_message       varchar(500),
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_ai_drama_storyboard PRIMARY KEY (id)
);

ALTER TABLE ai_drama_storyboard DROP CONSTRAINT IF EXISTS chk_ai_drama_storyboard_status;
ALTER TABLE ai_drama_storyboard ADD CONSTRAINT chk_ai_drama_storyboard_status
    CHECK (video_status IN ('PENDING','GENERATING','SUCCEEDED','FAILED'));

ALTER TABLE ai_drama_storyboard DROP CONSTRAINT IF EXISTS chk_ai_drama_storyboard_duration;
ALTER TABLE ai_drama_storyboard ADD CONSTRAINT chk_ai_drama_storyboard_duration
    CHECK (duration_seconds > 0 AND duration_seconds <= 60);

CREATE INDEX IF NOT EXISTS idx_ai_drama_storyboard_project
    ON ai_drama_storyboard (project_id, scene_no) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_drama_storyboard_status
    ON ai_drama_storyboard (tenant_id, video_status) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_ai_drama_storyboard_location
    ON ai_drama_storyboard (project_id, location_name) WHERE deleted = false;

COMMENT ON TABLE ai_drama_storyboard IS '分镜 — video_status 状态机 PENDING→GENERATING→SUCCEEDED/FAILED';
COMMENT ON COLUMN ai_drama_storyboard.last_frame_url IS '本镜末帧，供同场景下一镜作首帧承接';
COMMENT ON COLUMN ai_drama_storyboard.reference_images IS '参考图 URL 数组，≥2 走多参考 image-to-video';
