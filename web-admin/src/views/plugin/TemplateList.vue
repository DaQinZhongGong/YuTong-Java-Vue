<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getTemplates } from '@/api/plugin'
import type { MktTemplate } from '@/api/plugin'

const { t } = useI18n()

/**
 * 模板市场页面（45 号文档「插件与模板生态设计」）
 * 功能：模板列表、安装、预览
 */

const loading = ref(false)
const tableData = ref<MktTemplate[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const filters = reactive({
  keyword: '',
  category: '',
  status: '',
})

// 预览对话框
const previewVisible = ref(false)
const previewTemplate = ref<MktTemplate | null>(null)

// 分类选项
const categoryOptions = [
  { value: 'BUSINESS', label: t('plugin.template.category.business') },
  { value: 'PAGE', label: t('plugin.template.category.page') },
  { value: 'INDUSTRY', label: t('plugin.template.category.industry') },
  { value: 'THEME', label: t('plugin.template.category.theme') },
]

// 状态选项
const statusOptions = [
  { value: 'DRAFT', label: t('plugin.template.status.draft') },
  { value: 'PUBLISHED', label: t('plugin.template.status.published') },
]

// 分类标签
function categoryLabel(category: string): string {
  switch (category) {
    case 'BUSINESS':
      return t('plugin.template.category.business')
    case 'PAGE':
      return t('plugin.template.category.page')
    case 'INDUSTRY':
      return t('plugin.template.category.industry')
    case 'THEME':
      return t('plugin.template.category.theme')
    default:
      return category
  }
}

// 状态标签
function statusLabel(status: string): string {
  switch (status) {
    case 'DRAFT':
      return t('plugin.template.status.draft')
    case 'PUBLISHED':
      return t('plugin.template.status.published')
    default:
      return status
  }
}

/** 加载模板列表 */
async function loadData() {
  loading.value = true
  try {
    const res = await getTemplates({
      pageNo: currentPage.value,
      pageSize: pageSize.value,
      keyword: filters.keyword || undefined,
      category: filters.category || undefined,
      status: filters.status || undefined,
    })
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    ElMessage.error(t('plugin.template.msg.loadListFailed'))
  } finally {
    loading.value = false
  }
}

/** 搜索 */
function handleSearch() {
  currentPage.value = 1
  loadData()
}

/** 重置筛选 */
function handleReset() {
  filters.keyword = ''
  filters.category = ''
  filters.status = ''
  handleSearch()
}

/** 分页变更 */
function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

/** 预览模板 */
function handlePreview(row: MktTemplate) {
  previewTemplate.value = row
  previewVisible.value = true
}

/** 安装模板 */
async function handleInstall(row: MktTemplate) {
  if (!row.packageUrl) {
    ElMessage.warning(t('plugin.template.msg.noPackage'))
    return
  }
  ElMessage.info(t('plugin.template.msg.installDev', { name: row.templateName }))
}

/** 格式化时间 */
function formatTime(t?: string): string {
  if (!t) return '-'
  try {
    const d = new Date(t)
    if (isNaN(d.getTime())) return t
    return d.toLocaleString('zh-CN', { hour12: false })
  } catch {
    return t
  }
}

/** 解析预览图 */
function parsePreviewImages(previewImages?: string): string[] {
  if (!previewImages) return []
  try {
    return JSON.parse(previewImages)
  } catch {
    return []
  }
}

onMounted(loadData)
</script>

<template>
  <div class="template-page">
    <!-- 顶部筛选栏 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item :label="$t('plugin.template.field.keyword')">
          <el-input
            v-model="filters.keyword"
            :placeholder="$t('plugin.template.placeholder.keyword')"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item :label="$t('plugin.template.field.category')">
          <el-select
            v-model="filters.category"
            :placeholder="$t('plugin.template.placeholder.all')"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="o in categoryOptions"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('plugin.template.field.status')">
          <el-select
            v-model="filters.status"
            :placeholder="$t('plugin.template.placeholder.all')"
            clearable
            style="width: 120px"
          >
            <el-option
              v-for="o in statusOptions"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('plugin.template.action.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('plugin.template.action.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 模板列表 -->
    <el-card shadow="never" v-loading="loading">
      <el-table :data="tableData" border stripe>
        <el-table-column prop="templateCode" :label="$t('plugin.template.field.code')" width="180" />
        <el-table-column prop="templateName" :label="$t('plugin.template.field.name')" min-width="160" />
        <el-table-column :label="$t('plugin.template.field.category')" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small">
              {{ categoryLabel(row.category) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('plugin.template.field.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 'PUBLISHED' ? 'success' : 'info'"
              size="small"
            >
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="minVersion" :label="$t('plugin.template.field.minVersion')" width="100" align="center" />
        <el-table-column prop="installCount" :label="$t('plugin.template.field.installCount')" width="100" align="center" />
        <el-table-column prop="updatedTime" :label="$t('plugin.template.field.updatedTime')" width="180">
          <template #default="{ row }">{{ formatTime(row.updatedTime) }}</template>
        </el-table-column>
        <el-table-column :label="$t('plugin.template.field.operation')" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              size="small"
              @click="handlePreview(row as MktTemplate)"
            >
              {{ $t('plugin.template.action.preview') }}
            </el-button>
            <el-button
              link
              type="success"
              size="small"
              @click="handleInstall(row as MktTemplate)"
            >
              {{ $t('plugin.template.action.install') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 16px; justify-content: flex-end"
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="handleSearch"
      />
    </el-card>

    <!-- 预览对话框 -->
    <el-dialog
      v-model="previewVisible"
      :title="$t('plugin.template.dialog.previewTitle', { name: previewTemplate?.templateName || '' })"
      width="800px"
      :close-on-click-modal="false"
    >
      <div v-if="previewTemplate" class="preview-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="$t('plugin.template.field.code')">{{ previewTemplate.templateCode }}</el-descriptions-item>
          <el-descriptions-item :label="$t('plugin.template.field.name')">{{ previewTemplate.templateName }}</el-descriptions-item>
          <el-descriptions-item :label="$t('plugin.template.field.category')">{{ categoryLabel(previewTemplate.category) }}</el-descriptions-item>
          <el-descriptions-item :label="$t('plugin.template.field.status')">{{ statusLabel(previewTemplate.status) }}</el-descriptions-item>
          <el-descriptions-item :label="$t('plugin.template.field.minVersion')">{{ previewTemplate.minVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('plugin.template.field.installCount')">{{ previewTemplate.installCount }}</el-descriptions-item>
          <el-descriptions-item :label="$t('plugin.template.field.updatedTime')" :span="2">{{ formatTime(previewTemplate.updatedTime) }}</el-descriptions-item>
        </el-descriptions>

        <div class="preview-images" v-if="parsePreviewImages(previewTemplate.previewImages).length > 0">
          <h4 style="margin: 16px 0 8px">{{ $t('plugin.template.field.previewImages') }}</h4>
          <div class="image-list">
            <el-image
              v-for="(img, index) in parsePreviewImages(previewTemplate.previewImages)"
              :key="index"
              :src="img"
              :preview-src-list="parsePreviewImages(previewTemplate.previewImages)"
              :initial-index="index"
              fit="cover"
              style="width: 200px; height: 150px; margin-right: 8px"
            />
          </div>
        </div>

        <div v-else style="margin-top: 16px; color: #999; text-align: center">
          {{ $t('plugin.template.tip.noPreviewImages') }}
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.template-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.filter-card :deep(.el-card__body) {
  padding: 16px 20px 0 20px;
}

.preview-content {
  padding: 0 16px;
}

.preview-images {
  margin-top: 16px;
}

.image-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
