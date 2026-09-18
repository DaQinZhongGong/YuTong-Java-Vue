package com.yutong.sample.drama.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 短剧场景实体。
 * 设计来源: V041 drama_scene, 归属 drama_script, scene_no 剧内唯一
 */
@Getter
@Setter
@TableName(value = "drama_scene", autoResultMap = true)
public class DramaScene extends BaseEntity {

    /** 所属剧本 ID */
    private String dramaId;

    /** 场次序号 (>=1, 剧内唯一) */
    private Integer sceneNo;

    /** 场景描述/台词/分镜 */
    private String description;

    /** 角色一致性 JSON */
    @TableField("character_consistency_json")
    private String characterConsistencyJson;
}
