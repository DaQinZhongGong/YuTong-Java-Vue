package com.yutong.ai.rag.service.reranker;

import com.yutong.ai.rag.domain.RerankerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 智谱 Zhipu Reranker — mock 实现。
 * 设计来源: ai_knowledge_base.reranker_provider = 'zhipu'
 * 约束: 配置经 DB api_key_ref 注入，不落明文；mock 返回确定性分数。
 */
@Service
public class ZhiPuReranker implements RerankerProvider {

    private static final Logger log = LoggerFactory.getLogger(ZhiPuReranker.class);

    @Override
    public String getProviderCode() {
        return "zhipu";
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
                    // zhipu 偏置: 稍偏向 originalScore 高的 (信任初排)
                    double originBoost = clamp(c.originalScore() * 0.08);
                    double score = clamp(base + originBoost + 0.015);
                    return new RerankResult(c.chunkId(), score, c.originalScore());
                })
                .sorted(Comparator.comparingDouble(RerankResult::rerankScore).reversed())
                .limit(topN)
                .toList();

        log.debug("[ZhiPuReranker] queryLen={}, candidates={}, topN={}, apiKeyRef={}",
                query.length(), request.candidates().size(), topN,
                request.apiKeyRef() == null ? "null" : "***");
        return ranked;
    }

    private double mockScore(String query, String text) {
        if (text == null) text = "";
        String q = query.toLowerCase();
        String t = text.toLowerCase();
        // 位置加权: query 词在 text 前半段出现分数更高
        double posScore = 0;
        String[] qTokens = q.split("[^a-z0-9\u4e00-\u9fa5]+");
        int hit = 0;
        for (String tok : qTokens) {
            if (tok.isEmpty()) continue;
            int idx = t.indexOf(tok);
            if (idx >= 0) {
                hit++;
                // 越靠前越高: 前 30% 满分，线性衰减
                double rel = (double) idx / Math.max(1, t.length());
                posScore += Math.max(0, 1 - rel * 1.6);
            }
        }
        double avgPos = qTokens.length == 0 ? 0 : posScore / qTokens.length;
        double hitRate = qTokens.length == 0 ? 0 : (double) hit / qTokens.length;
        int hash = stableHash(q + "|" + t + "|zhipu");
        double hashNorm = (Math.abs(hash % 1000) / 1000.0) * 0.22;
        return clamp(hitRate * 0.45 + avgPos * 0.30 + hashNorm + 0.10);
    }

    private int stableHash(String s) {
        int h = 31;
        for (int i = 0; i < s.length(); i++) h = h * 37 + s.charAt(i);
        return h;
    }

    private double clamp(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
