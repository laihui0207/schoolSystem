package com.school.module.parent.controller;

import com.school.common.result.Result;
import com.school.module.log.annotation.OperationLog;
import com.school.module.parent.dto.ChildResponse;
import com.school.module.parent.service.ParentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 家长控制器：家长-学生绑定（管理员）与我的孩子（家长本人）
 * <p>
 * ponytail: /parents/me/children 是字面量路径，Spring MVC 优先于 /parents/{parentUserId}/children 匹配，
 * 不会被 {parentUserId} 吃掉变成 400/404。
 *
 * @author School System
 */
@RestController
@RequestMapping("/parents")
@RequiredArgsConstructor
public class ParentController {

    private final ParentService parentService;

    /**
     * 家长本人查看绑定的孩子（登录即可，Service 内要求家长身份）
     */
    @GetMapping("/me/children")
    public Result<List<ChildResponse>> listMyChildren() {
        return Result.success(parentService.listMyChildren());
    }

    /**
     * 管理员查看某家长绑定的孩子
     */
    @GetMapping("/{parentUserId}/children")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Result<List<ChildResponse>> listChildren(@PathVariable Long parentUserId) {
        return Result.success(parentService.listChildren(parentUserId));
    }

    /**
     * 管理员为家长绑定学生
     *
     * @param relationship 字典 relationship 的 value（father/mother/other），缺省 other
     */
    @PostMapping("/{parentUserId}/children")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    @OperationLog(value = "绑定家长学生", module = "parent")
    public Result<ChildResponse> bindChild(@PathVariable Long parentUserId,
                                           @RequestParam Long studentId,
                                           @RequestParam(required = false) String relationship) {
        return Result.success(parentService.bindChild(parentUserId, studentId, relationship));
    }

    /**
     * 管理员解除家长与学生的绑定
     */
    @DeleteMapping("/{parentUserId}/children/{studentId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    @OperationLog(value = "解绑家长学生", module = "parent")
    public Result<Void> unbindChild(@PathVariable Long parentUserId, @PathVariable Long studentId) {
        parentService.unbindChild(parentUserId, studentId);
        return Result.success();
    }

}
