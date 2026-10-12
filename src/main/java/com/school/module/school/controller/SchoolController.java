package com.school.module.school.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.result.Result;
import com.school.module.school.dto.SchoolCreateRequest;
import com.school.module.school.dto.SchoolQueryRequest;
import com.school.module.school.dto.SchoolResponse;
import com.school.module.school.dto.SchoolUpdateRequest;
import com.school.module.school.service.SchoolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 学校管理控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/schools")
@RequiredArgsConstructor
public class SchoolController {

    private final SchoolService schoolService;

    /**
     * 创建学校
     */
    // ponytail: G-2 — school 是平台级主数据且不在租户表白名单，写操作只允许超管
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping
    public Result<SchoolResponse> createSchool(@Valid @RequestBody SchoolCreateRequest request) {
        SchoolResponse response = schoolService.createSchool(request);
        return Result.success(response);
    }

    /**
     * 查询学校详情
     */
    @GetMapping("/{id}")
    public Result<SchoolResponse> getSchoolById(@PathVariable Long id) {
        SchoolResponse response = schoolService.getSchoolById(id);
        return Result.success(response);
    }

    /**
     * 更新学校信息
     */
    // ponytail: G-2 — 任何已登录账号都能改任意学校名称，必须收口到超管
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/{id}")
    public Result<SchoolResponse> updateSchool(
            @PathVariable Long id,
            @Valid @RequestBody SchoolUpdateRequest request) {
        SchoolResponse response = schoolService.updateSchool(id, request);
        return Result.success(response);
    }

    /**
     * 更新学校状态
     */
    // ponytail: G-2 — 停用学校是平台级操作，学生/教师账号不能触达
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PatchMapping("/{id}/status")
    public Result<Void> updateSchoolStatus(
            @PathVariable Long id,
            @RequestParam Integer status) {
        schoolService.updateSchoolStatus(id, status);
        return Result.success();
    }

    /**
     * 分页查询学校列表
     */
    @GetMapping
    public Result<Page<SchoolResponse>> listSchools(SchoolQueryRequest request) {
        Page<SchoolResponse> page = schoolService.listSchools(request);
        return Result.success(page);
    }

}
