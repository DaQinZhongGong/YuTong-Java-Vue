<script setup lang="ts">
/**
 * FormDialog 轻量表单弹窗容器
 * 设计来源: 66-前端组件API与状态管理详设 line 47-52
 *
 * 仅用于字段少、无复杂明细、提交后可快速返回列表的轻量系统配置。
 * 不得把申请单明细、附件或低代码属性面板塞入小弹窗, 那些场景请用 FormDrawer。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessageBox } from 'element-plus'

interface Props {
  visible: boolean
  title: string
  loading?: boolean
  /** 弹窗尺寸: small(480px) / default(600px) / large(800px) */
  size?: 'small' | 'default' | 'large'
  /** 表单是否已修改, 用于遮罩点击二次确认 */
  dirty?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  size: 'default',
  dirty: false,
})

const { t } = useI18n()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'submit'): void
  (e: 'cancel'): void
}>()

const dialogWidth = computed(() => {
  switch (props.size) {
    case 'small':
      return '480px'
    case 'large':
      return '800px'
    default:
      return '600px'
  }
})

function requestClose() {
  if (props.dirty) {
    ElMessageBox.confirm(t('component.formDialog.msg.dirtyCloseWarning'), t('component.formDialog.title.tip'), {
      type: 'warning',
      confirmButtonText: t('component.formDialog.action.confirmClose'),
      cancelButtonText: t('component.formDialog.action.continueEditing'),
    })
      .then(() => {
        emit('update:visible', false)
        emit('cancel')
      })
      .catch(() => {
        // 用户取消, 保持打开
      })
  } else {
    emit('update:visible', false)
    emit('cancel')
  }
}

function handleSubmit() {
  emit('submit')
}

function handleCancel() {
  requestClose()
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    :width="dialogWidth"
    :close-on-click-modal="false"
    @close="requestClose"
  >
    <slot />
    <template #footer>
      <slot name="footer" :loading="loading" :submit="handleSubmit" :cancel="handleCancel">
        <el-button :disabled="loading" @click="handleCancel">{{ $t('common.action.cancel') }}</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit">{{ $t('common.action.confirm') }}</el-button>
      </slot>
    </template>
  </el-dialog>
</template>
