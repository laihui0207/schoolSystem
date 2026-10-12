package com.school.ai.tools;

import com.school.ai.service.AiUserContext;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.security.model.LoginUser;
import com.school.security.service.TeacherScopeService;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.lang.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * AI 工具层权限检查器
 * 优先从 ToolContext（官方跨线程机制）读用户，降级到 AiUserContext 和 SecurityContextHolder
 */
@Component
@RequiredArgsConstructor
public class AiPermissionChecker {

    private final TeacherScopeService teacherScopeService;

    /**
     * 从 ToolContext 提取 LoginUser，并同步设置到 SecurityContextHolder
     * 这样工具调用链下游的 Service（StatService、ScoreService 等）里的 SecurityUtils 也能正常读取
     */
    public LoginUser currentUser(@Nullable ToolContext toolContext) {
        LoginUser user = resolveUser(toolContext);
        // ponytail: 同步设置到 SecurityContextHolder，让下游 Service 的 SecurityUtils 可用
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        user, null, user.getAuthorities()
                )
        );
        return user;
    }

    private LoginUser resolveUser(@Nullable ToolContext toolContext) {
        if (toolContext != null) {
            Object user = toolContext.getContext().get("loginUser");
            if (user instanceof LoginUser loginUser) return loginUser;
        }
        LoginUser fromContext = AiUserContext.get();
        if (fromContext != null) return fromContext;
        return SecurityUtils.currentUser();
    }

    public void assertCanViewClass(@Nullable ToolContext toolContext, Long classId) {
        var user = currentUser(toolContext);
        if (user.isSuperAdmin()) return;
        if (user.isSchoolAdmin()) return;
        if (user.isTeacher()) {
            if (classId != null) {
                Set<Long> allowed = teacherScopeService.allowedClassIdsFor(user.getTeacherId());
                if (!allowed.contains(classId)) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该班级数据");
                }
            }
            return;
        }
        // ponytail: 学生/家长不能看任何班级或全校维度的数据；classId=null 表示"全校范围"，更不能放行（之前 null 会漏过）
        throw new BusinessException(ResultCode.FORBIDDEN, "无权查看班级数据");
    }

    public void assertCanViewStudent(@Nullable ToolContext toolContext, Long studentId) {
        var user = currentUser(toolContext);
        if (user.isSuperAdmin()) return;
        if (user.isSchoolAdmin()) return;
        if (user.isStudent()) {
            if (!studentId.equals(user.getStudentId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "只能查看自己的成绩");
            }
            return;
        }
        if (user.isTeacher()) return;
        throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该学生数据");
    }

    public void assertIsAdmin(@Nullable ToolContext toolContext) {
        var user = currentUser(toolContext);
        if (!user.isSuperAdmin() && !user.isSchoolAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可执行此操作");
        }
    }

    public void assertIsHeadTeacherOrAdmin(@Nullable ToolContext toolContext) {
        var user = currentUser(toolContext);
        if (user.isSuperAdmin() || user.isSchoolAdmin() || user.isHeadTeacher()) return;
        throw new BusinessException(ResultCode.FORBIDDEN, "仅班主任或管理员可执行此操作");
    }

}
