-- ============================================================
-- V048__drama_compose_job.sql
-- P2-D 批次 5-C 短剧真机合成任务表
-- 设计来源:
--   - platform-drama CompositionWorker + ADR 0004 P2-D
--   - 后端实体: DramaComposeJob (yutong-ai-service)
--   - 约束: 幂等 IF NOT EXISTS / DROP IF EXISTS, 禁止 Flyway 占位 ${}(placeholder-replacement=false)
--   - 遵循 AGENTS.md 硬约束: ULID varchar(32) PK、tenant_id、deleted/version、
--     created_*/updated_* 规范、CHECK 状态机、中文 COMMENT
-- 状态机: PENDING → RUNNING → SUCCESS / FAILED (失败关闭, 不返回假 URL)
-- ============================================================

CREATE TABLE IF NOT EXISTS drama_compose_job (
    id                  varchar(32)     NOT NULL,
    tenant_id           varchar(32)     NOT NULL,
    title               varchar(256)    NOT NULL,
    shot_videos_json    text            NOT NULL,
    audio_tracks_json   text,
    subtitle_file       varchar(512),
    resolution          varchar(16)     NOT NULL DEFAULT '720p',
    transition_sec      int             NOT NULL DEFAULT 0,
    output_path         varchar(1024),
    output_size         bigint,
    duration_sec        numeric(10,3),
    status              varchar(16)     NOT NULL DEFAULT 'PENDING',
    error_message       varchar(500),
    log_tail            varchar(4000),
    exit_code           int,
    started_at          timestamptz,
    finished_at         timestamptz,
    created_by          varchar(64),
    created_time        timestamptz     NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_time        timestamptz,
    deleted             boolean         NOT NULL DEFAULT false,
    version             int             NOT NULL DEFAULT 0,
    remark              varchar(512),
    CONSTRAINT pk_drama_compose_job PRIMARY KEY (id)
);

ALTER TABLE drama_compose_job DROP CONSTRAINT IF EXISTS chk_drama_compose_job_status;
ALTER TABLE drama_compose_job ADD CONSTRAINT chk_drama_compose_job_status
    CHECK (status IN ('PENDING','RUNNING','SUCCESS','FAILED'));

ALTER TABLE drama_compose_job DROP CONSTRAINT IF EXISTS chk_drama_compose_job_resolution;
ALTER TABLE drama_compose_job ADD CONSTRAINT chk_drama_compose_job_resolution
    CHECK (resolution IN ('480p','720p','1080p'));

ALTER TABLE drama_compose_job DROP CONSTRAINT IF EXISTS chk_drama_compose_job_transition;
ALTER TABLE drama_compose_job ADD CONSTRAINT chk_drama_compose_job_transition
    CHECK (transition_sec >= 0 AND transition_sec <= 10);

ALTER TABLE drama_compose_job DROP CONSTRAINT IF EXISTS chk_drama_compose_job_size;
ALTER TABLE drama_compose_job ADD CONSTRAINT chk_drama_compose_job_size
    CHECK (output_size IS NULL OR output_size >= 0);

ALTER TABLE drama_compose_job DROP CONSTRAINT IF EXISTS chk_drama_compose_job_duration;
ALTER TABLE drama_compose_job ADD CONSTRAINT chk_drama_compose_job_duration
    CHECK (duration_sec IS NULL OR duration_sec >= 0);

CREATE INDEX IF NOT EXISTS idx_drama_compose_job_tenant ON drama_compose_job (tenant_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_drama_compose_job_tenant_status ON drama_compose_job (tenant_id, status) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_drama_compose_job_tenant_created ON drama_compose_job (tenant_id, created_time DESC) WHERE deleted = false;

COMMENT ON TABLE drama_compose_job IS '短剧合成任务 — V048 P2-D 批次 5-C: 真机 ffmpeg 执行, PENDING→RUNNING→SUCCESS/FAILED, 失败关闭无假 URL';
COMMENT ON COLUMN drama_compose_job.shot_videos_json IS '镜头视频相对路径 JSON 数组 (workDir 下, 已做越权校验)';
COMMENT ON COLUMN drama_compose_job.audio_tracks_json IS '配音音轨相对路径 JSON 数组 (可空)';
COMMENT ON COLUMN drama_compose_job.output_path IS '输出文件路径 (服务端分配 outputs/{jobId}.mp4)';
COMMENT ON COLUMN drama_compose_job.duration_sec IS '成片时长秒 (ffprobe 回填, 超上限转 FAILED)';
COMMENT ON COLUMN drama_compose_job.log_tail IS 'ffmpeg 执行日志尾部 4KB (排障)';
COMMENT ON COLUMN drama_compose_job.status IS '状态机 PENDING → RUNNING → SUCCESS/FAILED';
