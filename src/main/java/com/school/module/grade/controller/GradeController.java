package com.school.module.grade.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.result.Result;
import com.school.module.grade.dto.GradeCreateRequest;
import com.school.module.grade.dto.GradeQueryRequest;
import com.school.module.grade.dto.GradeResponse;
import com.school.module.grade.service.GradeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 年级管理控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/api/grades")
@RequiredArgsConstructor
public class GradeController {

    private final GradeService gradeService;

    @PostMapping
    public Result<GradeResponse> create(@Valid @RequestBody GradeCreateRequest request) {
        return Result.success(gradeService.create(request));
    }

    @GetMapping("/{id}")
    public Result<GradeResponse> getById(@PathVariable Long id) {
        return Result.success(gradeService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<GradeResponse> update(@PathVariable Long id,
                                        @Valid @RequestBody GradeCreateRequest request) {
        return Result.success(gradeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        gradeService.delete(id);
        return Result.success();
    }

    @GetMapping
    public Result<Page<GradeResponse>> list(GradeQueryRequest request) {
        return Result.success(gradeService.queryGrades(request));
    }

    @GetMapping("/school/{schoolId}")
    public Result<List<GradeResponse>> listBySchool(@PathVariable Long schoolId) {
        return Result.success(gradeService.listBySchoolId(schoolId));
    }

}
