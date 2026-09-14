package com.school.module.teacher.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.teacher.dto.TeacherQueryRequest;
import com.school.module.teacher.entity.Teacher;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 教师Mapper
 *
 * @author School System
 */
@Mapper
public interface TeacherMapper extends BaseMapper<Teacher> {

    /**
     * 分页查询教师列表
     *
     * @param page 分页对象
     * @param request 查询条件
     * @return 分页结果
     */
    Page<Teacher> selectPageByQuery(@Param("page") Page<Teacher> page,
                                   @Param("request") TeacherQueryRequest request);

    /**
     * 根据工号查询教师
     *
     * @param teacherNo 工号
     * @return 教师实体
     */
    Teacher selectByTeacherNo(@Param("teacherNo") String teacherNo);

}
