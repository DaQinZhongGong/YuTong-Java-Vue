package com.yutong.ai.drama.storyboard.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.media.domain.MediaJob;
import com.yutong.ai.media.service.MediaService;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认 {@link VideoGateway} 实现 — 委托 {@link MediaService#generateSync}。
 *
 * <p>失败关闭: 无 provider Key / 不支持 video 类型时 MediaService 抛业务异常,
 * 本类原样上抛, 绝不返回假 URL。
 */
@Component
public class MediaServiceVideoGateway implements VideoGateway {

    private static final Logger log = LoggerFactory.getLogger(MediaServiceVideoGateway.class);

    private final MediaService mediaService;
    private final ObjectMapper objectMapper;

    public MediaServiceVideoGateway(MediaService mediaService, ObjectMapper objectMapper) {
        this.mediaService = mediaService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Result generate(Command command) {
        if (command == null || command.prompt() == null || command.prompt().isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "视频提示词不能为空");
        }
        String prompt = buildPrompt(command);
        try {
            MediaJob job = mediaService.generateSync(MediaJob.TYPE_VIDEO, prompt);
            if (job == null || job.getOutputUrl() == null || job.getOutputUrl().isBlank()) {
                throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR,
                        "视频生成未返回有效 URL (失败关闭)");
            }
            String lastFrameUrl = extractJsonField(job.getOutputJson(), "lastFrameUrl");
            String videoId = firstNonBlank(
                    extractJsonField(job.getOutputJson(), "videoId"),
                    job.getExternalJobId());
            return new Result(job.getOutputUrl(), lastFrameUrl, videoId, job.getId());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("storyboard video gateway failed", e);
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR,
                    "分镜视频生成失败: " + e.getMessage());
        }
    }

    /**
     * 拼装完整提示词: 模式说明 + 风格/画幅 + 原始 video_prompt。
     * 参考图/首帧以文本标注进 prompt (底层 MediaService 仅接受 prompt 字符串);
     * 真实 image-to-video 多模态入参由后续 provider 扩展承载。
     */
    String buildPrompt(Command command) {
        StringBuilder sb = new StringBuilder();
        if (command.isMultiReference()) {
            sb.append("[image-to-video multi-reference] 参考图 ")
                    .append(command.referenceImages().size())
                    .append(" 张, 保持角色与场景一致性。");
        } else if (command.hasReferenceImage()) {
            sb.append("[image-to-video] 以参考图为首帧/风格锚点。");
        } else {
            sb.append("[text-to-video] ");
        }
        if (command.hasFirstFrame()) {
            sb.append("[first-frame continuity] 以提供的末帧作为本镜首帧承接。");
        }
        if (command.aspectRatio() != null && !command.aspectRatio().isBlank()) {
            sb.append("画幅 ").append(command.aspectRatio()).append("。");
        }
        if (command.durationSeconds() != null) {
            sb.append("时长约 ").append(command.durationSeconds()).append(" 秒。");
        }
        if (command.styleRef() != null && !command.styleRef().isBlank()) {
            sb.append("风格参考: ").append(command.styleRef()).append("。");
        }
        List<String> refs = command.referenceImages();
        if (refs != null && !refs.isEmpty()) {
            sb.append("参考图 URL: ").append(String.join(", ", refs)).append("。");
        }
        if (command.hasFirstFrame()) {
            sb.append("首帧 URL: ").append(command.firstFrameUrl()).append("。");
        }
        sb.append(command.prompt().trim());
        return sb.toString();
    }

    private String extractJsonField(String json, String key) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json).path(key);
            if (node.isMissingNode() || node.isNull()) {
                return null;
            }
            String text = node.asText(null);
            return text == null || text.isBlank() ? null : text;
        } catch (Exception e) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
