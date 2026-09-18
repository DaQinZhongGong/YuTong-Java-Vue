package com.yutong.ai.drama.storyboard.dto;

import com.yutong.ai.drama.storyboard.domain.AiDramaProject;
import com.yutong.ai.drama.storyboard.domain.AiDramaStoryboard;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 项目详情 VO — 扁平化项目字段 + storyboards，与前端 DramaProject 契约一致。
 */
@Getter
@Builder
public class ProjectDetailVO {

    private String id;
    private String title;
    private String synopsis;
    private String artStyle;
    private String styleRef;
    private String aspectRatio;
    private String composeStatus;
    private String composeJobId;
    private String composedPath;
    private List<AiDramaStoryboard> storyboards;
    private String createdTime;
    private String updatedTime;

    public static ProjectDetailVO of(AiDramaProject project, List<AiDramaStoryboard> storyboards) {
        if (project == null) {
            return ProjectDetailVO.builder().storyboards(List.of()).build();
        }
        return ProjectDetailVO.builder()
                .id(project.getId())
                .title(project.getTitle())
                .synopsis(project.getSynopsis())
                .artStyle(project.getArtStyle())
                .styleRef(project.getStyleRef())
                .aspectRatio(project.getAspectRatio())
                .composeStatus(project.getComposeStatus())
                .composeJobId(project.getComposeJobId())
                .composedPath(project.getComposedPath())
                .storyboards(storyboards == null ? List.of() : storyboards)
                .createdTime(project.getCreatedTime() == null ? null : project.getCreatedTime().toString())
                .updatedTime(project.getUpdatedTime() == null ? null : project.getUpdatedTime().toString())
                .build();
    }

    /** 兼容测试/服务侧：由扁平字段重建项目实体。 */
    public AiDramaProject getProject() {
        if (id == null) {
            return null;
        }
        AiDramaProject p = new AiDramaProject();
        p.setId(id);
        p.setTitle(title);
        p.setSynopsis(synopsis);
        p.setArtStyle(artStyle);
        p.setStyleRef(styleRef);
        p.setAspectRatio(aspectRatio);
        p.setComposeStatus(composeStatus);
        p.setComposeJobId(composeJobId);
        p.setComposedPath(composedPath);
        return p;
    }
}
