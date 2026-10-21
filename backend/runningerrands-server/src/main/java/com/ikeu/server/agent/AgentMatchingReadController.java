package com.ikeu.server.agent;

import com.ikeu.common.context.BaseContext;
import com.ikeu.common.result.Result;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Signed internal GET routes. Registered with existing AgentInternalAuthInterceptor. */
@RestController
@RequestMapping("/internal/agent/v1/matching")
@RequiredArgsConstructor
@Validated
public class AgentMatchingReadController {
    private final AgentReadOnlyService tasks;
    private final AgentWalkNetworkService network;

    @GetMapping("/candidates")
    public Result<AgentReadOnlyService.CandidateBatch> candidates(
            @RequestParam(defaultValue = "1") @Min(1) @Max(5) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(30) int size) {
        return Result.success(tasks.matchingCandidates(BaseContext.getCurrentId(),page,size));
    }

    @GetMapping("/network")
    public Result<AgentWalkNetworkService.Network> network() {
        return Result.success(network.verifiedForUser(BaseContext.getCurrentId()));
    }
}
