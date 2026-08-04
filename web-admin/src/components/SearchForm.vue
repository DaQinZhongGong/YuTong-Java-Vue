<script setup lang="ts">
/**
 * SearchForm 通用查询表单
 * 设计来源: 66-前端组件API与状态管理详设 line 37-45
 *
 * 内部使用 el-form inline 布局, 支持 input/select/date/daterange 4 种字段类型。
 * 业务页面通过 schema 声明字段, 不再重复实现 el-form-item。
 */
export interface SearchField {
  key: string
  label: string
  type: 'input' | 'select' | 'date' | 'daterange'
  options?: Array<{ label: string; value: string }>
  placeholder?: string
}

interface Props {
  schema: SearchField[]
  /** 表单模型, 父组件通过 v-model 双向绑定 */
  model: Record<string, any>
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
})

// props 通过 defineProps 自动暴露给模板, 此处显式引用避免 TS6133 未使用警告
void props

const emit = defineEmits<{
  (e: 'search'): void
  (e: 'reset'): void
}>()

function handleSearch() {
  emit('search')
}

function handleReset() {
  emit('reset')
}
</script>

<template>
  <el-form
    :inline="true"
    class="yt-search-form"
    style="margin-bottom: 16px"
    @submit.prevent="handleSearch"
  >
    <el-form-item v-for="field in schema" :key="field.key" :label="field.label">
      <el-input
        v-if="field.type === 'input'"
        v-model="model[field.key]"
        :placeholder="field.placeholder || ''"
        clearable
        @keyup.enter="handleSearch"
      />
      <el-select
        v-else-if="field.type === 'select'"
        v-model="model[field.key]"
        :placeholder="field.placeholder || $t('component.searchForm.placeholder.select')"
        clearable
        @change="handleSearch"
      >
        <el-option
          v-for="opt in field.options || []"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
      <el-date-picker
        v-else-if="field.type === 'date'"
        v-model="model[field.key]"
        :placeholder="field.placeholder || $t('component.searchForm.placeholder.selectDate')"
        type="date"
        value-format="YYYY-MM-DD"
      />
      <el-date-picker
        v-else-if="field.type === 'daterange'"
        v-model="model[field.key]"
        type="daterange"
        :range-separator="$t('component.searchForm.rangeSeparator')"
        :start-placeholder="$t('component.searchForm.placeholder.startDate')"
        :end-placeholder="$t('component.searchForm.placeholder.endDate')"
        value-format="YYYY-MM-DD"
      />
    </el-form-item>

    <el-form-item>
      <el-button type="primary" :loading="loading" @click="handleSearch">{{ $t('common.action.list') }}</el-button>
      <el-button :disabled="loading" @click="handleReset">{{ $t('common.action.reset') }}</el-button>
    </el-form-item>

    <!-- 业务页面可放新建按钮等额外操作 -->
    <el-form-item>
      <slot name="actions" :loading="loading" />
    </el-form-item>
  </el-form>
</template>
