package com.school.module.school.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.school.dto.SchoolQueryRequest;
import com.school.module.school.entity.School;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 学校Mapper
 *
 * @author School System
 */
@Mapper
public interface SchoolMapper extends BaseMapper<School> {

    Page<School> selectPageByQuery(@Param("page") Page<School> page,
                                   @Param("request") SchoolQueryRequest request);

}
