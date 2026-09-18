package com.yutong.ai.media.provider;

import com.yutong.ai.media.domain.MediaJob;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 视频/PPT 在未配置专用供应商时给出明确失败，禁止假 URL。
 */
@Component
public class UnsupportedMediaProvider implements MediaProvider {

    @Override
    public String providerCode() {
        return "unsupported";
    }

    @Override
    public boolean supports(String mediaType) {
        return MediaJob.TYPE_VIDEO.equals(mediaType) || MediaJob.TYPE_PPT.equals(mediaType);
    }

    @Override
    public GenerateResult generate(MediaJob job) {
        throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                "未配置可用的 " + job.getMediaType()
                        + " 供应商。请在「供应商管理」登记 model_type=" + job.getMediaType()
                        + " 的端点后重试，系统不会返回假 URL。");
    }
}
