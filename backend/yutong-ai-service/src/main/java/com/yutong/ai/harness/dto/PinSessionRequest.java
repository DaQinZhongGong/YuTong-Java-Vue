package com.yutong.ai.harness.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 置顶请求。pinned=false 取消置顶。
 */
@Getter
@Setter
public class PinSessionRequest {
    private boolean pinned = true;
}
