package com.ikeu.server.controller.admin;

import com.ikeu.common.constant.MessageConstant;
import com.ikeu.common.result.Result;
import com.ikeu.server.annotation.OperationLog;
import com.ikeu.server.annotation.RequireRole;
import com.ikeu.server.service.CampusCatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** Manage reference campuses. Campus membership itself is ONLY granted via certification review. */
@RestController
@RequestMapping("/admin/campuses")
@RequiredArgsConstructor
public class AdminCampusController {
    private final CampusCatalogService campuses;
    public record SchoolRequest(@NotBlank String name) {}
    public record CampusRequest(@NotNull Long schoolId, @NotBlank String name) {}

    @RequireRole({1,2})
    @GetMapping("/schools")
    public Result<List<Map<String,Object>>> schools() {
        return Result.success(campuses.listSchoolsForAdmin());
    }

    @RequireRole({1,2})
    @GetMapping
    public Result<List<CampusCatalogService.CampusOption>> list() {
        return Result.success(campuses.listActive());
    }

    @RequireRole({1})
    @OperationLog(module="校区管理", action="新增学校", description="新增学校")
    @PostMapping("/schools")
    public Result<Void> createSchool(@Valid @RequestBody SchoolRequest request) {
        campuses.createSchool(request.name());
        return Result.success(MessageConstant.SUCCESS);
    }

    @RequireRole({1})
    @OperationLog(module="校区管理", action="新增校区", description="新增校区")
    @PostMapping
    public Result<Void> createCampus(@Valid @RequestBody CampusRequest request) {
        campuses.createCampus(request.schoolId(), request.name());
        return Result.success(MessageConstant.SUCCESS);
    }
}
