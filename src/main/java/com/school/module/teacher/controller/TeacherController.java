package com.school.module.teacher.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.result.Result;
import com.school.module.teacher.dto.TeacherCreateRequest;
import com.school.module.teacher.dto.TeacherQueryRequest;
import com.school.module.teacher.dto.TeacherResponse;
import com.school.module.teacher.dto.TeacherUpdateRequest;
import com.school.module.teacher.service.TeacherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 教师管理控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/api/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;

    /**
     * 创建教师
     */
    @PostMapping
    public Result<TeacherResponse> createTeacher(@Valid @RequestBody TeacherCreateRequest request) {
        TeacherResponse response = teacherService.createTeacher(request);
        return Result.success(response);
    }

    /**
     * 查询教师详情
     */
    @GetMapping("/{id}")
    public Result<TeacherResponse> getTeacherById(@PathVariable Long id) {
        TeacherResponse response = teacherService.getTeacherById(id);
        return Result.success(response);
    }

    /**
     * 更新教师信息
     */
    @PutMapping("/{id}")
    public Result<TeacherResponse> updateTeacher(
            @PathVariable Long id,
            @Valid @RequestBody TeacherUpdateRequest request) {
        TeacherResponse response = teacherService.updateTeacher(id, request);
        return Result.success(response);
    }

    /**
     * 更新教师状态
     */
    @PatchMapping("/{id}/status")
    public Result<Void> updateTeacherStatus(
            @PathVariable Long id,
            @RequestParam Integer status) {
        teacherService.updateTeacherStatus(id, status);
        return Result.success();
    }

    /**
     * 设置主教科目
     */
    @PatchMapping("/{id}/subject")
    public Result<Void> setSubject(
            @PathVariable Long id,
            @RequestParam Long subjectId) {
        teacherService.setSubject(id, subjectId);
        return Result.success();
    }

    /**
     * 删除教师
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteTeacher(@PathVariable Long id) {
        teacherService.deleteTeacher(id);
        return Result.success();
    }

    /**
     * 分页查询教师列表
     */
    @GetMapping
    public Result<Page<TeacherResponse>> listTeachers(TeacherQueryRequest request) {
        Page<TeacherResponse> page = teacherService.listTeachers(request);
        return Result.success(page);
    }

}
