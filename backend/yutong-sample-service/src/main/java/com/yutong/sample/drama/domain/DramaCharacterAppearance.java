package com.yutong.sample.drama.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色形象实体 — 同一角色多形象，isSelected 标记选中。
 * 设计来源: V043 drama_character_appearance
 */
@Getter
@Setter
@TableName(value = "drama_character_appearance", autoResultMap = true)
public class DramaCharacterAppearance extends BaseEntity {

    /** 所属角色 ID (drama_character.id) */
    private String characterId;

    /** 形象 JSON：{prompt, style, age, gender, clothing, ...} */
    @TableField("appearance_json")
    private String appearanceJson;

    /** 形象图片 URL（生成结果） */
    private String imageUrl;

    /** 是否选中形象（同一角色仅一选中，由业务层保证） */
    @TableField("is_selected")
    private Boolean isSelected;
}
