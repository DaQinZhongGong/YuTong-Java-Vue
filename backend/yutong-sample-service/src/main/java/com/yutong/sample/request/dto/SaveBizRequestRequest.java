package com.yutong.sample.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 保存草稿请求。设计来源: 18-样例业务详细设计 API 契约
 * id 为空表示新建，非空表示修改 (修改时 version 必填用于乐观锁)。
 */
@Getter
@Setter
public class SaveBizRequestRequest {

    /** 为空表示新建 */
    private String id;

    /** 修改时必填，乐观锁版本号 */
    private Integer version;

    @NotBlank(message = "申请单标题不能为空")
    @Size(max = 200, message = "申请单标题长度不能超过200")
    private String title;

    private String customerId;

    private String customerNameSnapshot;

    private String applyReason;

    private List<Item> items;

    @Getter
    @Setter
    public static class Item {
        private String productId;
        private String productCodeSnapshot;
        private String productNameSnapshot;
        private String unit;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private Integer sortNo;
    }
}
