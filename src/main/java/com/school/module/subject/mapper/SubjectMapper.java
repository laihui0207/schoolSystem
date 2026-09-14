package com.school.module.subject.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.subject.dto.SubjectQueryRequest;
import com.school.module.subject.entity.Subject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 科目Mapper
 *
 * @author School System
 */
@Mapper
public interface SubjectMapper extends BaseMapper<Subject> {

    Page<Subject> selectPageByQuery(@Param("page") Page<Subject> page,
                                    @Param("query") SubjectQueryRequest query);

    List<Subject> selectBySchoolId(@Param("schoolId") Long schoolId);

}
