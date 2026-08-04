<script setup lang="ts">
/**
 * BaseTable 通用表格组件
 * 设计来源: 66-前端组件API与状态管理详设 line 11-21 + 71-80
 *
 * 状态机: loading(骨架屏)/empty/error/ready/forbidden
 * 业务页面禁止再直接使用 el-table+el-pagination 组合, 一律走本组件。
 */
import { computed } from 'vue'
import PageState from './PageState.vue'
import type { PageResult } from '@/api/types'

/** 列定义 (66 号文档 line 23-35) */
export interface Column {
  key: string
  title: string
  width?: number | string
  align?: 'left' | 'center' | 'right'
  fixed?: 'left' | 'right'
  ellipsis?: boolean
  sortable?: boolean
  formatter?: (row: any, col: Column, value: any) => string
  /** 列级权限码 (前端可选择性使用 v-permission 指令处理) */
  permission?: string
}

interface Props {
  columns: Column[]
  data: any[]
  loading?: boolean
  pagination?: PageResult<unknown>
  rowKey?: string
  /** 错误状态 (触发 PageState error) */
  error?: boolean
  /** 错误 traceId (展示在 PageState) */
  traceId?: string
  /** 错误消息覆盖 */
  errorMessage?: string
  /** 无权限状态 (触发 PageState no-permission) */
  forbidden?: boolean
  /** 权限码 (展示在 PageState no-permission) */
  permissionCode?: string
  /** 空状态自定义文案 */
  emptyText?: string
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  rowKey: 'id',
  error: false,
  forbidden: false,
})

const emit = defineEmits<{
  (e: 'page-change', page: number): void
  (e: 'sort-change', payload: { prop: string | null; order: string | null }): void
  (e: 'selection-change', selection: any[]): void
  (e: 'refresh'): void
}>()

/** 状态机: forbidden > error > loading > empty > ready */
const status = computed<'loading' | 'empty' | 'error' | 'ready' | 'forbidden'>(() => {
  if (props.forbidden) return 'forbidden'
  if (props.error) return 'error'
  if (props.loading) return 'loading'
  if (!props.data || props.data.length === 0) return 'empty'
  return 'ready'
})

const total = computed(() => props.pagination?.total ?? 0)
const currentPage = computed(() => props.pagination?.page ?? 1)
const pageSize = computed(() => props.pagination?.size ?? 10)

function handlePageChange(page: number) {
  emit('page-change', page)
}

function handleSortChange(payload: { prop: string | null; order: string | null }) {
  emit('sort-change', { prop: payload.prop, order: payload.order })
}

function handleSelectionChange(selection: any[]) {
  emit('selection-change', selection)
}

function handleRefresh() {
  emit('refresh')
}
</script>

<template>
  <div class="yt-base-table">
    <!-- 状态机: forbidden (403) -->
    <PageState
      v-if="status === 'forbidden'"
      type="no-permission"
      :permission-code="permissionCode"
    />

    <!-- 状态机: error -->
    <PageState
      v-else-if="status === 'error'"
      type="error"
      :trace-id="traceId"
      :message="errorMessage"
      @retry="handleRefresh"
    />

    <!-- 状态机: empty (首次加载完成且无数据时展示空状态) -->
    <PageState
      v-else-if="status === 'empty'"
      type="empty"
      :message="emptyText"
    />

    <!-- 状态机: loading / ready (表格主体, 表头稳定) -->
    <template v-else>
      <el-table
        v-loading="loading"
        :data="data"
        :row-key="rowKey"
        border
        stripe
        @sort-change="handleSortChange"
        @selection-change="handleSelectionChange"
      >
        <el-table-column
          v-for="col in columns"
          :key="col.key"
          :prop="col.key"
          :label="col.title"
          :width="col.width"
          :align="col.align || 'left'"
          :fixed="col.fixed"
          :show-overflow-tooltip="col.ellipsis"
          :sortable="col.sortable ? 'custom' : false"
        >
          <template #default="scope">
            <slot
              v-if="$slots[`col-${col.key}`]"
              :name="`col-${col.key}`"
              :row="scope.row"
              :index="scope.$index"
            />
            <template v-else-if="col.formatter">{{
              col.formatter(scope.row, col, scope.row[col.key])
            }}</template>
            <template v-else>{{ scope.row[col.key] }}</template>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > 0"
        style="margin-top: 16px; justify-content: flex-end"
        :current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next, jumper"
        @current-change="handlePageChange"
      />
    </template>
  </div>
</template>
