package com.yutong.sample.extsync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.id.IdGenerator;
import com.yutong.sample.extsync.domain.ExtSyncError;
import com.yutong.sample.extsync.domain.ExtSyncRecord;
import com.yutong.sample.extsync.domain.ExtSyncTask;
import com.yutong.sample.extsync.domain.ExtSystem;
import com.yutong.sample.extsync.mapper.ExtSyncErrorMapper;
import com.yutong.sample.extsync.mapper.ExtSyncRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 外部接口同步执行器。设计来源: 35-样例业务矩阵扩展设计 P2 外部接口同步。
 *
 * <p>核心 6 项能力实现:
 * <ol>
 *   <li>HTTP Client 适配: RestClient + 超时配置 (connect/read timeout) </li>
 *   <li>签名鉴权: HMAC-SHA256, 调用 ExtSignatureService 注入 4 个 Header</li>
 *   <li>失败重试: 按系统配置 max_retry_count + 退避 retry_backoff_ms * 2^attempt</li>
 *   <li>幂等写入: (task_id, business_key) 唯一索引兜底, 重复入库抛 DuplicateKeyException</li>
 *   <li>死信/错误队列: 失败写入 ext_sync_error 表, retry_count 达上限进入 DEAD_LETTER</li>
 *   <li>同步监控: ext_sync_record 写入 total/success/failed/duration 统计</li>
 * </ol>
 */
@Service
public class ExtSyncExecutor {

    private static final Logger log = LoggerFactory.getLogger(ExtSyncExecutor.class);
    private static final DateTimeFormatter BATCH_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int RESPONSE_SNAPSHOT_MAX = 16 * 1024;
    private static final int PAYLOAD_MAX = 8 * 1024;

    private final ExtSyncRecordMapper recordMapper;
    private final ExtSyncErrorMapper errorMapper;
    private final ExtSignatureService signatureService;
    private final ObjectMapper objectMapper;

