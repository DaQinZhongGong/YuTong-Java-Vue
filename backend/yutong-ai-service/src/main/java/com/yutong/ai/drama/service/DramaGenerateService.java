package com.yutong.ai.drama.service;

import com.yutong.ai.chat.service.llm.LlmMessage;
import com.yutong.ai.chat.service.llm.LlmProviderSelector;
import com.yutong.ai.chat.service.llm.LlmRequest;
import com.yutong.ai.chat.service.llm.LlmResponse;
import com.yutong.ai.drama.dto.DramaGenerateRequest;
import com.yutong.ai.media.domain.MediaJob;
import com.yutong.ai.media.service.MediaService;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 短剧 6 阶段生成：概念 / 剧本 / 角色 / 分镜 / 画面提示 / 表演提示。
 * 每阶段真实调用已启用 LLM；画面阶段再调用文生图，经 SSE 推送。
 */
@Service
public class DramaGenerateService {

    private static final Logger log = LoggerFactory.getLogger(DramaGenerateService.class);

    private static final List<Stage> STAGES = List.of(
            new Stage("concept", "概念", "用 120 字以内给出短剧概念、类型、冲突与结局钩子。"),
            new Stage("script", "剧本", "写 3 个场景的简版剧本，每场含场景号、地点、2-4 句对白。"),
            new Stage("character", "角色", "列出 2-3 个角色：姓名、定位、外形、性格、关系。"),
            new Stage("storyboard", "分镜", "为每个场景给 2 个分镜：机位、运动、画面重点。"),
            new Stage("image", "画面", "为每个分镜写一句可直接用于文生图的中文提示词，含光影与构图。"),
            new Stage("acting", "表演", "为关键对白标注情绪、节奏、停顿与配音提示。")
    );

    private final LlmProviderSelector providerSelector;
    private final MediaService mediaService;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "drama-generate");
        t.setDaemon(true);
        return t;
    });

    public DramaGenerateService(LlmProviderSelector providerSelector, MediaService mediaService) {
        this.providerSelector = providerSelector;
        this.mediaService = mediaService;
    }

    public SseEmitter generate(DramaGenerateRequest request) {
        var runtime = providerSelector.selectEnabledProvider()
                .orElseThrow(() -> new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                        "未启用可用 LLM，无法生成短剧阶段内容"));
        String tenantId = CurrentUserContext.getTenantId();
        String userId = CurrentUserContext.getUserId();
        String username = CurrentUserContext.getUsername();
        SseEmitter emitter = new SseEmitter(180_000L);
        executor.execute(() -> {
            try {
                CurrentUserContext.set(userId, tenantId, username);
                StringBuilder prior = new StringBuilder();
                int index = 1;
                for (Stage stage : STAGES) {
                    String prompt = buildPrompt(request, stage, prior.toString());
                    LlmResponse resp = runtime.adapter().chat(new LlmRequest(
                            runtime.defaultModel(),
                            List.of(
                                    LlmMessage.system("你是短剧编剧助手。只输出该阶段正文，不要标题装饰，不要 Markdown 代码块。"),
                                    LlmMessage.user(prompt)
                            )));
                    if (resp == null || resp.isError() || resp.content() == null || resp.content().isBlank()) {
                        throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR,
                                stage.label() + " 阶段生成失败");
                    }
                    String content = resp.content().trim();
                    prior.append('\n').append('[').append(stage.label()).append("]\n").append(content).append('\n');
                    Map<String, Object> payload = new java.util.LinkedHashMap<>();
                    payload.put("stage", stage.code());
                    payload.put("label", stage.label());
                    payload.put("index", index);
                    payload.put("total", STAGES.size());
                    payload.put("content", content);
                    if ("image".equals(stage.code())) {
                        String imagePrompt = firstImagePrompt(content, request.getTitle(),
                                request.getCharacterLock(), request.getStyleOrDefault(),
                                request.getAspectRatioOrDefault());
                        MediaJob job = mediaService.generateSync(MediaJob.TYPE_IMAGE, imagePrompt);
                        payload.put("imageUrl", job.getOutputUrl());
                        payload.put("mediaJobId", job.getId());
                    }
                    emitter.send(SseEmitter.event().name("stage").data(payload));
                    index++;
                }
                emitter.send(SseEmitter.event().name("done").data(Map.of("status", "SUCCESS")));
                emitter.complete();
            } catch (Exception e) {
                log.error("drama generate failed title={}", request.getTitle(), e);
                try {
                    String msg = e.getMessage() == null ? "unknown" : e.getMessage().replace("\"", "'");
                    emitter.send(SseEmitter.event().name("error").data(Map.of("error", msg)));
                } catch (Exception ignored) {
                    // ignore
                }
                emitter.completeWithError(e);
            } finally {
                CurrentUserContext.clear();
            }
        });
        return emitter;
    }

    private String buildPrompt(DramaGenerateRequest request, Stage stage, String prior) {
        StringBuilder sb = new StringBuilder();
        sb.append("短剧标题：").append(request.getTitle()).append('\n');
        if (request.getSynopsis() != null && !request.getSynopsis().isBlank()) {
            sb.append("梗概：").append(request.getSynopsis()).append('\n');
        }
        if (!prior.isBlank()) {
            sb.append("已完成前序阶段：\n").append(prior).append('\n');
        }
        sb.append("当前阶段：").append(stage.label()).append('\n');
        sb.append("任务：").append(stage.instruction());
        return sb.toString();
    }

    private String firstImagePrompt(String content, String title, String characterLock,
                                     String style, String aspectRatio) {
        String base;
        if (content == null || content.isBlank()) {
            base = "电影感分镜，标题《" + title + "》，" + aspectRatio + "，清晰光影";
        } else {
            String[] lines = content.split("\\R");
            String picked = content;
            for (String line : lines) {
                String t = line.trim();
                if (t.length() >= 12) {
                    picked = t;
                    break;
                }
            }
            base = picked.length() > 360 ? picked.substring(0, 360) : picked;
        }
        // 画风修饰
        String styleDesc = switch (style) {
            case "anime" -> "，日系动漫风格";
            case "cartoon" -> "，卡通风格";
            case "cinematic" -> "，电影级画质，浅景深";
            default -> "，写实风格";
        };
        base = base + styleDesc + "，" + aspectRatio + " 画幅";
        if (characterLock != null && !characterLock.isBlank()) {
            return base + "。角色一致性锁定：" + characterLock.trim() + "。同一角色外貌、服装、发色必须与锁定描述一致。";
        }
        return base;
    }

    private record Stage(String code, String label, String instruction) {}
}
