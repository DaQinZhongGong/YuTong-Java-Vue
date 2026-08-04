package com.yutong.system.idempotency.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.IdempotencyConflictException;
import com.yutong.common.exception.IdempotencyProcessingException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.idempotency.domain.IdempotencyRecord;
import com.yutong.system.idempotency.dto.IdempotentRequest;
import com.yutong.system.idempotency.dto.IdempotentResult;
import com.yutong.system.idempotency.mapper.IdempotencyRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 幂等服务。设计来源: 98-后端实现蓝图与代码骨架详设 (幂等实现模板)、57-完整DDL清单 sys_idempotency_record
 *
 * <p>实现 98 号文档定义的 8 步幂等处理流程:
 * <ol>
 *   <li>调用方计算 request_hash (本服务提供 {@link #computeRequestHash} 工具)</li>
 *   <li>Redis SETNX 短锁 (yutong:idem:{tenant}:{user}:{key}, TTL 30~120s)</li>
 *   <li>插入 sys_idempotency_record(status=PROCESSING) — REQUIRES_NEW 事务</li>
 *   <li>唯一键冲突时按 status 分流:
 *     <ul>
 *       <li>4.1 request_hash 不同 → SYS-409003 IdempotencyConflictException</li>
 *       <li>4.2 status=SUCCESS → 返回 response_snapshot 重放</li>
 *       <li>4.3 status=PROCESSING 且 locked_until 未过期 → SYS-409002 IdempotencyProcessingException</li>
 *       <li>4.4 status=FAILED 且 retryOnFailed → CAS 回到 PROCESSING 重新执行</li>
 *     </ul>
 *   </li>
 *   <li>执行业务事务 (业务方法自管理 @Transactional)</li>
 *   <li>业务成功后写 SUCCESS + response_snapshot — REQUIRES_NEW 事务 (afterCommit 语义)</li>
 *   <li>业务失败后写 FAILED + error_code — REQUIRES_NEW 事务 (不可因业务回滚丢失)</li>
 *   <li>释放 Redis 短锁 (仅释放自己的锁，CAS 校验 lockValue)</li>
 * </ol>
 *
 * <p>事务边界: PROCESSING 插入、SUCCESS/FAILED 更新均使用 REQUIRES_NEW 独立事务，
 * 确保幂等记录生命周期与业务事务解耦——业务回滚不会丢失幂等记录 (98 号文档第 7 步要求)。
 *
 * <p>快照约束: response_snapshot 只保存响应摘要，序列化前截断到 8KB，
 * 不保存敏感字段、附件内容、AI Prompt 或大对象 (98 号文档阻断清单)。
 */
@Service
public class IdempotencyService {

    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private static final String LOCK_PREFIX = "yutong:idem:";
    private static final long MIN_LOCK_TTL_SECONDS = 30L;
    private static final long MAX_LOCK_TTL_SECONDS = 120L;
    /** 快照最大长度 (8KB)，超过截断为 null 避免大对象落库。 */
    private static final int SNAPSHOT_MAX_LENGTH = 8192;
    /** 默认 expire_time 过期时长 (24 小时，到期可由补偿任务清理)。 */
    private static final long DEFAULT_EXPIRE_HOURS = 24L;

    private final IdempotencyRecordMapper mapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNewTx;

    public IdempotencyService(IdempotencyRecordMapper mapper,
                              StringRedisTemplate redis,
                              ObjectMapper objectMapper,
                              PlatformTransactionManager txManager) {
        this.mapper = mapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.requiresNewTx = new TransactionTemplate(txManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 执行幂等业务。完整实现 98 号文档 8 步流程。
     *
     * @param request      幂等请求参数 (key/resourceType/resourceId/action/hash/ttl/retry)
     * @param business     业务 Supplier，返回值会被序列化为 snapshot
     * @param responseType 业务返回类型，用于重放时反序列化 snapshot
     * @return IdempotentResult 包含 data 和 replay 标志
     * @throws IdempotencyProcessingException 同 key 正在处理中 (SYS-409002)
     * @throws IdempotencyConflictException    同 key 不同 hash (SYS-409003)
     * @throws BusinessConflictException       同 key 已失败且不允许重试 (SYS-409004)
     */
    public <T> IdempotentResult<T> execute(IdempotentRequest request,
                                           Supplier<T> business,
                                           Class<T> responseType) {
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            // 无 Idempotency-Key 直接透传业务，不启用幂等
            return IdempotentResult.success(business.get());
        }

        String tenantId = safe(CurrentUserContext.getTenantId());
        String userId = safe(CurrentUserContext.getUserId());
        String traceId = TraceContext.getTraceId();
        long lockTtl = clampLockTtl(request.ttlSeconds());

        // Step 2: Redis SETNX 短锁
        String lockKey = LOCK_PREFIX + tenantId + ":" + userId + ":" + request.idempotencyKey();
        String lockValue = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue()
                .setIfAbsent(lockKey, lockValue, lockTtl, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(acquired)) {
            // 短锁已被占 (同 key 并发)，按 PROCESSING 处理
            log.warn("幂等短锁已被占 key={} tenant={} user={}", request.idempotencyKey(), tenantId, userId);
            throw new IdempotencyProcessingException("幂等键正在处理中: " + request.idempotencyKey());
        }

        try {
            // Step 3: 插入 PROCESSING 记录 (REQUIRES_NEW 事务)
            IdempotencyRecord record = buildProcessingRecord(request, tenantId, traceId, lockTtl);
            try {
                requiresNewTx.executeWithoutResult(status -> {
                    record.setId(IdGenerator.nextId());
                    mapper.insert(record);
                });
            } catch (DuplicateKeyException dup) {
                // Step 4: 唯一键冲突，转查询分支
                log.info("幂等键冲突，转入查询分支 key={} tenant={}", request.idempotencyKey(), tenantId);
                return handleDuplicateKey(request, tenantId, traceId, business, responseType);
            }

            // Step 5: 执行业务事务 (业务方法自管理 @Transactional)
            T data;
            try {
                data = business.get();
            } catch (Throwable ex) {
                // Step 7: 业务失败，写 FAILED + error_code (REQUIRES_NEW，不可因业务回滚丢失)
                markFinalStatus(record.getId(), STATUS_FAILED, extractErrorCode(ex), null);
                throw ex;
            }

            // Step 6: 业务成功，写 SUCCESS + snapshot (REQUIRES_NEW，afterCommit 语义)
            String snapshot = serializeSnapshotSafely(data);
            markFinalStatus(record.getId(), STATUS_SUCCESS, "0", snapshot);
            return IdempotentResult.success(data);
        } finally {
            // Step 8: 释放 Redis 短锁 (CAS 校验 lockValue，避免误删别人的锁)
            releaseLockSafely(lockKey, lockValue);
        }
    }

    /**
     * Step 4 处理分支：唯一键冲突时查询已存在记录并按 status 分流。
     */
    private <T> IdempotentResult<T> handleDuplicateKey(IdempotentRequest request,
                                                       String tenantId, String traceId,
                                                       Supplier<T> business,
                                                       Class<T> responseType) {
        IdempotencyRecord existing = findExisting(request, tenantId);
        if (existing == null) {
            // 极端情况：冲突后又查不到 (可能已被清理)，回退到直接执行业务
            log.warn("幂等键冲突但查询不到记录，回退执行 key={} tenant={}", request.idempotencyKey(), tenantId);
            return IdempotentResult.success(business.get());
        }

        // 4.1 request_hash 不同 → SYS-409003
        if (!request.requestHash().equals(existing.getRequestHash())) {
            log.warn("幂等键 hash 冲突 key={} expect={} actual={}",
                    request.idempotencyKey(), request.requestHash(), existing.getRequestHash());
            throw new IdempotencyConflictException(
                    "Idempotency-Key 已被使用且请求体不一致: " + request.idempotencyKey());
        }

        String status = existing.getStatus();
        // 4.2 status=SUCCESS → 返回 response_snapshot 重放
        if (STATUS_SUCCESS.equals(status)) {
            T replayed = deserializeSnapshotSafely(existing.getResponseSnapshot(), responseType);
            log.info("幂等键重放成功 key={} recordId={}", request.idempotencyKey(), existing.getId());
            return IdempotentResult.replay(replayed);
        }

        // 4.3 status=PROCESSING 且未超时 → SYS-409002
        if (STATUS_PROCESSING.equals(status)) {
            if (existing.getLockedUntil() != null
                    && existing.getLockedUntil().isAfter(OffsetDateTime.now())) {
                throw new IdempotencyProcessingException(
                        "幂等键正在处理中: " + request.idempotencyKey());
            }
            // PROCESSING 但已超时：视为 stale，允许重新抢占 (CAS)
            log.info("幂等记录 PROCESSING 已超时，尝试重新抢占 recordId={}", existing.getId());
            if (tryReclaimProcessing(existing.getId(), existing.getVersion(), lockTtlFromRecord(existing))) {
                return reExecuteBusiness(existing.getId(), request, traceId, business);
            }
            // CAS 失败：他人已抢占，仍按 PROCESSING 拒绝
            throw new IdempotencyProcessingException(
                    "幂等键正在处理中: " + request.idempotencyKey());
        }

        // 4.4 status=FAILED
        if (STATUS_FAILED.equals(status)) {
            if (request.retryOnFailed()) {
                log.info("幂等记录 FAILED 允许重试，重新抢占 recordId={}", existing.getId());
                if (tryReclaimProcessing(existing.getId(), existing.getVersion(), lockTtlFromRecord(existing))) {
                    return reExecuteBusiness(existing.getId(), request, traceId, business);
                }
                // CAS 失败：他人已抢占
                throw new IdempotencyProcessingException(
                        "幂等键正在处理中: " + request.idempotencyKey());
            }
            // 不允许重试：拒绝同 key 重试
            throw new BusinessConflictException(
                    "幂等键已失败且不允许重试，请使用新 Idempotency-Key: " + request.idempotencyKey());
        }

        // 未知 status，兜底拒绝
        throw new BusinessConflictException("幂等记录状态异常: " + status);
    }

    /**
     * 重新执行业务 (FAILED 重试 或 PROCESSING 超时抢占后)。
     * 复用同一 recordId，写 SUCCESS/FAILED。
     */
    private <T> IdempotentResult<T> reExecuteBusiness(String recordId,
                                                      IdempotentRequest request,
                                                      String traceId,
                                                      Supplier<T> business) {
        T data;
        try {
            data = business.get();
        } catch (Throwable ex) {
            markFinalStatus(recordId, STATUS_FAILED, extractErrorCode(ex), null);
            throw ex;
        }
        String snapshot = serializeSnapshotSafely(data);
        markFinalStatus(recordId, STATUS_SUCCESS, "0", snapshot);
        return IdempotentResult.success(data);
    }

    /**
     * 查询已存在的幂等记录。resource_id 为空时匹配 NULL 或空串 (对齐 DB COALESCE 唯一索引)。
     */
    private IdempotencyRecord findExisting(IdempotentRequest request, String tenantId) {
        LambdaQueryWrapper<IdempotencyRecord> wrapper = new LambdaQueryWrapper<IdempotencyRecord>()
                .eq(IdempotencyRecord::getTenantId, tenantId)
                .eq(IdempotencyRecord::getResourceType, request.resourceType())
                .eq(IdempotencyRecord::getAction, request.action())
                .eq(IdempotencyRecord::getIdempotencyKey, request.idempotencyKey());
        if (isBlank(request.resourceId())) {
            wrapper.and(w -> w.isNull(IdempotencyRecord::getResourceId)
                    .or().eq(IdempotencyRecord::getResourceId, ""));
        } else {
            wrapper.eq(IdempotencyRecord::getResourceId, request.resourceId());
        }
        wrapper.last("LIMIT 1");
        return mapper.selectOne(wrapper);
    }

    /**
     * CAS 回到 PROCESSING 状态 (用于 4.3 超时抢占 / 4.4 FAILED 重试)。
     * 通过 version 乐观锁确保并发安全。
     */
    private boolean tryReclaimProcessing(String recordId, Integer currentVersion, long lockTtl) {
        try {
            return requiresNewTx.execute(status -> {
                IdempotencyRecord update = new IdempotencyRecord();
                update.setId(recordId);
                update.setStatus(STATUS_PROCESSING);
                update.setLockedUntil(OffsetDateTime.now().plusSeconds(lockTtl));
                update.setVersion(currentVersion);  // CAS 条件
                int affected = mapper.updateById(update);
                return affected == 1;
            });
        } catch (Exception e) {
            log.warn("幂等记录 CAS 重新抢占失败 recordId={} - {}", recordId, e.getMessage());
            return false;
        }
    }

    /**
     * Step 6/7: 写终态 (SUCCESS/FAILED) + response_code + snapshot (REQUIRES_NEW 事务)。
     * 使用 updateById 走 BaseEntity.version 乐观锁自动 +1。
     */
    private void markFinalStatus(String recordId, String status, String responseCode, String snapshot) {
        try {
            requiresNewTx.executeWithoutResult(statusInner -> {
                IdempotencyRecord update = new IdempotencyRecord();
                update.setId(recordId);
                update.setStatus(status);
                update.setResponseCode(responseCode);
                update.setResponseSnapshot(snapshot);
                update.setLockedUntil(null);
                mapper.updateById(update);
            });
        } catch (Exception e) {
            // 终态写入失败不能阻断业务结果返回，仅记录错误日志
            // (98 号文档第 7 步：不可因业务回滚丢失幂等记录——此处为反向情况：终态写入失败不破坏业务)
            log.error("幂等记录终态写入失败 recordId={} status={} - {}",
                    recordId, status, e.getMessage(), e);
        }
    }

    /**
     * 构建 PROCESSING 记录 (Step 3 插入前)。
     */
    private IdempotencyRecord buildProcessingRecord(IdempotentRequest request,
                                                    String tenantId, String traceId, long lockTtl) {
        OffsetDateTime now = OffsetDateTime.now();
        IdempotencyRecord record = new IdempotencyRecord();
        // tenantId/createdBy/createdTime/deleted/version 由 MetaObjectHandler 自动填充
        record.setTenantId(tenantId);
        record.setResourceType(request.resourceType());
        record.setResourceId(isBlank(request.resourceId()) ? null : request.resourceId());
        record.setAction(request.action());
        record.setIdempotencyKey(request.idempotencyKey());
        record.setRequestHash(request.requestHash());
        record.setStatus(STATUS_PROCESSING);
        record.setLockedUntil(now.plusSeconds(lockTtl));
        record.setExpireTime(now.plusHours(DEFAULT_EXPIRE_HOURS));
        record.setTraceId(traceId);
        return record;
    }

    /**
     * 释放 Redis 短锁 (CAS 校验 lockValue)。
     * 仅当当前持有者是自己时才删除，避免误删他人的锁 (因 TTL 过期后他人可能已抢占)。
     */
    protected void releaseLockSafely(String lockKey, String lockValue) {
        try {
            String current = redis.opsForValue().get(lockKey);
            if (lockValue.equals(current)) {
                redis.delete(lockKey);
            }
        } catch (Exception e) {
            log.warn("释放幂等短锁失败 key={} - {}", lockKey, e.getMessage());
        }
    }

    /**
     * 序列化业务返回值为快照 JSON。失败或超长返回 null。
     */
    private String serializeSnapshotSafely(Object data) {
        if (data == null) return null;
        try {
            String json = objectMapper.writeValueAsString(data);
            if (json.length() > SNAPSHOT_MAX_LENGTH) {
                log.warn("幂等快照超长 ({} chars)，截断为 null 避免大对象落库", json.length());
                return null;
            }
            return json;
        } catch (Exception e) {
            log.warn("幂等快照序列化失败 type={} - {}", data.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化快照为业务返回类型。失败返回 null (重放失败时业务返回 null)。
     */
    private <T> T deserializeSnapshotSafely(String snapshot, Class<T> responseType) {
        if (snapshot == null || snapshot.isBlank()) return null;
        try {
            return objectMapper.readValue(snapshot, responseType);
        } catch (Exception e) {
            log.warn("幂等快照反序列化失败 type={} - {}", responseType.getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * 从异常提取错误码。BusinessException 取 errorCode.code()，其他取类名。
     */
    private String extractErrorCode(Throwable ex) {
        if (ex instanceof BusinessException be) {
            return be.errorCode().code();
        }
        return ex.getClass().getSimpleName();
    }

    /**
     * 从记录中恢复 lockTtl (用于 4.3/4.4 重新抢占时计算新的 locked_until)。
     */
    private long lockTtlFromRecord(IdempotencyRecord record) {
        if (record.getLockedUntil() != null) {
            long seconds = Duration.between(OffsetDateTime.now(), record.getLockedUntil()).getSeconds();
            if (seconds > 0) return clampLockTtl(seconds);
        }
        return MIN_LOCK_TTL_SECONDS;
    }

    /**
     * 计算 SHA-256 hash (hex 截断 128 字符)。供 IdempotentAspect 调用。
     */
    public static String computeRequestHash(Object... args) {
        if (args == null || args.length == 0) return "empty";
        try {
            StringBuilder sb = new StringBuilder();
            for (Object arg : args) {
                if (arg == null) {
                    sb.append("null|");
                } else {
                    sb.append(arg.getClass().getName()).append(':').append(arg.hashCode()).append('|');
                }
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 标准算法，不应缺失
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 扫描超时的 PROCESSING 记录 (供补偿任务使用)。
     */
    public List<IdempotencyRecord> scanStaleProcessing(Duration stale) {
        OffsetDateTime threshold = OffsetDateTime.now().minus(stale);
        LambdaQueryWrapper<IdempotencyRecord> wrapper = new LambdaQueryWrapper<IdempotencyRecord>()
                .eq(IdempotencyRecord::getStatus, STATUS_PROCESSING)
                .lt(IdempotencyRecord::getLockedUntil, threshold);
        return mapper.selectList(wrapper);
    }

    private long clampLockTtl(long ttl) {
        return Math.max(MIN_LOCK_TTL_SECONDS, Math.min(ttl, MAX_LOCK_TTL_SECONDS));
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
