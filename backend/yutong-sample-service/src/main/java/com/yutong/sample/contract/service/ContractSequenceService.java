package com.yutong.sample.contract.service;

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
 * 合同号序列服务。生成合同号 CTyyyyMMddNNNN，复用 Sequence 表。
 * 设计来源: 35-样例业务矩阵扩展设计 P1 合同档案（参考工单号/申请单号序列模式）。
 */
@Service
public class ContractSequenceService {

    private static final int MAX_RETRY = 3;
    private static final String PREFIX = "CT";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;
    /** 复用 Sequence 表，新增合同号序列编码 */
    public static final String CODE_CONTRACT_NO = "CONTRACT_NO";

    private final SequenceMapper sequenceMapper;

    public ContractSequenceService(SequenceMapper sequenceMapper) {
        this.sequenceMapper = sequenceMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextContractNo() {
        String tenantId = CurrentUserContext.getTenantId();
        String bizDate = LocalDate.now().format(DATE_FMT);

        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            Sequence seq = sequenceMapper.selectOne(new LambdaQueryWrapper<Sequence>()
                    .eq(Sequence::getTenantId, tenantId)
                    .eq(Sequence::getSequenceCode, CODE_CONTRACT_NO)
                    .eq(Sequence::getBizDate, bizDate));

            if (seq == null) {
                Sequence newSeq = new Sequence();
                newSeq.setId(IdGenerator.nextId());
                newSeq.setTenantId(tenantId);
                newSeq.setCreatedBy(CurrentUserContext.getUserId());
                newSeq.setSequenceCode(CODE_CONTRACT_NO);
                newSeq.setBizDate(bizDate);
                newSeq.setCurrentValue(1);
                newSeq.setStep(1);
                newSeq.setResetPolicy(Sequence.RESET_POLICY_DAILY);
                try {
                    sequenceMapper.insert(newSeq);
                } catch (DuplicateKeyException e) {
                    continue;
                }
                return PREFIX + bizDate + String.format("%04d", 1);
            }

            int nextValue = seq.getCurrentValue() + seq.getStep();
            seq.setCurrentValue(nextValue);
            int affected = sequenceMapper.updateById(seq);
            if (affected > 0) {
                return PREFIX + bizDate + String.format("%04d", nextValue);
            }
        }
        throw new BusinessConflictException("生成合同号失败，重试次数耗尽");
    }
}
