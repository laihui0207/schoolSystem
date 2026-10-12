package com.school.module.parent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 家长绑定的孩子（学生简要信息）
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChildResponse {

    /**
     * 关联记录ID（parent_student.id）
     */
    private Long id;

    /**
     * 家长用户ID（sys_user.id）
     */
    private Long parentUserId;

    /**
     * 学生ID（student.id）：前端查成绩 / 趋势 / 成绩单一律用它
     */
    private Long studentId;

    private String studentNo;

    private String name;

    /**
     * 当前班级名（取 class_student 的第一条），未分班为 null
     */
    private String className;

    private String gradeName;

    /**
     * 关系编码：father / mother / other
     */
    private String relationship;

    /**
     * 关系展示名：字典 relationship 的 dict_label
     */
    private String relationshipName;

}
