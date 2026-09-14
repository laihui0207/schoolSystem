package com.school.module.grade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.grade.dto.GradeQueryRequest;
import com.school.module.grade.entity.Grade;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 年级Mapper
 *
 * @author School System
 */
@Mapper
public interface GradeMapper extends BaseMapper<Grade> {

    Page<Grade> selectPageByQuery(@Param("page") Page<Grade> page,
                                  @Param("query") GradeQueryRequest query);

    List<Grade> selectBySchoolId(@Param("schoolId") Long schoolId);

    Grade selectBySchoolIdAndCode(@Param("schoolId") Long schoolId,
                                  @Param("gradeCode") Integer gradeCode);

    Integer countClassesByGradeId(@Param("gradeId") Long gradeId);

}
