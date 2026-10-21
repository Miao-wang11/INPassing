package com.ikeu.server.agent;

import com.ikeu.common.context.BaseContext;
import com.ikeu.common.result.Result;
import com.ikeu.model.vo.OrderAcceptVO;
import com.ikeu.server.annotation.RequireCertify;
import com.ikeu.server.service.TaskOrderService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Existing JWT protected endpoint for an explicit user click.
 * Agent ranking never calls acceptOrder. The original Java Service rechecks
 * campus, worker eligibility, task validity, and performs concurrent claim CAS.
 */
@RestController
@RequestMapping("/agent/matches")
@RequiredArgsConstructor
@Validated
public class AgentMatchController {
    private final TaskOrderService orders;

    @RequireCertify
    @PostMapping("/accept/{taskId}")
    public Result<OrderAcceptVO> accept(@PathVariable @Min(1) Long taskId) {
        Long orderId = orders.acceptOrder(BaseContext.getCurrentId(),taskId);
        return Result.success(OrderAcceptVO.builder().orderId(orderId).build());
    }
}
