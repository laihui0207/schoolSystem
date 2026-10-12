package com.school.module.teacher.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.module.teacher.entity.TeacherClass;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 教师班级关联 Mapper
 */
@Mapper
public interface TeacherClassMapper extends BaseMapper<TeacherClass> {

    List<TeacherClass> selectByTeacherId(@Param("teacherId") Long teacherId);

    List<Long> selectHeadClassIdsByTeacherId(@Param("teacherId") Long teacherId);

    int countByTeacherClassSubject(@Param("teacherId") Long teacherId,
                                   @Param("classId") Long classId,
                                   @Param("subjectId") Long subjectId);

    int deleteByTeacherClassSubject(@Param("teacherId") Long teacherId,
                                    @Param("classId") Long classId,
                                    @Param("subjectId") Long subjectId);

    List<TeacherClassDetail> selectDetailByTeacherId(@Param("teacherId") Long teacherId);

    // ponytail: 列表页按一批 teacherId 一次 IN 查完任课明细，避免每行一次查询
    List<TeacherClassDetail> selectDetailByTeacherIds(@Param("teacherIds") Collection<Long> teacherIds);

    // ponytail: static inner class, not interface — MyBatis needs a concrete type with no-arg ctor
    @Data
    class TeacherClassDetail {
        /** 批量查询时用于按教师分组；单教师查询不填 */
        private Long teacherId;
        private Long classId;
        private String className;
        private Long subjectId;
        private String subjectName;
        private Boolean isHeadTeacher;
    }
}

