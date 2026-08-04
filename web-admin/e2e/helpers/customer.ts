/**
 * GA2-L183: 客户管理表单操作辅助函数。
 *
 * 设计来源: web-admin/src/views/customer/CustomerList.vue。
 *
 * 关键决策:
 *  1. Element Plus el-form-item 未通过 for/id 关联控件, 用 form-item label 文本
 *     锚定容器, 再在容器内查找 .el-input__inner / .el-select 等控件。
 *  2. el-select 选项渲染到 body 末尾 (.el-select-dropdown__item), 用文本匹配选项。
 *  3. 客户表格操作列使用 "编辑" / "删除" 文本按钮, 用 getByRole('button') + name 定位;
 *     多行时通过 row context 缩小范围。
 *  4. 创建客户默认表单值 (CustomerList.vue line 40):
 *       { customerType: 'ENTERPRISE', status: 'ENABLED' }
 *     GA2-L184: 前端 status 枚举已对齐后端 ENABLED/DISABLED, 联系人字段对齐 contactName。
 */
import type { Page, Locator } from '@playwright/test'
import { expect } from '@playwright/test'
import {
  customerSelectors,
  customerFormFieldLabels,
  customerTypeOptions,
  customerStatusOptions,
} from './selectors'

/** 客户表单输入值 (字段对齐 CustomerList.vue el-form)。 */
export interface CustomerFormInput {
  customerCode: string
  customerName: string
  customerType?: keyof typeof customerTypeOptions
  contactName?: string
  contactPhone?: string
  contactEmail?: string
  address?: string
  status?: keyof typeof customerStatusOptions
  remark?: string
}

/** 在 el-form 内按 label 文本定位 form-item。 */
function getFormItem(page: Page, label: string): Locator {
  // GA2-L184: 限定在可见 dialog 内查找 form-item, 避免与列表页搜索表单字段
  // (如 "客户名称") 冲突 — 搜索表单和对话框表单均有 "客户名称" label,
  // 不限定 scope 时 .first() 会命中搜索框而非对话框输入框。
  return page.locator('.el-dialog:visible .el-form-item', {
    has: page.locator('.el-form-item__label', { hasText: label }),
  })
}

/** 在 form-item 内填写文本输入框。 */
async function fillInput(page: Page, label: string, value: string): Promise<void> {
  const formItem = getFormItem(page, label)
  const input = formItem.locator('input.el-input__inner').first()
  await input.fill(value)
}

/** 在 form-item 内填写 textarea。 */
async function fillTextarea(page: Page, label: string, value: string): Promise<void> {
  const formItem = getFormItem(page, label)
  const textarea = formItem.locator('textarea').first()
  await textarea.fill(value)
}

/** 在 form-item 内的 el-select 中选择指定选项文本。 */
async function selectOption(page: Page, label: string, optionText: string): Promise<void> {
  const formItem = getFormItem(page, label)
  // Element Plus el-select 触发器 wrapper (内部 input 会被 placeholder 覆盖, 需点击 wrapper)
  const selectTrigger = formItem.locator('.el-select').first()
  await selectTrigger.click()
  // 选项渲染在 body 末尾
  const option = page.locator('.el-select-dropdown__item:visible', {
    hasText: optionText,
  })
  await option.first().click()
  // 等下拉关闭
  await expect(page.locator('.el-select-dropdown:visible')).toHaveCount(0, {
    timeout: 3_000,
  })
}

/**
 * 打开新增客户对话框。
 * 前置: 已登录且具备 biz:customer:add 权限 (admin/biz)。
 */
export async function openCreateDialog(page: Page): Promise<void> {
  await page.goto(customerSelectors.listPath)
  await page.waitForLoadState('networkidle')
  // 等列表渲染 (即使 API 失败, 按钮也应可见)
  await page.waitForSelector('.el-table', { timeout: 10_000 })
  await page.getByRole('button', { name: customerSelectors.addButton }).click()
  // 等对话框标题可见
  await expect(
    page.locator('.el-dialog__title', { hasText: customerSelectors.addDialogTitle })
  ).toBeVisible({ timeout: 5_000 })
  // 等对话框动画完成
  await page.waitForTimeout(300)
}

/**
 * 打开编辑客户对话框 (按客户名称定位行)。
 * 前置: 已登录且具备 biz:customer:edit 权限; 该客户在当前页可见。
 */
export async function openEditDialog(page: Page, customerName: string): Promise<void> {
  await page.goto(customerSelectors.listPath)
  await page.waitForLoadState('networkidle')
  await page.waitForSelector('.el-table', { timeout: 10_000 })

  // 定位包含目标客户名称的表格行
  const row = page.locator('.el-table__row', {
    has: page.locator('td', { hasText: customerName }),
  })
  await expect(row.first()).toBeVisible({ timeout: 5_000 })

  // 点击行内 "编辑" 按钮
  await row.first().getByRole('button', { name: customerSelectors.editButton }).click()

  await expect(
    page.locator('.el-dialog__title', { hasText: customerSelectors.editDialogTitle })
  ).toBeVisible({ timeout: 5_000 })
  await page.waitForTimeout(300)
}

