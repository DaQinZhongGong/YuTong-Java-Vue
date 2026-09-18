package com.yutong.lowcode.copilot.controller;

import com.yutong.lowcode.copilot.dto.CopilotGenerateRequest;
import com.yutong.lowcode.copilot.dto.CopilotGenerateResponse;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.response.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 旧入口已下线。生产草稿请走 POST /api/v1/ai/copilot/generate。
 */
@Tag(name = "Copilot-编程助手")
@RestController
@RequestMapping("/api/v1/copilot")
public class CopilotController {

    @Operation(summary = "已下线：请改用 /api/v1/ai/copilot/generate", operationId = "copilotGenerate")
    @PostMapping("/generate")
    public Result<CopilotGenerateResponse> generate(@Valid @RequestBody CopilotGenerateRequest request) {
        throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                "该 Mock 入口已下线，请使用 POST /api/v1/ai/copilot/generate");
    }
}
