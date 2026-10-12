package com.school.module.parent.service;

import com.school.module.parent.dto.ChildResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 家长服务：家长-学生绑定与孩子查询
 *
 * @author School System
 */
public interface ParentService {

    /**
     * 管理员为家长绑定学生
     *
     * @param parentUserId 家长用户ID（sys_user.id，user_type 必须为 5）
     * @param studentId    学生ID（必须与家长同校）
     * @param relationship 关系（字典 relationship 的 value：father/mother/other；空则 other）
     * @return 绑定后的孩子信息
     */
    ChildResponse bindChild(Long parentUserId, Long studentId, String relationship);

    /**
     * 管理员解除家长与学生的绑定（软删）
     */
    void unbindChild(Long parentUserId, Long studentId);

    /**
     * 管理员查看某家长绑定的孩子
     */
    List<ChildResponse> listChildren(Long parentUserId);

    /**
     * 家长本人查看自己绑定的孩子（非家长账号拒绝）
     */
    List<ChildResponse> listMyChildren();

    /**
     * 这批学生各自绑定的家长用户ID（student.id → sys_user.id 列表），供通知扩散
     *
     * @param studentIds 学生ID集合，可为空
     * @return 无家长的学生不出现在结果里
     */
    Map<Long, List<Long>> parentUserIdsByStudentIds(Collection<Long> studentIds);

    /**
     * 这批学生的全部家长用户ID，去重、保持首次出现顺序
     */
    List<Long> parentUserIdsOf(Collection<Long> studentIds);

}
