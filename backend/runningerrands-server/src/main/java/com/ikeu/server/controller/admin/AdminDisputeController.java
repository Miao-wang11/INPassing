package com.ikeu.server.controller.admin;

import com.ikeu.common.context.BaseContext;
import com.ikeu.common.result.PageResult;
import com.ikeu.common.result.Result;
import com.ikeu.server.annotation.OperationLog;
import com.ikeu.server.annotation.RequireRole;
import com.ikeu.server.service.OrderDisputeService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/admin/disputes")
@RequiredArgsConstructor
@Validated
public class AdminDisputeController {
    private final OrderDisputeService disputes;

    @RequireRole({1,2})
    @GetMapping
    public Result<PageResult<OrderDisputeService.CaseView>> list(
            @RequestParam(required=false) Integer status,
            @RequestParam(defaultValue="1") @Min(1) int page,
            @RequestParam(defaultValue="10") @Min(1) @Max(100) int size) {
        return Result.success(disputes.adminCases(status,page,size));
    }
    @RequireRole({1,2})
    @GetMapping("/{id}")
    public Result<OrderDisputeService.CaseView> detail(@PathVariable Long id) {
        return Result.success(disputes.getById(id));
    }
    @RequireRole({1,2})
    @GetMapping("/{id}/events")
    public Result<List<OrderDisputeService.CaseEvent>> events(@PathVariable Long id) {
        return Result.success(disputes.events(id));
    }
    @RequireRole({1})
    @OperationLog(module="售后纠纷",action="仲裁",description="售后单 #id 仲裁")
    @PostMapping("/{id}/decision")
    public Result<OrderDisputeService.CaseView> decide(@PathVariable Long id,
            @RequestBody OrderDisputeService.DecisionForm form) {
        return Result.success(disputes.decide(BaseContext.getCurrentId(),id,form));
    }
}