/**
 * 在新增/编辑对话框填写表单。
 * 仅填写传入的字段, 未传入字段保持表单当前值 (含默认值)。
 */
export async function fillCustomerForm(
  page: Page,
  input: CustomerFormInput,
): Promise<void> {
  if (input.customerCode !== undefined) {
    await fillInput(page, customerFormFieldLabels.customerCode, input.customerCode)
  }
  if (input.customerName !== undefined) {
    await fillInput(page, customerFormFieldLabels.customerName, input.customerName)
  }
  if (input.customerType !== undefined) {
    await selectOption(
      page,
      customerFormFieldLabels.customerType,
      customerTypeOptions[input.customerType],
    )
  }
  if (input.contactName !== undefined) {
    await fillInput(page, customerFormFieldLabels.contactName, input.contactName)
  }
  if (input.contactPhone !== undefined) {
    await fillInput(page, customerFormFieldLabels.contactPhone, input.contactPhone)
  }
  if (input.contactEmail !== undefined) {
    await fillInput(page, customerFormFieldLabels.contactEmail, input.contactEmail)
  }
  if (input.address !== undefined) {
    await fillInput(page, customerFormFieldLabels.address, input.address)
  }
  if (input.status !== undefined) {
    await selectOption(
      page,
      customerFormFieldLabels.status,
      customerStatusOptions[input.status],
    )
  }
  if (input.remark !== undefined) {
    await fillTextarea(page, customerFormFieldLabels.remark, input.remark)
  }
}

/**
 * 点击对话框 "确定" 提交表单。
 * 不在此处断言结果, 由调用方根据预期 (成功 toast / 错误 toast / 字段错误) 断言。
 */
export async function submitCustomerForm(page: Page): Promise<void> {
  // 对话框 footer 的确定按钮
  const dialog = page.locator('.el-dialog:visible').first()
  await dialog.getByRole('button', { name: customerSelectors.confirmButton }).click()
}

/** 点击对话框 "取消" 关闭。 */
export async function cancelCustomerForm(page: Page): Promise<void> {
  const dialog = page.locator('.el-dialog:visible').first()
  await dialog.getByRole('button', { name: customerSelectors.cancelButton }).click()
  await expect(page.locator('.el-dialog:visible')).toHaveCount(0, { timeout: 3_000 })
}

/**
 * 删除指定客户 (按客户名称定位行)。
 * 前置: 已登录且具备 biz:customer:delete 权限; 该客户在当前页可见。
 */
export async function deleteCustomerByName(page: Page, customerName: string): Promise<void> {
  await page.goto(customerSelectors.listPath)
  await page.waitForLoadState('networkidle')
  await page.waitForSelector('.el-table', { timeout: 10_000 })

  const row = page.locator('.el-table__row', {
    has: page.locator('td', { hasText: customerName }),
  })
  await expect(row.first()).toBeVisible({ timeout: 5_000 })

  await row.first().getByRole('button', { name: customerSelectors.deleteButton }).click()

  // ElMessageBox.confirm 确认弹窗
  const confirmBox = page.locator('.el-message-box:visible').first()
  await confirmBox.getByRole('button', { name: customerSelectors.confirmDeleteButton }).click()
}

/**
 * 在客户列表搜索框输入关键字并查询。
 */
export async function searchCustomer(page: Page, keyword: string): Promise<void> {
  await page.goto(customerSelectors.listPath)
  await page.waitForLoadState('networkidle')
  await page.waitForSelector('.el-table', { timeout: 10_000 })

  const searchInput = page.getByPlaceholder(customerSelectors.searchInputPlaceholder)
  await searchInput.fill(keyword)
  await page.getByRole('button', { name: customerSelectors.searchButton }).click()
  await page.waitForLoadState('networkidle')
}

/**
 * 断言客户列表中包含指定名称的行。
 */
export async function expectCustomerInList(
  page: Page,
  customerName: string,
): Promise<void> {
  const row = page.locator('.el-table__row', {
    has: page.locator('td', { hasText: customerName }),
  })
  await expect(row.first()).toBeVisible({ timeout: 5_000 })
}

/**
 * 断言客户列表中不包含指定名称的行。
 */
export async function expectCustomerNotInList(
  page: Page,
  customerName: string,
): Promise<void> {
  const row = page.locator('.el-table__row', {
    has: page.locator('td', { hasText: customerName }),
  })
  await expect(row).toHaveCount(0, { timeout: 5_000 })
}

/**
 * 断言成功 toast (ElMessage success) 出现。
 */
export async function expectSuccessToast(page: Page, textFragment?: string): Promise<void> {
  const toast = page.locator('.el-message--success')
  await expect(toast.first()).toBeVisible({ timeout: 5_000 })
  if (textFragment) {
    await expect(toast.first()).toContainText(textFragment)
  }
}

/**
 * 断言错误 toast (ElMessage error) 出现。
 */
export async function expectErrorToast(page: Page, textFragment?: string): Promise<void> {
  const toast = page.locator('.el-message--error')
  await expect(toast.first()).toBeVisible({ timeout: 5_000 })
  if (textFragment) {
    await expect(toast.first()).toContainText(textFragment)
  }
}
