package com.yutong.ai.rag.service.reranker;

import com.yutong.ai.rag.domain.RerankerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 阿里百炼 Reranker — mock 实现。
 * 设计来源: ai_knowledge_base.reranker_provider = 'alibailian'
 * 约束: 配置经 DB api_key_ref 注入，不落明文；mock 返回确定性分数，无外部网络调用。
 */
@Service
public class AliBaiLianReranker implements RerankerProvider {

    private static final Logger log = LoggerFactory.getLogger(AliBaiLianReranker.class);

    @Override
    public String getProviderCode() {
        return "alibailian";
    }

    @Override
    public List<RerankResult> rerank(RerankRequest request) {
        if (request == null || request.candidates() == null || request.candidates().isEmpty()) {
            return List.of();
        }
        String query = request.query() == null ? "" : request.query();
        int topN = request.topN() > 0 ? request.topN() : request.candidates().size();

        // mock: 哈希相似度 + 供应商偏置 0.03，分数归一到 0~1
        List<RerankResult> ranked = request.candidates().stream()
                .map(c -> {
                    double base = mockScore(query, c.chunkText());
                    // alibailian 偏置: 稍偏向长文本
                    double lenBias = Math.min(c.chunkText() == null ? 0 : c.chunkText().length() / 4000.0, 0.05);
                    double score = clamp(base + lenBias + 0.03);
                    return new RerankResult(c.chunkId(), score, c.originalScore());
                })
                .sorted(Comparator.comparingDouble(RerankResult::rerankScore).reversed())
                .limit(topN)
                .toList();

        log.debug("[AliBaiLianReranker] queryLen={}, candidates={}, topN={}, apiKeyRef={}",
                query.length(), request.candidates().size(), topN,
                request.apiKeyRef() == null ? "null" : "***");
        return ranked;
    }

    private double mockScore(String query, String text) {
        if (text == null) text = "";
        // 确定性分数: 词重叠率近似 + 哈希扰动，稳定可复现
        String q = query.toLowerCase();
        String t = text.toLowerCase();
        int overlap = 0;
        for (String token : q.split("\\s+")) {
            if (!token.isEmpty() && t.contains(token)) overlap++;
        }
        double overlapScore = q.isEmpty() ? 0.5 : (double) overlap / Math.max(1, q.split("\\s+").length);
        int hash = stableHash(q + "|" + t);
        double hashNorm = (Math.abs(hash % 1000) / 1000.0) * 0.25; // 0~0.25 扰动
        return clamp(overlapScore * 0.65 + hashNorm + 0.15);
    }

    private int stableHash(String s) {
        int h = 17;
        for (int i = 0; i < s.length(); i++) h = h * 31 + s.charAt(i);
        return h;
    }

    private double clamp(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
