<#-- Vue 详情页模板 | 设计来源: 65-低代码代码生成模板详设 -->
<#-- @ftlvariable name="entity" type="com.yutong.lowcode.generator.service.CodeTemplateService.TemplateEntity" -->
<#-- @ftlvariable name="fields" type="java.util.List<com.yutong.lowcode.generator.service.CodeTemplateService.TemplateField>" -->
<#-- @ftlvariable name="className" type="String" -->
<!-- 由低代码生成器生成，可二开 -->
<template>
  <div class="${entity.entityCode?replace('_', '-')}-detail" v-loading="loading">
    <el-descriptions :column="2" border>
      <el-descriptions-item label="ID">{{ detail?.id }}</el-descriptions-item>
<#list fields as f><#if !(f.primaryFlag!false) || f.fieldCode != 'id'>
      <el-descriptions-item label="${f.fieldName!''}">{{ detail?.${f.fieldCodeCamel} }}</el-descriptions-item>
</#if></#list>
      <el-descriptions-item label="创建人">{{ detail?.createdBy }}</el-descriptions-item>
      <el-descriptions-item label="创建时间">{{ detail?.createdTime }}</el-descriptions-item>
      <el-descriptions-item label="更新人">{{ detail?.updatedBy }}</el-descriptions-item>
      <el-descriptions-item label="更新时间">{{ detail?.updatedTime }}</el-descriptions-item>
      <el-descriptions-item label="版本">{{ detail?.version }}</el-descriptions-item>
    </el-descriptions>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { get${className}, type ${className} } from '@/api/generated/${entity.entityCode?replace('_', '-')}-api'

const props = defineProps<{ id: string }>()
const detail = ref<${className} | null>(null)
const loading = ref(false)

async function loadDetail() {
  loading.value = true
  try {
    const res = await get${className}(props.id)
    detail.value = res.data
  } catch (e: any) {
    ElMessage.error(e?.messageKey || e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)
</script>

<style scoped>
.${entity.entityCode?replace('_', '-')}-detail {
  padding: 16px;
}
</style>
