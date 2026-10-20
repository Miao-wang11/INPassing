package com.ikeu.server.controller.user;

import com.ikeu.common.result.Result;
import com.ikeu.server.service.CampusCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** Public catalog only. No student membership can be changed here. */
@RestController
@RequestMapping("/campus")
@RequiredArgsConstructor
public class CampusController {
    private final CampusCatalogService campuses;

    @GetMapping("/list")
    public Result<List<CampusCatalogService.CampusOption>> list() {
        return Result.success(campuses.listActive());
    }
}
