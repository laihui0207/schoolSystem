package com.school.ai.tools;

import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.security.model.LoginUser;
import com.school.module.stat.dto.ClassStatResponse;
import com.school.module.stat.dto.GradeStatResponse;
import com.school.module.stat.dto.StudentTrendResponse;
import com.school.module.stat.service.StatService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnalysisTools {

    private final StatService statService;
    private final AiPermissionChecker permissionChecker;

    @Tool(description = """
            年级或全校横向对比：获取同一次考试中各班级的成绩统计汇总，
            包含每个班级的平均分、及格率、优秀率、排名。
            适合回答"哪个班考得最好"、"各班差距多大"等问题。仅管理员可用。
            """)
    public GradeStatResponse compareClassesInExam(
            @ToolParam(description = "考试ID") Long examId,
            @ToolParam(description = "年级ID，可选，不传则查全校") Long gradeId) {
        permissionChecker.assertIsAdmin();
        return statService.getGradeStat(examId, gradeId);
    }

    @Tool(description = """
            学生纵向趋势对比：获取学生在多次考试中的成绩变化趋势，
            分析进步幅度、排名变化、各科目强弱。
            适合回答"这学期进步了吗"、"哪科提升最明显"等问题。
            """)
    public StudentTrendResponse analyzeStudentProgress(
            @ToolParam(description = "学生ID，学生角色不传则默认当前用户") Long studentId,
            @ToolParam(description = "科目ID，可选，不传则分析全科") Long subjectId,
            @ToolParam(description = "学期ID，可选，不传则查全部学期") Long termId) {
        Long targetId = studentId;
        if (targetId == null) {
            LoginUser user = permissionChecker.currentUser();
            if (user.isStudent()) targetId = user.getStudentId();
            else throw new BusinessException(ResultCode.PARAM_ERROR, "请指定学生ID");
        }
        permissionChecker.assertCanViewStudent(targetId);
        return statService.getStudentTrend(targetId, subjectId, termId);
    }

    @Tool(description = """
            班级教学质量分析：获取班级在某次考试中的完整统计，
            含各科目表现、薄弱科目识别、优秀生与待提升生名单。
            """)
    public ClassStatResponse analyzeClassPerformance(
            @ToolParam(description = "考试ID") Long examId,
            @ToolParam(description = "班级ID") Long classId) {
        permissionChecker.assertCanViewClass(classId);
        return statService.getClassStat(examId, classId);
    }
}
