package com.school.ai.tools;

import com.school.security.model.LoginUser;
import com.school.module.class_module.dto.ClassResponse;
import com.school.module.class_module.service.ClassService;
import com.school.module.exam.dto.ExamQueryRequest;
import com.school.module.exam.dto.ExamResponse;
import com.school.module.exam.service.ExamService;
import com.school.module.term.dto.AcademicTermResponse;
import com.school.module.term.service.AcademicTermService;
import com.school.security.service.TeacherScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ContextTools {

    private final AcademicTermService termService;
    private final ExamService examService;
    private final ClassService classService;
    private final TeacherScopeService teacherScopeService;
    private final AiPermissionChecker permissionChecker;

    @Tool(description = """
            获取当前进行中的学期信息，包含学期ID、学年、学期号、开始/结束日期。
            用户说"这学期"、"本学期"、"当前学期"时调用此工具获取 termId。
            """)
    public AcademicTermResponse getCurrentTerm(ToolContext toolContext) {
        LoginUser user = permissionChecker.currentUser(toolContext);
        if (user.getSchoolId() == null) return null;
        return termService.getCurrentTerm(user.getSchoolId());
    }

    @Tool(description = """
            获取最近一次已发布的考试（状态=3）。
            用户说"这次考试"、"最近的考试"时调用此工具获取 examId。
            examType: 1月考/2期中/3期末/4模拟/5其他。
            """)
    public ExamResponse getLatestExam(
            @ToolParam(description = "学期ID，可选") Long termId,
            @ToolParam(description = "考试类型，可选") Integer examType,
            ToolContext toolContext) {
        LoginUser user = permissionChecker.currentUser(toolContext);
        var req = new ExamQueryRequest();
        req.setTermId(termId);
        req.setExamType(examType);
        req.setStatus(3);
        req.setPageNum(1L);
        req.setPageSize(1L);
        if (!user.isSuperAdmin()) req.setSchoolId(user.getSchoolId());
        var records = examService.listExams(req).getRecords();
        return records.isEmpty() ? null : records.get(0);
    }

    @Tool(description = """
            获取当前教师任教的所有班级列表（含班主任班级）。
            教师说"我的班"、"我教的班"时调用此工具获取 classId 列表。
            """)
    public List<ClassResponse> getMyClasses(ToolContext toolContext) {
        LoginUser user = permissionChecker.currentUser(toolContext);
        if (!user.isTeacher()) return List.of();
        // ponytail: 用 teacherId 参数调用，不走 SecurityContextHolder
        Set<Long> classIds = teacherScopeService.allowedClassIdsFor(user.getTeacherId());
        return classIds.stream()
                .map(classService::getClassById)
                .collect(Collectors.toList());
    }

    @Tool(description = """
            列出指定学期的所有考试，按考试日期降序。
            用于回答"这学期考了哪些试"等问题。examType: 1月考/2期中/3期末/4模拟/5其他。
            """)
    public List<ExamResponse> listExamsByTerm(
            @ToolParam(description = "学期ID，不传则查当前学期") Long termId,
            @ToolParam(description = "考试类型，可选") Integer examType,
            ToolContext toolContext) {
        LoginUser user = permissionChecker.currentUser(toolContext);
        Long resolvedTermId = termId;
        if (resolvedTermId == null) {
            AcademicTermResponse current = getCurrentTerm(toolContext);
            if (current != null) resolvedTermId = current.getId();
        }
        var req = new ExamQueryRequest();
        req.setTermId(resolvedTermId);
        req.setExamType(examType);
        req.setPageNum(1L);
        req.setPageSize(50L);
        if (!user.isSuperAdmin()) req.setSchoolId(user.getSchoolId());
        return examService.listExams(req).getRecords();
    }

}
