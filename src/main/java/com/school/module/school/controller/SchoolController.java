package com.school.module.school.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.result.Result;
import com.school.module.school.dto.SchoolCreateRequest;
import com.school.module.school.dto.SchoolQueryRequest;
import com.school.module.school.dto.SchoolResponse;
import com.school.module.school.service.SchoolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 学校管理控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/api/schools")
@RequiredArgsConstructor
public class SchoolController {

    private final SchoolService schoolService;

    @PostMapping
    public Result<SchoolResponse> create(@Valid @RequestBody SchoolCreateRequest request) {
        return Result.success(schoolService.create(request));
    }

    @GetMapping("/{id}")
    public Result<SchoolResponse> getById(@PathVariable Long id) {
        return Result.success(schoolService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<SchoolResponse> update(@PathVariable Long id,
                                         @Valid @RequestBody SchoolCreateRequest request) {
        return Result.success(schoolService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        schoolService.delete(id);
        return Result.success();
    }

    @GetMapping
    public Result<Page<SchoolResponse>> list(SchoolQueryRequest request) {
        return Result.success(schoolService.listSchools(request));
    }

    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        schoolService.updateStatus(id, status);
        return Result.success();
    }

}
