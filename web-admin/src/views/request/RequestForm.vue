<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { createRequest, updateRequest, getRequest, submitRequest } from '@/api/biz-request'
import { getCustomers } from '@/api/customer'
import { getProducts } from '@/api/product'
import type { SaveBizRequestPayload, Customer, Product, PageResult, BizRequestItem } from '@/api/types'
import { track } from '@/utils/tracker'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const formRef = ref<FormInstance>()
const loading = ref(false)
const saving = ref(false)
const editId = computed(() => (route.params.id as string) || '')
const isEdit = computed(() => !!editId.value)
const originalVersion = ref<number | undefined>(undefined)
const originalStatus = ref<string>('')
const customerOptions = ref<Customer[]>([])
const productOptions = ref<Product[]>([])
const form = ref<{ title: string; customerId: string; applyReason: string; items: Array<{ productId: string; quantity: number; unitPrice: number; sortNo?: number }> }>({ title: '', customerId: '', applyReason: '', items: [] })
const rules: FormRules = {
  title: [{ required: true, message: t('biz.request.msg.requiredField'), trigger: 'blur' }],
  customerId: [{ required: true, message: t('biz.request.error.customerRequired'), trigger: 'change' }],
}
const totalAmount = computed(() => form.value.items.reduce((sum, item) => sum + item.quantity * item.unitPrice, 0))

async function loadOptions() {
  try {
    const [customerRes, productRes] = await Promise.all([getCustomers({ pageNo: 1, pageSize: 1000 }), getProducts({ pageNo: 1, pageSize: 1000 })])
    customerOptions.value = (customerRes as PageResult<Customer>).records || []
    productOptions.value = (productRes as PageResult<Product>).records || []
  } catch (e) { console.error('loadOptions failed', e) }
}

async function loadRequest() {
  if (!isEdit.value) return
  loading.value = true
  try {
    const detail = await getRequest(editId.value)
    originalVersion.value = detail.version
    originalStatus.value = detail.requestStatus
    form.value.title = detail.title || ''
    form.value.customerId = detail.customerId || ''
    form.value.applyReason = detail.applyReason || ''
    form.value.items = (detail.items || []).map((item: BizRequestItem) => ({ productId: item.productId, quantity: item.quantity, unitPrice: item.unitPrice, sortNo: item.sortNo }))
  } catch (e) { console.error('loadRequest failed', e); ElMessage.error(t('biz.request.error.notFound')); router.push('/biz/requests') } finally { loading.value = false }
}

function handleAddItem() { form.value.items.push({ productId: '', quantity: 1, unitPrice: 0 }) }
function handleRemoveItem(index: number) { form.value.items.splice(index, 1) }
function handleProductChange(index: number) {
  const item = form.value.items[index]
  const product = productOptions.value.find((p) => p.id === item.productId)
  if (product) { item.unitPrice = product.price || 0 }
}

function buildPayload(): SaveBizRequestPayload {
  return {
    id: isEdit.value ? editId.value : undefined,
    version: originalVersion.value,
    title: form.value.title,
    customerId: form.value.customerId,
    customerNameSnapshot: customerOptions.value.find((c) => c.id === form.value.customerId)?.customerName,
    applyReason: form.value.applyReason || undefined,
    items: form.value.items.map((item, idx) => ({
      productId: item.productId,
      productCodeSnapshot: productOptions.value.find((p) => p.id === item.productId)?.productCode,
      productNameSnapshot: productOptions.value.find((p) => p.id === item.productId)?.productName,
      quantity: item.quantity,
      unitPrice: item.unitPrice,
      sortNo: idx + 1,
    })),
  }
}

function validateItems(): boolean {
  if (form.value.items.length === 0) { ElMessage.warning(t('biz.request.msg.itemRequired')); return false }
  for (const item of form.value.items) {
    if (!item.productId) { ElMessage.warning(t('biz.request.msg.productRequired')); return false }
    if (item.quantity <= 0) { ElMessage.warning(t('biz.request.msg.quantityPositive')); return false }
    if (item.unitPrice < 0) { ElMessage.warning(t('biz.request.msg.priceNotNegative')); return false }
  }
  return true
}

