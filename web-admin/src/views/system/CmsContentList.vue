<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Edit, Delete } from '@element-plus/icons-vue'
import {
  getCmsContents,
  saveCmsContent,
  publishCmsContent,
  archiveCmsContent,
  deleteCmsContent,
  type CmsPageQuery,
} from '@/api/cms'
import type { CmsContent, SaveCmsContent } from '@/api/types'
import i18n from '@/locales'

/**
 * CMS 内容管理页。设计来源: 84-CMS 运营详设、ADR 0004 P2-F 批次 6-A。
 * 状态机 DRAFT → PUBLISHED → ARCHIVED, 已发布内容不可改回草稿、不可直接删除。
 */

const loading = ref(false)
const tableData = ref<CmsContent[]>([])
const total = ref(0)

const query = reactive<CmsPageQuery>({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  category: '',
})

async function fetchData() {
  loading.value = true
  try {
    const res = await getCmsContents(query)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  fetchData()
}

function handleReset() {
  query.keyword = ''
  query.status = ''
  query.category = ''
  query.page = 1
  fetchData()
}

function handlePageChange(page: number) {
  query.page = page
  fetchData()
}

function handleSizeChange(size: number) {
  query.size = size
  query.page = 1
  fetchData()
}

function statusTagType(status?: string) {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'ARCHIVED') return 'info'
  return 'warning'
}

// ===== 新建/编辑对话框 =====
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitting = ref(false)
const isEdit = ref(false)
const form = reactive<SaveCmsContent>({
  id: '',
  title: '',
  slug: '',
  contentMd: '',
  summary: '',
  category: 'announcement',
  tags: '',
})

function openCreate() {
  isEdit.value = false
  dialogTitle.value = i18n.global.t('system.cms.dialog.createTitle') as string
  Object.assign(form, {
    id: '',
    title: '',
    slug: '',
    contentMd: '',
    summary: '',
    category: 'announcement',
    tags: '',
  })
  dialogVisible.value = true
}