    public ExtSyncExecutor(ExtSyncRecordMapper recordMapper,
                           ExtSyncErrorMapper errorMapper,
                           ExtSignatureService signatureService,
                           ObjectMapper objectMapper) {
        this.recordMapper = recordMapper;
        this.errorMapper = errorMapper;
        this.signatureService = signatureService;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行一次同步任务 (同步调用, 含重试 + 错误队列写入)。
     *
     * @param task       同步任务
     * @param system     关联外部系统
     * @param triggerType 触发方式 MANUAL / SCHEDULED
     * @return 同步记录 (已写入数据库)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExtSyncRecord execute(ExtSyncTask task, ExtSystem system, String triggerType) {
        OffsetDateTime now = OffsetDateTime.now();
        String recordNo = "EXT" + now.format(BATCH_FMT) + UUID.randomUUID().toString().substring(0, 6);
        String batchNo = now.format(BATCH_FMT) + UUID.randomUUID().toString().substring(0, 4);

        ExtSyncRecord record = new ExtSyncRecord();
        record.setId(IdGenerator.nextId());
        record.setRecordNo(recordNo);
        record.setTaskId(task.getId());
        record.setSystemId(system.getId());
        record.setBatchNo(batchNo);
        record.setStatus(ExtSyncRecord.STATUS_RUNNING);
        record.setTriggerType(triggerType == null ? ExtSyncRecord.TRIGGER_MANUAL : triggerType);
        record.setTotalCount(0);
        record.setSuccessCount(0);
        record.setFailedCount(0);
        record.setStartedTime(now);
        recordMapper.insert(record);

        String url = buildUrl(system.getEndpoint(), task.getSourceApi());
        String requestBody = task.getRequestTemplate();
        String method = task.getHttpMethod() == null ? ExtSyncTask.METHOD_GET : task.getHttpMethod();

        log.info("ext sync start: recordNo={} task={} system={} url={} method={}",
                recordNo, task.getTaskCode(), system.getSystemCode(), url, method);

        int maxRetry = system.getMaxRetryCount() == null ? 3 : system.getMaxRetryCount();
        int backoffMs = system.getRetryBackoffMs() == null ? 1000 : system.getRetryBackoffMs();

        RestClient client = buildRestClient(system);
        int attempt = 0;
        Exception lastError = null;
        Integer httpStatus = null;
        String responseBody = null;

        while (attempt <= maxRetry) {
            try {
                if (attempt > 0) {
                    long backoff = backoffMs * (1L << (attempt - 1));
                    log.info("ext sync retry: recordNo={} attempt={} backoff={}ms", recordNo, attempt, backoff);
                    Thread.sleep(backoff);
                }

                HttpHeaders headers = buildAuthHeaders(method, task.getSourceApi(), requestBody, system);

                RestClient.ResponseSpec responseSpec = client.method(HttpMethod.valueOf(method))
                        .uri(url)
                        .headers(h -> h.addAll(headers))
                        .retrieve();

                String body = responseSpec.body(String.class);
                httpStatus = 200;
                responseBody = body;
                log.info("ext sync http ok: recordNo={} attempt={} bodyLen={}", recordNo, attempt, body == null ? 0 : body.length());

                int[] stats = processResponse(body, task, record);
                record.setTotalCount(stats[0]);
                record.setSuccessCount(stats[1]);
                record.setFailedCount(stats[2]);
                record.setHttpStatus(httpStatus);
                record.setRequestSnapshot(truncate("[" + method + "] " + url + (requestBody == null ? "" : "\n" + requestBody), RESPONSE_SNAPSHOT_MAX));
                record.setResponseSnapshot(truncate(body, RESPONSE_SNAPSHOT_MAX));

                if (stats[2] > 0 && stats[1] > 0) {
                    record.setStatus(ExtSyncRecord.STATUS_PARTIAL);
                } else if (stats[2] > 0) {
                    record.setStatus(ExtSyncRecord.STATUS_FAILED);
                } else {
                    record.setStatus(ExtSyncRecord.STATUS_SUCCESS);
                }
                record.setFinishedTime(OffsetDateTime.now());
                record.setDurationMs(Duration.between(now, record.getFinishedTime()).toMillis());
                recordMapper.updateById(record);

                log.info("ext sync done: recordNo={} status={} total={} success={} failed={} duration={}ms",
                        recordNo, record.getStatus(), stats[0], stats[1], stats[2], record.getDurationMs());
                return record;

            } catch (Exception e) {
                attempt++;
                lastError = e;
                log.warn("ext sync attempt failed: recordNo={} attempt={}/{} err={}",
                        recordNo, attempt, maxRetry + 1, e.getMessage());
                if (attempt > maxRetry) {
                    break;
                }
            }
        }

        record.setStatus(ExtSyncRecord.STATUS_FAILED);
        record.setHttpStatus(httpStatus);
        record.setRequestSnapshot(truncate("[" + method + "] " + url, RESPONSE_SNAPSHOT_MAX));
        record.setResponseSnapshot(truncate(responseBody, RESPONSE_SNAPSHOT_MAX));
        record.setErrorMessage(lastError == null ? "unknown" : truncate(lastError.getMessage(), 1024));
        record.setFinishedTime(OffsetDateTime.now());
        record.setDurationMs(Duration.between(now, record.getFinishedTime()).toMillis());
        recordMapper.updateById(record);

        enqueueError(record, task, "HTTP_FAILED",
                lastError == null ? "unknown" : lastError.getMessage(), httpStatus);
        log.error("ext sync final failed: recordNo={} duration={}ms err={}",
                recordNo, record.getDurationMs(), record.getErrorMessage());
        return record;
    }

    /**
     * 解析响应并按业务键幂等写入 (此处仅模拟写入到 ext_sync_error 表的失败场景,
     * 实际生产环境会写入 target_table; v1.0 样例验证 ext_sync_record 统计)。
     *
     * @return [total, success, failed]
     */
    private int[] processResponse(String body, ExtSyncTask task, ExtSyncRecord record) {
        if (body == null || body.isBlank()) {
            return new int[]{0, 0, 0};
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode dataNode;
            if (root.isArray()) {
                dataNode = root;
            } else if (root.has("data") && root.get("data").isArray()) {
                dataNode = root.get("data");
            } else if (root.has("items") && root.get("items").isArray()) {
                dataNode = root.get("items");
            } else {
                dataNode = objectMapper.createArrayNode().add(root);
            }

            String keyField = task.getBusinessKeyField() == null ? "id" : task.getBusinessKeyField();
            int total = dataNode.size();
            int success = 0;
            int failed = 0;

            for (JsonNode item : dataNode) {
                JsonNode keyNode = item.get(keyField);
                if (keyNode == null || keyNode.isNull()) {
                    failed++;
                    enqueueError(record, task, "BUSINESS_KEY_MISSING",
                            "missing field: " + keyField, null);
                    continue;
                }
                String businessKey = keyNode.asText();
                try {
                    if (writeBusinessRecord(task, businessKey, item.toString())) {
                        success++;
                    } else {
                        success++;
                    }
                } catch (DuplicateKeyException dup) {
                    log.info("ext sync idempotent skip: task={} bizKey={}", task.getTaskCode(), businessKey);
                    success++;
                } catch (Exception e) {
                    failed++;
                    enqueueError(record, task, businessKey,
                            "WRITE_FAILED: " + e.getMessage(), item.toString(), null);
                }
            }
            return new int[]{total, success, failed};
        } catch (Exception e) {
            log.error("processResponse parse failed: record={}", record.getRecordNo(), e);
            enqueueError(record, task, "RESPONSE_PARSE_FAILED", e.getMessage(), null);
            return new int[]{0, 0, 1};
        }
    }

    /**
     * 写入业务记录 (v1.0 样例: 仅记录到日志, 实际生产会写入 target_table)。
     * 返回 true 表示新写入, false 表示已存在 (幂等)。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean writeBusinessRecord(ExtSyncTask task, String businessKey, String payload) {
        log.info("ext sync writeBusinessRecord: task={} bizKey={} payloadLen={}",
                task.getTaskCode(), businessKey, payload == null ? 0 : payload.length());
        return true;
    }

    /**
     * 写入错误明细 (死信队列)。
     * 幂等保证: (task_id, business_key) 未解决错误唯一索引兜底, 重复入队被拦截。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueError(ExtSyncRecord record, ExtSyncTask task,
                             String businessKey, String errorMessage, String payload, Integer httpStatus) {
        ExtSyncError error = new ExtSyncError();
        error.setId(IdGenerator.nextId());
        error.setRecordId(record.getId());
        error.setTaskId(task.getId());
        error.setBusinessKey(businessKey);
        error.setPayload(truncate(payload, PAYLOAD_MAX));
        error.setErrorCode("EXT_SYNC_ERROR");
        error.setErrorMessage(truncate(errorMessage, 1024));
        error.setHttpStatus(httpStatus);
        error.setStatus(ExtSyncError.STATUS_PENDING);
        error.setRetryCount(0);
        try {
            errorMapper.insert(error);
            log.info("ext sync enqueueError: record={} bizKey={} status={}",
                    record.getRecordNo(), businessKey, error.getStatus());
        } catch (DuplicateKeyException dup) {
            log.info("ext sync enqueueError idempotent skip: task={} bizKey={}",
                    task.getTaskCode(), businessKey);
        }
    }

    private void enqueueError(ExtSyncRecord record, ExtSyncTask task,
                              String errorCode, String errorMessage, Integer httpStatus) {
        enqueueError(record, task, errorCode, errorMessage, null, httpStatus);
    }

    private String buildUrl(String endpoint, String sourceApi) {
        if (endpoint.endsWith("/") && sourceApi.startsWith("/")) {
            return endpoint + sourceApi.substring(1);
        }
        if (!endpoint.endsWith("/") && !sourceApi.startsWith("/")) {
            return endpoint + "/" + sourceApi;
        }
        return endpoint + sourceApi;
    }

    private HttpHeaders buildAuthHeaders(String method, String path, String body, ExtSystem system) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (ExtSystem.AUTH_HMAC_SHA256.equals(system.getAuthType())) {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String nonce = UUID.randomUUID().toString().replace("-", "");
            String signature = signatureService.sign(method, path, timestamp, nonce, body, system);
            headers.set("X-Ext-Access-Key", extractAccessKey(system));
            headers.set("X-Ext-Timestamp", timestamp);
            headers.set("X-Ext-Nonce", nonce);
            headers.set("X-Ext-Signature", signature);
        } else if (ExtSystem.AUTH_API_KEY.equals(system.getAuthType())) {
            headers.set("X-Api-Key", extractAccessKey(system));
        } else if (ExtSystem.AUTH_BEARER_TOKEN.equals(system.getAuthType())) {
            headers.setBearerAuth(extractAccessKey(system));
        }
        return headers;
    }

    private String extractAccessKey(ExtSystem system) {
        try {
            if (system.getCredentials() == null || system.getCredentials().isBlank()) {
                return "";
            }
            JsonNode node = objectMapper.readTree(system.getCredentials());
            JsonNode key = node.get("accessKey");
            if (key == null) {
                key = node.get("apiKey");
            }
            if (key == null) {
                key = node.get("token");
            }
            return key == null ? "" : key.asText();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.EXT_REQUEST_INVALID,
                    "外部系统 credentials 解析失败: " + e.getMessage());
        }
    }

    private RestClient buildRestClient(ExtSystem system) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(system.getConnectTimeout() == null ? 5 : system.getConnectTimeout()) ;
        factory.setReadTimeout(system.getReadTimeout() == null ? 15 : system.getReadTimeout());
        return RestClient.builder().requestFactory(factory).build();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
