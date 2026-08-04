package com.yutong.sample.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.id.IdGenerator;
import com.yutong.sample.request.domain.Sequence;
import com.yutong.sample.request.mapper.SequenceMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 库存单号序列服务。生成 INyyyyMMddNNNN / OUTyyyyMMddNNNN / TXyyyyMMddNNNNNN。
 * 设计来源: 35-样例业务矩阵扩展设计 P1 库存出入库（参考合同号/工单号序列模式）。
 * 复用 Sequence 表, REQUIRES_NEW 独立事务 + 乐观锁重试。
 */
@Service
public class InventorySequenceService {

    private static final int MAX_RETRY = 3;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    public static final String CODE_INBOUND_NO = "INBOUND_NO";
    public static final String CODE_OUTBOUND_NO = "OUTBOUND_NO";
    public static final String CODE_TX_NO = "STOCK_TX_NO";

    private static final String PREFIX_INBOUND = "IN";
    private static final String PREFIX_OUTBOUND = "OUT";
    private static final String PREFIX_TX = "TX";

    private final SequenceMapper sequenceMapper;

    public InventorySequenceService(SequenceMapper sequenceMapper) {
        this.sequenceMapper = sequenceMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextInboundNo() {
        return nextNo(CODE_INBOUND_NO, PREFIX_INBOUND, 4);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextOutboundNo() {
        return nextNo(CODE_OUTBOUND_NO, PREFIX_OUTBOUND, 4);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextTransactionNo() {
        return nextNo(CODE_TX_NO, PREFIX_TX, 6);
    }

    private String nextNo(String code, String prefix, int digits) {
        String tenantId = CurrentUserContext.getTenantId();
        String bizDate = LocalDate.now().format(DATE_FMT);

        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            Sequence seq = sequenceMapper.selectOne(new LambdaQueryWrapper<Sequence>()
                    .eq(Sequence::getTenantId, tenantId)
                    .eq(Sequence::getSequenceCode, code)
                    .eq(Sequence::getBizDate, bizDate));

            if (seq == null) {
                Sequence newSeq = new Sequence();
                newSeq.setId(IdGenerator.nextId());
                newSeq.setTenantId(tenantId);
                newSeq.setCreatedBy(CurrentUserContext.getUserId());
                newSeq.setSequenceCode(code);
                newSeq.setBizDate(bizDate);
                newSeq.setCurrentValue(1);
                newSeq.setStep(1);
                newSeq.setResetPolicy(Sequence.RESET_POLICY_DAILY);
                try {
                    sequenceMapper.insert(newSeq);
                } catch (DuplicateKeyException e) {
                    continue;
                }
                return prefix + bizDate + String.format("%0" + digits + "d", 1);
            }

            int nextValue = seq.getCurrentValue() + seq.getStep();
            seq.setCurrentValue(nextValue);
            int affected = sequenceMapper.updateById(seq);
            if (affected > 0) {
                return prefix + bizDate + String.format("%0" + digits + "d", nextValue);
            }
        }
        throw new BusinessConflictException("生成库存单号失败, 重试次数耗尽: " + code);
    }
}
