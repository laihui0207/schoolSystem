package com.school.module.subject.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.result.Result;
import com.school.module.subject.dto.SubjectCreateRequest;
import com.school.module.subject.dto.SubjectQueryRequest;
import com.school.module.subject.dto.SubjectResponse;
import com.school.module.subject.service.SubjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 科目管理控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;

    @PostMapping
    public Result<SubjectResponse> create(@Valid @RequestBody SubjectCreateRequest request) {
        return Result.success(subjectService.create(request));
    }

    @GetMapping("/{id}")
    public Result<SubjectResponse> getById(@PathVariable Long id) {
        return Result.success(subjectService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<SubjectResponse> update(@PathVariable Long id,
                                          @Valid @RequestBody SubjectCreateRequest request) {
        return Result.success(subjectService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        subjectService.delete(id);
        return Result.success();
    }

    @GetMapping
    public Result<Page<SubjectResponse>> list(SubjectQueryRequest request) {
        return Result.success(subjectService.querySubjects(request));
    }

    @GetMapping("/school/{schoolId}")
    public Result<List<SubjectResponse>> listBySchool(@PathVariable Long schoolId) {
        return Result.success(subjectService.listBySchoolId(schoolId));
    }

}
