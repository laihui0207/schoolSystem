package com.school.ai.tools;

import com.school.security.model.LoginUser;
import com.school.module.class_module.dto.ClassQueryRequest;
import com.school.module.class_module.dto.ClassResponse;
import com.school.module.class_module.service.ClassService;
import com.school.module.exam.dto.ExamQueryRequest;
import com.school.module.exam.dto.ExamResponse;
import com.school.module.exam.service.ExamService;
import com.school.module.grade.dto.GradeQueryRequest;
import com.school.module.grade.dto.GradeResponse;
import com.school.module.grade.service.GradeService;
import com.school.module.school.dto.SchoolQueryRequest;
import com.school.module.school.dto.SchoolResponse;
import com.school.module.school.service.SchoolService;
import com.school.module.student.dto.StudentQueryRequest;
import com.school.module.student.dto.StudentResponse;
import com.school.module.student.service.StudentService;
import com.school.module.subject.dto.SubjectQueryRequest;
import com.school.module.subject.dto.SubjectResponse;
import com.school.module.subject.service.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LookupTools {

    private final SchoolService schoolService;
    private final ClassService classService;
    private final GradeService gradeService;
    private final ExamService examService;
    private final StudentService studentService;
    private final SubjectService subjectService;
    private final AiPermissionChecker permissionChecker;

    @Tool(description = """
            按名称模糊搜索学校，返回学校ID和名称列表。
            用于将"阳光小学"等名称解析为 schoolId。
            """)
    public List<SchoolResponse> searchSchools(
            @ToolParam(description = "学校名称关键字，模糊匹配") String schoolName,
            ToolContext toolContext) {
        var req = new SchoolQueryRequest();
        req.setSchoolName(schoolName);
        req.setPageNum(1L);
        req.setPageSize(10L);
        return schoolService.listSchools(req).getRecords();
    }

    @Tool(description = """
            按名称模糊搜索班级，返回班级ID、名称、年级信息。
            用于将"一年1班"、"高三(2)班"等名称解析为 classId。
            """)
    public List<ClassResponse> searchClasses(
            @ToolParam(description = "班级名称关键字，模糊匹配") String className,
            @ToolParam(description = "年级ID，可选") Long gradeId,
            ToolContext toolContext) {
        LoginUser user = permissionChecker.currentUser(toolContext);
        var req = new ClassQueryRequest();
        req.setClassName(className);
        req.setGradeId(gradeId);
        if (!user.isSuperAdmin()) req.setSchoolId(user.getSchoolId());
        req.setPageNum(1L);
        req.setPageSize(20L);
        return classService.listClasses(req).getRecords();
    }

    @Tool(description = "按名称模糊搜索年级，返回年级ID和名称列表。用于将一年级、高三等名称解析为 gradeId。")
    public List<GradeResponse> searchGrades(
            @ToolParam(description = "年级名称关键字，模糊匹配") String gradeName,
            ToolContext toolContext) {
        LoginUser user = permissionChecker.currentUser(toolContext);
        var req = new GradeQueryRequest();
        req.setGradeName(gradeName);
        if (!user.isSuperAdmin()) req.setSchoolId(user.getSchoolId());
        req.setPageNum(1L);
        req.setPageSize(20L);
        return gradeService.queryGrades(req).getRecords();
    }

    @Tool(description = """
            按名称模糊搜索考试，返回考试ID、名称、日期、状态列表。
            status: 3=已发布。examType: 1月考/2期中/3期末/4模拟/5其他。
            """)
    public List<ExamResponse> searchExams(
            @ToolParam(description = "考试名称关键字，可为空") String examName,
            @ToolParam(description = "考试状态，可选。3=已发布") Integer status,
            @ToolParam(description = "考试类型，可选：1月考/2期中/3期末/4模拟/5其他") Integer examType,
            @ToolParam(description = "学期ID，可选") Long termId,
            ToolContext toolContext) {
        LoginUser user = permissionChecker.currentUser(toolContext);
        var req = new ExamQueryRequest();
        req.setExamName(examName);
        req.setStatus(status);
        req.setExamType(examType);
        req.setTermId(termId);
        if (!user.isSuperAdmin()) req.setSchoolId(user.getSchoolId());
        req.setPageNum(1L);
        req.setPageSize(10L);
        return examService.listExams(req).getRecords();
    }

    @Tool(description = "按姓名模糊搜索学生，返回学生ID、姓名、学号、班级信息。用于将张三等姓名解析为 studentId。")
    public List<StudentResponse> searchStudents(
            @ToolParam(description = "学生姓名关键字，模糊匹配") String name,
            @ToolParam(description = "班级ID，可选") Long classId,
            ToolContext toolContext) {
        LoginUser user = permissionChecker.currentUser(toolContext);
        var req = new StudentQueryRequest();
        req.setName(name);
        req.setClassId(classId);
        if (!user.isSuperAdmin()) req.setSchoolId(user.getSchoolId());
        req.setPageNum(1L);
        req.setPageSize(20L);
        return studentService.listStudents(req).getRecords();
    }

    @Tool(description = "按名称模糊搜索科目，返回科目ID、名称、满分信息。用于将数学、语文等名称解析为 subjectId。")
    public List<SubjectResponse> searchSubjects(
            @ToolParam(description = "科目名称关键字，模糊匹配") String subjectName,
            ToolContext toolContext) {
        LoginUser user = permissionChecker.currentUser(toolContext);
        var req = new SubjectQueryRequest();
        req.setSubjectName(subjectName);
        if (!user.isSuperAdmin()) req.setSchoolId(user.getSchoolId());
        req.setPageNum(1L);
        req.setPageSize(20L);
        return subjectService.querySubjects(req).getRecords();
    }

    @Tool(description = "获取当前登录用户的基本信息：userId、schoolId、角色类型。")
    public String getCurrentUserInfo(ToolContext toolContext) {
        LoginUser user = permissionChecker.currentUser(toolContext);
        return String.format(
                "{\"userId\":%d,\"schoolId\":%s,\"userType\":%d,\"teacherId\":%s,\"studentId\":%s}",
                user.getUserId(),
                user.getSchoolId() != null ? user.getSchoolId() : "null",
                user.getUserType(),
                user.getTeacherId() != null ? user.getTeacherId() : "null",
                user.getStudentId() != null ? user.getStudentId() : "null"
        );
    }

}
