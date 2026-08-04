<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCustomers, createCustomer, updateCustomer, deleteCustomer } from '@/api/customer'
import type { Customer, PageResult } from '@/api/types'

const { t } = useI18n()
const loading = ref(false)
const tableData = ref<Customer[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const searchName = ref('')

const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref<Partial<Customer>>({})

async function loadData() {
  loading.value = true
  try {
    const res: PageResult<Customer> = await getCustomers({
      pageNo: currentPage.value,
      pageSize: pageSize.value,
      keyword: searchName.value || undefined,
    })
    tableData.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadData()
}

function handleAdd() {
  isEdit.value = false
  form.value = { status: 'ENABLED' }
  dialogVisible.value = true
}

function handleEdit(row: Customer) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function handleDelete(row: Customer) {
  await ElMessageBox.confirm(t('customer.msg.confirmDelete', { name: row.customerName }), t('customer.msg.tip'), { type: 'warning' })
  await deleteCustomer(row.id)
  ElMessage.success(t('customer.msg.deleteSuccess'))
  loadData()
}

async function handleSave() {
  if (isEdit.value) {
    await updateCustomer(form.value.id!, form.value)
    ElMessage.success(t('customer.msg.updateSuccess'))
  } else {
    await createCustomer(form.value)
    ElMessage.success(t('customer.msg.createSuccess'))
  }
  dialogVisible.value = false
  loadData()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div>
    <el-form :inline="true" style="margin-bottom: 16px">
      <el-form-item :label="$t('customer.list.customerName')">
        <el-input v-model="searchName" :placeholder="$t('customer.list.searchPlaceholder')" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">{{ $t('common.action.search') }}</el-button>
        <!-- GA2-16: 新增按钮按 biz:customer:add 权限码隐藏 (96 号文档基础后台按钮矩阵) -->
        <el-button v-permission="'biz:customer:add'" type="success" @click="handleAdd">{{ $t('customer.list.addCustomer') }}</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="customerCode" :label="$t('customer.list.customerCode')" width="150" />
      <el-table-column prop="customerName" :label="$t('customer.list.customerName')" min-width="200" />
      <el-table-column prop="contactName" :label="$t('customer.list.contactName')" width="120" />
      <el-table-column prop="contactPhone" :label="$t('customer.list.phone')" width="150" />
      <el-table-column prop="status" :label="$t('customer.list.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
            {{ row.status === 'ENABLED' ? $t('customer.list.enabled') : $t('customer.list.disabled') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('customer.list.action')" width="150" fixed="right">
        <template #default="{ row }">
          <!-- GA2-16: 编辑/删除按钮分别按 biz:customer:edit / biz:customer:delete 权限码隐藏 -->
          <el-button v-permission="'biz:customer:edit'" size="small" @click="handleEdit(row as Customer)">{{ $t('common.action.edit') }}</el-button>
          <el-button v-permission="'biz:customer:delete'" size="small" type="danger" @click="handleDelete(row as Customer)">{{ $t('common.action.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 16px; justify-content: flex-end"
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="handlePageChange"
    />

    <el-dialog :title="isEdit ? $t('customer.list.editCustomer') : $t('customer.list.addCustomer')" v-model="dialogVisible" width="600px">
      <el-form :model="form" label-width="100px">
        <el-form-item :label="$t('customer.list.customerCode')"><el-input v-model="form.customerCode" /></el-form-item>
        <el-form-item :label="$t('customer.list.customerName')"><el-input v-model="form.customerName" /></el-form-item>
        <el-form-item :label="$t('customer.list.contactName')"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item :label="$t('customer.list.phone')"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item :label="$t('customer.list.address')"><el-input v-model="form.address" /></el-form-item>
        <el-form-item :label="$t('customer.list.status')">
          <el-select v-model="form.status">
            <el-option :label="$t('customer.list.enabled')" value="ENABLED" />
            <el-option :label="$t('customer.list.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('customer.list.remark')"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.action.cancel') }}</el-button>
        <el-button type="primary" @click="handleSave">{{ $t('common.action.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>
