package com.yutong.sample.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yutong.sample.payment.domain.PayRefundOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 退款单 Mapper。设计来源: 35-样例业务矩阵扩展设计 P2 支付订单 (GA2-41)。
 */
@Mapper
public interface PayRefundOrderMapper extends BaseMapper<PayRefundOrder> {
}
