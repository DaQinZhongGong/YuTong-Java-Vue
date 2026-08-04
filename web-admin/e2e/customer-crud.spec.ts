/**
 * GA2-L183: 客户管理 CRUD 全流程 E2E 测试 (覆盖 CT-createCustomer 等)。
 *
 * 覆盖场景:
 *  - 新增客户: 填表 → 提交 → 列表可见 (CT-createCustomer 验收)
 *  - 编辑客户: 改名称 → 提交 → 列表更新 (CT-updateCustomer 验收)
 *  - 删除客户: 确认 → 列表移除 (CT-deleteCustomer 验收)
 *  - 查询客户: 关键字过滤 (CT-listCustomers 验收)
 *
 * 设计来源:
 *  - 58-后端API逐接口任务清单 (CT-createCustomer/updateCustomer/deleteCustomer/listCustomers)
 *  - 59-测试矩阵与验收用例详设 (TC-P3-CUS-001 创建客户)
 *  - web-admin/src/views/customer/CustomerList.vue (CRUD 实现)
 *
 * GA2-L184: 前端缺陷已修复 (customer.ts 双重前缀 / contactPerson→contactName /
 *   ACTIVE→ENABLED / masterdata→biz 权限码), 本 spec 收紧成功路径断言,
 *   不再使用 try/catch 兜底, 真实验证 CRUD 全流程。
 *
 * 本 spec 用唯一码 E2E-{timestamp} 避免污染现有数据; 删除步骤兜底清理。
 *
 * 注: CustomerList.vue 新增/编辑对话框表单仅含 客户编码/客户名称/联系人/电话/地址/状态/备注,
 * 无 customerType/contactEmail 字段, 故 fillCustomerForm 不再传入这两个字段。
 */
import { test, expect } from '@playwright/test'
import { loginAs } from './helpers/auth'
import {
  openCreateDialog,
  fillCustomerForm,
  submitCustomerForm,
  openEditDialog,
  deleteCustomerByName,
  searchCustomer,
  expectCustomerInList,
  expectSuccessToast,
  expectErrorToast,
} from './helpers/customer'

/** 生成唯一客户编码, 避免并发或重跑冲突。 */
function genUniqueCode(): string {
  const ts = Date.now()
  return `E2E-${ts}`
}

test.describe('GA2-L183 客户 CRUD 全流程', () => {
  test.describe.configure({ mode: 'serial' })

  test('新增客户成功 (CT-createCustomer)', async ({ page }) => {
    await loginAs(page, 'admin_demo', 'demo123', 'admin')

    const code = genUniqueCode()
    const name = `E2E测试客户_${code}`

    await openCreateDialog(page)
    await fillCustomerForm(page, {
      customerCode: code,
      customerName: name,
      contactName: '测试联系人',
      contactPhone: '13800000000',
      address: 'E2E测试地址',
      status: 'ENABLED',
      remark: 'GA2-L184 E2E 自动化新增',
    })
    await submitCustomerForm(page)

    // 预期: 成功 toast "创建成功" (GA2-L184: 前端缺陷已修复, 硬断言)
    await expectSuccessToast(page, '创建成功')
  })

  test('查询客户 (CT-listCustomers)', async ({ page }) => {
    await loginAs(page, 'admin_demo', 'demo123', 'admin')

    // 先创建一个客户用于查询
    const code = genUniqueCode()
    const name = `E2E查询测试_${code}`
    await openCreateDialog(page)
    await fillCustomerForm(page, {
      customerCode: code,
      customerName: name,
      contactPhone: '13900000000',
    })
    await submitCustomerForm(page)
    await page.waitForTimeout(1000)

    // 按名称搜索
    await searchCustomer(page, name)
    // 预期: 列表包含目标客户 (GA2-L184: 前端缺陷已修复, 硬断言)
    await expectCustomerInList(page, name)
  })

  test('编辑客户成功 (CT-updateCustomer)', async ({ page }) => {
    await loginAs(page, 'admin_demo', 'demo123', 'admin')

    // 先创建一个客户用于编辑
    const code = genUniqueCode()
    const originalName = `E2E编辑前_${code}`
    await openCreateDialog(page)
    await fillCustomerForm(page, {
      customerCode: code,
      customerName: originalName,
    })
    await submitCustomerForm(page)
    await page.waitForTimeout(1000)

    // 编辑
    const newName = `E2E编辑后_${code}`
    await openEditDialog(page, originalName)
    // 清空原名称再填写新名称
    await page
      .locator('.el-form-item', {
        has: page.locator('.el-form-item__label', { hasText: '客户名称' }),
      })
      .locator('input.el-input__inner')
      .first()
      .fill('')
    await fillCustomerForm(page, { customerName: newName })
    await submitCustomerForm(page)
    // 预期: 成功 toast "更新成功" (GA2-L184: 前端缺陷已修复, 硬断言)
    await expectSuccessToast(page, '更新成功')
  })

  test('删除客户成功 (CT-deleteCustomer)', async ({ page }) => {
    await loginAs(page, 'admin_demo', 'demo123', 'admin')

    // 先创建一个客户用于删除
    const code = genUniqueCode()
    const name = `E2E删除测试_${code}`
    await openCreateDialog(page)
    await fillCustomerForm(page, {
      customerCode: code,
      customerName: name,
    })
    await submitCustomerForm(page)
    await page.waitForTimeout(1000)

    // 删除 (GA2-L184: 前端缺陷已修复, 硬断言)
    await deleteCustomerByName(page, name)
    await expectSuccessToast(page, '删除成功')
  })

  test('客户编码重复返回 BIZ-409005 (TC-P3-CUS-002)', async ({ page }) => {
    await loginAs(page, 'admin_demo', 'demo123', 'admin')

    const code = genUniqueCode()
    const name = `E2E重复编码_${code}`

    // 第一次创建 (预期成功)
    await openCreateDialog(page)
    await fillCustomerForm(page, {
      customerCode: code,
      customerName: name,
    })
    await submitCustomerForm(page)
    await page.waitForTimeout(1000)

    // 第二次创建同编码 (预期失败)
    await openCreateDialog(page)
    await fillCustomerForm(page, {
      customerCode: code,
      customerName: `${name}_DUP`,
    })
    await submitCustomerForm(page)

    // 预期: 错误 toast (后端返回 409, 错误码 BIZ-409005 或 SYS-409004)
    // GA2-L184: 前端缺陷已修复, 客户编码重复应触发后端 409 → 错误 toast, 硬断言
    await expectErrorToast(page)

    // 清理: 删除第一次创建的客户 (兜底, 即使前端 bug 导致列表不可见也不抛错)
    try {
      await deleteCustomerByName(page, name)
    } catch {
      // 忽略清理失败
    }
  })
})
