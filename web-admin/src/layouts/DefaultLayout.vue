<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Menu as IconMenu,
  Document,
  Setting,
  SwitchButton,
  User,
  Fold,
  Expand,
  Goods,
  Tickets,
  Bell,
  ChatDotRound,
  Folder,
  Files,
  DataBoard,
  Tools,
  List,
  DocumentCopy,
  Upload,
  DocumentChecked,
  TrendCharts,
  Box,
  Sell,
  Connection,
  Reading,
  Coin,
  Share,
  Histogram,
  Key,
  Monitor,
  Cpu,
  Odometer,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useLicenseStore } from '@/stores/license'
import { hasPermission, hasAnyPermission } from '@/utils/permission'
import { MOCK_USER_LABELS, type MockUserType } from '@/utils/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const licenseStore = useLicenseStore()

const isCollapse = ref(false)
const activeMenu = computed(() => route.path)

/**
 * GA2-16: 基于权限码过滤的菜单结构。
 * 设计来源: 96-端侧权限可见性矩阵详设 (菜单权限矩阵)
 *
 * 顶层菜单: 工作台 / 业务管理 / 系统管理 / 平台能力
 * 子菜单: 每项指定所需权限码 (满足任一即可见), 通过 hasAnyPermission 过滤。
 * 子菜单全部不可见时, 父菜单也不可见。
 *
 * GA2-L171: 新增 license 字段 (商业模块授权过滤)。
 * 设计来源: 70-商业授权与版本能力裁剪详设「模块授权行为矩阵」。
 * 子菜单标注所属商业模块编码, 通过 licenseStore.isModuleActive 过滤未授权模块。
 * 后端 LicenseInterceptor 是强制边界，前端菜单隐藏只是体验优化。
 */
interface MenuItem {
  index: string
  title: string
  icon: typeof DataBoard
  permission?: string | string[]
  /** GA2-L171: 商业模块编码 (lowcode/ai/report/workflow/datasource/plugin)，未设置表示基础模块 */
  license?: string
}

interface MenuGroup {
  index: string
  title: string
  icon: typeof DataBoard
  children: MenuItem[]
}

