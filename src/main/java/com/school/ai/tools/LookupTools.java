package com.school.ai.tools;

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
import com.school.security.model.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 名称→ID 查询工具集
 * 让 LLM 可以先按名称检索到 ID，再调用其他工具
 */
@Component
@RequiredArgsConstructor
public class LookupTools {

    private final SchoolService schoolService;
    private final ClassService classService;
    private final GradeService gradeService;
    private final ExamService examService;
    private final StudentService studentService;
    private final SubjectService subjectService;

    // ponytail: 由 ToolFactory 注入，不走 ThreadLocal
    LoginUser currentUser;

    @Tool(description = """
            按名称模糊搜索学校，返回学校ID和名称列表。
            仅超级管理员可用，其他用户的学校ID从当前登录信息自动获取。
            用于将"阳光小学"等名称解析为 schoolId。
            """)
    public List<SchoolResponse> searchSchools(
            @ToolParam(description = "学校名称关键字，模糊匹配") String schoolName) {
        var req = new SchoolQueryRequest();
        req.setSchoolName(schoolName);
        req.setPageNum(1L);
        req.setPageSize(10L);
        return schoolService.listSchools(req).getRecords();
    }

    @Tool(description = """
            按名称模糊搜索班级，返回班级ID、名称、年级信息。
            可同时传入年级ID缩小范围。
            用于将"一年1班"、"高三(2)班"等名称解析为 classId。
            """)
    public List<ClassResponse> searchClasses(
            @ToolParam(description = "班级名称关键字，模糊匹配") String className,
            @ToolParam(description = "年级ID，可选，不传则在全校搜索") Long gradeId) {
        var req = new ClassQueryRequest();
        req.setClassName(className);
        req.setGradeId(gradeId);
        if (!currentUser.isSuperAdmin()) {
            req.setSchoolId(currentUser.getSchoolId());
        }
        req.setPageNum(1L);
        req.setPageSize(20L);
        return classService.listClasses(req).getRecords();
    }

    @Tool(description = """
            按名称模糊搜索年级，返回年级ID和名称列表。
            用于将"一年级"、"高三"等名称解析为 gradeId。
            """)
    public List<GradeResponse> searchGrades(
            @ToolParam(description = "年级名称关键字，模糊匹配") String gradeName) {
        var req = new GradeQueryRequest();
        req.setGradeName(gradeName);
        if (!currentUser.isSuperAdmin()) {
            req.setSchoolId(currentUser.getSchoolId());
        }
        req.setPageNum(1L);
        req.setPageSize(20L);
        return gradeService.queryGrades(req).getRecords();
    }

    @Tool(description = """
            按名称模糊搜索考试，返回考试ID、名称、日期、状态列表，按考试日期降序。
            用于将"2024年期中考试"、"上学期期末"等描述解析为 examId。
            status: 3=已发布（有成绩数据）。
            examType: 1月考/2期中/3期末/4模拟/5其他。
            """)
    public List<ExamResponse> searchExams(
            @ToolParam(description = "考试名称关键字，模糊匹配，可为空") String examName,
            @ToolParam(description = "考试状态，可选。3=已发布") Integer status,
            @ToolParam(description = "考试类型，可选：1月考/2期中/3期末/4模拟/5其他") Integer examType,
            @ToolParam(description = "学期ID，可选") Long termId) {
        var req = new ExamQueryRequest();
        req.setExamName(examName);
        req.setStatus(status);
        req.setExamType(examType);
        req.setTermId(termId);
        if (!currentUser.isSuperAdmin()) {
            req.setSchoolId(currentUser.getSchoolId());
        }
        req.setPageNum(1L);
        req.setPageSize(10L);
        return examService.listExams(req).getRecords();
    }

    @Tool(description = """
            按姓名模糊搜索学生，返回学生ID、姓名、学号、班级信息。
            可按班级ID缩小范围。
            用于将"张三"等姓名解析为 studentId。
            """)
    public List<StudentResponse> searchStudents(
            @ToolParam(description = "学生姓名关键字，模糊匹配") String name,
            @ToolParam(description = "班级ID，可选，不传则在全校搜索") Long classId) {
        var req = new StudentQueryRequest();
        req.setName(name);
        req.setClassId(classId);
        if (!currentUser.isSuperAdmin()) {
            req.setSchoolId(currentUser.getSchoolId());
        }
        req.setPageNum(1L);
        req.setPageSize(20L);
        return studentService.listStudents(req).getRecords();
    }

    @Tool(description = """
            按名称模糊搜索科目，返回科目ID、名称、满分信息。
            用于将"数学"、"语文"等名称解析为 subjectId。
            """)
    public List<SubjectResponse> searchSubjects(
            @ToolParam(description = "科目名称关键字，模糊匹配") String subjectName) {
        var req = new SubjectQueryRequest();
        req.setSubjectName(subjectName);
        if (!currentUser.isSuperAdmin()) {
            req.setSchoolId(currentUser.getSchoolId());
        }
        req.setPageNum(1L);
        req.setPageSize(20L);
        return subjectService.querySubjects(req).getRecords();
    }

    @Tool(description = """
            获取当前登录用户的基本信息：userId、schoolId、角色类型。
            当需要知道"当前用户是谁"、"我的学校ID是多少"时调用此工具。
            """)
    public String getCurrentUserInfo() {
        return String.format(
                "{\"userId\":%d,\"schoolId\":%s,\"userType\":%d,\"teacherId\":%s,\"studentId\":%s}",
                currentUser.getUserId(),
                currentUser.getSchoolId() != null ? currentUser.getSchoolId() : "null",
                currentUser.getUserType(),
                currentUser.getTeacherId() != null ? currentUser.getTeacherId() : "null",
                currentUser.getStudentId() != null ? currentUser.getStudentId() : "null"
        );
    }

}
