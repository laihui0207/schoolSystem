package com.school.module.score.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.score.dto.ScoreQueryRequest;
import com.school.module.score.entity.Score;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 成绩Mapper
 *
 * @author School System
 */
@Mapper
public interface ScoreMapper extends BaseMapper<Score> {

    /**
     * 分页查询成绩列表
     */
    Page<Score> selectPageByQuery(@Param("page") Page<Score> page,
                                  @Param("request") ScoreQueryRequest request);

    /**
     * 查询成绩是否已存在（含 school_id 防跨校覆盖）
     */
    Score selectByExamStudentSubject(@Param("schoolId") Long schoolId,
                                    @Param("examId") Long examId,
                                    @Param("studentId") Long studentId,
                                    @Param("subjectId") Long subjectId);

    /**
     * 批量查询某次考试的成绩
     * <p>
     * schoolId 是跨校隔离的唯一保障：租户插件不改写手写 XML。
     * 传 null 表示不限学校（仅超级管理员），取值一律来自
     * {@link com.school.security.util.SecurityUtils#currentSchoolIdForQuery()}，
     * 不要直接透传请求参数。
     */
    default List<Score> selectByExamId(Long schoolId, Long examId, Long classId) {
        return selectByExamId(schoolId, examId, classId, null);
    }

    /**
     * 同上，额外按科目过滤。subjectId 传 null 等同于不限科目。
     * <p>
     * 单科统计走这个重载：库里只返回该科的行，不要取回整场考试再内存过滤。
     */
    List<Score> selectByExamId(@Param("schoolId") Long schoolId,
                               @Param("examId") Long examId,
                               @Param("classId") Long classId,
                               @Param("subjectId") Long subjectId);

    /**
     * 审核列表（按考试+班级+科目聚合），SQL 内分页
     * <p>
     * ponytail: B-3——不走 PaginationInnerInterceptor（maxLimit=1000 会把成绩行截断导致分组丢失），
     * 用 LIMIT/OFFSET 对聚合后的分组分页。status 为 null 表示不过滤；整组状态取 MIN(s.status)。
     */
    List<Map<String, Object>> selectReviewList(@Param("examId") Long examId,
                                                @Param("classId") Long classId,
                                                @Param("subjectId") Long subjectId,
                                                @Param("schoolId") Long schoolId,
                                                @Param("status") Integer status,
                                                @Param("size") long size,
                                                @Param("offset") long offset);

    /**
     * 审核列表分组总数（与 selectReviewList 同一套过滤条件）
     */
    long selectReviewListCount(@Param("examId") Long examId,
                               @Param("classId") Long classId,
                               @Param("subjectId") Long subjectId,
                               @Param("schoolId") Long schoolId,
                               @Param("status") Integer status);

    /**
     * 班级+科目的成绩列表（含学生信息）
     */
    List<Map<String, Object>> selectClassScoreItems(@Param("examId") Long examId,
                                                     @Param("classId") Long classId,
                                                     @Param("subjectId") Long subjectId);

    /**
     * 考试录入进度（按班级+科目统计）
     */
    List<Map<String, Object>> selectExamProgress(@Param("examId") Long examId);

}
