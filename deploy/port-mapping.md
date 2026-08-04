# YuTong 服务端口统一规划 (20000-20099)

> 文档 ID: DEP-PORT-001 | 生效日期: 2026-07-29 | 状态: active
> 约束范围: 所有 Docker Compose 宿主机映射端口、后端/前端开发端口、监控端口、AI 推理端口

## 规划原则

1. **统一段**: 所有 YuTong 相关服务宿主机端口必须落入 `20000-20099`，避免与系统默认端口(5432/6379/8080等)及其他项目冲突。
2. **预留缓冲**: 同类别服务按 10 为间隔分组，预留扩展位。
3. **容器内端口不变**: Spring Boot 容器内仍使用 8080，仅宿主机映射端口调整。
4. **文档即代码**: 本文件为唯一权威端口源，所有配置必须与其保持一致；冲突时以本文件为准。

## 端口总表

| 服务 | 宿主机端口 | 容器内端口 | 协议 | 说明 | 所在配置 |
|------|-----------|-----------|------|------|----------|
| PostgreSQL | 20000 | 5432 | TCP | 主数据库 | docker-compose.*.yml, .env |
| Redis | 20001 | 6379 | TCP | 缓存/会话/实时广播 | docker-compose.*.yml, .env |
| MinIO API | 20002 | 9000 | HTTP | 对象存储 S3 API | docker-compose.*.yml, .env |
| MinIO Console | 20003 | 9001 | HTTP | 对象存储管理控制台 | docker-compose.*.yml, .env |
| yutong-backend-run | 20010 | 8080 | HTTP | 单体后端(默认启动模式) | docker-compose.run.yml, vite.config.ts, mobile |
| yutong-gateway-run | 20011 | 8080 | HTTP | 微服务网关入口 | docker-compose.gateway.yml |
| Prometheus | 20020 | 9090 | HTTP | 指标采集与告警 | docker-compose.monitoring.yml |
| Grafana | 20021 | 3000 | HTTP | 监控看板 | docker-compose.monitoring.yml |
| Ollama | 20030 | 11434 | HTTP | 本地 LLM 推理服务 | docker-compose.ollama.yml |
| web-admin (Vite dev) | 20050 | 20050 | HTTP | 前端开发服务器 | vite.config.ts |
| web-admin (preview) | 20051 | 20051 | HTTP | 前端预览服务器 | vite preview |
| e2e test backend | 20060 | 8080 | HTTP | E2E 测试专用后端 | playwright.config.ts |

## 端口分组记忆法

- `20000-20009`: 数据层 (DB / Cache / Storage)
- `20010-20019`: 应用层 (Backend / Gateway)
- `20020-20029`: 可观测性 (Prometheus / Grafana / 预留 Loki/Tempo)
- `20030-20039`: AI / 推理 (Ollama / 预留 vLLM/TGI)
- `20040-20049`: 消息与流 (预留 Kafka/RabbitMQ / NATS)
- `20050-20059`: 前端开发 (Vite dev / preview / 预留 H5/移动端调试)
- `20060-20069`: 测试与质量 (E2E / 契约测试 / 压测)
- `20070-20099`: 预留扩展

## 变更记录

| 日期 | 变更人 | 内容 |
|------|--------|------|
| 2026-07-29 | yutong-ai | 初始制定，将历史散落端口(5434/6381/9020/8082/8092/9091/3001/11434/5183)统一迁移至 20000 段 |

## 检查命令

```bash
# 检查 20000-20099 段端口占用 (Windows PowerShell)
for ($p=20000; $p -le 20099; $p++) { $c=netstat -ano | findstr ":$p "; if ($c) { Write-Host "PORT $p OCCUPIED" -ForegroundColor Red } else { Write-Host "PORT $p FREE" -ForegroundColor Green } }

# 快速验证全栈端口连通性
$ports = @(20000,20001,20002,20003,20010,20011,20020,20021,20030,20050)
foreach ($p in $ports) { Test-NetConnection -ComputerName localhost -Port $p -WarningAction SilentlyContinue | Select-Object RemotePort,TcpTestSucceeded }
```
