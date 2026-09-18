package com.yutong.sample.drama.dto;

import com.yutong.sample.drama.domain.DramaScene;
import com.yutong.sample.drama.domain.DramaScript;
import lombok.Data;

import java.util.List;

@Data
public class DramaDetailVO {
    private DramaScript script;
    private List<DramaScene> scenes;

    public static DramaDetailVO of(DramaScript script, List<DramaScene> scenes) {
        DramaDetailVO vo = new DramaDetailVO();
        vo.setScript(script);
        vo.setScenes(scenes);
        return vo;
    }
}
