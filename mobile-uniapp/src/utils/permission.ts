/**
 * 权限可见性工具（P0-6 基础接入）。
 *
 * 设计来源：96 号文档移动端权限可见性矩阵。本文件提供：
 *   - checkPermission(code)：工具函数，用于模板 v-if / 脚本判断
 *   - vPermission：可选的自定义指令（需在 main.ts 中 app.directive('permission', vPermission) 注册）
 *
 * 实际权限判定委托给 store/auth.ts 的 hasPermission，admin 的 `*` 通配放行。
 * 96 号文档要求 11 个权限码可见性控制，完整矩阵工作量较大，本任务仅做基础接入：
 * 接入点见 workbench 快捷入口、详情审批按钮、扫码页入口校验。
 */

import type { Directive } from 'vue';
import { hasPermission } from '@/store/auth';

/**
 * 校验当前用户是否拥有指定权限码。
 * @param code 权限码，如 mobile:scan:use
 */
export function checkPermission(code: string): boolean {
  return hasPermission(code);
}

/**
 * 可选的 v-permission 自定义指令。
 * 用法：v-permission="'mobile:scan:use'"，无权限时移除元素。
 * 注意：指令为一次性挂载移除，不具备响应式；需要响应式请使用 checkPermission + v-if。
 */
export const vPermission: Directive<HTMLElement, string> = {
  mounted(el, binding) {
    if (!checkPermission(binding.value)) {
      if (el.parentNode) {
        el.parentNode.removeChild(el);
      }
    }
  },
};