const menuGroups = computed<MenuGroup[]>(() => {
  const groups: MenuGroup[] = [
    // 工作台: 所有登录用户可见 (dashboard:view)
    {
      index: 'dashboard',
      title: '工作台',
      icon: DataBoard,
      children: [
        { index: '/dashboard', title: '工作台', icon: DataBoard, permission: 'dashboard:view' },
      ],
    },
    // 业务管理: 申请单/客户/商品/合同档案
    {
      index: 'business',
      title: '业务管理',
      icon: Document,
      children: [
        { index: '/biz/requests', title: '申请单管理', icon: Tickets, permission: 'biz:request:list' },
        { index: '/biz/customers', title: '客户管理', icon: User, permission: 'biz:customer:list' },
        { index: '/biz/products', title: '商品管理', icon: Goods, permission: 'biz:product:list' },
        // GA2-35: 合同档案。复用 system:todo:list 权限码（与工单中心一致，全角色可见）
        { index: '/contracts', title: '合同档案', icon: DocumentChecked, permission: 'system:todo:list' },
        // GA2-36: 报表分析。权限码 report:view (admin/biz/viewer 可见，approver 不可见)
        // GA2-L171: 商业模块 report，License 未授权时隐藏
        { index: '/reports', title: '报表分析', icon: TrendCharts, permission: 'report:view', license: 'report' },
        // GA2-R3: 数据大屏。权限码 dashboard:view，商业模块 report
        { index: '/dashboards', title: '数据大屏', icon: DataBoard, permission: 'dashboard:view', license: 'report' },
        // GA2-37: 库存出入库。复用 system:todo:list 权限码（与工单/合同一致，全角色可见）
        { index: '/inventory', title: '库存余额', icon: Box, permission: 'system:todo:list' },
        { index: '/inventory/in-out', title: '出入库操作', icon: Sell, permission: 'system:todo:list' },
        // GA2-38: 外部接口同步。复用 system:todo:list 权限码（与工单/合同/库存一致，全角色可见）
        { index: '/ext-sync', title: '外部接口同步', icon: Connection, permission: 'system:todo:list' },
        // GA2-39: 知识库运营。复用 system:todo:list 权限码 (与工单/合同/库存/外部接口同步一致, 全角色可见)
        { index: '/kb-ops', title: '知识库运营', icon: Reading, permission: 'system:todo:list' },
        // GA2-40: 通知运营。复用 system:todo:list 权限码 (与 GA2-39 一致, 全角色可见)
        { index: '/notification-ops', title: '通知运营', icon: Bell, permission: 'system:todo:list' },
        // GA2-41: 支付订单。复用 system:todo:list 权限码 (与 GA2-40 一致, 全角色可见)
        { index: '/payment-ops', title: '支付订单', icon: Coin, permission: 'system:todo:list' },
        // GA2-42: 问卷表单。复用 system:todo:list 权限码 (与 GA2-41 一致, 全角色可见)
        { index: '/survey-ops', title: '问卷表单', icon: DocumentChecked, permission: 'system:todo:list' },
      ],
    },
    // GA2-44: BPMN 工作流引擎。设计来源: 41-工作流与BPMN引擎设计 L1+L2 轻量自研
    // 验证 7 项能力: 流程定义 CRUD + BPMN 解析 + 流程实例启动 + 任务办理通过 + 任务驳回 + 委派转办 + 运营监控
    // GA2-L171: 商业模块 workflow，License 未授权时隐藏整个分组
    {
      index: 'workflow',
      title: '工作流',
      icon: Share,
      children: [
        { index: '/workflow/definitions', title: '流程定义', icon: Document, permission: 'system:todo:list', license: 'workflow' },
        { index: '/workflow/instances', title: '流程实例', icon: Tickets, permission: 'system:todo:list', license: 'workflow' },
        { index: '/workflow/todo', title: '任务待办', icon: Bell, permission: 'system:todo:list', license: 'workflow' },
      ],
    },
    // GA2-45: AI 治理与评测。设计来源: 37-AI治理与评测设计
    // 5 大能力域: Prompt 治理 / AI 工具注册 / 成本治理 / 反馈闭环 / RAG 评测
    // 复用 system:todo:list 权限码 (与 GA2-40~44 一致, 全角色可见)
    // GA2-L171: 商业模块 ai，License 未授权时隐藏整个分组
    {
      index: 'ai-governance',
      title: 'AI 治理',
      icon: ChatDotRound,
      children: [
        { index: '/ai-governance/prompts', title: 'Prompt 治理', icon: Document, permission: 'system:todo:list', license: 'ai' },
        { index: '/ai-governance/tools', title: 'AI 工具注册', icon: Tools, permission: 'system:todo:list', license: 'ai' },
        { index: '/ai-governance/monitor', title: 'AI 治理监控', icon: DataBoard, permission: 'system:todo:list', license: 'ai' },
      ],
    },
    // 系统管理: 待办/消息/文件/字典/参数配置/操作日志/任务日志/导入导出
    {
      index: 'system',
      title: '系统管理',
      icon: Setting,
      children: [
        { index: '/todos', title: '待办任务', icon: Bell, permission: 'system:todo:list' },
        { index: '/system/messages', title: '站内消息', icon: Document, permission: 'system:message:list' },
        { index: '/system/files', title: '文件管理', icon: Folder, permission: 'system:file:list' },
        { index: '/system/dict-types', title: '字典类型', icon: IconMenu, permission: 'system:dict:list' },
        { index: '/system/dict-items', title: '字典项', icon: Files, permission: 'system:dict-item:list' },
        { index: '/system/configs', title: '参数配置', icon: Tools, permission: 'system:config:list' },
        { index: '/system/operation-logs', title: '操作日志', icon: DocumentCopy, permission: 'system:operation-log:list' },
        { index: '/system/job-logs', title: '任务日志', icon: List, permission: 'system:job-log:list' },
        { index: '/system/import-export-tasks', title: '导入导出任务', icon: Upload, permission: 'system:import-export-task:list' },
        // GA2-L172: 商业授权管理。设计来源: 70-商业授权与版本能力裁剪详设
        // 复用 system:config:list 权限码 (系统管理员可见，与参数配置同级)
        { index: '/system/license', title: '商业授权', icon: Key, permission: 'system:config:list' },
        // GA2-46: 多数据源管理。设计来源: 46-多数据源与数据集设计
        // 复用 system:todo:list 权限码 (与 GA2-45 一致, 全角色可见)
        // GA2-L171: 商业模块 datasource，License 未授权时隐藏
        { index: '/datasources', title: '数据源管理', icon: Histogram, permission: 'system:todo:list', license: 'datasource' },
      ],
    },
    // 平台能力: AI 助手 / 低代码
    // GA2-L171: ai-chat 和低代码均为商业模块，License 未授权时隐藏
    {
      index: 'platform',
      title: '平台能力',
      icon: ChatDotRound,
      children: [
        { index: '/ai/assistant', title: 'AI 助手', icon: ChatDotRound, permission: 'ai:assistant:use', license: 'ai' },
        // AI 供应商管理: 展示供应商列表与健康状态, 触发健康检查。复用 ai:assistant:use 权限码, 商业模块 ai
        { index: '/ai/providers', title: '供应商管理', icon: Connection, permission: 'ai:assistant:use', license: 'ai' },
        { index: '/lowcode/entities', title: '低代码实体', icon: DataBoard, permission: 'lc:entity:list', license: 'lowcode' },
        { index: '/lowcode/pages', title: '低代码页面', icon: DataBoard, permission: 'lc:page:list', license: 'lowcode' },
        // GA2-47: 生成任务中心。设计来源: 47-低代码设计器交互详设 生成任务中心章节
        // 复用 system:todo:list 权限码 (与 GA2-40~46 一致, 全角色可见)
        { index: '/lowcode/generator-tasks', title: '生成任务中心', icon: DataBoard, permission: 'system:todo:list', license: 'lowcode' },
        // 45 号文档: 插件市场
        { index: '/plugin-market', title: '插件市场', icon: Goods, permission: 'plugin:view', license: 'plugin' },
        // 45 号文档 E0: 插件注册表管理
        { index: '/plugin-registries', title: '插件注册表', icon: Tools, permission: 'plugin:view', license: 'plugin' },
        // 45 号文档 E0: 代码模板管理
        { index: '/code-templates', title: '代码模板管理', icon: Document, permission: 'template:view', license: 'plugin' },
      ],
    },
    // Phase 6: AI 扩展 — MCP/技能/Agent/AI Flow
    {
      index: 'ai-ext',
      title: 'AI 扩展',
      icon: Cpu,
      children: [
        { index: '/mcp', title: 'MCP 广场', icon: Connection, permission: 'system:config:list', license: 'ai' },
        { index: '/skill', title: '技能市场', icon: DocumentCopy, permission: 'system:config:list', license: 'ai' },
        { index: '/agent', title: 'Agent 列表', icon: Share, permission: 'ai:assistant:use', license: 'ai' },
        { index: '/aiflow', title: 'AI Flow 设计器', icon: Histogram, permission: 'ai:assistant:use', license: 'ai' },
        { index: '/ai/memories', title: '记忆管理', icon: DocumentCopy, permission: 'ai:assistant:use', license: 'ai' },
        { index: '/ai/media', title: '多模态生成', icon: Monitor, permission: 'ai:assistant:use', license: 'ai' },
        { index: '/ai/copilot', title: 'Copilot 草稿', icon: Cpu, permission: 'ai:assistant:use', license: 'ai' },
      ],
    },
    // GA2-L177: 平台监控。设计来源: 91-Web基础后台逐页交互详设「服务健康页/缓存概览页」
    // 权限码对齐 contracts/registries/permissions.yaml line 35 (monitor 域)，admin 可见
    {
      index: 'monitor',
      title: '平台监控',
      icon: Monitor,
      children: [
        { index: '/monitor/health', title: '服务健康', icon: Cpu, permission: 'monitor:health:view' },
        { index: '/monitor/cache', title: '缓存监控', icon: DataBoard, permission: 'monitor:cache:view' },
        { index: '/monitor/metrics', title: '性能指标', icon: Odometer, permission: 'monitor:metrics:view' },
      ],
    },
  ]
  // 过滤: 子菜单按权限 + License 可见, 父菜单按是否有可见子菜单决定
  // GA2-L171: 新增 license 维度过滤 (licenseStore.isModuleActive)
  return groups
    .map((g) => ({
      ...g,
      children: g.children.filter((c) => {
        // GA2-16: 权限码校验
        if (c.permission) {
          if (Array.isArray(c.permission)) {
            if (!hasAnyPermission(c.permission)) return false
          } else {
            if (!hasPermission(c.permission)) return false
          }
        }
        // GA2-L171: 商业模块 License 校验
        if (c.license && !licenseStore.isModuleActive(c.license)) {
          return false
        }
        return true
      }),
    }))
    .filter((g) => g.children.length > 0)
})

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await authStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  } catch {
    // 用户取消
  }
}

