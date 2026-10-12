package com.school.module.term.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.common.result.Result;
import com.school.module.term.dto.AcademicTermCreateRequest;
import com.school.module.term.dto.AcademicTermQueryRequest;
import com.school.module.term.dto.AcademicTermResponse;
import com.school.module.term.dto.AcademicTermUpdateRequest;
import com.school.module.term.service.AcademicTermService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Academic Term Controller Unit Tests
 *
 * @author School System
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("学年学期控制器测试")
class AcademicTermControllerTest {

    @Mock
    private AcademicTermService termService;

    @InjectMocks
    private AcademicTermController termController;

    private AcademicTermCreateRequest createRequest;
    private AcademicTermUpdateRequest updateRequest;
    private AcademicTermResponse mockResponse;

    @BeforeEach
    void setUp() {
        createRequest = AcademicTermCreateRequest.builder()
                .schoolId(1L)
                .year("2024-2025")
                .term(1)
                .startDate(LocalDate.of(2024, 9, 1))
                .endDate(LocalDate.of(2025, 1, 31))
                .build();

        updateRequest = AcademicTermUpdateRequest.builder()
                .year("2024-2025")
                .term(1)
                .startDate(LocalDate.of(2024, 9, 5))
                .endDate(LocalDate.of(2025, 2, 5))
                .build();

        mockResponse = AcademicTermResponse.builder()
                .id(1L)
                .schoolId(1L)
                .year("2024-2025")
                .term(1)
                .startDate(LocalDate.of(2024, 9, 1))
                .endDate(LocalDate.of(2025, 1, 31))
                .status(1)
                .build();
    }

    @Test
    @DisplayName("创建学期 - 成功")
    void createTerm_Success() {
        // Given
        when(termService.createTerm(any(AcademicTermCreateRequest.class))).thenReturn(mockResponse);

        // When
        Result<AcademicTermResponse> result = termController.createTerm(createRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getYear()).isEqualTo("2024-2025");
        verify(termService).createTerm(createRequest);
    }

    @Test
    @DisplayName("创建学期 - 业务异常")
    void createTerm_BusinessException() {
        // Given
        when(termService.createTerm(any(AcademicTermCreateRequest.class)))
                .thenThrow(new BusinessException("该学校的学年学期已存在"));

        // When & Then
        assertThatThrownBy(() -> termController.createTerm(createRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该学校的学年学期已存在");

        verify(termService).createTerm(createRequest);
    }

    @Test
    @DisplayName("查询学期详情 - 成功")
    void getTermById_Success() {
        // Given
        when(termService.getById(1L)).thenReturn(mockResponse);

        // When
        Result<AcademicTermResponse> result = termController.getTermById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getId()).isEqualTo(1L);
        verify(termService).getById(1L);
    }

    @Test
    @DisplayName("更新学期 - 成功")
    void updateTerm_Success() {
        // Given
        AcademicTermResponse updatedResponse = AcademicTermResponse.builder()
                .id(1L)
                .schoolId(1L)
                .year("2024-2025")
                .term(1)
                .startDate(LocalDate.of(2024, 9, 5))
                .endDate(LocalDate.of(2025, 2, 5))
                .status(1)
                .build();

        when(termService.updateTerm(eq(1L), any(AcademicTermUpdateRequest.class)))
                .thenReturn(updatedResponse);

        // When
        Result<AcademicTermResponse> result = termController.updateTerm(1L, updateRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getStartDate()).isEqualTo(LocalDate.of(2024, 9, 5));
        verify(termService).updateTerm(1L, updateRequest);
    }

    @Test
    @DisplayName("结束学期 - 成功")
    void endTerm_Success() {
        // Given
        doNothing().when(termService).endTerm(1L);

        // When
        Result<Void> result = termController.endTerm(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(200);
        verify(termService).endTerm(1L);
    }

    @Test
    @DisplayName("分页查询学期列表 - 成功")
    void queryTerms_Success() {
        // Given
        AcademicTermQueryRequest queryRequest = new AcademicTermQueryRequest();
        queryRequest.setPageNum(1L);
        queryRequest.setPageSize(10L);

        Page<AcademicTermResponse> mockPage = new Page<>(1, 10);
        mockPage.setRecords(Arrays.asList(mockResponse));
        mockPage.setTotal(1);

        when(termService.queryTerms(any(AcademicTermQueryRequest.class))).thenReturn(mockPage);

        // When
        Result<Page<AcademicTermResponse>> result = termController.queryTerms(queryRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getTotal()).isEqualTo(1);
        assertThat(result.getData().getRecords()).hasSize(1);
        verify(termService).queryTerms(queryRequest);
    }

    @Test
    @DisplayName("获取当前学期 - 成功")
    void getCurrentTerm_Success() {
        // Given
        when(termService.getCurrentTerm(1L)).thenReturn(mockResponse);

        // When
        Result<AcademicTermResponse> result = termController.getCurrentTerm(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getStatus()).isEqualTo(1);
        verify(termService).getCurrentTerm(1L);
    }

    @Test
    @DisplayName("按学校查询学期列表 - 成功")
    void listBySchool_Success() {
        // Given
        AcademicTermResponse response2 = AcademicTermResponse.builder()
                .id(2L)
                .schoolId(1L)
                .year("2024-2025")
                .term(2)
                .startDate(LocalDate.of(2025, 2, 1))
                .endDate(LocalDate.of(2025, 7, 31))
                .status(0)
                .build();

        when(termService.listBySchool(1L)).thenReturn(Arrays.asList(mockResponse, response2));

        // When
        Result<List<AcademicTermResponse>> result = termController.listBySchool(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData()).hasSize(2);
        verify(termService).listBySchool(1L);
    }
}
