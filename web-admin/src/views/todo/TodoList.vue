<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getTodos, completeTodo } from '@/api/todo'
import type { TodoTask, PageResult } from '@/api/types'

const { t } = useI18n()
const loading = ref(false)
const tableData = ref<TodoTask[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const statusLabel: Record<string, string> = { PENDING: t('todo.status.pending'), DONE: t('todo.status.done'), CANCELLED: t('todo.status.cancelled') }
const statusTagType: Record<string, '' | 'primary' | 'success' | 'warning' | 'info' | 'danger'> = { PENDING: 'warning', DONE: 'success', CANCELLED: 'info' }

async function loadData() {
  loading.value = true
  try {
    const res: PageResult<TodoTask> = await getTodos({ page: currentPage.value, size: pageSize.value })
    tableData.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function handleComplete(row: TodoTask) {
  await completeTodo(row.id)
  ElMessage.success(t('todo.msg.completed'))
  loadData()
}

function handlePageChange(page: number) { currentPage.value = page; loadData() }

onMounted(loadData)
</script>

<template>
  <div>
    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="title" :label="$t('todo.field.title')" min-width="200" />
      <el-table-column prop="businessType" :label="$t('todo.field.businessType')" width="120" />
      <el-table-column prop="todoStatus" :label="$t('common.field.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType[row.todoStatus] || 'info'">{{ statusLabel[row.todoStatus] || row.todoStatus }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="priority" :label="$t('todo.field.priority')" width="100" />
      <el-table-column prop="dueDate" :label="$t('todo.field.dueDate')" width="180" />
      <el-table-column prop="createdAt" :label="$t('common.field.createdTime')" width="180" />
      <el-table-column :label="$t('todo.field.operation')" width="100" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.todoStatus === 'PENDING'" size="small" type="success" @click="handleComplete(row as TodoTask)">{{ $t('todo.action.complete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top: 16px; justify-content: flex-end" v-model:current-page="currentPage" v-model:page-size="pageSize" :total="total" layout="total, prev, pager, next" @current-change="handlePageChange" />
  </div>
</template>
