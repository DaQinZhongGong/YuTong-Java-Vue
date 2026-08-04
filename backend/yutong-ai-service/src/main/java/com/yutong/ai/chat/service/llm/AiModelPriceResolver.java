package com.yutong.ai.chat.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * AI 模型价格解析器。设计来源: GA2-45 成本治理闭环
 * <p>
 * 从供应商模型列表 JSON 中解析输入/输出单价（元/千 token），用于调用后成本计算。
 */
@Component
public class AiModelPriceResolver {

    private final ObjectMapper objectMapper;

    public AiModelPriceResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析指定模型的输入/输出单价。
     *
     * @param modelCode     模型编码
     * @param modelListJson 供应商模型列表 JSON
     * @return 价格封装，未找到时返回 0
     */
    public Price resolvePrice(String modelCode, String modelListJson) {
        if (modelCode == null || modelCode.isBlank()
                || modelListJson == null || modelListJson.isBlank()) {
            return Price.ZERO;
        }
        try {
            JsonNode array = objectMapper.readTree(modelListJson);
            if (array.isArray()) {
                for (JsonNode node : array) {
                    if (modelCode.equals(node.path("code").asText(null))) {
                        BigDecimal input = parseDecimal(node.path("priceInputCny"));
                        BigDecimal output = parseDecimal(node.path("priceOutputCny"));
                        return new Price(input, output);
                    }
                }
            }
        } catch (Exception e) {
            // 解析失败按 0 处理，避免价格故障阻断业务流程
        }
        return Price.ZERO;
    }

    private BigDecimal parseDecimal(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return BigDecimal.ZERO;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String text = node.asText(null);
        if (text == null || text.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 模型价格（元/千 token）。
     */
    public record Price(BigDecimal inputCnyPer1k, BigDecimal outputCnyPer1k) {
        public static final Price ZERO = new Price(BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
