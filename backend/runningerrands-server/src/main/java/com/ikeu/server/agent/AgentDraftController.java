package com.ikeu.server.agent;

import com.ikeu.common.context.BaseContext;
import com.ikeu.common.result.Result;
import com.ikeu.server.annotation.RequireCertify;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** JWT authenticated Java-only side-effect boundary. Payment secrets never enter Python. */
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
@Validated
public class AgentDraftController {
    private final AgentDraftService drafts;

    public record Confirm(@NotBlank String confirmToken, @NotBlank String payPassword) {}

    @RequireCertify
    @PostMapping("/drafts")
    public Result<AgentDraftService.Prepared> prepare(
            @RequestBody AgentDraftService.DraftInput input) {
        return Result.success(drafts.prepare(BaseContext.getCurrentId(), input));
    }

    @RequireCertify
    @PostMapping("/operations/{id}/confirm")
    public Result<AgentDraftService.Receipt> confirm(
            @PathVariable @Pattern(regexp = "^[a-f0-9-]{36}$") String id,
            @Valid @RequestBody Confirm input) {
        return Result.success(drafts.confirm(BaseContext.getCurrentId(), id,
                input.confirmToken(), input.payPassword()));
    }

    @RequireCertify
    @GetMapping("/operations/{id}")
    public Result<AgentDraftService.Receipt> receipt(
            @PathVariable @Pattern(regexp = "^[a-f0-9-]{36}$") String id) {
        return Result.success(drafts.receipt(BaseContext.getCurrentId(), id));
    }
}
