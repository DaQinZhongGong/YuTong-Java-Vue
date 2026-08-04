-- ============================================================
-- V034__plugin_installation_unique_constraint.sql
-- 插件安装记录增加唯一约束，防止并发安装产生脏数据
-- 设计来源: 45-插件与模板生态设计、Phase 6 交叉验证修复
-- ============================================================

-- 增加唯一约束：同一租户下同一插件只能有一条 ACTIVE/DISABLED 记录
-- INACTIVE（已卸载）状态排除在外，允许重新安装
CREATE UNIQUE INDEX IF NOT EXISTS uk_plugin_installation_tenant_plugin
    ON plugin_installation (tenant_id, plugin_id)
    WHERE status IN ('ACTIVE', 'DISABLED') AND deleted = false;

COMMENT ON INDEX uk_plugin_installation_tenant_plugin IS
    '同一租户同一插件只能安装一次（未卸载状态下），防止并发重复安装';