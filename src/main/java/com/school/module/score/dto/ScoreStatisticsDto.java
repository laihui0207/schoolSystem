package com.school.module.score.dto;

import lombok.Data;

@Data
public class ScoreStatisticsDto {
    private int totalCount;
    private int enteredCount;
    private double averageScore;
    private double highestScore;
    private double lowestScore;
    private double passRate;
    private double excellentRate;
}
