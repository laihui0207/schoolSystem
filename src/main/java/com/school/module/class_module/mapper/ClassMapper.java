package com.school.module.class_module.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.class_module.dto.ClassQueryRequest;
import com.school.module.class_module.entity.SchoolClass;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 班级Mapper
 *
 * @author School System
 */
@Mapper
public interface ClassMapper extends BaseMapper<SchoolClass> {

    /**
     * 分页查询班级列表（关联学校、年级、学期、教师信息）
     *
     * @param page 分页对象
     * @param request 查询条件
     * @return 分页结果
     */
    Page<SchoolClass> selectPageByQuery(@Param("page") Page<SchoolClass> page,
                                        @Param("request") ClassQueryRequest request);

    /**
     * 根据学校ID、年级ID、班级编码查询班级
     *
     * @param schoolId 学校ID
     * @param gradeId 年级ID
     * @param classCode 班级编码
     * @return 班级实体
     */
    SchoolClass selectBySchoolGradeCode(@Param("schoolId") Long schoolId,
                                       @Param("gradeId") Long gradeId,
                                       @Param("classCode") Integer classCode);

}
