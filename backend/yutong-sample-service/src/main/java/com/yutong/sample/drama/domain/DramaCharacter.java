package com.yutong.sample.drama.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 短剧角色实体。
 * 设计来源: V043 drama_character, 归属 drama_script
 */
@Getter
@Setter
@TableName(value = "drama_character", autoResultMap = true)
public class DramaCharacter extends BaseEntity {

    /** 所属剧本 ID (drama_script.id) */
    private String dramaId;

    /** 角色名称 */
    private String name;

    /** 角色定位（主角/配角/反派等） */
    private String role;

    /** 角色描述/人设 */
    private String description;
}
