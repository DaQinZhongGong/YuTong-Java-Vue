package com.yutong.system.auth.online;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.Result;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 在线用户监控接口。
 * 落点: 业界同类实现 SysUserOnlineController + ADR 0005 P1-D。
 *
 * <p>端点:
 * <ul>
 *   <li>GET /api/v1/monitor/online — 在线用户列表 (keyword 可选)</li>
 *   <li>DELETE /api/v1/monitor/online/{tokenId} — 强制下线</li>
 * </ul>
 */
@Tag(name = "监控-在线用户")
@RestController
@RequestMapping("/api/v1/monitor/online")
public class OnlineUserController {

    private final OnlineUserService service;

    public OnlineUserController(OnlineUserService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission("system:monitor:online")
    @Operation(summary = "在线用户列表")
    public Result<List<OnlineUserSession>> list(
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.listByKeyword(keyword));
    }

    @DeleteMapping("/{tokenId}")
    @RequiresPermission("system:monitor:online")
    @Auditable(operationType = "FORCE_LOGOUT", module = "system", bizType = "online_user",
            bizIdExpr = "#tokenId", content = "强制下线")
    @Operation(summary = "强制下线 (踢人)")
    public Result<Boolean> kick(@PathVariable("tokenId") String tokenId) {
        return Result.ok(service.kick(tokenId));
    }
}
