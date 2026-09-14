package com.school.module.student.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.student.dto.StudentQueryRequest;
import com.school.module.student.entity.Student;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 学生Mapper
 *
 * @author School System
 */
@Mapper
public interface StudentMapper extends BaseMapper<Student> {

    /**
     * 分页查询学生列表
     *
     * @param page 分页对象
     * @param request 查询条件
     * @return 分页结果
     */
    Page<Student> selectPageByQuery(@Param("page") Page<Student> page,
                                   @Param("request") StudentQueryRequest request);

    /**
     * 根据学号查询学生
     *
     * @param studentNo 学号
     * @return 学生实体
     */
    Student selectByStudentNo(@Param("studentNo") String studentNo);

}
