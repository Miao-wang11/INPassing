package com.ikeu.server.controller.user;

import com.ikeu.common.context.BaseContext;
import com.ikeu.common.result.Result;
import com.ikeu.server.service.OrderDisputeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/disputes")
@RequiredArgsConstructor
public class OrderDisputeController {
    private final OrderDisputeService disputes;

    @PostMapping("/orders/{orderId}")
    public Result<OrderDisputeService.CaseView> apply(
            @PathVariable Long orderId,@RequestBody OrderDisputeService.ApplyForm form) {
        return Result.success(disputes.apply(BaseContext.getCurrentId(),orderId,form));
    }
    @GetMapping("/mine")
    public Result<List<OrderDisputeService.CaseView>> mine() {
        return Result.success(disputes.mine(BaseContext.getCurrentId()));
    }
    @GetMapping("/{id}")
    public Result<OrderDisputeService.CaseView> detail(@PathVariable Long id) {
        return Result.success(disputes.getForParty(BaseContext.getCurrentId(),id));
    }
    @GetMapping("/{id}/events")
    public Result<List<OrderDisputeService.CaseEvent>> events(@PathVariable Long id) {
        return Result.success(disputes.eventsForParty(BaseContext.getCurrentId(),id));
    }
}
