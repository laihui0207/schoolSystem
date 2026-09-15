package com.school.module.term.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.result.Result;
import com.school.module.term.dto.AcademicTermCreateRequest;
import com.school.module.term.dto.AcademicTermQueryRequest;
import com.school.module.term.dto.AcademicTermResponse;
import com.school.module.term.service.AcademicTermService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 学期管理控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/api/terms")
@RequiredArgsConstructor
public class AcademicTermController {

    private final AcademicTermService academicTermService;

    @PostMapping
    public Result<AcademicTermResponse> create(@Valid @RequestBody AcademicTermCreateRequest request) {
        return Result.success(academicTermService.create(request));
    }

    @GetMapping("/{id}")
    public Result<AcademicTermResponse> getById(@PathVariable Long id) {
        return Result.success(academicTermService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<AcademicTermResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody AcademicTermCreateRequest request) {
        return Result.success(academicTermService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        academicTermService.delete(id);
        return Result.success();
    }

    @GetMapping
    public Result<Page<AcademicTermResponse>> list(AcademicTermQueryRequest request) {
        return Result.success(academicTermService.queryTerms(request));
    }

    @PatchMapping("/{id}/end")
    public Result<Void> endTerm(@PathVariable Long id) {
        academicTermService.endTerm(id);
        return Result.success();
    }

}
