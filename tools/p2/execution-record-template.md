# P2 命令执行记录模板

> 模板来源：`YuTong-Java-Docs/86-P2命令模板与执行记录详设/86-P2命令模板与执行记录详设.md`
> 用途：P2 阶段每轮命令执行后回写记录。可由 `tools/p2/run-p2-checks.sh | tee` 直接生成证据。

## 执行信息

| 项 | 内容 |
| --- | --- |
| 执行日期 | {{date}} |
| 执行人 | {{executor}} |
| 执行环境 | dev / test / prod-like |
| 执行机器 | {{hostname}} |
| P2 阶段 | D{{dayIndex}} |
| 关联任务编号 | {{task_id}} |
| 关联设计文档 | {{doc_id}} |
| 证据归档路径 | `release-evidence/v0.2.0/p2-checks-{{date}}.txt` |

## 命令执行清单

按 86 号文档 10 类命令逐条记录。命令模板见 `tools/p2/run-p2-checks.sh`。

| 编号 | 命令分类 | 命令 | 结果 | 证据路径 | 备注 |
| --- | --- | --- | --- | --- | --- |
| CMD-01 | 目录检查 | `bash tools/checks/check-dir-layout` | PASS / FAIL / SKIP | `release-evidence/v0.2.0/p2-checks-{{date}}.txt#CMD-01` |  |
| CMD-02a | 文档总览扫描 | `grep -E 'document-catalog\|product-baseline\|...' YuTong-Java-Docs/00-设计文档总览.md` |  |  |  |
| CMD-02b | check_docs.rb | `ruby YuTong-Java-Docs/quality/check_docs.rb` |  |  | Docker: `ruby:3.2-slim` |
| CMD-02c | generate_manifest.rb | `ruby YuTong-Java-Docs/quality/generate_manifest.rb` |  |  |  |
| CMD-02d | generate_manifest.rb --verify | `ruby YuTong-Java-Docs/quality/generate_manifest.rb --verify` |  |  |  |
| CMD-03 | 后端健康检查 | `curl -fsS http://localhost:8080/actuator/health` |  |  | 端口可能为 8082 |
| CMD-04 | Flyway 迁移 | `./mvnw -pl backend/yutong-boot -am flyway:migrate` |  |  |  |
| CMD-05 | OpenAPI 导出 | `curl -fsS http://localhost:8080/v3/api-docs -o openapi/openapi.json` |  |  |  |
| CMD-06 | Web 启动 | `pnpm install && pnpm dev`（web-admin） |  |  |  |
| CMD-07 | 移动端启动 | `pnpm install && pnpm dev:h5`（mobile-uniapp） |  |  |  |
| CMD-08 | 质量检查 | `pnpm lint && pnpm typecheck` |  |  |  |
| CMD-09 | Secret Scan | `gitleaks detect --source . --no-banner` |  |  |  |
| CMD-10 | 禁止自增主键 | `bash tools/checks/check-no-autoincrement.sh` |  |  |  |

## 汇总

| 指标 | 值 |
| --- | --- |
| 总命令数 | 13 |
| PASS | {{pass_count}} |
| FAIL | {{fail_count}} |
| SKIP | {{skip_count}} |

## 失败处理

参照 `tools/p2/failure-handling.yaml`。每个失败项必须记录：

| 失败编号 | 命令 | 失败原因 | 处理路径 | 责任人 | 截止时间 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  |  |  |  |

## 23 追踪记录回写

- 是否需登记偏差：是 / 否
- 偏差编号：{{deviation_id}}（如 DEV-2026-XXX）
- 23 追踪记录路径：`YuTong-Java-Docs/23-设计到落地追踪记录/23-设计到落地追踪记录.md#偏差{{deviation_id}}`

## 签署

| 角色 | 姓名 | 签字 |
| --- | --- | --- |
| 执行人 |  |  |
| 复核人 |  |  |