function openEdit(row: CmsContent) {
  isEdit.value = true
  dialogTitle.value = i18n.global.t('system.cms.dialog.editTitle') as string
  Object.assign(form, {
    id: row.id,
    title: row.title,
    slug: row.slug,
    contentMd: row.contentMd || '',
    summary: row.summary || '',
    category: row.category || 'announcement',
    tags: row.tags || '',
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.title) {
    ElMessage.warning(i18n.global.t('system.cms.message.titleRequired') as string)
    return
  }
  if (!form.slug || !/^[a-zA-Z0-9_-]{1,100}$/.test(form.slug)) {
    ElMessage.warning(i18n.global.t('system.cms.message.slugInvalid') as string)
    return
  }
  if (!form.contentMd) {
    ElMessage.warning(i18n.global.t('system.cms.message.contentRequired') as string)
    return
  }
  submitting.value = true
  try {
    await saveCmsContent({ ...form, id: isEdit.value ? form.id : '' })
    ElMessage.success(i18n.global.t('system.cms.message.saveSuccess') as string)
    dialogVisible.value = false
    fetchData()
  } finally {
    submitting.value = false
  }
}

async function handlePublish(row: CmsContent) {
  try {
    await ElMessageBox.confirm(
      i18n.global.t('system.cms.message.publishConfirm', { title: row.title }) as string,
      i18n.global.t('common.message.tip') as string,
      {
        confirmButtonText: i18n.global.t('common.action.confirm') as string,
        cancelButtonText: i18n.global.t('common.action.cancel') as string,
        type: 'info',
      }
    )
    await publishCmsContent(row.id)
    ElMessage.success(i18n.global.t('system.cms.message.publishSuccess') as string)
    fetchData()
  } catch {
    // 用户取消
  }
}

async function handleArchive(row: CmsContent) {
  try {
    await ElMessageBox.confirm(
      i18n.global.t('system.cms.message.archiveConfirm', { title: row.title }) as string,
      i18n.global.t('common.message.tip') as string,
      {
        confirmButtonText: i18n.global.t('common.action.confirm') as string,
        cancelButtonText: i18n.global.t('common.action.cancel') as string,
        type: 'warning',
      }
    )
    await archiveCmsContent(row.id)
    ElMessage.success(i18n.global.t('system.cms.message.archiveSuccess') as string)
    fetchData()
  } catch {
    // 用户取消
  }
}

async function handleDelete(row: CmsContent) {
  try {
    await ElMessageBox.confirm(
      i18n.global.t('system.cms.message.deleteConfirm', { title: row.title }) as string,
      i18n.global.t('common.message.tip') as string,
      {
        confirmButtonText: i18n.global.t('common.action.confirm') as string,
        cancelButtonText: i18n.global.t('common.action.cancel') as string,
        type: 'warning',
      }
    )
    await deleteCmsContent(row.id)
    ElMessage.success(i18n.global.t('system.cms.message.deleteSuccess') as string)
    fetchData()
  } catch {
    // 用户取消
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="cms-view">
    <h2 class="page-title" id="page-title">{{ $t('system.cms.page.list') }}</h2>

    <el-card shadow="never">
      <div class="toolbar" role="search" :aria-label="$t('system.cms.aria.search')">
        <el-input
          v-model="query.keyword"
          :placeholder="$t('system.cms.placeholder.keyword')"
          clearable
          style="width: 220px"
          :prefix-icon="Search"
          :aria-label="$t('system.cms.placeholder.keyword')"
          @keyup.enter="handleSearch"
        />
        <el-select
          v-model="query.status"
          :placeholder="$t('system.cms.placeholder.status')"
          clearable
          style="width: 140px"
          :aria-label="$t('system.cms.placeholder.status')"
        >
          <el-option :label="$t('system.cms.status.draft')" value="DRAFT" />
          <el-option :label="$t('system.cms.status.published')" value="PUBLISHED" />
          <el-option :label="$t('system.cms.status.archived')" value="ARCHIVED" />
        </el-select>
        <el-input
          v-model="query.category"
          :placeholder="$t('system.cms.placeholder.category')"
          clearable
          style="width: 160px"
          :aria-label="$t('system.cms.placeholder.category')"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" :icon="Search" @click="handleSearch">{{ $t('common.action.search') }}</el-button>
        <el-button @click="handleReset">{{ $t('common.action.reset') }}</el-button>
        <el-button v-permission="'cms:content:save'" type="success" :icon="Plus" @click="openCreate">{{ $t('common.action.create') }}</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        :empty-text="$t('system.cms.empty.noData')"
        :aria-label="$t('system.cms.aria.list')"
      >
        <el-table-column prop="title" :label="$t('system.cms.field.title')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="slug" :label="$t('system.cms.field.slug')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="category" :label="$t('system.cms.field.category')" width="130" align="center" />
        <el-table-column :label="$t('system.cms.field.status')" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ row.status === 'PUBLISHED' ? $t('system.cms.status.published') : row.status === 'ARCHIVED' ? $t('system.cms.status.archived') : $t('system.cms.status.draft') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="viewCount" :label="$t('system.cms.field.viewCount')" width="90" align="center" />
        <el-table-column prop="publishedAt" :label="$t('system.cms.field.publishedAt')" width="180" />
        <el-table-column :label="$t('system.cms.field.operation')" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'cms:content:save'"
              type="primary"
              link
              :icon="Edit"
              size="small"
              :aria-label="$t('common.action.edit')"
              @click="openEdit(row as CmsContent)"
            >{{ $t('common.action.edit') }}</el-button>
            <el-button
              v-if="row.status !== 'PUBLISHED'"
              v-permission="'cms:content:publish'"
              type="success"
              link
              size="small"
              @click="handlePublish(row as CmsContent)"
            >{{ $t('system.cms.action.publish') }}</el-button>
            <el-button
              v-if="row.status === 'PUBLISHED'"
              v-permission="'cms:content:archive'"
              type="warning"
              link
              size="small"
              @click="handleArchive(row as CmsContent)"
            >{{ $t('system.cms.action.archive') }}</el-button>
            <el-button
              v-if="row.status !== 'PUBLISHED'"
              v-permission="'cms:content:delete'"
              type="danger"
              link
              :icon="Delete"
              size="small"
              :aria-label="$t('common.action.delete')"
              @click="handleDelete(row as CmsContent)"
            >{{ $t('common.action.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="720px"
      :aria-label="$t('system.cms.aria.formDialog')"
    >
      <el-form label-width="90px" :model="form">
        <el-form-item :label="$t('system.cms.field.title')">
          <el-input v-model="form.title" :placeholder="$t('system.cms.placeholder.title')" :aria-label="$t('system.cms.field.title')" />
        </el-form-item>
        <el-form-item :label="$t('system.cms.field.slug')">
          <el-input v-model="form.slug" placeholder="about-us" :aria-label="$t('system.cms.field.slug')" />
        </el-form-item>
        <el-form-item :label="$t('system.cms.field.category')">
          <el-select v-model="form.category" style="width: 100%" :aria-label="$t('system.cms.field.category')">
            <el-option label="announcement" value="announcement" />
            <el-option label="help" value="help" />
            <el-option label="terms" value="terms" />
            <el-option label="blog" value="blog" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('system.cms.field.tags')">
          <el-input v-model="form.tags" :placeholder="$t('system.cms.placeholder.tags')" :aria-label="$t('system.cms.field.tags')" />
        </el-form-item>
        <el-form-item :label="$t('system.cms.field.summary')">
          <el-input v-model="form.summary" type="textarea" :rows="2" :aria-label="$t('system.cms.field.summary')" />
        </el-form-item>
        <el-form-item :label="$t('system.cms.field.contentMd')">
          <el-input
            v-model="form.contentMd"
            type="textarea"
            :rows="10"
            :placeholder="$t('system.cms.placeholder.contentMd')"
            :aria-label="$t('system.cms.field.contentMd')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.action.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ $t('common.action.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 20px;
  font-size: 20px;
  font-weight: 600;
}
.toolbar {
  margin-bottom: 16px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
