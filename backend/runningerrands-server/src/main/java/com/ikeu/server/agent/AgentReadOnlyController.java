package com.ikeu.server.agent;

import com.ikeu.common.context.BaseContext;
import com.ikeu.common.result.PageResult;
import com.ikeu.common.result.Result;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Internal-only, signed and replay-protected adapter. Never forward arbitrary Mapper/SQL
 * or side-effecting Java Service methods.
 */
@RestController
@RequestMapping("/internal/agent/v1")
@RequiredArgsConstructor
@Validated
public class AgentReadOnlyController {
    private final AgentReadOnlyService service;

    @GetMapping("/tasks/search")
    public Result<PageResult<AgentReadOnlyService.TaskCard>> search(
            @RequestParam(required = false) @Size(max = 80) String keyword,
            @RequestParam(required = false) @Size(max = 32) String type,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(30) int size) {
        return Result.success(service.searchTasks(BaseContext.getCurrentId(), keyword, type, page, size));
    }

    @GetMapping("/tasks/filter")
    public Result<PageResult<AgentReadOnlyService.TaskCard>> filter(
            @RequestParam(required = false) @Size(max = 32) String type,
            @RequestParam(required = false) @Size(max = 80) String pickup,
            @RequestParam(required = false) @Size(max = 80) String delivery,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(30) int size) {
        return Result.success(service.filterTasks(BaseContext.getCurrentId(), type, pickup, delivery, page, size));
    }

    @GetMapping("/offers")
    public Result<PageResult<AgentReadOnlyService.OfferCard>> offers(
            @RequestParam(required = false) @Size(max = 32) String category,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(30) int size) {
        return Result.success(service.offers(BaseContext.getCurrentId(), category, page, size));
    }


    @GetMapping("/tasks/{taskId}")
    public Result<AgentReadOnlyService.TaskCard> taskDetail(@PathVariable @Min(1) Long taskId) {
        return Result.success(service.taskDetail(BaseContext.getCurrentId(), taskId));
    }

    @GetMapping("/offers/{offerId}")
    public Result<AgentReadOnlyService.OfferCard> offerDetail(@PathVariable @Min(1) Long offerId) {
        return Result.success(service.offerDetail(BaseContext.getCurrentId(), offerId));
    }

    @GetMapping("/campus")
    public Result<List<AgentReadOnlyService.CampusPlace>> campus() {
        return Result.success(service.campus(BaseContext.getCurrentId()));
    }

    @GetMapping("/tasks/{taskId}/eligibility")
    public Result<AgentReadOnlyService.Eligibility> eligibility(@PathVariable @Min(1) Long taskId) {
        return Result.success(service.eligibility(BaseContext.getCurrentId(), taskId));
    }
}
