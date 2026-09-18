package com.yutong.ai.drama.controller;

import com.yutong.ai.drama.dto.DramaGenerateRequest;
import com.yutong.ai.drama.service.DramaGenerateService;
import com.yutong.auth.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "AI-短剧生成")
@RestController
@RequestMapping("/api/v1/ai/drama")
public class DramaGenerateController {

    private final DramaGenerateService generateService;

    public DramaGenerateController(DramaGenerateService generateService) {
        this.generateService = generateService;
    }

    @Operation(summary = "短剧 6 阶段 SSE 生成（不落库）", operationId = "generateDramaStages")
    @RequiresPermission("ai:assistant:use")
    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generate(@Valid @RequestBody DramaGenerateRequest request) {
        return generateService.generate(request);
    }
}
