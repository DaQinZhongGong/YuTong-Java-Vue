package com.yutong.sample.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.sample.payment.domain.PayCallbackLog;
import com.yutong.sample.payment.domain.PayOrder;
import com.yutong.sample.payment.domain.PayReconciliation;
import com.yutong.sample.payment.domain.PayRefundOrder;
import com.yutong.sample.payment.dto.CallbackRequest;
import com.yutong.sample.payment.dto.CreateOrderRequest;
import com.yutong.sample.payment.dto.PaymentStatsVO;
import com.yutong.sample.payment.dto.ReconciliationImportRequest;
import com.yutong.sample.payment.dto.RefundRequest;
import com.yutong.sample.payment.mapper.PayCallbackLogMapper;
import com.yutong.sample.payment.mapper.PayOrderMapper;
import com.yutong.sample.payment.mapper.PayReconciliationMapper;
import com.yutong.sample.payment.mapper.PayRefundOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付订单应用服务。设计来源: 35-样例业务矩阵扩展设计 P2 支付订单 (GA2-41)。
 *
 * <p>核心 6 项能力实现:
 * <ol>
 *   <li>支付单状态机: createOrder → PENDING; handleCallback → PAID/FAILED; cancel → CANCELLED; refund → REFUNDING/REFUNDED; close → CLOSED</li>
 *   <li>第三方回调验签: PaymentSignUtil HMAC-SHA256 (签名串 = orderNo + channel + amount + timestamp)</li>
 *   <li>回调幂等: pay_callback_log.idempotency_key 唯一索引 + DuplicateKeyException 兜底</li>
 *   <li>对账文件导入: importReconciliation 解析 CSV/JSON, 比对本地与第三方, 输出 matched/mismatched/missing/extra</li>
 *   <li>金额精度: 全程使用 Long (分), 避免 BigDecimal 浮点精度问题</li>
 *   <li>安全审计: Controller 层 @Auditable AOP 写入 sys_operation_log</li>
 * </ol>
 */
