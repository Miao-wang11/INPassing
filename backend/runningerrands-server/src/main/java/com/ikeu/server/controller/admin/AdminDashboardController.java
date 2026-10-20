package com.ikeu.server.controller.admin;

import com.ikeu.common.result.Result;
import com.ikeu.model.vo.DashboardVO;
import com.ikeu.server.annotation.RequireRole;
import com.ikeu.server.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端仪表盘接口，提供平台运营数据统计（用户数、任务数、订单数、收益等）。
 * @author ikeu
 * @since 2025/06/01
 */
@Tag(name = "管理端-仪表盘接口")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @RequireRole({1, 2})
    @Operation(summary = "仪表盘统计")
    @GetMapping("/dashboard")
    public Result<DashboardVO> dashboard() {
        DashboardVO summary = adminDashboardService.getDashboardSummary();
        summary.setUserTrend(adminDashboardService.getUserTrend());
        summary.setRevenueTrend(adminDashboardService.getRevenueTrend());
        summary.setTaskCategories(adminDashboardService.getTaskCategories());
        summary.setOrderStatusDistribution(adminDashboardService.getOrderStatusDistribution());
        return Result.success(summary);
    }
}
