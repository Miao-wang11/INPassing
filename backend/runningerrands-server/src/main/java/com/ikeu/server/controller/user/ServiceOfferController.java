package com.ikeu.server.controller.user;

import com.ikeu.common.context.BaseContext;
import com.ikeu.common.result.PageResult;
import com.ikeu.common.result.Result;
import com.ikeu.server.annotation.RequireCertify;
import com.ikeu.server.service.ServiceOfferService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/service-offers")
@RequiredArgsConstructor
@Validated
public class ServiceOfferController {
    private final ServiceOfferService service;

    @RequireCertify
    @GetMapping
    public Result<PageResult<ServiceOfferService.OfferView>> list(
            @RequestParam(required=false) String category,
            @RequestParam(defaultValue="1") @Min(1) int page,
            @RequestParam(defaultValue="10") @Min(1) @Max(100) int size) {
        return Result.success(service.hall(BaseContext.getCurrentId(), category, page,size));
    }

    @RequireCertify
    @GetMapping("/mine")
    public Result<List<ServiceOfferService.OfferView>> mine() {
        return Result.success(service.mine(BaseContext.getCurrentId()));
    }

    @RequireCertify
    @GetMapping("/{id}")
    public Result<ServiceOfferService.OfferView> detail(@PathVariable Long id) {
        return Result.success(service.detail(BaseContext.getCurrentId(),id));
    }

    @RequireCertify
    @PostMapping
    public Result<Long> publish(@RequestBody ServiceOfferService.OfferForm form) {
        return Result.success(service.publish(BaseContext.getCurrentId(),form));
    }

    @RequireCertify
    @PutMapping("/{id}")
    public Result<Void> edit(@PathVariable Long id, @RequestBody ServiceOfferService.OfferForm form) {
        service.edit(BaseContext.getCurrentId(),id,form);
        return Result.success();
    }

    @RequireCertify
    @PutMapping("/{id}/status")
    public Result<Void> status(@PathVariable Long id,@RequestParam int status) {
        service.changeStatus(BaseContext.getCurrentId(),id,status);
        return Result.success();
    }

    @RequireCertify
    @PostMapping("/{id}/book")
    public Result<ServiceOfferService.BookingReceipt> book(
            @PathVariable Long id, @RequestBody ServiceOfferService.BookingForm form) {
        return Result.success(service.book(BaseContext.getCurrentId(),id,form));
    }
}
