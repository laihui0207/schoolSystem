package com.school.module.term.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.term.dto.AcademicTermQueryRequest;
import com.school.module.term.entity.AcademicTerm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 学年学期Mapper
 *
 * @author School System
 */
@Mapper
public interface AcademicTermMapper extends BaseMapper<AcademicTerm> {

    /**
     * 根据学校ID、学年、学期查询
     */
    AcademicTerm selectBySchoolYearTerm(@Param("schoolId") Long schoolId,
                                        @Param("year") String year,
                                        @Param("term") Integer term);

    /**
     * 查询学校的进行中的学期
     */
    AcademicTerm selectActiveTermBySchool(@Param("schoolId") Long schoolId);

    /**
     * 分页查询学年学期列表
     */
    Page<AcademicTerm> selectPageByQuery(@Param("page") Page<AcademicTerm> page,
                                         @Param("query") AcademicTermQueryRequest query);

    /**
     * 根据学校ID查询所有学期
     */
    List<AcademicTerm> selectBySchoolId(@Param("schoolId") Long schoolId);

    /**
     * 获取当前学期（status=1且当前日期在学期范围内）
     */
    AcademicTerm selectCurrentTerm(@Param("schoolId") Long schoolId);

}
