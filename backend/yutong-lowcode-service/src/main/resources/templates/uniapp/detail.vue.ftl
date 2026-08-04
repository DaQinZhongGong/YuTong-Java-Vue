<#-- Uniapp 详情页模板 | 设计来源: 65-低代码代码生成模板详设 -->
<#-- @ftlvariable name="entity" type="com.yutong.lowcode.generator.service.CodeTemplateService.TemplateEntity" -->
<#-- @ftlvariable name="fields" type="java.util.List<com.yutong.lowcode.generator.service.CodeTemplateService.TemplateField>" -->
<#-- @ftlvariable name="className" type="String" -->
<!-- 由低代码生成器生成，可二开 -->
<template>
  <view class="${entity.entityCode?replace('_', '-')}-detail" v-if="detail">
    <view class="item-row"><text class="label">ID:</text><text class="value">{{ detail.id }}</text></view>
<#list fields as f><#if !(f.primaryFlag!false) || f.fieldCode != 'id'>
    <view class="item-row"><text class="label">${f.fieldName!''}:</text><text class="value">{{ detail.${f.fieldCodeCamel} }}</text></view>
</#if></#list>
    <view class="item-row"><text class="label">创建人:</text><text class="value">{{ detail.createdBy }}</text></view>
    <view class="item-row"><text class="label">创建时间:</text><text class="value">{{ detail.createdTime }}</text></view>
  </view>
  <view v-else class="loading">加载中...</view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { get${className}, type ${className} } from '@/api/generated/${entity.entityCode?replace('_', '-')}-api'

const props = defineProps<{ id: string }>()
const detail = ref<${className} | null>(null)

async function loadDetail() {
  const res = await get${className}(props.id)
  detail.value = res.data
}

onMounted(loadDetail)
</script>

<style>
.${entity.entityCode?replace('_', '-')}-detail { padding: 16rpx; background: #fff; min-height: 100vh; }
.item-row { display: flex; padding: 16rpx 0; border-bottom: 1rpx solid #f0f0f0; font-size: 28rpx; }
.label { width: 200rpx; color: #999; }
.value { flex: 1; color: #333; }
.loading { text-align: center; padding: 64rpx; color: #999; }
</style>
