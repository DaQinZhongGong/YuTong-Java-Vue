<#-- Vue 列表页模板 | 设计来源: 65-低代码代码生成模板详设
  约束:
    - 使用生成的 API 类型
    - 页面颜色/间距引用 50 Token
    - 列表页包含 SearchForm + BaseTable
    - 错误显示使用 messageKey
-->
<#-- @ftlvariable name="entity" type="com.yutong.lowcode.generator.service.CodeTemplateService.TemplateEntity" -->
<#-- @ftlvariable name="fields" type="java.util.List<com.yutong.lowcode.generator.service.CodeTemplateService.TemplateField>" -->
<#-- @ftlvariable name="className" type="String" -->
<!-- 由低代码生成器生成，可二开 -->
<template>
  <div class="${entity.entityCode?replace('_', '-')}-list">
    <el-form :inline="true" :model="query" @submit.prevent="handleSearch">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="请输入关键词" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="180" />
<#list fields as f><#if !(f.primaryFlag!false) || f.fieldCode != 'id'>
      <el-table-column prop="${f.fieldCodeCamel}" label="${f.fieldName!''}" />
</#if></#list>
      <el-table-column prop="createdTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      @size-change="loadList"
      @current-change="loadList"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { list${className}, delete${className}, type ${className}, type ${className}Query } from '@/api/generated/${entity.entityCode?replace('_', '-')}-api'

const list = ref<${className}[]>([])
const loading = ref(false)
const total = ref(0)
const query = reactive<${className}Query>({ page: 1, size: 20, keyword: '' })

async function loadList() {
  loading.value = true
  try {
    const res = await list${className}(query)
    list.value = res.data.data
    total.value = res.data.total
  } catch (e: any) {
    ElMessage.error(e?.messageKey || e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadList()
}

function handleReset() {
  query.keyword = ''
  query.page = 1
  loadList()
}

async function handleDelete(row: ${className}) {
  await ElMessageBox.confirm('确认删除该记录?', '提示', { type: 'warning' })
  await delete${className}(row.id)
  ElMessage.success('删除成功')
  loadList()
}

function handleEdit(row: ${className}) {
  // TODO: 跳转编辑页
}

onMounted(loadList)
</script>

<style scoped>
.${entity.entityCode?replace('_', '-')}-list {
  padding: 16px;
}
</style>
