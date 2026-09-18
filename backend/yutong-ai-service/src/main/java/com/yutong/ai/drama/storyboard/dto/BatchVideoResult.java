package com.yutong.ai.drama.storyboard.dto;

import com.yutong.ai.drama.storyboard.domain.AiDramaStoryboard;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 批量分镜视频生成结果摘要。
 */
@Getter
@Builder
public class BatchVideoResult {

    private String projectId;

    private int total;

    private int succeeded;

    private int failed;

    private List<AiDramaStoryboard> storyboards;

    public static BatchVideoResult of(String projectId, List<AiDramaStoryboard> storyboards) {
        int ok = 0;
        int fail = 0;
        if (storyboards != null) {
            for (AiDramaStoryboard b : storyboards) {
                if (AiDramaStoryboard.STATUS_SUCCEEDED.equals(b.getVideoStatus())) {
                    ok++;
                } else if (AiDramaStoryboard.STATUS_FAILED.equals(b.getVideoStatus())) {
                    fail++;
                }
            }
        }
        return BatchVideoResult.builder()
                .projectId(projectId)
                .total(storyboards == null ? 0 : storyboards.size())
                .succeeded(ok)
                .failed(fail)
                .storyboards(storyboards == null ? List.of() : storyboards)
                .build();
    }
}
