package com.yutong.sample.inventory.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 仓库主表。设计来源: 35-样例业务矩阵扩展设计 P1 库存出入库。
 */
@Getter
@Setter
@TableName("inv_warehouse")
public class InvWarehouse extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    public static final String TYPE_CENTRAL = "CENTRAL";
    public static final String TYPE_BRANCH = "BRANCH";

    private String warehouseCode;

    private String warehouseName;

    private String warehouseType;

    private String address;

    private String managerUserId;

    private String status;
}
