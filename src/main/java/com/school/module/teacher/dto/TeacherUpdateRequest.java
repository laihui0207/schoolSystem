package com.school.module.teacher.dto;

import lombok.Data;

/**
 * 教师更新请求
 *
 * @author School System
 */
@Data
public class TeacherUpdateRequest {

    /**
     * 姓名
     */
    private String name;

    /**
     * 性别：1-男，2-女
     */
    private Integer gender;

    /**
     * 主教科目ID
     */
    private Long subjectId;

    /**
     * 状态：0-离职，1-在职
     */
    private Integer status;

    /**
     * 绑定的登录账号 ID（sys_user.id）
     * <p>
     * ponytail: 教师档案与登录账号此前只能在创建时绑定，已存在的档案无法补绑，
     * 导致教师账号登录后 LoginUser.teacherId 为空、任教范围判定全部落空。
     * 传 null 表示不改动；要解绑请传 0。
     */
    private Long userId;

}
