# AI 单任务执行模板

> 模板来源：`YuTong-Java-Docs/90-AI实现总控与上下文编排规范/90-AI实现总控与上下文编排规范.md`
> 用途：AI 每次执行一个实现任务时，必须复制本模板填写任务说明或提交说明。
> 强约束：90 号文档"AI 实现前封版条件补充"要求 P2-0 阶段建立任务追踪；本模板是单任务追踪的最小单元。

## 任务基本信息

```text
任务编号：{{task_id}}
目标：{{objective}}
必读设计：{{required_docs}}
输入契约：{{input_contracts}}
输出文件：{{output_files}}
禁止事项：{{prohibitions}}
验证命令：{{verify_commands}}
验收证据：{{acceptance_evidence}}
偏差记录：无 / 已登记到 23
```

## 字段填写指引

### 任务编号

格式：`P{阶段号}-{领域}-{序号}`，例如 `P2-BACKEND-001`、`P3-SAMPLE-012`、`P4-LC-003`。

### 目标

一句话描述本任务的可验收输出，避免"完成 XX 功能"这类不可量化描述。

- 好：实现 `POST /api/v1/biz/requests` 端点，返回 201 + 申请单 ID
- 差：完成申请单创建功能

### 必读设计

列出本任务必读的设计文档 ID + 路径。按 90 号文档"AI 上下文读取分层"装载：

- 全局上下文：`contracts/document-catalog.yaml`、`contracts/openapi/openapi.yaml` 等
- 领域上下文：本任务所属领域的主设计 + 详设
- 验收上下文：本任务的验收标准文档

示例：

```text
必读设计：
- 18-样例业务详细设计（领域主设计）
- 52-后端服务分工与接口实现详设（后端详设）
- 58-后端API逐接口任务清单（接口任务）
- 48-错误码注册表与API契约详设（错误码）
- contracts/openapi/openapi.yaml#/paths/~1api~1v1~1biz~1requests（输入契约）
- contracts/registries/permissions.yaml（权限码）
```

### 输入契约

列出本任务消费的机器契约文件 + 路径片段：

```text
输入契约：
- contracts/openapi/openapi.yaml#/components/schemas/BizRequestCreateRequest
- contracts/registries/permissions.yaml#biz:request:create
- contracts/registries/errors.yaml#BIZ-001
- contracts/registries/statuses.yaml#biz_request_status
```

### 输出文件

列出本任务产出的代码/配置/测试文件路径。每个文件标注类型（新增/修改/删除）：

```text
输出文件：
- backend/yutong-sample-service/.../BizRequestController.java [新增]
- backend/yutong-sample-service/.../BizRequestApplicationService.java [修改]
- backend/yutong-sample-service/.../BizRequestControllerTest.java [新增]
- database/migrations/V009__add_biz_request_index.sql [新增]
```

### 禁止事项

引用 90 号文档"AI 禁止临时发挥清单"和"非 Demo 实现要求"，列出本任务特别注意的禁止项：

```text
禁止事项：
- 禁止使用 Long id / id: number / 自增主键（按 06/08/51）
- 禁止 Controller 直接访问 Mapper（按 52 走 Application/Domain/Repository）
- 禁止为跑通而关闭 @Auditable 审计（按 67 走审计契约）
- 禁止 UI 临时色值（按 50/56 走 Token）
- 禁止跳过状态机直接更新状态字段（按 56 走状态机）
```

### 验证命令

列出本任务可执行的验证命令，命令输出可直接作为证据：

```text
验证命令：
- cd backend && ./mvnw -pl yutong-sample-service test -Dtest=BizRequestControllerTest
- curl -fsS http://localhost:8080/actuator/health
- curl -fsS -X POST http://localhost:8080/api/v1/biz/requests -H 'Content-Type: application/json' -d '{"title":"test"}'
- ruby YuTong-Java-Docs/quality/check_docs.rb
```

### 验收证据

列出本任务归档的证据路径（按 90 号"每阶段最小验收证据"）：

```text
验收证据：
- release-evidence/v0.2.0/backend-smoke-{{date}}.txt（启动日志）
- release-evidence/v0.2.0/api-response-{{date}}.json（API 响应样例）
- release-evidence/v0.2.0/unit-test-{{date}}.txt（单元测试报告）
```

### 偏差记录

90 号强约束：实现与设计不一致时，先写 23 偏差记录，再决定改代码或改设计。

```text
偏差记录：
- 状态：无 / 已登记到 23
- 偏差编号（如已登记）：DEV-2026-XXX
- 偏差类型：字段 / 接口 / 状态机 / 权限 / 错误码 / 目录 / 主键 / 其他
- 处置路径：改代码不改设计 / 走 84 ADR / 走 23 暂不改 / 走 23 + P1 整改
- 23 追踪记录路径：YuTong-Java-Docs/23-设计到落地追踪记录/23-设计到落地追踪记录.md#偏差DEV-2026-XXX
```

## 上下文装载清单

每次任务执行前必须填写本清单（90 号"上下文装载必须记录本次使用的 docId、契约路径和 manifest hash"）：

```text
本次使用的 docId：18, 52, 58, 48
本次使用的契约路径：
  - contracts/openapi/openapi.yaml
  - contracts/registries/permissions.yaml
  - contracts/registries/errors.yaml
本次 manifest hash：{{design_manifest_sha256}}
本次 manifest 路径：YuTong-Java-Docs/contracts/design-manifest.json
```

可使用 `tools/p2/ai-context-manifest-template.yaml` 作为机器可读版本。

## 任务完成确认

任务完成前自检：

- [ ] 所有必读设计已读取
- [ ] 所有输入契约已对齐
- [ ] 所有输出文件已落盘
- [ ] 所有禁止事项已规避
- [ ] 所有验证命令已执行通过
- [ ] 所有验收证据已归档
- [ ] 偏差已登记到 23（如有）
- [ ] 上下文装载清单已填写
