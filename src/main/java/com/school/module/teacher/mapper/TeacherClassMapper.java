package com.school.module.teacher.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.module.teacher.entity.TeacherClass;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 教师班级关联 Mapper
 *
 * @author School System
 */
@Mapper
public interface TeacherClassMapper extends BaseMapper<TeacherClass> {

    /**
     * 查询教师任教的所有(classId, subjectId)组合
     */
    List<TeacherClass> selectByTeacherId(@Param("teacherId") Long teacherId);

    /**
     * 查询教师担任班主任的所有班级ID（class.head_teacher_id = teacher.id）
     */
    List<Long> selectHeadClassIdsByTeacherId(@Param("teacherId") Long teacherId);

}
