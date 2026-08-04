package com.yutong.sample.inventory.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 物料主表。设计来源: 35-样例业务矩阵扩展设计 P1 库存出入库。
 * 验证能力: 导入物料、物料编码唯一约束。
 */
@Getter
@Setter
@TableName("inv_material")
public class InvMaterial extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    public static final String TYPE_ELECTRONIC = "ELECTRONIC";
    public static final String TYPE_MECHANICAL = "MECHANICAL";
    public static final String TYPE_ACCESSORY = "ACCESSORY";
    public static final String TYPE_GENERAL = "GENERAL";

    /** 物料编码 (租户内唯一) */
    private String materialCode;

    private String materialName;

    /** 物料类型 ELECTRONIC/MECHANICAL/ACCESSORY/GENERAL */
    private String materialType;

    /** 规格 */
    private String spec;

    /** 单位 PCS/BOX/KG/M */
    private String unit;

    private String category;

    private String barcode;

    /** 参考单价 */
    private BigDecimal referencePrice;

    private String status;
}
