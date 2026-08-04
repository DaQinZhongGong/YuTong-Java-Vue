# RB-03 Redis 连接失败 Runbook

> 适用: sa-token 会话异常、缓存击穿、Redis 不可达
> 优先级: P1 | 影响: 登录态丢失、限流失效、热点数据穿透 | 设计来源: 33/63

## 1. 现象

- `/actuator/health` 返回 `{"redis":{"status":"DOWN"}}`。
- 用户登录后立即被踢出，或所有接口返回 401。
- 应用日志出现 `RedisConnectionFailureException` / `JedisConnectionException` / `NOREPLICAS`。
- sa-token 报 `NotLoginException` 概率激增。
- 限流（如配置在 Redis）失效，可能引发后端雪崩。

## 2. 影响范围

- 所有需要登录的接口（写入类、AI、低代码）返回 401。
- 字典/配置类缓存失效，DB 压力短时间激增。
- 分布式锁失效，可能导致任务重复执行。

## 3. 快速判断命令

```bash
# 1) Redis 容器状态
docker ps --filter "name=yutong-redis" --format "{{.Names}}\t{{.Status}}\t{{.Ports}}"

# 2) PING 测试
docker exec yutong-redis redis-cli -p 6379 PING
# 期望: PONG

# 3) 从应用容器内测试
docker exec yutong-boot sh -c "echo PING | redis-cli -h yutong-redis -p 6379"

# 4) 内存与连接数
docker exec yutong-redis redis-cli -p 6379 INFO memory | findstr "used_memory_human maxmemory_human"
docker exec yutong-redis redis-cli -p 6379 INFO clients | findstr "connected_clients maxclients"

# 5) 慢日志
docker exec yutong-redis redis-cli -p 6379 SLOWLOG GET 10

# 6) sa-token 键数量
docker exec yutong-redis redis-cli -p 6379 KEYS "satoken:*" | findstr /c:"satoken:" /v
docker exec yutong-redis redis-cli -p 6379 DBSIZE

# 7) 应用侧健康检查
curl.exe -s http://localhost:20010/actuator/health | python -m json.tool
```

## 4. 关键日志位置

| 位置 | 内容 |
| --- | --- |
| `docker logs yutong-redis` | Redis 启动、AOF/RDB、内存溢出 |
| `docker logs yutong-boot` | Lettuce/Jedis 客户端异常、sa-token 异常 |
| `redis-cli INFO` | 内存、连接、持久化 |
| `redis-cli SLOWLOG` | 慢命令 |

## 5. 回滚或临时降级方案

### 5.1 Redis 重启（数据可丢失场景）

```bash
# 1) 备份当前 RDB（如还能访问）
docker exec yutong-redis redis-cli -p 6379 BGSAVE
docker cp yutong-redis:/data/dump.rdb ./dump.rdb.bak

# 2) 重启
docker restart yutong-redis
sleep 10
docker exec yutong-redis redis-cli -p 6379 PING

# 3) 重启应用（让 sa-token 重建会话）
docker restart yutong-boot
```

### 5.2 sa-token 降级到本地缓存（紧急）

```yaml
# application.yml 紧急配置
sa-token:
  token-style: uuid
  is-share: false
  is-concurrent: true
spring:
  data:
    redis:
      # 临时指向本地 redis 或切换到 standalone mock
      host: localhost
```

> 注意: 切到本地缓存会导致多实例会话不一致，仅作单实例紧急降级。

### 5.3 持久化恢复（AOF 损坏）

```bash
# 1) 停止 Redis
docker stop yutong-redis

# 2) 修复 AOF
docker run --rm -v yutong_redis_data:/data redis:7 redis-check-aof --fix /data/appendonly.aof

# 3) 启动
docker start yutong-redis
```

## 6. 根因分析模板

```markdown
## RCA - {incidentId}

- 触发原因:
  - [ ] Redis 容器停止
  - [ ] 内存溢出（maxmemory 配置过低）
  - [ ] AOF/RDB 损坏
  - [ ] 网络分区
  - [ ] 慢命令阻塞（KEYS * / 大 HGETALL）
  - [ ] 客户端连接数耗尽
  - [ ] 其他: ___
- 根因: {详细描述}
- 影响范围: {会话丢失用户数/接口 401 数}
- 实际 RTO: {minutes} 分钟
- 防复发任务:
  1. {任务1}
  2. {任务2}
```

## 7. 后续修复任务

1. 配置 `maxmemory-policy = allkeys-lru` 并接入告警。
2. 禁用 `KEYS *` 命令，统一用 `SCAN`。
3. sa-token 增加本地 fallback 缓存（短 TTL）。
