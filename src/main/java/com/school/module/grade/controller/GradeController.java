package com.school.module.grade.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.result.Result;
import com.school.module.grade.dto.GradeCreateRequest;
import com.school.module.grade.dto.GradeQueryRequest;
import com.school.module.grade.dto.GradeResponse;
import com.school.module.grade.dto.GradeUpdateRequest;
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
@RequestMapping("/grades")
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

    // ponytail: 编辑绑 UpdateRequest 而非 CreateRequest，否则 schoolId @NotNull 让前端只发更新字段的 PUT 必 400
    @PutMapping("/{id}")
    public Result<GradeResponse> update(@PathVariable Long id,
                                        @Valid @RequestBody GradeUpdateRequest request) {
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
