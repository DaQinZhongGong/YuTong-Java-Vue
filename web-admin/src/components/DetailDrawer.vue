<script setup lang="ts">
/**
 * DetailDrawer 通用详情抽屉
 * 设计来源: 53-Web管理端页面级分工详设 line 62 (通用组件任务表) + 66-前端组件API与状态管理详设
 *
 * 从右侧滑入, 展示对象详情。data 的 key-value 自动渲染为 el-descriptions 描述列表:
 *   - 基础类型直接展示, 空值显示 '-'
 *   - 嵌套对象支持一层深度, 以子描述列表内联展示
 *   - 数组字段展示为 el-tag 标签组
 *   - 顶部 title 插槽可覆盖默认标题, 底部 footer 插槽用于操作按钮
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

interface Props {
  /** 是否显示 (v-model) */
  visible: boolean
  /** 抽屉标题 */
  title: string
  /** 抽屉宽度, 数字按 px 处理 */
  width?: string | number
  /** 详情数据对象, 自动渲染为描述列表 */
  data: Record<string, any>
  /** 描述列表列数 */
  column?: number
}

const props = withDefaults(defineProps<Props>(), {
  width: 600,
  column: 1,
})

const { t } = useI18n()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'close'): void
}>()

/** el-drawer 的 size 属性值 */
const drawerSize = computed(() => {
  if (typeof props.width === 'number') {
    return `${props.width}px`
  }
  return props.width
})

/** 将 data 转为有序的描述项列表, 便于模板遍历 */
const descriptionRows = computed(() => {
  const data = props.data
  if (!data || typeof data !== 'object') return []
  return Object.keys(data).map((key) => ({
    key,
    label: key,
    value: data[key],
  }))
})

/** 判断是否为嵌套对象 (非数组、非 null, 且为普通对象) */
function isPlainObject(v: any): boolean {
  return v !== null && typeof v === 'object' && !Array.isArray(v)
}

/** 将嵌套对象 (一层) 拍平为 key-value 数组 */
function nestedEntries(v: Record<string, any>): Array<{ key: string; value: any }> {
  if (!isPlainObject(v)) return []
  return Object.keys(v).map((k) => ({ key: k, value: v[k] }))
}

/** 将任意值格式化为字符串展示 */
function formatScalar(v: any): string {
  if (v === null || v === undefined || v === '') return '-'
  if (typeof v === 'boolean') return v ? t('component.boolean.yes') : t('component.boolean.no')
  return String(v)
}

function handleVisibleChange(v: boolean) {
  emit('update:visible', v)
  if (!v) {
    emit('close')
  }
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    :size="drawerSize"
    :close-on-click-modal="true"
    @update:model-value="handleVisibleChange"
  >
    <template #header>
      <slot name="title">
        <span class="yt-detail-drawer__title">{{ title }}</span>
      </slot>
    </template>

    <div class="yt-detail-drawer__body">
      <slot name="body" :data="data">
        <el-descriptions :column="column" border>
          <el-descriptions-item
            v-for="row in descriptionRows"
            :key="row.key"
            :label="row.label"
          >
            <!-- 数组: 渲染为标签组 -->
            <template v-if="Array.isArray(row.value)">
              <el-tag
                v-for="(tag, idx) in row.value"
                :key="idx"
                class="yt-detail-drawer__tag"
                type="info"
                size="small"
              >
                {{ formatScalar(tag) }}
              </el-tag>
              <span v-if="row.value.length === 0">-</span>
            </template>

            <!-- 嵌套对象 (一层深度): 内联子描述列表 -->
            <template v-else-if="isPlainObject(row.value)">
              <el-descriptions
                :column="1"
                border
                size="small"
                class="yt-detail-drawer__nested"
              >
                <el-descriptions-item
                  v-for="entry in nestedEntries(row.value)"
                  :key="entry.key"
                  :label="entry.key"
                >
                  <template v-if="Array.isArray(entry.value)">
                    <el-tag
                      v-for="(tag, idx) in entry.value"
                      :key="idx"
                      class="yt-detail-drawer__tag"
                      type="info"
                      size="small"
                    >
                      {{ formatScalar(tag) }}
                    </el-tag>
                    <span v-if="entry.value.length === 0">-</span>
                  </template>
                  <template v-else>{{ formatScalar(entry.value) }}</template>
                </el-descriptions-item>
              </el-descriptions>
            </template>

            <!-- 基础类型 -->
            <template v-else>{{ formatScalar(row.value) }}</template>
          </el-descriptions-item>
        </el-descriptions>
      </slot>
    </div>

    <template #footer>
      <slot name="footer" :data="data" :close="() => handleVisibleChange(false)">
        <el-button @click="handleVisibleChange(false)">{{ $t('common.action.close') }}</el-button>
      </slot>
    </template>
  </el-drawer>
</template>

<style scoped>
.yt-detail-drawer__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.yt-detail-drawer__body {
  padding: 0 4px;
}

.yt-detail-drawer__tag {
  margin-right: 6px;
  margin-bottom: 4px;
}

.yt-detail-drawer__nested {
  margin-top: 4px;
}
</style>