<#-- Vue 表单页模板 | 设计来源: 65-低代码代码生成模板详设
  约束:
    - 表单页包含校验、提交中、离开确认
-->
<#-- @ftlvariable name="entity" type="com.yutong.lowcode.generator.service.CodeTemplateService.TemplateEntity" -->
<#-- @ftlvariable name="fields" type="java.util.List<com.yutong.lowcode.generator.service.CodeTemplateService.TemplateField>" -->
<#-- @ftlvariable name="className" type="String" -->
<!-- 由低代码生成器生成，可二开 -->
<template>
  <div class="${entity.entityCode?replace('_', '-')}-form">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" v-loading="loading">
<#list fields as f><#if !(f.primaryFlag!false) || f.fieldCode != 'id'>
      <el-form-item label="${f.fieldName!''}" prop="${f.fieldCodeCamel}">
        <el-input v-model="form.${f.fieldCodeCamel}" placeholder="请输入${f.fieldName!''}" />
      </el-form-item>
</#if></#list>
      <el-form-item>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交</el-button>
        <el-button @click="handleCancel">取消</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { get${className}, create${className}, update${className}, type ${className}Request } from '@/api/generated/${entity.entityCode?replace('_', '-')}-api'

const props = defineProps<{ id?: string }>()
const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const submitting = ref(false)
const form = reactive<${className}Request>({})

const rules: FormRules = {
<#list fields as f><#if !(f.primaryFlag!false) || f.fieldCode != 'id'><#if f.nullable?? && !f.nullable>
  ${f.fieldCodeCamel}: [{ required: true, message: '${f.fieldName!''}不能为空', trigger: 'blur' }],
</#if></#if></#list>
}

async function loadDetail() {
  if (!props.id) return
  loading.value = true
  try {
    const res = await get${className}(props.id)
    Object.assign(form, res.data)
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (props.id) {
      await update${className}(props.id, form)
      ElMessage.success('更新成功')
    } else {
      await create${className}(form)
      ElMessage.success('创建成功')
    }
    router.back()
  } catch (e: any) {
    ElMessage.error(e?.messageKey || e?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

function handleCancel() {
  router.back()
}

onMounted(loadDetail)
</script>

<style scoped>
.${entity.entityCode?replace('_', '-')}-form {
  padding: 16px;
  max-width: 720px;
}
</style>
