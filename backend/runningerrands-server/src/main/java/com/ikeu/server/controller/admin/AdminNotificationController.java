package com.ikeu.server.controller.admin;

import com.ikeu.common.constant.MessageConstant;
import com.ikeu.common.result.Result;
import com.ikeu.model.dto.NotificationBroadcastDTO;
import com.ikeu.model.dto.NotificationSendDTO;
import com.ikeu.server.annotation.OperationLog;
import com.ikeu.server.annotation.RequireRole;
import com.ikeu.server.service.AdminNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端消息管理接口，提供向指定用户发送通知和全站广播通知功能。
 * @author ikeu
 * @since 2025/06/02
 */
@Tag(name = "管理端-消息管理接口")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final AdminNotificationService adminNotificationService;

    @OperationLog(module = "消息管理", action = "发送", description = "向指定用户发送通知")
    @RequireRole({1, 2})
    @Operation(summary = "向指定用户发送通知")
    @PostMapping("/notifications/send")
    public Result<Void> send(@Valid @RequestBody NotificationSendDTO dto) {
        adminNotificationService.sendToUsers(dto);
        return Result.success(MessageConstant.SUCCESS);
    }

    @OperationLog(module = "消息管理", action = "广播", description = "向所有用户广播通知")
    @RequireRole({1, 2})
    @Operation(summary = "向所有用户广播通知")
    @PostMapping("/notifications/broadcast")
    public Result<Void> broadcast(@Valid @RequestBody NotificationBroadcastDTO dto) {
        adminNotificationService.broadcastToAll(dto);
        return Result.success(MessageConstant.SUCCESS);
    }
}
