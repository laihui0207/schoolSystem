package com.school.module.scorechange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.scorechange.dto.ScoreChangeQueryRequest;
import com.school.module.scorechange.entity.ScoreChangeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 成绩变更日志Mapper
 *
 * @author School System
 */
@Mapper
public interface ScoreChangeLogMapper extends BaseMapper<ScoreChangeLog> {

    /**
     * 分页查询变更日志列表
     *
     * @param page 分页对象
     * @param request 查询条件
     * @return 分页结果
     */
    Page<ScoreChangeLog> selectPageByQuery(@Param("page") Page<ScoreChangeLog> page,
                                          @Param("request") ScoreChangeQueryRequest request);

}
