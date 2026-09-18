package com.yutong.sample.drama.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 短剧剧本实体。
 * 设计来源: V041 drama_script, Phase 7 垂类占位短剧垂类最小闭环
 */
@Getter
@Setter
@TableName(value = "drama_script", autoResultMap = true)
public class DramaScript extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 剧本标题 */
    private String title;

    /** 梗概 */
    private String synopsis;

    /** 状态: DRAFT/PUBLISHED/ARCHIVED */
    private String status;
}

