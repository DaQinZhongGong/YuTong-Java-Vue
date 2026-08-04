<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  getCodeTemplate,
  createCodeTemplate,
  updateCodeTemplate,
} from '@/api/code-template'
import type { CodeTemplate } from '@/api/code-template'

const route = useRoute()
const router = useRouter()

const { t } = useI18n()

/**
 * 代码生成模板编辑页面（45 号文档「插件与模板生态设计」E0）
 * 支持新建和编辑模板内容
 */

const id = route.query.id as string | undefined
const isEdit = !!id
const loading = ref(false)
const saveLoading = ref(false)

const form = reactive<Partial<CodeTemplate>>({
  templateCode: '',
  templateName: '',
  category: 'ENTITY',
  engineType: 'FREEMARKER',
  status: 'ACTIVE',
  content: '',
  description: '',
  tags: '',
})

const categoryOptions = [
  { value: 'ENTITY', label: 'Entity' },
  { value: 'CONTROLLER', label: 'Controller' },
  { value: 'SERVICE', label: 'Service' },
  { value: 'MAPPER', label: 'Mapper' },
  { value: 'VUE_LIST', label: 'Vue List' },
  { value: 'VUE_FORM', label: 'Vue Form' },
  { value: 'DDL', label: 'DDL' },
]

const engineOptions = [
  { value: 'FREEMARKER', label: 'FreeMarker' },
  { value: 'VELOCITY', label: 'Velocity' },
]

const statusOptions = [
  { value: 'ACTIVE', label: t('plugin.codeTemplate.status.active') },
  { value: 'INACTIVE', label: t('plugin.codeTemplate.status.inactive') },
]

/** 加载详情 */
async function loadDetail() {
  if (!id) return
  loading.value = true
  try {
    const res = await getCodeTemplate(id)
    Object.assign(form, {
      templateCode: res.templateCode,
      templateName: res.templateName,
      category: res.category,
      engineType: res.engineType,
      status: res.status,
      content: res.content,
      description: res.description,
      tags: res.tags,
    })
  } catch (e) {
    ElMessage.error(t('plugin.codeTemplate.msg.loadFailed'))
    router.back()
  } finally {
    loading.value = false
  }
}

/** 保存 */
async function handleSave() {
  if (!form.templateCode || !form.templateName || !form.content) {
    ElMessage.warning(t('plugin.codeTemplate.msg.formIncomplete'))
    return
  }
  saveLoading.value = true
  try {
    const payload = {
      templateCode: form.templateCode!,
      templateName: form.templateName!,
      category: form.category!,
      engineType: form.engineType!,
      status: form.status!,
      content: form.content!,
      description: form.description,
      tags: form.tags,
    } as any
    if (isEdit) {
      await updateCodeTemplate(id!, payload)
      ElMessage.success(t('plugin.codeTemplate.msg.updateSuccess'))
    } else {
      await createCodeTemplate(payload)
      ElMessage.success(t('plugin.codeTemplate.msg.createSuccess'))
    }
    router.push({ name: 'CodeTemplates' })
  } catch (e) {
    ElMessage.error(isEdit ? t('plugin.codeTemplate.msg.updateFailed') : t('plugin.codeTemplate.msg.createFailed'))
  } finally {
    saveLoading.value = false
  }
}

/** 返回列表 */
function handleBack() {
  router.push({ name: 'CodeTemplates' })
}

onMounted(loadDetail)
</script>

<template>
  <div class="code-template-edit-page" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ isEdit ? $t('plugin.codeTemplate.dialog.editTitle') : $t('plugin.codeTemplate.dialog.createTitle') }}</span>
          <el-button @click="handleBack">{{ $t('plugin.codeTemplate.action.back') }}</el-button>
        </div>
      </template>

      <el-form :model="form" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$t('plugin.codeTemplate.field.code')" required>
              <el-input v-model="form.templateCode" :placeholder="$t('plugin.codeTemplate.placeholder.code')" :disabled="isEdit" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('plugin.codeTemplate.field.name')" required>
              <el-input v-model="form.templateName" :placeholder="$t('plugin.codeTemplate.placeholder.name')" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item :label="$t('plugin.codeTemplate.field.category')" required>
              <el-select v-model="form.category" :placeholder="$t('plugin.codeTemplate.placeholder.selectCategory')" style="width: 100%">
                <el-option v-for="o in categoryOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('plugin.codeTemplate.field.engine')" required>
              <el-select v-model="form.engineType" :placeholder="$t('plugin.codeTemplate.placeholder.selectEngine')" style="width: 100%">
                <el-option v-for="o in engineOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('plugin.codeTemplate.field.status')">
              <el-select v-model="form.status" :placeholder="$t('plugin.codeTemplate.placeholder.selectStatus')" style="width: 100%">
                <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item :label="$t('plugin.codeTemplate.field.tags')">
          <el-input v-model="form.tags" :placeholder="$t('plugin.codeTemplate.placeholder.tags')" />
        </el-form-item>

        <el-form-item :label="$t('plugin.codeTemplate.field.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" :placeholder="$t('plugin.codeTemplate.placeholder.description')" />
        </el-form-item>

        <el-form-item :label="$t('plugin.codeTemplate.field.content')" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="20"
            :placeholder="$t('plugin.codeTemplate.placeholder.content')"
            class="template-content"
          />
        </el-form-item>
      </el-form>

      <div class="actions">
        <el-button @click="handleBack">{{ $t('plugin.codeTemplate.action.cancel') }}</el-button>
        <el-button type="primary" :loading="saveLoading" @click="handleSave">{{ $t('plugin.codeTemplate.action.save') }}</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.code-template-edit-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.template-content :deep(textarea) {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
}
</style>
