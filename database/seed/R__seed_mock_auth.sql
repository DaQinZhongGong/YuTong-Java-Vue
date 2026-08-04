-- R__seed_mock_auth: Mock 鉴权种子数据 (可重复执行)
-- 仅用于 local/test profile；生产环境禁止启用 Mock 鉴权
-- 设计来源: 15-权限接入边界设计、32-企业级权限与租户接入方案、98-后端实现蓝图

-- Mock 账号 / 权限 / 角色以代码常量方式承载 (见 MockAuthAdapter)
-- 这里仅保留迁移校验占位，便于 P2 检查脚本能验证 seed 目录存在
SELECT 1;
