# YuTong Runbook 目录

> 设计来源: 33-商用运维SLA与灾备设计、63-生产部署运维Runbook详设
> 适用范围: v0.5+（C1 项目底座 / C2 私有化交付）

## Runbook 列表（第一版 7 个 + GA2 增补 3 个）

| 编号 | 名称 | 触发场景 | 优先级 |
| --- | --- | --- | --- |
| RB-01 | 应用启动失败 | yutong-boot 容器无法启动、Spring 启动报错 | P0 |
| RB-02 | 数据库连接失败 | PostgreSQL 不可达、连接池耗尽、Flyway 失败 | P0 |
| RB-03 | Redis 连接失败 | sa-token 会话丢失、缓存击穿 | P1 |
| RB-04 | 文件上传失败 | MinIO 不可达、签名失败、大小超限 | P1 |
| RB-05 | 导入导出任务失败 | 异步任务异常、积压、死信 | P2 |
| RB-06 | AI 调用失败 | AI Provider 不可用、超时、token 异常 | P2 |
| RB-07 | 登录失败 | sa-token 校验失败、租户隔离异常、密码错误激增 | P1 |
| RB-08 | 插件安装失败 | 插件市场安装时版本不兼容、依赖缺失、初始化异常 | P2 |
| RB-09 | 大屏渲染失败 / 数据源异常 | 大屏全屏空白、数据集无数据、ECharts 渲染异常 | P2 |
| RB-10 | 监控指标采集失败 | Actuator/Prometheus 无数据、JVM/连接池指标缺失 | P1 |

## 使用规范

1. 触发 Runbook 时必须先确认 Incident Commander、记录 incidentId。
2. P0/P1 必须按 33 号文档时效响应（P0: 15min 拉群 / 30min 首次通知；P1: 30min 响应 / 1h 初步判断）。
3. 执行每一步必须记录：操作者、时间戳、命令摘要、输出证据、traceId/incidentId。
4. 关闭事件后 2 个工作日内完成 RCA，归档到 `release-evidence/incidents/{incidentId}/` 并回写 `23-设计到落地追踪记录`。

## 环境变量约定

执行 Runbook 命令前，先确认环境变量：

```bash
# 本地 / Docker Compose
export YT_NS=yutong
export YT_DOMAIN=localhost
export YT_BOOT_PORT=20010
export YT_DB_HOST=localhost
export YT_DB_PORT=5433
export YT_DB_NAME=yutong
export YT_DB_USER=yutong
export YT_REDIS_HOST=localhost
export YT_REDIS_PORT=6380
export YT_MINIO_ENDPOINT=http://localhost:9000
```

生产环境须替换为对应 namespace / 域名 / 端口。
