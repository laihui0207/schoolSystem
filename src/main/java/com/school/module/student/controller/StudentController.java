package com.school.module.student.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.result.Result;
import com.school.module.student.dto.StudentCreateRequest;
import com.school.module.student.dto.StudentQueryRequest;
import com.school.module.student.dto.StudentResponse;
import com.school.module.student.dto.StudentUpdateRequest;
import com.school.module.student.roster.RosterImportResult;
import com.school.module.student.service.StudentRosterService;
import com.school.module.student.service.StudentService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 学生管理控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;
    private final StudentRosterService studentRosterService;

    /**
     * 下载学生导入模板
     * <p>
     * ponytail: 字面路径 /template /export /import 与 /{id} 并存，Spring 优先匹配字面量；
     * 但 /{id} 是 Long，万一前端拼错路径会被当作类型不匹配返回「该路径可能不存在」
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SCHOOL_ADMIN')")
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        studentRosterService.downloadTemplate(response);
    }

    /**
     * 批量导入学生（multipart：file 必填；schoolId 超管必填、校管理员可省略）
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SCHOOL_ADMIN')")
    @PostMapping("/import")
    public Result<RosterImportResult> importStudents(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long schoolId) {
        return Result.success(studentRosterService.importStudents(file, schoolId));
    }

    /**
     * 按当前筛选条件导出学生档案（含班级、账号用户名）
     */
    // ponytail: 导出列含账号用户名，与导入同为管理员专属；教师/学生按钮不可见、直连也会被拒
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SCHOOL_ADMIN')")
    @GetMapping("/export")
    public void exportStudents(StudentQueryRequest request, HttpServletResponse response) {
        studentRosterService.exportStudents(request, response);
    }

    /**
     * 创建学生
     */
    @PostMapping
    public Result<StudentResponse> createStudent(@Valid @RequestBody StudentCreateRequest request) {
        StudentResponse response = studentService.createStudent(request);
        return Result.success(response);
    }

    /**
     * 查询学生详情
     */
    @GetMapping("/{id}")
    public Result<StudentResponse> getStudentById(@PathVariable Long id) {
        StudentResponse response = studentService.getStudentById(id);
        return Result.success(response);
    }

    /**
     * 更新学生信息
     */
    @PutMapping("/{id}")
    public Result<StudentResponse> updateStudent(
            @PathVariable Long id,
            @Valid @RequestBody StudentUpdateRequest request) {
        StudentResponse response = studentService.updateStudent(id, request);
        return Result.success(response);
    }

    /**
     * 更新学生状态
     */
    @PatchMapping("/{id}/status")
    public Result<Void> updateStudentStatus(
            @PathVariable Long id,
            @RequestParam Integer status) {
        studentService.updateStudentStatus(id, status);
        return Result.success();
    }

    /**
     * 删除学生
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteStudent(@PathVariable Long id) {
        studentService.deleteStudent(id);
        return Result.success();
    }

    /**
     * 分页查询学生列表
     */
    @GetMapping
    public Result<Page<StudentResponse>> listStudents(StudentQueryRequest request) {
        Page<StudentResponse> page = studentService.listStudents(request);
        return Result.success(page);
    }

    /**
     * 学生分班
     */
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN') or principal.headTeacher")
    @PostMapping("/{studentId}/assign-class")
    public Result<Void> assignToClass(
            @PathVariable Long studentId,
            @RequestParam Long classId) {
        studentService.assignToClass(studentId, classId);
        return Result.success();
    }

    /**
     * 学生转班
     */
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN') or principal.headTeacher")
    @PostMapping("/{studentId}/transfer-class")
    public Result<Void> transferClass(
            @PathVariable Long studentId,
            @RequestParam Long fromClassId,
            @RequestParam Long toClassId) {
        studentService.transferClass(studentId, fromClassId, toClassId);
        return Result.success();
    }

    /**
     * 移除学生的班级
     */
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN') or principal.headTeacher")
    @DeleteMapping("/{studentId}/class/{classId}")
    public Result<Void> removeFromClass(
            @PathVariable Long studentId,
            @PathVariable Long classId) {
        studentService.removeFromClass(studentId, classId);
        return Result.success();
    }

    /**
     * 查询班级下的所有学生
     */
    @GetMapping("/by-class/{classId}")
    public Result<List<StudentResponse>> getStudentsByClassId(@PathVariable Long classId) {
        List<StudentResponse> students = studentService.getStudentsByClassId(classId);
        return Result.success(students);
    }

}
