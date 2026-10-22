package com.ikeu.server.agent;

import com.ikeu.common.context.BaseContext;
import com.ikeu.common.result.Result;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
            @RequestParam(defaultValue = "20") @Min(1) @Max(30) int size,
            @RequestParam(required = false) @Size(max = 80) String keyword,
            @RequestParam(required = false) @Size(max = 32) String type,
            @RequestParam(required = false) @Size(max = 80) String pickup,
            @RequestParam(required = false) @Size(max = 80) String delivery) {
        return Result.success(tasks.matchingCandidates(BaseContext.getCurrentId(),page,size,
                keyword,type,pickup,delivery));
    }

    @GetMapping("/network")
    public Result<AgentWalkNetworkService.Network> network() {
        return Result.success(network.verifiedForUser(BaseContext.getCurrentId()));
    }
}
