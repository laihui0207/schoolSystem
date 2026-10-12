package com.school.module.teacher.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.result.Result;
import com.school.module.teacher.dto.TeacherCreateRequest;
import com.school.module.teacher.dto.TeacherQueryRequest;
import com.school.module.teacher.dto.TeacherResponse;
import com.school.module.student.roster.RosterImportResult;
import com.school.module.teacher.dto.TeacherUpdateRequest;
import com.school.module.teacher.service.TeacherRosterService;
import com.school.module.teacher.service.TeacherService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 教师管理控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;
    private final TeacherRosterService teacherRosterService;

    /**
     * 下载教师导入模板
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SCHOOL_ADMIN')")
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        teacherRosterService.downloadTemplate(response);
    }

    /**
     * 批量导入教师（multipart：file 必填；schoolId 超管必填、校管理员可省略）
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SCHOOL_ADMIN')")
    @PostMapping("/import")
    public Result<RosterImportResult> importTeachers(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long schoolId) {
        return Result.success(teacherRosterService.importTeachers(file, schoolId));
    }

    /**
     * 按当前筛选条件导出教师档案（含主教科目、账号用户名）
     */
    // ponytail: 导出列含账号用户名，与导入同为管理员专属
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SCHOOL_ADMIN')")
    @GetMapping("/export")
    public void exportTeachers(TeacherQueryRequest request, HttpServletResponse response) {
        teacherRosterService.exportTeachers(request, response);
    }

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
    // ponytail: G-1 — 主教科目影响教师数据范围展示，只允许超管/校管改，教师不能自改
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SCHOOL_ADMIN')")
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

    /**
     * 查询教师任教班级列表
     */
    @GetMapping("/{id}/classes")
    public Result<List<TeacherResponse.ClassAssignment>> getClassAssignments(@PathVariable Long id) {
        return Result.success(teacherService.getClassAssignments(id));
    }

    /**
     * 为教师分配班级+科目
     */
    // ponytail: G-1 — teacher_class 是 TeacherScopeService 算教师成绩读写范围的依据，教师/学生账号不能自行加任课班级扩权
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SCHOOL_ADMIN')")
    @PostMapping("/{id}/classes")
    public Result<Void> assignClass(
            @PathVariable Long id,
            @RequestParam Long classId,
            @RequestParam Long subjectId) {
        teacherService.assignClass(id, classId, subjectId);
        return Result.success();
    }

    /**
     * 移除教师的班级+科目分配
     */
    // ponytail: G-1 — 与 assignClass 对称，只有超管/校管能改任课分配
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SCHOOL_ADMIN')")
    @DeleteMapping("/{id}/classes")
    public Result<Void> removeClassAssignment(
            @PathVariable Long id,
            @RequestParam Long classId,
            @RequestParam Long subjectId) {
        teacherService.removeClassAssignment(id, classId, subjectId);
        return Result.success();
    }

}
