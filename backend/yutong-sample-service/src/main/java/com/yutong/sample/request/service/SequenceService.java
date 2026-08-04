package com.yutong.sample.request.service;

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
 * 序列号服务。设计来源: 18-样例业务详细设计 编码与金额规则
 * 生成申请单号 REQyyyyMMddNNNN，按 tenant_id + sequence_code + biz_date 隔离。
 * 采用 @Version 乐观锁更新，冲突重试最多 3 次。
 * 使用 REQUIRES_NEW 独立事务提交，保证并发可见性与序列号立即可用。
 */
@Service
public class SequenceService {

    private static final int MAX_RETRY = 3;
    private static final String PREFIX = "REQ";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    private final SequenceMapper sequenceMapper;

    public SequenceService(SequenceMapper sequenceMapper) {
        this.sequenceMapper = sequenceMapper;
    }

    /**
     * 生成下一个申请单号，格式 REQyyyyMMddNNNN。
     * 不存在则插入新记录 (current_value=1)，存在则 current_value + step。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextRequestNo() {
        String tenantId = CurrentUserContext.getTenantId();
        String bizDate = LocalDate.now().format(DATE_FMT);

        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            Sequence seq = sequenceMapper.selectOne(new LambdaQueryWrapper<Sequence>()
                    .eq(Sequence::getTenantId, tenantId)
                    .eq(Sequence::getSequenceCode, Sequence.CODE_BIZ_REQUEST_NO)
                    .eq(Sequence::getBizDate, bizDate));

            if (seq == null) {
                // 首次: 插入新记录，current_value=1
                Sequence newSeq = new Sequence();
                newSeq.setId(IdGenerator.nextId());
                newSeq.setTenantId(tenantId);
                newSeq.setCreatedBy(CurrentUserContext.getUserId());
                newSeq.setSequenceCode(Sequence.CODE_BIZ_REQUEST_NO);
                newSeq.setBizDate(bizDate);
                newSeq.setCurrentValue(1);
                newSeq.setStep(1);
                newSeq.setResetPolicy(Sequence.RESET_POLICY_DAILY);
                try {
                    sequenceMapper.insert(newSeq);
                } catch (DuplicateKeyException e) {
                    // 并发插入冲突，重试
                    continue;
                }
                return formatRequestNo(bizDate, 1);
            }

            // 已存在: current_value + step
            int nextValue = seq.getCurrentValue() + seq.getStep();
            seq.setCurrentValue(nextValue);
            int affected = sequenceMapper.updateById(seq);
            if (affected > 0) {
                return formatRequestNo(bizDate, nextValue);
            }
            // 乐观锁冲突，重试
        }
        throw new BusinessConflictException("生成申请单号失败，重试次数耗尽");
    }

    private String formatRequestNo(String bizDate, int value) {
        return PREFIX + bizDate + String.format("%04d", value);
    }
}
