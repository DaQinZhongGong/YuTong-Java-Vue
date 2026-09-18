package com.yutong.ai.rag.service.reranker;

import com.yutong.ai.rag.domain.RerankerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * SiliconFlow Reranker — mock 实现。
 * 设计来源: ai_knowledge_base.reranker_provider = 'siliconflow'
 * 约束: 配置经 DB api_key_ref 注入，不落明文；mock 返回确定性分数。
 */
@Service
public class SiliconFlowReranker implements RerankerProvider {

    private static final Logger log = LoggerFactory.getLogger(SiliconFlowReranker.class);

    @Override
    public String getProviderCode() {
        return "siliconflow";
    }

    @Override
    public List<RerankResult> rerank(RerankRequest request) {
        if (request == null || request.candidates() == null || request.candidates().isEmpty()) {
            return List.of();
        }
        String query = request.query() == null ? "" : request.query();
        int topN = request.topN() > 0 ? request.topN() : request.candidates().size();

        List<RerankResult> ranked = request.candidates().stream()
                .map(c -> {
                    double base = mockScore(query, c.chunkText());
                    // siliconflow 偏置: 稍偏向短文本 (倒数长度)
                    double len = c.chunkText() == null ? 0 : c.chunkText().length();
                    double shortBias = len > 0 ? Math.max(0, (800 - len) / 8000.0) : 0;
                    double score = clamp(base + shortBias + 0.02);
                    return new RerankResult(c.chunkId(), score, c.originalScore());
                })
                .sorted(Comparator.comparingDouble(RerankResult::rerankScore).reversed())
                .limit(topN)
                .toList();

        log.debug("[SiliconFlowReranker] queryLen={}, candidates={}, topN={}, apiKeyRef={}",
                query.length(), request.candidates().size(), topN,
                request.apiKeyRef() == null ? "null" : "***");
        return ranked;
    }

    private double mockScore(String query, String text) {
        if (text == null) text = "";
        // 确定性分数: Jaccard 近似 + 哈希
        String q = query.toLowerCase();
        String t = text.toLowerCase();
        java.util.Set<String> qTokens = tokenSet(q);
        java.util.Set<String> tTokens = tokenSet(t);
        double jaccard = 0;
        if (!qTokens.isEmpty() || !tTokens.isEmpty()) {
            java.util.Set<String> inter = new java.util.HashSet<>(qTokens);
            inter.retainAll(tTokens);
            java.util.Set<String> union = new java.util.HashSet<>(qTokens);
            union.addAll(tTokens);
            jaccard = union.isEmpty() ? 0 : (double) inter.size() / union.size();
        }
        int hash = stableHash(q + "|" + t + "|sf");
        double hashNorm = (Math.abs(hash % 1000) / 1000.0) * 0.20;
        return clamp(jaccard * 0.70 + hashNorm + 0.12);
    }

    private java.util.Set<String> tokenSet(String s) {
        java.util.Set<String> set = new java.util.HashSet<>();
        for (String tok : s.split("[^a-z0-9\u4e00-\u9fa5]+")) {
            if (!tok.isEmpty()) set.add(tok);
        }
        // 中文单字展开
        java.util.Set<String> expanded = new java.util.HashSet<>(set);
        for (String tok : set) {
            for (int i = 0; i < tok.length(); i++) {
                char c = tok.charAt(i);
                if (c >= '\u4e00' && c <= '\u9fa5') expanded.add(String.valueOf(c));
            }
        }
        return expanded;
    }

    private int stableHash(String s) {
        int h = 23;
        for (int i = 0; i < s.length(); i++) h = h * 31 + s.charAt(i);
        return h;
    }

    private double clamp(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
