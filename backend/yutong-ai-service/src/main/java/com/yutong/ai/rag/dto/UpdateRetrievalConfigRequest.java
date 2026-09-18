package com.yutong.ai.rag.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 检索配置更新请求 (V050 P2-E 混合检索可配)。
 * 全字段可选 (null = 不修改); 范围由 service 层校验。
 */
@Data
public class UpdateRetrievalConfigRequest {

    /** 是否启用混合检索 */
    private Boolean hybridEnabled;

    /** 向量权重 0~1 */
    private BigDecimal hybridVectorWeight;

    /** 返回数量 1~50 */
    private Integer hybridTopK;

    /** 准入门限 0~1 */
    private BigDecimal hybridMinScore;
}
