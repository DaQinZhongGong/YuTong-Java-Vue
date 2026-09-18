package com.yutong.ai.rag.service.embedding;

import com.yutong.ai.gateway.domain.AiProvider;

/**
 * 远程 Embedding 供应商抽象 — 真实 HTTP 调用。
 * 设计来源: P0-3 Embedding 真远程降级 / AiProviderRegistry 路由
 * 约束: 5s 超时，失败抛异常由上层降级至哈希；不落明文密钥 via api_key_ref。
 */
public interface RemoteEmbeddingProvider {

    /** 供应商类型 code，与 ai_provider.provider_type 一致 (siliconflow / zhipu)。 */
    String getProviderType();

    /** 是否支持该 AiProvider 配置 */
    boolean supports(AiProvider provider);

    /**
     * 调用远端 embedding API。
     *
     * @param text     待向量化文本
     * @param model    模型编码（如 BAAI/bge-m3, embedding-3）
     * @param provider 供应商配置（含 endpoint、api_key_ref、config_json）
     * @return 归一化或原始 float 向量（维度由远端决定，调用方负责校验/归一）
     * @throws Exception 网络或解析异常，调用方应捕获并降级
     */
    float[] embed(String text, String model, AiProvider provider) throws Exception;
}
