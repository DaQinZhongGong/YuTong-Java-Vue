package com.yutong.ai.media.provider;

import com.yutong.ai.media.domain.MediaJob;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;

/**
 * 已退役：禁止返回假 URL。任何调用直接失败。
 */
public class MockMediaProvider implements MediaProvider {

    @Override
    public String providerCode() {
        return "mock";
    }

    @Override
    public boolean supports(String mediaType) {
        return false;
    }

    @Override
    public GenerateResult generate(MediaJob job) {
        throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "MockMediaProvider 已退役，禁止假 URL");
    }
}
