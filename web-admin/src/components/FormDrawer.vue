<script setup lang="ts">
/**
 * FormDrawer 复杂表单抽屉容器
 * 设计来源: 66-前端组件API与状态管理详设 line 47-52 + 91-96
 *
 * 适用于申请单、低代码属性面板、字段较多或包含明细/附件的场景。
 * 行为规则:
 *   - loading=true 时禁用确认按钮, 防止重复提交
 *   - 点击遮罩关闭时, 如果表单 dirty, 必须二次确认
 *   - 提交失败时不关闭容器 (由父组件控制 visible)
 */
import { useI18n } from 'vue-i18n'
import { ElMessageBox } from 'element-plus'

interface Props {
  visible: boolean
  title: string
  loading?: boolean
  width?: string | number
  /** 表单是否已修改, 用于遮罩点击二次确认 */
  dirty?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  width: '600px',
  dirty: false,
})

const { t } = useI18n()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'submit'): void
  (e: 'cancel'): void
}>()

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
  <el-drawer
    :model-value="visible"
    :title="title"
    :size="width"
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
  </el-drawer>
</template>
