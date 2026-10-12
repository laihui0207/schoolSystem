package com.school.module.term.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.result.Result;
import com.school.module.term.dto.AcademicTermCreateRequest;
import com.school.module.term.dto.AcademicTermQueryRequest;
import com.school.module.term.dto.AcademicTermResponse;
import com.school.module.term.dto.AcademicTermUpdateRequest;
import com.school.module.term.service.AcademicTermService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学年学期管理控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/terms")
@RequiredArgsConstructor
public class AcademicTermController {

    private final AcademicTermService termService;

    /**
     * 创建学期
     */
    @PostMapping
    public Result<AcademicTermResponse> createTerm(@Valid @RequestBody AcademicTermCreateRequest request) {
        AcademicTermResponse response = termService.createTerm(request);
        return Result.success(response);
    }

    /**
     * 查询学期详情
     */
    @GetMapping("/{id}")
    public Result<AcademicTermResponse> getTermById(@PathVariable Long id) {
        AcademicTermResponse response = termService.getById(id);
        return Result.success(response);
    }

    /**
     * 更新学期信息
     */
    @PutMapping("/{id}")
    public Result<AcademicTermResponse> updateTerm(
            @PathVariable Long id,
            @Valid @RequestBody AcademicTermUpdateRequest request) {
        AcademicTermResponse response = termService.updateTerm(id, request);
        return Result.success(response);
    }

    /**
     * 结束学期
     */
    @PatchMapping("/{id}/end")
    public Result<Void> endTerm(@PathVariable Long id) {
        termService.endTerm(id);
        return Result.success();
    }

    /**
     * 分页查询学期列表
     */
    @GetMapping
    public Result<Page<AcademicTermResponse>> queryTerms(AcademicTermQueryRequest request) {
        Page<AcademicTermResponse> page = termService.queryTerms(request);
        return Result.success(page);
    }

    /**
     * 获取当前学期
     */
    @GetMapping("/current")
    public Result<AcademicTermResponse> getCurrentTerm(@RequestParam Long schoolId) {
        AcademicTermResponse response = termService.getCurrentTerm(schoolId);
        return Result.success(response);
    }

    /**
     * 删除学期
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteTerm(@PathVariable Long id) {
        termService.deleteTerm(id);
        return Result.success();
    }

    /**
     * 按学校查询学期列表
     */
    @GetMapping("/school/{schoolId}")
    public Result<List<AcademicTermResponse>> listBySchool(@PathVariable Long schoolId) {
        List<AcademicTermResponse> list = termService.listBySchool(schoolId);
        return Result.success(list);
    }

}