async function handleSaveDraft() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (!validateItems()) return
    saving.value = true
    try {
      const payload = buildPayload()
      if (isEdit.value) { await updateRequest(editId.value, payload); ElMessage.success(t('biz.request.msg.draftUpdated')) }
      else {
        const created = await createRequest(payload)
        ElMessage.success(t('biz.request.msg.draftSaved'))
        if (created?.id) { router.push(`/biz/requests/${created.id}`) } else { router.push('/biz/requests') }
      }
      track('biz.request.draftSaved')
    } catch (e) { console.error('saveDraft failed', e) } finally { saving.value = false }
  })
}

async function handleSaveAndSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (!validateItems()) return
    try {
      await ElMessageBox.confirm(t('biz.request.msg.saveAndSubmitConfirm'), t('biz.request.msg.saveAndSubmitConfirmTitle'), { type: 'warning' })
    } catch { return }
    saving.value = true
    try {
      const payload = buildPayload()
      let requestId = editId.value
      if (isEdit.value) { await updateRequest(editId.value, payload) }
      else { const created = await createRequest(payload); requestId = created.id }
      if (requestId) { await submitRequest(requestId); ElMessage.success(t('biz.request.msg.savedAndSubmitted')) }
      track('biz.request.submitted')
      router.push(`/biz/requests/${requestId}`)
    } catch (e) { console.error('saveAndSubmit failed', e) } finally { saving.value = false }
  })
}

function handleBack() {
  if (isEdit.value) { router.push(`/biz/requests/${editId.value}`) } else { router.push('/biz/requests') }
}

onMounted(async () => { await loadOptions(); await loadRequest() })
</script>

<template>
  <div v-loading="loading">
    <el-page-header :content="isEdit ? t('biz.request.form.editTitle') : t('biz.request.form.createTitle')" @back="handleBack">
      <template #extra>
        <el-button @click="handleBack">{{ t('biz.request.form.backToList') }}</el-button>
      </template>
    </el-page-header>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" style="margin-top: 20px">
      <el-form-item :label="t('biz.request.form.titleLabel')" prop="title">
        <el-input v-model="form.title" :placeholder="t('biz.request.form.titlePlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('biz.request.field.customerId')" prop="customerId">
        <el-select v-model="form.customerId" :placeholder="t('biz.request.form.customerPlaceholder')" filterable>
          <el-option v-for="c in customerOptions" :key="c.id" :label="c.customerName" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('biz.request.field.applyReason')">
        <el-input v-model="form.applyReason" type="textarea" :rows="3" :placeholder="t('biz.request.form.applyReasonPlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('biz.request.field.items')">
        <div style="width: 100%">
          <el-button type="primary" plain size="small" @click="handleAddItem" style="margin-bottom: 10px">{{ t('biz.request.form.addItem') }}</el-button>
          <el-table :data="form.items" border style="width: 100%">
            <el-table-column type="index" width="50" />
            <el-table-column :label="t('biz.request.form.product')" min-width="200">
              <template #default="{ row, $index }">
                <el-select v-model="row.productId" :placeholder="t('biz.request.form.productPlaceholder')" filterable @change="() => handleProductChange($index)">
                  <el-option v-for="p in productOptions" :key="p.id" :label="p.productName" :value="p.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="t('biz.request.field.quantity')" width="120">
              <template #default="{ row }">
                <el-input-number v-model="row.quantity" :min="1" controls-position="right" />
              </template>
            </el-table-column>
            <el-table-column :label="t('biz.product.field.price')" width="150">
              <template #default="{ row }">
                <el-input-number v-model="row.unitPrice" :min="0" :precision="2" controls-position="right" />
              </template>
            </el-table-column>
            <el-table-column :label="t('biz.request.field.lineAmount')" width="120">
              <template #default="{ row }">
                {{ (row.quantity * row.unitPrice).toFixed(2) }}
              </template>
            </el-table-column>
            <el-table-column :label="t('biz.request.field.operation')" width="80">
              <template #default="{ $index }">
                <el-button type="danger" size="small" @click="handleRemoveItem($index)">{{ t('common.action.delete') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="form.items.length === 0" :description="t('biz.request.form.emptyItems')" />
          <div v-if="form.items.length > 0" style="text-align: right; margin-top: 10px; font-weight: bold">
            {{ t('biz.request.field.totalLabel') }} {{ totalAmount.toFixed(2) }}
          </div>
        </div>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="handleSaveDraft">{{ t('biz.request.form.saveDraft') }}</el-button>
        <el-button type="success" :loading="saving" @click="handleSaveAndSubmit">{{ t('biz.request.form.saveAndSubmit') }}</el-button>
        <el-button @click="handleBack">{{ t('biz.request.form.backToList') }}</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>