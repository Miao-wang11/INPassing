package com.ikeu.server.controller.admin;

import com.ikeu.common.constant.MessageConstant;
import com.ikeu.common.result.Result;
import com.ikeu.model.dto.SystemConfigBatchUpdateDTO;
import com.ikeu.model.vo.SystemConfigVO;
import com.ikeu.server.annotation.OperationLog;
import com.ikeu.server.annotation.RequireRole;
import com.ikeu.server.service.AdminSystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端系统配置接口，仅超管可访问。
 * @author ikeu
 * @since 2026/06/18
 */
@Tag(name = "管理端-系统配置接口")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminSystemConfigController {

    private final AdminSystemConfigService adminSystemConfigService;

    /**
     * 获取全部系统配置
     * @return 配置项 VO 列表
     */
    @Operation(summary = "获取全部系统配置")
    @GetMapping("/settings")
    @RequireRole({1})
    public Result<List<SystemConfigVO>> listAll() {
        return Result.success(adminSystemConfigService.listAll());
    }

    /**
     * 批量更新系统配置
     * @param dto 批量更新请求 DTO
     * @return 操作结果
     */
    @OperationLog(module = "系统设置", action = "修改", description = "批量更新系统配置")
    @Operation(summary = "批量更新系统配置")
    @PutMapping("/settings")
    @RequireRole({1})
    public Result<Void> batchUpdate(@Valid @RequestBody SystemConfigBatchUpdateDTO dto) {
        adminSystemConfigService.batchUpdate(dto);
        return Result.success(MessageConstant.SYSTEM_CONFIG_UPDATE_SUCCESS);
    }

}
