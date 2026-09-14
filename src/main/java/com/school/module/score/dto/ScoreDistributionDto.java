package com.school.module.score.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ScoreDistributionDto {
    private String range;
    private int count;
}
