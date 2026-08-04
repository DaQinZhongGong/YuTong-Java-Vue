package com.yutong.sample.request.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 申请单明细。设计来源: 18-样例业务详细设计、57-完整DDL清单 biz_request_item
 * 行金额 = quantity * unit_price，由后端计算；保存采用"全删全插"策略。
 */
@Getter
@Setter
@TableName("biz_request_item")
public class BizRequestItem extends BaseEntity {

    private String requestId;

    private String productId;

    private String productCodeSnapshot;

    private String productNameSnapshot;

    private String unit;

    /** 数量，精度 4 位小数 */
    private BigDecimal quantity;

    /** 单价，精度 2 位小数 */
    private BigDecimal unitPrice;

    /** 行金额 = quantity * unit_price，精度 2 位小数 */
    private BigDecimal lineAmount;

    /** 排序，前端可拖拽排序，后端校正连续性 */
    private Integer sortNo;
}
