package com.school.module.term.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.term.dto.AcademicTermQueryRequest;
import com.school.module.term.entity.AcademicTerm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 学期Mapper
 *
 * @author School System
 */
@Mapper
public interface AcademicTermMapper extends BaseMapper<AcademicTerm> {

    Page<AcademicTerm> selectPageByQuery(@Param("page") Page<AcademicTerm> page,
                                         @Param("query") AcademicTermQueryRequest query);

}
