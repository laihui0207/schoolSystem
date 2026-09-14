package com.school.module.scorechange.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.module.score.entity.Score;
import com.school.module.score.mapper.ScoreMapper;
import com.school.module.scorechange.dto.*;
import com.school.module.scorechange.entity.ScoreChangeLog;
import com.school.module.scorechange.mapper.ScoreChangeLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 成绩变更服务实现类
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class ScoreChangeServiceImpl implements ScoreChangeService {

    private final ScoreChangeLogMapper changeLogMapper;
    private final ScoreMapper scoreMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScoreChangeLogResponse applyScoreChange(ScoreChangeApplyRequest request, Long applicantId) {
        // 查询成绩
        Score score = scoreMapper.selectById(request.getScoreId());
        if (score == null) {
            throw new BusinessException("成绩不存在");
        }

        // 只能修改已发布的成绩
        if (score.getStatus() != 1) {
            throw new BusinessException("只能修改已审核通过的成绩");
        }

        // 检查是否有待审批的申请
        ScoreChangeQueryRequest queryRequest = ScoreChangeQueryRequest.builder()
                .status(0)
                .build();
        Page<ScoreChangeLog> page = new Page<>(1, 1);
        Page<ScoreChangeLog> existingApplies = changeLogMapper.selectPageByQuery(page, queryRequest);

        for (ScoreChangeLog log : existingApplies.getRecords()) {
            if (log.getScoreId().equals(request.getScoreId())) {
                throw new BusinessException("该成绩已有待审批的修改申请，请勿重复提交");
            }
        }

        // 创建变更日志
        ScoreChangeLog changeLog = new ScoreChangeLog();
        changeLog.setScoreId(request.getScoreId());
        changeLog.setExamId(score.getExamId());
        changeLog.setStudentId(score.getStudentId());
        changeLog.setSubjectId(score.getSubjectId());
        changeLog.setOldScore(score.getScore());
        changeLog.setNewScore(request.getNewScore());
        changeLog.setChangeReason(request.getChangeReason());
        changeLog.setApplicantId(applicantId);
        changeLog.setStatus(0); // 待审批
        changeLog.setApplyTime(LocalDateTime.now());

        changeLogMapper.insert(changeLog);

        return convertToResponse(changeLog);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveScoreChange(ScoreChangeApproveRequest request, Long approverId) {
        // 校验审批结果
        if (request.getResult() != 1 && request.getResult() != 2) {
            throw new BusinessException("审批结果无效，只能是1（通过）或2（驳回）");
        }

        // 驳回时必须填写原因
        if (request.getResult() == 2 &&
            (request.getRejectReason() == null || request.getRejectReason().isEmpty())) {
            throw new BusinessException("驳回时必须填写原因");
        }

        LocalDateTime now = LocalDateTime.now();

        for (Long changeLogId : request.getChangeLogIds()) {
            ScoreChangeLog changeLog = changeLogMapper.selectById(changeLogId);
            if (changeLog == null) {
                continue;
            }

            // 只能审批待审批状态的申请
            if (changeLog.getStatus() != 0) {
                continue;
            }

            // 更新变更日志
            ScoreChangeLog updated = new ScoreChangeLog();
            updated.setId(changeLog.getId());
            updated.setScoreId(changeLog.getScoreId());
            updated.setExamId(changeLog.getExamId());
            updated.setStudentId(changeLog.getStudentId());
            updated.setSubjectId(changeLog.getSubjectId());
            updated.setOldScore(changeLog.getOldScore());
            updated.setNewScore(changeLog.getNewScore());
            updated.setChangeReason(changeLog.getChangeReason());
            updated.setApplicantId(changeLog.getApplicantId());
            updated.setApproverId(approverId);
            updated.setStatus(request.getResult());
            updated.setApplyTime(changeLog.getApplyTime());
            updated.setApproveTime(now);
            updated.setRejectReason(request.getRejectReason());

            changeLogMapper.updateById(updated);

            // 如果审批通过，更新成绩
            if (request.getResult() == 1) {
                Score score = scoreMapper.selectById(changeLog.getScoreId());
                if (score != null) {
                    Score updatedScore = new Score();
                    updatedScore.setId(score.getId());
                    updatedScore.setExamId(score.getExamId());
                    updatedScore.setStudentId(score.getStudentId());
                    updatedScore.setSubjectId(score.getSubjectId());
                    updatedScore.setClassId(score.getClassId());
                    updatedScore.setSchoolId(score.getSchoolId());
                    updatedScore.setScore(changeLog.getNewScore());
                    updatedScore.setIsAbsent(score.getIsAbsent());
                    updatedScore.setRankInClass(score.getRankInClass());
                    updatedScore.setRankInGrade(score.getRankInGrade());
                    updatedScore.setRankInSchool(score.getRankInSchool());
                    updatedScore.setRecorderId(score.getRecorderId());
                    updatedScore.setStatus(score.getStatus());
                    updatedScore.setRecordedAt(score.getRecordedAt());
                    updatedScore.setReviewedAt(score.getReviewedAt());

                    scoreMapper.updateById(updatedScore);
                }
            }
        }
    }

    @Override
    public ScoreChangeLogResponse getChangeLogById(Long id) {
        ScoreChangeLog changeLog = changeLogMapper.selectById(id);
        if (changeLog == null) {
            throw new BusinessException("变更日志不存在");
        }
        return convertToResponse(changeLog);
    }

    @Override
    public Page<ScoreChangeLogResponse> listChangeLogs(ScoreChangeQueryRequest request) {
        Page<ScoreChangeLog> page = new Page<>(request.getPageNum(), request.getPageSize());
        Page<ScoreChangeLog> resultPage = changeLogMapper.selectPageByQuery(page, request);

        Page<ScoreChangeLogResponse> responsePage = new Page<>(
                resultPage.getCurrent(),
                resultPage.getSize(),
                resultPage.getTotal()
        );

        List<ScoreChangeLogResponse> responses = resultPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        responsePage.setRecords(responses);
        return responsePage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelApply(Long id, Long applicantId) {
        ScoreChangeLog changeLog = changeLogMapper.selectById(id);
        if (changeLog == null) {
            throw new BusinessException("变更日志不存在");
        }

        // 只能撤销自己的申请
        if (!changeLog.getApplicantId().equals(applicantId)) {
            throw new BusinessException("只能撤销自己的申请");
        }

        // 只能撤销待审批的申请
        if (changeLog.getStatus() != 0) {
            throw new BusinessException("只能撤销待审批的申请");
        }

        changeLogMapper.deleteById(id);
    }

    /**
     * 转换为响应DTO
     */
    private ScoreChangeLogResponse convertToResponse(ScoreChangeLog changeLog) {
        return ScoreChangeLogResponse.builder()
                .id(changeLog.getId())
                .scoreId(changeLog.getScoreId())
                .examId(changeLog.getExamId())
                .studentId(changeLog.getStudentId())
                .subjectId(changeLog.getSubjectId())
                .oldScore(changeLog.getOldScore())
                .newScore(changeLog.getNewScore())
                .changeReason(changeLog.getChangeReason())
                .applicantId(changeLog.getApplicantId())
                .approverId(changeLog.getApproverId())
                .status(changeLog.getStatus())
                .statusName(getStatusName(changeLog.getStatus()))
                .applyTime(changeLog.getApplyTime())
                .approveTime(changeLog.getApproveTime())
                .rejectReason(changeLog.getRejectReason())
                .build();
    }

    /**
     * 获取状态名称
     */
    private String getStatusName(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 0 -> "待审批";
            case 1 -> "已通过";
            case 2 -> "已驳回";
            default -> "未知";
        };
    }

}
