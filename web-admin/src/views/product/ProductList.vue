<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProducts, createProduct, updateProduct, deleteProduct } from '@/api/product'
import type { Product, PageResult } from '@/api/types'

const { t } = useI18n()
const loading = ref(false)
const tableData = ref<Product[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const searchName = ref('')

const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref<Partial<Product>>({})

async function loadData() {
  loading.value = true
  try {
    const res: PageResult<Product> = await getProducts({
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

function handleSearch() { currentPage.value = 1; loadData() }

function handleAdd() {
  isEdit.value = false
  form.value = { unit: 'PCS', status: 'ENABLED' }
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function handleDelete(row: Product) {
  await ElMessageBox.confirm(t('product.msg.confirmDelete', { name: row.productName }), t('product.msg.tip'), { type: 'warning' })
  await deleteProduct(row.id)
  ElMessage.success(t('product.msg.deleteSuccess'))
  loadData()
}

async function handleSave() {
  if (isEdit.value) {
    await updateProduct(form.value.id!, form.value)
    ElMessage.success(t('product.msg.updateSuccess'))
  } else {
    await createProduct(form.value)
    ElMessage.success(t('product.msg.createSuccess'))
  }
  dialogVisible.value = false
  loadData()
}

function handlePageChange(page: number) { currentPage.value = page; loadData() }

onMounted(loadData)
</script>

<template>
  <div>
    <el-form :inline="true" style="margin-bottom: 16px">
      <el-form-item :label="$t('product.field.name')">
        <el-input v-model="searchName" :placeholder="$t('product.placeholder.searchName')" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">{{ $t('common.action.search') }}</el-button>
        <el-button type="success" @click="handleAdd">{{ $t('product.action.add') }}</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="productCode" :label="$t('product.field.code')" width="150" />
      <el-table-column prop="productName" :label="$t('product.field.name')" min-width="200" />
      <el-table-column prop="unit" :label="$t('product.field.unit')" width="80" />
      <el-table-column prop="price" :label="$t('product.field.price')" width="120" align="right">
        <template #default="{ row }">{{ row.price != null ? '¥' + row.price.toFixed(2) : '-' }}</template>
      </el-table-column>
      <el-table-column prop="status" :label="$t('product.field.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
            {{ row.status === 'ENABLED' ? $t('product.status.enabled') : $t('product.status.disabled') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('product.field.operation')" width="150" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="handleEdit(row as Product)">{{ $t('common.action.edit') }}</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row as Product)">{{ $t('common.action.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 16px; justify-content: flex-end" v-model:current-page="currentPage" v-model:page-size="pageSize" :total="total" layout="total, prev, pager, next" @current-change="handlePageChange" />

    <el-dialog :title="isEdit ? $t('product.action.edit') : $t('product.action.add')" v-model="dialogVisible" width="600px">
      <el-form :model="form" label-width="100px">
        <el-form-item :label="$t('product.field.code')"><el-input v-model="form.productCode" /></el-form-item>
        <el-form-item :label="$t('product.field.name')"><el-input v-model="form.productName" /></el-form-item>
        <el-form-item :label="$t('product.field.unit')"><el-input v-model="form.unit" /></el-form-item>
        <el-form-item :label="$t('product.field.price')"><el-input-number v-model="form.price" :precision="2" :min="0" /></el-form-item>
        <el-form-item :label="$t('product.field.status')">
          <el-select v-model="form.status">
            <el-option :label="$t('product.status.enabled')" value="ENABLED" />
            <el-option :label="$t('product.status.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('product.field.remark')"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.action.cancel') }}</el-button>
        <el-button type="primary" @click="handleSave">{{ $t('common.action.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>
