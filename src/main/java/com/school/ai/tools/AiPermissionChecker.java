package com.school.ai.tools;

import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.security.model.LoginUser;
import com.school.security.service.TeacherScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * AI 工具层权限检查器
 * 兼容 ThreadLocal（Servlet 线程）和 Reactor Context（工具执行线程）
 */
@Component
@RequiredArgsConstructor
public class AiPermissionChecker {

    private final TeacherScopeService teacherScopeService;

    /**
     * 获取当前登录用户，兼容两种线程模型：
     * 1. ThreadLocal —— Servlet 线程直接可读
     * 2. Reactor Context —— Spring AI 工具回调线程通过 contextWrite 传入
     */
    public LoginUser currentUser() {
        // 优先走 ThreadLocal（Servlet 线程）
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser u) {
            return u;
        }
        // 降级走 Reactor Context（工具回调线程）
        // block() 在 Reactor 线程上调用是安全的，这里只是读一个已存在的 Mono.just(...)
        SecurityContext reactiveCtx = ReactiveSecurityContextHolder.getContext().block();
        if (reactiveCtx != null
                && reactiveCtx.getAuthentication() != null
                && reactiveCtx.getAuthentication().getPrincipal() instanceof LoginUser u) {
            return u;
        }
        throw new BusinessException(ResultCode.UNAUTHORIZED);
    }

    public void assertCanViewClass(Long classId) {
        var user = currentUser();
        if (user.isSuperAdmin()) return;
        if (user.getUserType() == 2) return;
        if (user.isTeacher()) {
            if (classId != null && !teacherScopeService.currentAllowedClassIds().contains(classId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该班级数据");
            }
            return;
        }
        if (classId != null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看班级数据");
        }
    }

    public void assertCanViewStudent(Long studentId) {
        var user = currentUser();
        if (user.isSuperAdmin()) return;
        if (user.getUserType() == 2) return;
        if (user.isStudent()) {
            if (!studentId.equals(user.getStudentId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "只能查看自己的成绩");
            }
            return;
        }
        if (user.isTeacher()) return;
        throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该学生数据");
    }

    public void assertIsAdmin() {
        var user = currentUser();
        if (!user.isSuperAdmin() && user.getUserType() != 2) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可执行此操作");
        }
    }

}
