package com.school.module.parent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.module.parent.entity.ParentStudent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 家长学生关联 Mapper
 *
 * @author School System
 */
@Mapper
public interface ParentStudentMapper extends BaseMapper<ParentStudent> {

    /**
     * 某家长绑定的全部学生ID（未删除行，按绑定先后）
     * <p>
     * 登录时由 UserDetailsServiceImpl 调用装载 LoginUser.childStudentIds。
     *
     * @param parentUserId 家长用户ID（sys_user.id）
     * @return student.id 列表，未绑定时为空列表
     */
    List<Long> selectStudentIdsByParentUserId(@Param("parentUserId") Long parentUserId);

    /**
     * 这批学生各自绑定的家长关联行（未删除）
     * <p>
     * 成绩发布 / 预警通知扩散到家长时使用。
     *
     * @param studentIds 学生ID集合，调用方保证非空
     * @return 关联行，按 student_id、id 排序
     */
    List<ParentStudent> selectByStudentIds(@Param("studentIds") Collection<Long> studentIds);

}
