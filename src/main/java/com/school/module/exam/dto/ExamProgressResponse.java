package com.school.module.exam.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExamProgressResponse {
    private Long examId;
    private List<ExamProgressItemDto> items;
}
