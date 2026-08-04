# YuTong 雨桐 — 商业级全栈技术底座 [v1.0 GA]

> Java 25 · Spring Boot 4.0 · PostgreSQL 18 · Vue 3.5 · Uniapp · AI

通用开发平台 + 完整样例工程 + 常见中间件集成 + 低代码快速开发能力。

## 目录结构

```
YuTong-Java-Vue/
├── backend/            # Java 后端多模块工程 (yutong-boot 单体启动器)
├── web-admin/          # Vue3 管理端
├── mobile-uniapp/      # Uniapp 移动端
├── database/           # Flyway 迁移脚本与种子数据
├── deploy/             # Docker Compose、Nginx、部署脚本
├── openapi/            # OpenAPI 契约输出
├── tools/              # 检查脚本、辅助命令
├── docs-site/          # 产品官网与文档站 (预留)
├── tests/              # E2E、验收脚本
├── release-evidence/   # 发布证据归档
├── build/              # 构建产物、SBOM、安全扫描报告
└── YuTong-Java-Docs/   # 设计文档与机器契约
```

## 本地启动

依赖：Docker 24+、Node 24、pnpm 10+、JDK 25、Maven 3.9+（后两者可通过 Docker 提供）。

```bash
# 1. 启动本地依赖 (PostgreSQL 18 / Redis 7 / MinIO)
docker compose -f deploy/docker-compose.boot.yml up -d

# 2. 执行数据库迁移 (随 yutong-boot 启动自动执行 Flyway)
cd backend && mvn -pl yutong-boot -am spring-boot:run

# 3. 启动 Web 管理端
pnpm install
pnpm dev:web
```

## 健康检查与冒烟

- 后端健康: `GET http://localhost:8080/actuator/health`
- 业务健康: `GET http://localhost:8080/api/v1/monitor/health`
- Mock 登录: `POST http://localhost:8080/api/v1/auth/login`
- 字典查询: `GET http://localhost:8080/api/v1/dict-items/by-type/biz_request_status`
- OpenAPI: `GET http://localhost:8080/v3/api-docs`

## 约定

- 主键统一字符串 (ULID，varchar(32))，禁止数据库自增。
- API 路径统一 `/api/v1`，ID 类型统一 string。
- 第一版默认 `boot` 模块化单体，不依赖 Nacos/Gateway。
