package com.school.ai.tools;

import com.school.module.class_module.dto.ClassResponse;
import com.school.module.class_module.service.ClassService;
import com.school.module.exam.dto.ExamQueryRequest;
import com.school.module.exam.dto.ExamResponse;
import com.school.module.exam.service.ExamService;
import com.school.module.term.dto.AcademicTermResponse;
import com.school.module.term.service.AcademicTermService;
import com.school.security.service.TeacherScopeService;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 上下文工具集
 * 解决"这学期"、"这次考试"、"我的班级"等口语化引用
 */
@Component
@RequiredArgsConstructor
public class ContextTools {

    private final AcademicTermService termService;
    private final ExamService examService;
    private final ClassService classService;
    private final TeacherScopeService teacherScopeService;

    @Tool(description = """
            获取当前进行中的学期信息，包含学期ID、学年、学期号、开始/结束日期。
            用户说"这学期"、"本学期"、"当前学期"时调用此工具获取 termId。
            """)
    public AcademicTermResponse getCurrentTerm() {
        var user = SecurityUtils.currentUser();
        // 超管没有 schoolId，getCurrentTerm 需要 schoolId，此时返回 null
        if (user.isSuperAdmin() && user.getSchoolId() == null) {
            return null;
        }
        Long schoolId = user.isSuperAdmin() ? user.getSchoolId() : SecurityUtils.currentSchoolId();
        return termService.getCurrentTerm(schoolId);
    }

    @Tool(description = """
            获取最近一次已发布的考试（状态=3）。
            用户说"这次考试"、"最近的考试"、"上次考试"时调用此工具获取 examId。
            可按学期ID或考试类型筛选：examType 1=月考 2=期中 3=期末 4=模拟 5=其他。
            """)
    public ExamResponse getLatestExam(
            @ToolParam(description = "学期ID，可选，不传则查全部学期") Long termId,
            @ToolParam(description = "考试类型，可选：1月考/2期中/3期末/4模拟/5其他") Integer examType) {
        var user = SecurityUtils.currentUser();
        var req = new ExamQueryRequest();
        req.setTermId(termId);
        req.setExamType(examType);
        req.setStatus(3); // 只查已发布
        req.setPageSize(1L);
        if (!user.isSuperAdmin()) {
            req.setSchoolId(user.getSchoolId());
        }
        var records = examService.listExams(req).getRecords();
        return records.isEmpty() ? null : records.get(0);
    }

    @Tool(description = """
            获取当前教师任教的所有班级列表（含班主任班级）。
            教师说"我的班"、"我教的班"时调用此工具获取 classId 列表。
            非教师角色返回空列表。
            """)
    public List<ClassResponse> getMyClasses() {
        var user = SecurityUtils.currentUser();
        if (!user.isTeacher()) {
            return List.of();
        }
        Set<Long> classIds = teacherScopeService.currentAllowedClassIds();
        return classIds.stream()
                .map(classService::getClassById)
                .collect(Collectors.toList());
    }

    @Tool(description = """
            列出指定学期的所有考试，按考试日期降序排列（最近的在前）。
            用于回答"这学期考了哪些试"、"列出所有考试"等问题。
            examType 可选：1月考/2期中/3期末/4模拟/5其他。
            """)
    public List<ExamResponse> listExamsByTerm(
            @ToolParam(description = "学期ID，不传则查当前学期") Long termId,
            @ToolParam(description = "考试类型，可选") Integer examType) {
        var user = SecurityUtils.currentUser();

        // termId 不传时自动取当前学期
        Long resolvedTermId = termId;
        if (resolvedTermId == null) {
            AcademicTermResponse current = getCurrentTerm();
            if (current != null) resolvedTermId = current.getId();
        }

        var req = new ExamQueryRequest();
        req.setTermId(resolvedTermId);
        req.setExamType(examType);
        req.setPageSize(50L);
        if (!user.isSuperAdmin()) {
            req.setSchoolId(user.getSchoolId());
        }
        return examService.listExams(req).getRecords();
    }

}
