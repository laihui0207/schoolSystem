package com.school.module.class_module.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.result.Result;
import com.school.module.class_module.dto.ClassCreateRequest;
import com.school.module.class_module.dto.ClassQueryRequest;
import com.school.module.class_module.dto.ClassResponse;
import com.school.module.class_module.dto.ClassUpdateRequest;
import com.school.module.class_module.service.ClassService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 班级管理控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/classes")
@RequiredArgsConstructor
public class ClassController {

    private final ClassService classService;

    /**
     * 创建班级
     */
    @PostMapping
    public Result<ClassResponse> createClass(@Valid @RequestBody ClassCreateRequest request) {
        ClassResponse response = classService.createClass(request);
        return Result.success(response);
    }

    /**
     * 查询班级详情
     */
    @GetMapping("/{id}")
    public Result<ClassResponse> getClassById(@PathVariable Long id) {
        ClassResponse response = classService.getClassById(id);
        return Result.success(response);
    }

    /**
     * 更新班级信息
     */
    @PutMapping("/{id}")
    public Result<ClassResponse> updateClass(
            @PathVariable Long id,
            @Valid @RequestBody ClassUpdateRequest request) {
        ClassResponse response = classService.updateClass(id, request);
        return Result.success(response);
    }

    /**
     * 设置班主任（headTeacherId 省略表示取消班主任）
     */
    // ponytail: required=false 让前端能不传参来撤掉班主任，否则这个接口只能换人不能取消
    @PatchMapping("/{id}/head-teacher")
    public Result<Void> setHeadTeacher(
            @PathVariable Long id,
            @RequestParam(required = false) Long headTeacherId) {
        classService.setHeadTeacher(id, headTeacherId);
        return Result.success();
    }

    /**
     * 删除班级
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteClass(@PathVariable Long id) {
        classService.deleteClass(id);
        return Result.success();
    }

    /**
     * 分页查询班级列表
     */
    @GetMapping
    public Result<Page<ClassResponse>> listClasses(ClassQueryRequest request) {
        Page<ClassResponse> page = classService.listClasses(request);
        return Result.success(page);
    }

}