/** GA2-16: dev 模式快速切换 Mock 用户 (用于权限矩阵验证)。 */
function handleSwitchMockUser(type: MockUserType) {
  authStore.switchMockUser(type)
  ElMessage.success(`已切换到 ${MOCK_USER_LABELS[type]}，请重新登录`)
  router.push('/login')
}
</script>

<template>
  <el-container class="layout">
    <!-- WCAG 2.4.1 Bypass Blocks: 跳过导航至主内容 -->
    <a href="#main-content" class="skip-link">跳过导航至主内容</a>
    <el-aside
      :width="isCollapse ? 'var(--yutong-sidebar-collapsed-width)' : 'var(--yutong-sidebar-width)'"
      class="layout-aside"
      role="navigation"
      aria-label="主导航菜单"
    >
      <div class="logo">
        <span v-if="!isCollapse" class="logo-full">YuTong 管理后台</span>
        <span v-else class="logo-short">YT</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        router
        background-color="#001529"
        text-color="#c0c4cc"
        active-text-color="#ffffff"
        aria-label="侧边栏导航"
      >
        <template v-for="group in menuGroups" :key="group.index">
          <!-- 单子菜单的分组直接渲染为顶层 menu-item -->
          <el-menu-item v-if="group.children.length === 1" :index="group.children[0].index">
            <el-icon><component :is="group.children[0].icon" /></el-icon>
            <template #title>{{ group.children[0].title }}</template>
          </el-menu-item>
          <!-- 多子菜单的分组渲染为 sub-menu -->
          <el-sub-menu v-else :index="group.index">
            <template #title>
              <el-icon><component :is="group.icon" /></el-icon>
              <span>{{ group.title }}</span>
            </template>
            <el-menu-item v-for="c in group.children" :key="c.index" :index="c.index">
              <el-icon><component :is="c.icon" /></el-icon>
              <template #title>{{ c.title }}</template>
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header" role="banner">
        <div class="header-left">
          <!-- WCAG 4.1.2 Name, Role, Value: 图标按钮必须有 aria-label -->
          <el-icon
            class="collapse-btn"
            role="button"
            tabindex="0"
            :aria-label="isCollapse ? '展开侧边栏' : '折叠侧边栏'"
            @click="isCollapse = !isCollapse"
            @keyup.enter="isCollapse = !isCollapse"
          >
            <Expand v-if="isCollapse" />
            <Fold v-else />
          </el-icon>
        </div>
        <div class="header-right">
          <!-- P2-G: 主题切换按钮(三态:light/dark/auto) -->
          <ThemeToggle />
          <!-- GA2-16: dev 模式 Mock 用户切换器 (96 号文档权限矩阵验证) -->
          <el-dropdown v-if="authStore.mockUser" trigger="click" @command="handleSwitchMockUser">
            <el-tag type="warning" size="small" effect="plain" class="mock-badge" role="button" tabindex="0"
              :aria-label="`当前 Mock 角色: ${MOCK_USER_LABELS[authStore.mockUser]}, 点击切换`">
              Mock: {{ MOCK_USER_LABELS[authStore.mockUser] }}
            </el-tag>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="t in (['admin','biz','approver','viewer'] as MockUserType[])" :key="t" :command="t">
                  {{ MOCK_USER_LABELS[t] }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-dropdown trigger="click">
            <span class="user-info" role="button" tabindex="0" aria-label="用户菜单">
              <el-icon class="user-avatar" aria-hidden="true"><User /></el-icon>
              <span class="username">{{ authStore.userInfo?.username || 'admin' }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon>
                  <span>退出登录</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <!-- WCAG 2.4.1: 主内容区锚点 -->
      <el-main id="main-content" class="layout-main" role="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}
.layout-aside {
  background-color: #001529;
  transition: width 0.28s;
  overflow: hidden;
}
.logo {
  height: var(--yutong-header-height);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background-color: #002140;
  font-weight: 600;
}
.logo-full {
  font-size: 16px;
  white-space: nowrap;
}
.logo-short {
  font-size: 20px;
}
.layout-aside :deep(.el-menu) {
  border-right: none;
}
.layout-header {
  height: var(--yutong-header-height);
  background-color: #fff;
  border-bottom: 1px solid var(--yutong-border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.mock-badge {
  cursor: pointer;
}
.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: var(--yutong-text-regular);
}
.collapse-btn:hover {
  color: var(--yutong-primary);
}
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.user-avatar {
  font-size: 18px;
  color: var(--yutong-primary);
}
.username {
  font-size: 14px;
  color: var(--yutong-text-regular);
}
.layout-main {
  background-color: var(--yutong-bg-page);
  padding: var(--yutong-content-padding);
}
</style>



