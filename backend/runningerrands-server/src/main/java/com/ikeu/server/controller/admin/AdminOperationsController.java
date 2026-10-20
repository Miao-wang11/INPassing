package com.ikeu.server.controller.admin;

import com.ikeu.common.result.Result;
import com.ikeu.server.annotation.RequireRole;
import com.ikeu.server.service.OperationsAuditService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/admin/operations")
@RequiredArgsConstructor
@Validated
public class AdminOperationsController {
    private final OperationsAuditService operations;

    @RequireRole({1,2})
    @GetMapping("/summary")
    public Result<OperationsAuditService.Summary> summary() {
        return Result.success(operations.summary());
    }
    @RequireRole({1,2})
    @GetMapping("/ledger/{taskId}")
    public Result<OperationsAuditService.LedgerCheck> check(@PathVariable Long taskId) {
        return Result.success(operations.inspect(taskId));
    }
    @RequireRole({1,2})
    @GetMapping("/anomalies")
    public Result<List<OperationsAuditService.LedgerCheck>> anomalies(
            @RequestParam(defaultValue="100") @Min(1) @Max(200) int limit) {
        return Result.success(operations.suspicious(limit));
    }
}
