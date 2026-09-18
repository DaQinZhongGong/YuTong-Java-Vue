-- ============================================================
-- V062__ai_drama_storyboard_scene_unique.sql
-- 分镜 scene_no 项目内唯一，保证 compose 排序稳定
-- 约束: 幂等 CREATE UNIQUE INDEX IF NOT EXISTS
-- ============================================================

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_drama_storyboard_project_scene
    ON ai_drama_storyboard (project_id, scene_no)
    WHERE deleted = false;

COMMENT ON INDEX uk_ai_drama_storyboard_project_scene IS
    '同一项目内 scene_no 唯一（软删过滤），compose 按 scene_no 排序依赖此约束';
