package com.yutong.sample.drama.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 短剧场景地实体。
 * 设计来源: V043 drama_location, 归属 drama_script
 */
@Getter
@Setter
@TableName(value = "drama_location", autoResultMap = true)
public class DramaLocation extends BaseEntity {

    /** 所属剧本 ID (drama_script.id) */
    private String dramaId;

    /** 场景地名称 */
    private String name;

    /** 场景描述 */
    private String description;

    /** 场景图片 URL */
    private String imageUrl;
}