@Service
public class PaymentOpsApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentOpsApplicationService.class);
    private static final DateTimeFormatter ORDER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter REFUND_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final long DEFAULT_EXPIRED_MINUTES = 30L;

    /** 支付资源编码，对齐 permissions.yaml biz:payment:* 命名。 */
    public static final String RESOURCE_CODE = "biz:payment";

    private final PayOrderMapper orderMapper;
    private final PayCallbackLogMapper callbackMapper;
    private final PayRefundOrderMapper refundMapper;
    private final PayReconciliationMapper reconMapper;
    private final PaymentSignUtil signUtil;
    private final ObjectMapper objectMapper;
    private final DataScopeResolver dataScopeResolver;

    public PaymentOpsApplicationService(PayOrderMapper orderMapper,
                                        PayCallbackLogMapper callbackMapper,
                                        PayRefundOrderMapper refundMapper,
                                        PayReconciliationMapper reconMapper,
                                        PaymentSignUtil signUtil,
                                        ObjectMapper objectMapper,
                                        DataScopeResolver dataScopeResolver) {
        this.orderMapper = orderMapper;
        this.callbackMapper = callbackMapper;
        this.refundMapper = refundMapper;
        this.reconMapper = reconMapper;
        this.signUtil = signUtil;
        this.objectMapper = objectMapper;
        this.dataScopeResolver = dataScopeResolver;
    }

    /**
     * GA2-DS: 对 LambdaQueryWrapper 追加 DataScope 过滤条件。
     * - ALL/TENANT: 无附加条件 (admin)
     * - 其它(SELF/DEPT/CUSTOM/NONE 等): created_by = currentUserId，userId 缺失时安全降级 1=0
     */
    private void applyDataScope(LambdaQueryWrapper<?> wrapper, DataScope scope) {
        if (scope == null) {
            return;
        }
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) {
            return;
        }
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.apply("created_by = {0}", userId);
    }

    // ==================== 1. 支付单 (状态机) ====================

    /**
     * 创建支付单 (幂等键防重复)。
     * 状态机: 初始 PENDING。
     */
    @Transactional
    public PayOrder createOrder(CreateOrderRequest request) {
        validateChannel(request.getChannel());
        if (request.getAmount() == null || request.getAmount() <= 0) {
            throw new BusinessException(ErrorCode.PAY_AMOUNT_INVALID, "支付金额必须大于 0");
        }
        // 幂等键查重 (业务方携带同一 idempotencyKey 重复调用, 返回已有订单)
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            PayOrder existing = orderMapper.selectOne(new LambdaQueryWrapper<PayOrder>()
                    .eq(PayOrder::getIdempotencyKey, request.getIdempotencyKey()));
            if (existing != null) {
                log.info("createOrder idempotent hit: idemKey={} orderNo={}", request.getIdempotencyKey(), existing.getOrderNo());
                return existing;
            }
        }

        PayOrder order = new PayOrder();
        order.setId(IdGenerator.nextId());
        order.setOrderNo(generateOrderNo());
        order.setBizType(request.getBizType());
        order.setBizId(request.getBizId());
        order.setChannel(request.getChannel());
        order.setAmount(request.getAmount());
        order.setCurrency(request.getCurrency() == null ? "CNY" : request.getCurrency());
        order.setSubject(request.getSubject());
        order.setPayerId(request.getPayerId());
        order.setStatus(PayOrder.STATUS_PENDING);
        order.setExpiredTime(OffsetDateTime.now().plusMinutes(DEFAULT_EXPIRED_MINUTES));
        order.setIdempotencyKey(request.getIdempotencyKey());
        // 渠道凭证: 优先使用业务方传入, 未传时填充 Mock 渠道默认凭证 (便于样例测试)
        order.setChannelConfig(resolveChannelConfig(request.getChannel(), request.getChannelConfig()));
        order.setExtraParams(request.getExtraParams());
        order.setRemark(request.getRemark());
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // 幂等键唯一索引兜底 (并发场景)
            if (request.getIdempotencyKey() != null) {
                PayOrder existing = orderMapper.selectOne(new LambdaQueryWrapper<PayOrder>()
                        .eq(PayOrder::getIdempotencyKey, request.getIdempotencyKey()));
                if (existing != null) {
                    return existing;
                }
            }
            throw new BusinessException(ErrorCode.PAY_ORDER_NO_DUPLICATE, "支付单号已存在: " + order.getOrderNo());
        }
        log.info("createOrder: orderNo={} channel={} amount={}", order.getOrderNo(), order.getChannel(), order.getAmount());
        return order;
    }

    /**
     * 用户取消支付 (PENDING → CANCELLED)。
     */
    @Transactional
    public PayOrder cancelOrder(String orderId) {
        PayOrder order = getOrder(orderId);
        if (!PayOrder.STATUS_PENDING.equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.PAY_ORDER_STATUS_NOT_ALLOWED,
                    "仅 PENDING 状态可取消, 当前: " + order.getStatus());
        }
        order.setStatus(PayOrder.STATUS_CANCELLED);
        order.setClosedTime(OffsetDateTime.now());
        orderMapper.updateById(order);
        log.info("cancelOrder: orderNo={} -> CANCELLED", order.getOrderNo());
        return order;
    }

    /**
     * 关闭订单 (PAID/REFUNDED → CLOSED, 用于归档)。
     */
    @Transactional
    public PayOrder closeOrder(String orderId) {
        PayOrder order = getOrder(orderId);
        if (PayOrder.STATUS_PAID.equals(order.getStatus()) || PayOrder.STATUS_REFUNDED.equals(order.getStatus())) {
            order.setStatus(PayOrder.STATUS_CLOSED);
            order.setClosedTime(OffsetDateTime.now());
            orderMapper.updateById(order);
            log.info("closeOrder: orderNo={} -> CLOSED", order.getOrderNo());
        } else {
            throw new BusinessException(ErrorCode.PAY_ORDER_STATUS_NOT_ALLOWED,
                    "仅 PAID/REFUNDED 状态可关闭, 当前: " + order.getStatus());
        }
        return order;
    }

    public PayOrder getOrder(String id) {
        PayOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new ResourceNotFoundException(ErrorCode.PAY_ORDER_NOT_FOUND);
        }
        return order;
    }

    public Page<PayOrder> pageOrders(int pageNo, int pageSize, String orderNo, String channel,
                                     String status, String bizType) {
        Page<PayOrder> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<PayOrder> wrapper = new LambdaQueryWrapper<PayOrder>()
                .orderByDesc(PayOrder::getCreatedTime);
        if (orderNo != null && !orderNo.isBlank()) {
            wrapper.eq(PayOrder::getOrderNo, orderNo);
        }
        if (channel != null && !channel.isBlank()) {
            wrapper.eq(PayOrder::getChannel, channel);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(PayOrder::getStatus, status);
        }
        if (bizType != null && !bizType.isBlank()) {
            wrapper.eq(PayOrder::getBizType, bizType);
        }
        // GA2-DS: 接入 DataScope 过滤，admin(ALL/TENANT) 放行，非 admin 按 created_by 过滤
        applyDataScope(wrapper, dataScopeResolver.resolve(RESOURCE_CODE));
        return orderMapper.selectPage(page, wrapper);
    }

    // ==================== 2. 第三方回调 (验签 + 幂等) ====================

    /**
     * 处理第三方支付回调。
     *
     * <p>核心流程:
     * <ol>
     *   <li>幂等键查重 (orderNo + callbackAction + channelTradeNo), 已处理则 IGNORED</li>
     *   <li>查询支付单, 不存在则记录 FAILED 日志并返回</li>
     *   <li>HMAC-SHA256 验签 (使用渠道 secretKey), 失败则记录 FAILED 日志, 返回 IGNORED</li>
     *   <li>状态机流转: PENDING → PAID (PAY_SUCCESS) / PENDING → FAILED (PAY_FAIL)</li>
     *   <li>记录回调日志, PROCESSED</li>
     * </ol>
     */
    @Transactional
    public PayCallbackLog handleCallback(CallbackRequest request) {
        String idempotencyKey = buildCallbackIdempotencyKey(
                request.getOrderNo(), request.getCallbackAction(), request.getChannelTradeNo());

        // 幂等查重 (同 orderNo + callbackAction + channelTradeNo 已处理过, 直接 IGNORED)
        PayCallbackLog existing = callbackMapper.selectOne(new LambdaQueryWrapper<PayCallbackLog>()
                .eq(PayCallbackLog::getIdempotencyKey, idempotencyKey)
                .last("LIMIT 1"));
        if (existing != null) {
            log.info("handleCallback idempotent hit: idemKey={} processResult={}", idempotencyKey, existing.getProcessResult());
            // 返回已有日志, 但不抛异常 (重复回调是第三方常态)
            existing.setProcessMessage("重复回调忽略, 已有处理记录: " + existing.getProcessResult());
            return existing;
        }

        // 查询支付单
        PayOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getOrderNo, request.getOrderNo())
                .last("LIMIT 1"));

        // 构建日志主体
        PayCallbackLog callbackLog = new PayCallbackLog();
        callbackLog.setId(IdGenerator.nextId());
        callbackLog.setOrderId(order == null ? null : order.getId());
        callbackLog.setOrderNo(request.getOrderNo());
        callbackLog.setChannel(request.getChannel());
        callbackLog.setChannelTradeNo(request.getChannelTradeNo());
        callbackLog.setCallbackAction(request.getCallbackAction());
        callbackLog.setSignature(request.getSignature());
        callbackLog.setCallbackTimestamp(request.getCallbackTimestamp());
        callbackLog.setRawPayload(request.getRawPayload());
        callbackLog.setIdempotencyKey(idempotencyKey);
        callbackLog.setReceivedTime(OffsetDateTime.now());

        // 支付单不存在, 记录 FAILED 日志
        if (order == null) {
            callbackLog.setVerifyResult(PayCallbackLog.VERIFY_FAILED);
            callbackLog.setProcessResult(PayCallbackLog.PROCESS_IGNORED);
            callbackLog.setProcessMessage("支付单不存在, 忽略回调: " + request.getOrderNo());
            callbackMapper.insert(callbackLog);
            log.warn("handleCallback order not found: orderNo={}", request.getOrderNo());
            return callbackLog;
        }

        // 验签 (HMAC-SHA256)
        String secretKey = extractSecretKey(order);
        boolean verified = signUtil.verify(
                request.getOrderNo(),
                request.getChannel(),
                request.getAmount() != null ? request.getAmount() : order.getAmount(),
                request.getCallbackTimestamp(),
                request.getSignature(),
                secretKey);
        callbackLog.setVerifyResult(verified ? PayCallbackLog.VERIFY_SUCCESS : PayCallbackLog.VERIFY_FAILED);

        if (!verified) {
            callbackLog.setProcessResult(PayCallbackLog.PROCESS_IGNORED);
            callbackLog.setProcessMessage("验签失败, 忽略回调");
            try {
                callbackMapper.insert(callbackLog);
            } catch (DuplicateKeyException dke) {
                log.info("handleCallback duplicate (concurrent): idemKey={}", idempotencyKey);
            }
            log.warn("handleCallback verify failed: orderNo={} channel={}", request.getOrderNo(), request.getChannel());
            return callbackLog;
        }

        // 状态机流转
        String processMessage;
        try {
            switch (request.getCallbackAction()) {
                case PayCallbackLog.ACTION_PAY_SUCCESS:
                    if (PayOrder.STATUS_PENDING.equals(order.getStatus())) {
                        order.setStatus(PayOrder.STATUS_PAID);
                        order.setChannelTradeNo(request.getChannelTradeNo());
                        order.setPaidTime(OffsetDateTime.now());
                        orderMapper.updateById(order);
                        processMessage = "支付成功, 状态机 PENDING → PAID";
                    } else {
                        processMessage = "状态机不匹配, 当前: " + order.getStatus() + " (期望 PENDING)";
                    }
                    break;
                case PayCallbackLog.ACTION_PAY_FAIL:
                    if (PayOrder.STATUS_PENDING.equals(order.getStatus())) {
                        order.setStatus(PayOrder.STATUS_FAILED);
                        order.setFailReason(request.getFailReason());
                        orderMapper.updateById(order);
                        processMessage = "支付失败, 状态机 PENDING → FAILED: " + request.getFailReason();
                    } else {
                        processMessage = "状态机不匹配, 当前: " + order.getStatus() + " (期望 PENDING)";
                    }
                    break;
                default:
                    processMessage = "未支持的回调动作: " + request.getCallbackAction();
            }
            callbackLog.setProcessResult(PayCallbackLog.PROCESS_PROCESSED);
            callbackLog.setProcessMessage(processMessage);
            // 解析后的 payload 快照
            callbackLog.setParsedPayload(buildParsedPayload(order, request));
        } catch (Exception e) {
            callbackLog.setProcessResult(PayCallbackLog.PROCESS_ERROR);
            callbackLog.setProcessMessage("处理异常: " + e.getMessage());
            log.error("handleCallback process error: orderNo={}", request.getOrderNo(), e);
        }

        try {
            callbackMapper.insert(callbackLog);
        } catch (DuplicateKeyException dke) {
            // 并发场景: 同 idempotencyKey 已写入, 返回已有记录
            log.info("handleCallback duplicate (concurrent insert): idemKey={}", idempotencyKey);
            PayCallbackLog concurrent = callbackMapper.selectOne(new LambdaQueryWrapper<PayCallbackLog>()
                    .eq(PayCallbackLog::getIdempotencyKey, idempotencyKey)
                    .last("LIMIT 1"));
            if (concurrent != null) {
                return concurrent;
            }
        }
        log.info("handleCallback done: orderNo={} verify={} process={}",
                request.getOrderNo(), callbackLog.getVerifyResult(), callbackLog.getProcessResult());
        return callbackLog;
    }

    public Page<PayCallbackLog> pageCallbacks(int pageNo, int pageSize, String orderNo, String channel,
                                              String verifyResult, String processResult) {
        Page<PayCallbackLog> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<PayCallbackLog> wrapper = new LambdaQueryWrapper<PayCallbackLog>()
                .orderByDesc(PayCallbackLog::getReceivedTime);
        if (orderNo != null && !orderNo.isBlank()) {
            wrapper.eq(PayCallbackLog::getOrderNo, orderNo);
        }
        if (channel != null && !channel.isBlank()) {
            wrapper.eq(PayCallbackLog::getChannel, channel);
        }
        if (verifyResult != null && !verifyResult.isBlank()) {
            wrapper.eq(PayCallbackLog::getVerifyResult, verifyResult);
        }
        if (processResult != null && !processResult.isBlank()) {
            wrapper.eq(PayCallbackLog::getProcessResult, processResult);
        }
        return callbackMapper.selectPage(page, wrapper);
    }

    // ==================== 3. 退款 ====================

    /**
     * 发起退款 (PAID → REFUNDING → REFUNDED/SUCCESS)。
     *
     * <p>累计退款金额不超过原支付金额。
     */
    @Transactional
    public PayRefundOrder refund(RefundRequest request) {
        PayOrder order = getOrder(request.getOriginalOrderId());
        if (!PayOrder.STATUS_PAID.equals(order.getStatus())
                && !PayOrder.STATUS_REFUNDING.equals(order.getStatus())
                && !PayOrder.STATUS_REFUNDED.equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.PAY_ORDER_STATUS_NOT_ALLOWED,
                    "仅 PAID/REFUNDING/REFUNDED 状态可退款, 当前: " + order.getStatus());
        }
        if (request.getRefundAmount() == null || request.getRefundAmount() <= 0) {
            throw new BusinessException(ErrorCode.PAY_REFUND_AMOUNT_EXCEEDED, "退款金额必须大于 0");
        }

        // 计算已退款金额
        Long alreadyRefunded = sumRefundedAmount(order.getId());
        long remaining = order.getAmount() - alreadyRefunded;
        if (request.getRefundAmount() > remaining) {
            throw new BusinessException(ErrorCode.PAY_REFUND_AMOUNT_EXCEEDED,
                    "退款金额超过可退余额: refundAmount=" + request.getRefundAmount()
                            + " remaining=" + remaining);
        }

        // 创建退款单 (Mock 模式直接 SUCCESS, 实际生产应异步等第三方回调)
        PayRefundOrder refund = new PayRefundOrder();
        refund.setId(IdGenerator.nextId());
        refund.setRefundNo(generateRefundNo());
        refund.setOriginalOrderId(order.getId());
        refund.setOriginalOrderNo(order.getOrderNo());
        refund.setRefundAmount(request.getRefundAmount());
        refund.setReason(request.getReason());
        refund.setStatus(PayRefundOrder.STATUS_PENDING);
        refund.setOperatorId(CurrentUserContext.getUserId());
        refund.setRemark(request.getRemark());
        try {
            refundMapper.insert(refund);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.PAY_ORDER_NO_DUPLICATE, "退款单号已存在: " + refund.getRefundNo());
        }

        // Mock 模式: 直接置 SUCCESS, 并回写支付单状态
        refund.setStatus(PayRefundOrder.STATUS_SUCCESS);
        refund.setChannelRefundNo("MOCK-REFUND-" + refund.getRefundNo());
        refund.setRefundedTime(OffsetDateTime.now());
        refundMapper.updateById(refund);

        // 更新支付单状态
        long totalRefunded = alreadyRefunded + request.getRefundAmount();
        if (totalRefunded >= order.getAmount()) {
            order.setStatus(PayOrder.STATUS_REFUNDED);
        } else {
            order.setStatus(PayOrder.STATUS_REFUNDING);
        }
        orderMapper.updateById(order);
        log.info("refund: refundNo={} orderNo={} amount={} totalRefunded={}/{}",
                refund.getRefundNo(), order.getOrderNo(), refund.getRefundAmount(),
                totalRefunded, order.getAmount());
        return refund;
    }

    public Page<PayRefundOrder> pageRefunds(int pageNo, int pageSize, String refundNo, String originalOrderId,
                                            String status) {
        Page<PayRefundOrder> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<PayRefundOrder> wrapper = new LambdaQueryWrapper<PayRefundOrder>()
                .orderByDesc(PayRefundOrder::getCreatedTime);
        if (refundNo != null && !refundNo.isBlank()) {
            wrapper.eq(PayRefundOrder::getRefundNo, refundNo);
        }
        if (originalOrderId != null && !originalOrderId.isBlank()) {
            wrapper.eq(PayRefundOrder::getOriginalOrderId, originalOrderId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(PayRefundOrder::getStatus, status);
        }
        // GA2-DS: 接入 DataScope 过滤，admin(ALL/TENANT) 放行，非 admin 按 created_by 过滤
        applyDataScope(wrapper, dataScopeResolver.resolve(RESOURCE_CODE));
        return refundMapper.selectPage(page, wrapper);
    }

    private Long sumRefundedAmount(String orderId) {
        List<PayRefundOrder> existing = refundMapper.selectList(new LambdaQueryWrapper<PayRefundOrder>()
                .eq(PayRefundOrder::getOriginalOrderId, orderId)
                .eq(PayRefundOrder::getStatus, PayRefundOrder.STATUS_SUCCESS));
        return existing.stream().mapToLong(PayRefundOrder::getRefundAmount).sum();
    }

    // ==================== 4. 对账文件导入 ====================

    /**
     * 对账文件导入 (PENDING → MATCHED / MISMATCHED / IMPORTED)。
     *
     * <p>对账逻辑:
     * <ol>
     *   <li>遍历对账文件 items, 在本地 pay_order 表按 orderNo 查找匹配</li>
     *   <li>matched: 本地有 + 第三方有, 金额一致</li>
     *   <li>mismatched: 本地有 + 第三方有, 金额不一致</li>
     *   <li>missing: 本地缺失 (第三方有 + 本地无)</li>
     *   <li>extra: 本地多余 (本地有 PAID 状态 + 第三方无, 由反向扫描得出)</li>
     * </ol>
     */
    @Transactional
    public PayReconciliation importReconciliation(ReconciliationImportRequest request) {
        validateChannel(request.getChannel());

        // 同日同渠道已有对账记录则报冲突 (避免重复导入)
        PayReconciliation existing = reconMapper.selectOne(new LambdaQueryWrapper<PayReconciliation>()
                .eq(PayReconciliation::getReconDate, request.getReconDate())
                .eq(PayReconciliation::getChannel, request.getChannel())
                .last("LIMIT 1"));
        if (existing != null) {
            throw new BusinessException(ErrorCode.PAY_RECON_DATE_CHANNEL_DUPLICATE,
                    "同日同渠道对账记录已存在: " + request.getReconDate() + " " + request.getChannel());
        }

        // 比对
        long totalAmount = 0L;
        int matchedCount = 0;
        long matchedAmount = 0L;
        int mismatchedCount = 0;
        long mismatchedAmount = 0L;
        int missingCount = 0;
        long missingAmount = 0L;
        List<Map<String, Object>> details = new ArrayList<>();
        List<String> remoteOrderNos = new ArrayList<>();

        for (ReconciliationImportRequest.ReconciliationItem item : request.getItems()) {
            totalAmount += (item.getAmount() == null ? 0L : item.getAmount());
            remoteOrderNos.add(item.getOrderNo());

            PayOrder localOrder = orderMapper.selectOne(new LambdaQueryWrapper<PayOrder>()
                    .eq(PayOrder::getOrderNo, item.getOrderNo())
                    .eq(PayOrder::getChannel, request.getChannel())
                    .last("LIMIT 1"));

            Map<String, Object> detail = new HashMap<>();
            detail.put("orderNo", item.getOrderNo());
            detail.put("channelTradeNo", item.getChannelTradeNo());
            detail.put("remoteAmount", item.getAmount());
            detail.put("localAmount", localOrder == null ? 0L : localOrder.getAmount());

            if (localOrder == null) {
                missingCount++;
                missingAmount += (item.getAmount() == null ? 0L : item.getAmount());
                detail.put("diff", item.getAmount());
                detail.put("diffType", "MISSING");
            } else if (localOrder.getAmount() == (item.getAmount() == null ? 0L : item.getAmount())) {
                matchedCount++;
                matchedAmount += localOrder.getAmount();
                detail.put("diff", 0L);
                detail.put("diffType", "MATCHED");
            } else {
                mismatchedCount++;
                mismatchedAmount += Math.abs(localOrder.getAmount() - (item.getAmount() == null ? 0L : item.getAmount()));
                detail.put("diff", localOrder.getAmount() - (item.getAmount() == null ? 0L : item.getAmount()));
                detail.put("diffType", "MISMATCHED");
            }
            details.add(detail);
        }

        // 反向扫描: 本地有 PAID 状态, 但第三方文件无 → extra
        List<PayOrder> localPaidOrders = orderMapper.selectList(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getChannel, request.getChannel())
                .in(PayOrder::getStatus, PayOrder.STATUS_PAID, PayOrder.STATUS_REFUNDING, PayOrder.STATUS_REFUNDED));
        int extraCount = 0;
        long extraAmount = 0L;
        for (PayOrder localOrder : localPaidOrders) {
            if (!remoteOrderNos.contains(localOrder.getOrderNo())) {
                extraCount++;
                extraAmount += localOrder.getAmount();
                Map<String, Object> detail = new HashMap<>();
                detail.put("orderNo", localOrder.getOrderNo());
                detail.put("channelTradeNo", localOrder.getChannelTradeNo());
                detail.put("remoteAmount", 0L);
                detail.put("localAmount", localOrder.getAmount());
                detail.put("diff", -localOrder.getAmount());
                detail.put("diffType", "EXTRA");
                details.add(detail);
            }
        }

        // 写入对账记录
        PayReconciliation recon = new PayReconciliation();
        recon.setId(IdGenerator.nextId());
        recon.setReconDate(request.getReconDate());
        recon.setChannel(request.getChannel());
        recon.setFileName(request.getFileName());
        recon.setTotalCount(request.getItems().size());
        recon.setTotalAmount(totalAmount);
        recon.setMatchedCount(matchedCount);
        recon.setMatchedAmount(matchedAmount);
        recon.setMismatchedCount(mismatchedCount);
        recon.setMismatchedAmount(mismatchedAmount);
        recon.setMissingCount(missingCount);
        recon.setMissingAmount(missingAmount);
        recon.setExtraCount(extraCount);
        recon.setExtraAmount(extraAmount);
        recon.setImportedBy(CurrentUserContext.getUserId());
        recon.setImportedTime(OffsetDateTime.now());

        // 状态判定
        String status;
        String processMessage;
        if (mismatchedCount == 0 && missingCount == 0 && extraCount == 0) {
            status = PayReconciliation.STATUS_MATCHED;
            processMessage = "对账匹配成功, 无差异";
        } else {
            status = PayReconciliation.STATUS_MISMATCHED;
            processMessage = String.format("对账存在差异: mismatched=%d missing=%d extra=%d",
                    mismatchedCount, missingCount, extraCount);
        }
        recon.setStatus(status);
        recon.setProcessMessage(processMessage);
        try {
            recon.setDetails(objectMapper.writeValueAsString(details));
        } catch (Exception e) {
            log.warn("importReconciliation serialize details failed: {}", e.getMessage());
            recon.setDetails("[]");
        }
        try {
            reconMapper.insert(recon);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.PAY_RECON_DATE_CHANNEL_DUPLICATE,
                    "同日同渠道对账记录已存在: " + request.getReconDate() + " " + request.getChannel());
        }
        log.info("importReconciliation: date={} channel={} status={} matched={}/{} mismatched={} missing={} extra={}",
                request.getReconDate(), request.getChannel(), status,
                matchedCount, request.getItems().size(), mismatchedCount, missingCount, extraCount);
        return recon;
    }

    public Page<PayReconciliation> pageReconciliations(int pageNo, int pageSize, String channel,
                                                       String status, LocalDate startDate, LocalDate endDate) {
        Page<PayReconciliation> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<PayReconciliation> wrapper = new LambdaQueryWrapper<PayReconciliation>()
                .orderByDesc(PayReconciliation::getReconDate);
        if (channel != null && !channel.isBlank()) {
            wrapper.eq(PayReconciliation::getChannel, channel);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(PayReconciliation::getStatus, status);
        }
        if (startDate != null) {
            wrapper.ge(PayReconciliation::getReconDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(PayReconciliation::getReconDate, endDate);
        }
        // GA2-DS: 接入 DataScope 过滤，admin(ALL/TENANT) 放行，非 admin 按 created_by 过滤
        applyDataScope(wrapper, dataScopeResolver.resolve(RESOURCE_CODE));
        return reconMapper.selectPage(page, wrapper);
    }

    public PayReconciliation getReconciliation(String id) {
        PayReconciliation recon = reconMapper.selectById(id);
        if (recon == null) {
            throw new ResourceNotFoundException(ErrorCode.PAY_RECON_NOT_FOUND);
        }
        return recon;
    }

    // ==================== 5. 监控统计 ====================

    public PaymentStatsVO getStats() {
        PaymentStatsVO stats = new PaymentStatsVO();
        stats.setTotalOrders(orderMapper.selectCount(null));

        // 按状态分布
        for (String status : new String[]{PayOrder.STATUS_PENDING, PayOrder.STATUS_PAID, PayOrder.STATUS_FAILED,
                PayOrder.STATUS_CANCELLED, PayOrder.STATUS_REFUNDING, PayOrder.STATUS_REFUNDED, PayOrder.STATUS_CLOSED}) {
            long count = orderMapper.selectCount(new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getStatus, status));
            stats.getStatusCounts().put(status, count);
        }
        // 按渠道分布
        for (String channel : new String[]{PayOrder.CHANNEL_MOCK_ALIPAY, PayOrder.CHANNEL_MOCK_WECHAT, PayOrder.CHANNEL_MOCK_UNIONPAY}) {
            long count = orderMapper.selectCount(new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getChannel, channel));
            stats.getChannelCounts().put(channel, count);
        }
        // 已支付总额
        List<PayOrder> paidOrders = orderMapper.selectList(new LambdaQueryWrapper<PayOrder>()
                .in(PayOrder::getStatus, PayOrder.STATUS_PAID, PayOrder.STATUS_REFUNDING,
                        PayOrder.STATUS_REFUNDED, PayOrder.STATUS_CLOSED));
        stats.setTotalAmountPaid(paidOrders.stream().mapToLong(PayOrder::getAmount).sum());

        // 退款统计
        stats.setTotalRefunds(refundMapper.selectCount(null));
        List<PayRefundOrder> successRefunds = refundMapper.selectList(new LambdaQueryWrapper<PayRefundOrder>()
                .eq(PayRefundOrder::getStatus, PayRefundOrder.STATUS_SUCCESS));
        stats.setTotalAmountRefunded(successRefunds.stream().mapToLong(PayRefundOrder::getRefundAmount).sum());

        // 回调统计
        stats.setCallbackTotal(callbackMapper.selectCount(null));
        stats.setCallbackVerifyFailed(callbackMapper.selectCount(new LambdaQueryWrapper<PayCallbackLog>()
                .eq(PayCallbackLog::getVerifyResult, PayCallbackLog.VERIFY_FAILED)));

        // 对账统计
        stats.setReconTotal(reconMapper.selectCount(null));
        stats.setReconMatchedCount(reconMapper.selectCount(new LambdaQueryWrapper<PayReconciliation>()
                .eq(PayReconciliation::getStatus, PayReconciliation.STATUS_MATCHED)));
        stats.setReconMismatchedCount(reconMapper.selectCount(new LambdaQueryWrapper<PayReconciliation>()
                .eq(PayReconciliation::getStatus, PayReconciliation.STATUS_MISMATCHED)));
        return stats;
    }

    // ==================== 工具方法 ====================

    private void validateChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            throw new BusinessException(ErrorCode.PAY_REQUEST_INVALID, "支付渠道不能为空");
        }
        if (!PayOrder.CHANNEL_MOCK_ALIPAY.equals(channel)
                && !PayOrder.CHANNEL_MOCK_WECHAT.equals(channel)
                && !PayOrder.CHANNEL_MOCK_UNIONPAY.equals(channel)) {
            throw new BusinessException(ErrorCode.PAY_REQUEST_INVALID, "不支持的支付渠道: " + channel);
        }
    }

    private String generateOrderNo() {
        return "PO" + OffsetDateTime.now().format(ORDER_NO_FMT)
                + String.format("%06d", System.nanoTime() % 1000000);
    }

    private String generateRefundNo() {
        return "RF" + OffsetDateTime.now().format(REFUND_NO_FMT)
                + String.format("%06d", System.nanoTime() % 1000000);
    }

    private String buildCallbackIdempotencyKey(String orderNo, String callbackAction, String channelTradeNo) {
        return orderNo + "|" + callbackAction + "|" + (channelTradeNo == null ? "null" : channelTradeNo);
    }

    private String extractSecretKey(PayOrder order) {
        String channelConfig = order.getChannelConfig();
        // 兜底: 凭证缺失时回退到 Mock 渠道默认凭证 (便于样例测试)
        if (channelConfig == null || channelConfig.isBlank()) {
            String defaultKey = defaultMockSecretKey(order.getChannel());
            if (defaultKey != null) {
                return defaultKey;
            }
            throw new BusinessException(ErrorCode.PAY_CALLBACK_VERIFY_FAILED,
                    "渠道凭证缺失, 无法验签: " + order.getOrderNo());
        }
        try {
            JsonNode node = objectMapper.readTree(channelConfig);
            JsonNode secret = node.get("secretKey");
            if (secret == null || secret.isNull()) {
                String defaultKey = defaultMockSecretKey(order.getChannel());
                if (defaultKey != null) {
                    return defaultKey;
                }
                throw new BusinessException(ErrorCode.PAY_CALLBACK_VERIFY_FAILED,
                        "渠道凭证缺少 secretKey: " + order.getOrderNo());
            }
            return secret.asText();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PAY_CALLBACK_VERIFY_FAILED,
                    "渠道凭证解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析渠道凭证: 业务方传入优先, 否则填充 Mock 渠道默认凭证 (便于样例测试)。
     */
    private String resolveChannelConfig(String channel, String provided) {
        if (provided != null && !provided.isBlank()) {
            return provided;
        }
        String defaultKey = defaultMockSecretKey(channel);
        String defaultAccessKey = defaultMockAccessKey(channel);
        if (defaultKey == null) {
            return null;
        }
        try {
            Map<String, String> config = new HashMap<>();
            config.put("accessKey", defaultAccessKey);
            config.put("secretKey", defaultKey);
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            return null;
        }
    }

    private String defaultMockAccessKey(String channel) {
        if (channel == null) return null;
        return switch (channel) {
            case PayOrder.CHANNEL_MOCK_ALIPAY -> "mock-alipay-ak";
            case PayOrder.CHANNEL_MOCK_WECHAT -> "mock-wechat-ak";
            case PayOrder.CHANNEL_MOCK_UNIONPAY -> "mock-unionpay-ak";
            default -> null;
        };
    }

    private String defaultMockSecretKey(String channel) {
        if (channel == null) return null;
        return switch (channel) {
            case PayOrder.CHANNEL_MOCK_ALIPAY -> "mock-alipay-sk-32bytes-xxxxx";
            case PayOrder.CHANNEL_MOCK_WECHAT -> "mock-wechat-sk-32bytes-xxxxx";
            case PayOrder.CHANNEL_MOCK_UNIONPAY -> "mock-unionpay-sk-32bytes-x";
            default -> null;
        };
    }

    private String buildParsedPayload(PayOrder order, CallbackRequest request) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("orderNo", order.getOrderNo());
            payload.put("channel", order.getChannel());
            payload.put("amount", order.getAmount());
            payload.put("status", order.getStatus());
            payload.put("channelTradeNo", request.getChannelTradeNo());
            payload.put("callbackAction", request.getCallbackAction());
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }
}
