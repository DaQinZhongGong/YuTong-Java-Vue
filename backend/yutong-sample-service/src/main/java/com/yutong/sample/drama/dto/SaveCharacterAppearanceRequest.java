package com.yutong.sample.drama.dto;

import lombok.Data;

@Data
public class SaveCharacterAppearanceRequest {
    private String appearanceJson;
    private String imageUrl;
    private Boolean isSelected;
    private String remark;
}
