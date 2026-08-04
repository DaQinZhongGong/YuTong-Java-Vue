<#-- Uniapp 列表页模板 | 设计来源: 65-低代码代码生成模板详设
  约束:
    - 移动简表只展示核心字段
    - 底部操作栏固定
    - 支持弱网提示
-->
<#-- @ftlvariable name="entity" type="com.yutong.lowcode.generator.service.CodeTemplateService.TemplateEntity" -->
<#-- @ftlvariable name="fields" type="java.util.List<com.yutong.lowcode.generator.service.CodeTemplateService.TemplateField>" -->
<#-- @ftlvariable name="className" type="String" -->
<!-- 由低代码生成器生成，可二开 -->
<template>
  <view class="${entity.entityCode?replace('_', '-')}-list">
    <view v-if="weakNetwork" class="weak-network-tip">网络较差，加载可能延迟</view>
    <view v-for="item in list" :key="item.id" class="item" @tap="handleTap(item)">
      <view class="item-title">{{ item.id }}</view>
<#list fields as f><#if !(f.primaryFlag!false) && f.fieldCode != 'id'>
      <view class="item-row">
        <text class="item-label">${f.fieldName!''}:</text>
        <text class="item-value">{{ item.${f.fieldCodeCamel} }}</text>
      </view>
</#if></#list>
    </view>
    <view v-if="loading" class="loading">加载中...</view>
    <view v-if="!loading && list.length === 0" class="empty">暂无数据</view>

    <view class="bottom-bar">
      <button class="btn-refresh" @tap="loadList">刷新</button>
      <button class="btn-add" @tap="handleAdd">新增</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { list${className}, type ${className} } from '@/api/generated/${entity.entityCode?replace('_', '-')}-api'

const list = ref<${className}[]>([])
const loading = ref(false)
const weakNetwork = ref(false)

async function loadList() {
  loading.value = true
  try {
    const res = await list${className}({ page: 1, size: 50 })
    list.value = res.data.data
    weakNetwork.value = false
  } catch (e) {
    weakNetwork.value = true
  } finally {
    loading.value = false
  }
}

function handleTap(item: ${className}) {
  uni.navigateTo({ url: '/pages/generated/${entity.entityCode?replace('_', '-')}-detail?id=' + item.id })
}

function handleAdd() {
  uni.navigateTo({ url: '/pages/generated/${entity.entityCode?replace('_', '-')}-form' })
}

onMounted(loadList)
</script>

<style>
.${entity.entityCode?replace('_', '-')}-list { padding: 12rpx; }
.item { background: #fff; padding: 16rpx; margin-bottom: 12rpx; border-radius: 8rpx; }
.item-title { font-size: 28rpx; font-weight: bold; margin-bottom: 8rpx; }
.item-row { display: flex; font-size: 24rpx; color: #666; margin-top: 4rpx; }
.item-label { width: 160rpx; }
.weak-network-tip { background: #fff7e6; color: #fa8c16; padding: 8rpx 16rpx; font-size: 24rpx; }
.loading, .empty { text-align: center; padding: 32rpx; color: #999; font-size: 26rpx; }
.bottom-bar { position: fixed; bottom: 0; left: 0; right: 0; display: flex; background: #fff; border-top: 1rpx solid #eee; }
.btn-refresh, .btn-add { flex: 1; border-radius: 0; font-size: 28rpx; }
.btn-add { background: #409eff; color: #fff; }
</style>
