package com.school.module.school.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.module.school.dto.SchoolCreateRequest;
import com.school.module.school.dto.SchoolQueryRequest;
import com.school.module.school.dto.SchoolResponse;
import com.school.module.school.dto.SchoolUpdateRequest;
import com.school.module.school.entity.School;
import com.school.module.school.mapper.SchoolMapper;
import com.school.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * School Service Unit Tests
 * TDD Approach: Test First, Implementation Second
 *
 * @author School System
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("学校服务测试")
class SchoolServiceTest {

    @Mock
    private SchoolMapper schoolMapper;

    @InjectMocks
    private SchoolServiceImpl schoolService;

    private SchoolCreateRequest createRequest;
    private SchoolUpdateRequest updateRequest;
    private School mockSchool;

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @BeforeEach
    void setUp() {
        // ponytail: G-2 — listSchools/getSchoolById 现在按登录身份收窄，纯 Mockito 测试需要登录桩；默认超管保持原有用例语义
        TestAuth.asSuperAdmin();

        // 准备测试数据
        createRequest = SchoolCreateRequest.builder()
                .schoolName("北京第一中学")
                .schoolCode("BJ001")
                .schoolType("PUBLIC")
                .province("北京市")
                .city("北京市")
                .district("海淀区")
                .address("中关村大街1号")
                .contactPerson("张三")
                .contactPhone("13800138000")
                .build();

        updateRequest = SchoolUpdateRequest.builder()
                .schoolName("北京第一中学（更新）")
                .schoolType("PUBLIC")
                .province("北京市")
                .city("北京市")
                .district("海淀区")
                .address("中关村大街2号")
                .contactPerson("李四")
                .contactPhone("13800138001")
                .build();

        mockSchool = new School();
        mockSchool.setId(1L);
        mockSchool.setSchoolName("北京第一中学");
        mockSchool.setSchoolCode("BJ001");
        mockSchool.setSchoolType("PUBLIC");
        mockSchool.setProvince("北京市");
        mockSchool.setCity("北京市");
        mockSchool.setDistrict("海淀区");
        mockSchool.setAddress("中关村大街1号");
        mockSchool.setContactPerson("张三");
        mockSchool.setContactPhone("13800138000");
        mockSchool.setStatus(1);
    }

    @Test
    @DisplayName("创建学校 - 成功")
    void createSchool_Success() {
        // Given
        when(schoolMapper.selectBySchoolCode(createRequest.getSchoolCode())).thenReturn(null);
        when(schoolMapper.insert(any(School.class))).thenReturn(1);

        // When
        SchoolResponse response = schoolService.createSchool(createRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getSchoolName()).isEqualTo("北京第一中学");
        assertThat(response.getSchoolCode()).isEqualTo("BJ001");
        verify(schoolMapper).selectBySchoolCode("BJ001");
        verify(schoolMapper).insert(any(School.class));
    }

    @Test
    @DisplayName("创建学校 - 学校编码已存在")
    void createSchool_DuplicateCode() {
        // Given
        when(schoolMapper.selectBySchoolCode(createRequest.getSchoolCode())).thenReturn(mockSchool);

        // When & Then
        assertThatThrownBy(() -> schoolService.createSchool(createRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学校编码已存在");

        verify(schoolMapper).selectBySchoolCode("BJ001");
        verify(schoolMapper, never()).insert(any(School.class));
    }

    @Test
    @DisplayName("创建学校 - 空值校验")
    void createSchool_NullRequest() {
        // When & Then
        assertThatThrownBy(() -> schoolService.createSchool(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("查询学校详情 - 成功")
    void getSchoolById_Success() {
        // Given
        when(schoolMapper.selectById(1L)).thenReturn(mockSchool);

        // When
        SchoolResponse response = schoolService.getSchoolById(1L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getSchoolName()).isEqualTo("北京第一中学");
        verify(schoolMapper).selectById(1L);
    }

    @Test
    @DisplayName("查询学校详情 - 学校不存在")
    void getSchoolById_NotFound() {
        // Given
        when(schoolMapper.selectById(999L)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> schoolService.getSchoolById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学校不存在");

        verify(schoolMapper).selectById(999L);
    }

    @Test
    @DisplayName("更新学校 - 成功")
    void updateSchool_Success() {
        // Given
        when(schoolMapper.selectById(1L)).thenReturn(mockSchool);
        when(schoolMapper.updateById(any(School.class))).thenReturn(1);

        // When
        SchoolResponse response = schoolService.updateSchool(1L, updateRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getSchoolName()).isEqualTo("北京第一中学（更新）");
        assertThat(response.getAddress()).isEqualTo("中关村大街2号");
        verify(schoolMapper).selectById(1L);
        verify(schoolMapper).updateById(any(School.class));
    }

    @Test
    @DisplayName("更新学校 - 学校不存在")
    void updateSchool_NotFound() {
        // Given
        when(schoolMapper.selectById(999L)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> schoolService.updateSchool(999L, updateRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学校不存在");

        verify(schoolMapper).selectById(999L);
        verify(schoolMapper, never()).updateById(any(School.class));
    }

    @Test
    @DisplayName("更新学校状态 - 启用")
    void updateSchoolStatus_Enable() {
        // Given
        when(schoolMapper.selectById(1L)).thenReturn(mockSchool);
        when(schoolMapper.updateById(any(School.class))).thenReturn(1);

        // When
        schoolService.updateSchoolStatus(1L, 1);

        // Then
        verify(schoolMapper).selectById(1L);
        verify(schoolMapper).updateById(any(School.class));
    }

    @Test
    @DisplayName("更新学校状态 - 禁用")
    void updateSchoolStatus_Disable() {
        // Given
        when(schoolMapper.selectById(1L)).thenReturn(mockSchool);
        when(schoolMapper.updateById(any(School.class))).thenReturn(1);

        // When
        schoolService.updateSchoolStatus(1L, 0);

        // Then
        verify(schoolMapper).selectById(1L);
        verify(schoolMapper).updateById(any(School.class));
    }

    @Test
    @DisplayName("更新学校状态 - 无效状态值")
    void updateSchoolStatus_InvalidStatus() {
        // When & Then
        assertThatThrownBy(() -> schoolService.updateSchoolStatus(1L, 99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无效的状态值");

        verify(schoolMapper, never()).selectById(anyLong());
        verify(schoolMapper, never()).updateById(any(School.class));
    }

    @Test
    @DisplayName("分页查询学校列表 - 成功")
    void listSchools_Success() {
        // Given
        SchoolQueryRequest queryRequest = new SchoolQueryRequest();
        queryRequest.setPageNum(1L);
        queryRequest.setPageSize(10L);

        Page<School> mockPage = new Page<>(1, 10);
        mockPage.setRecords(Arrays.asList(mockSchool));
        mockPage.setTotal(1);

        when(schoolMapper.selectPageByQuery(any(Page.class), any(SchoolQueryRequest.class)))
                .thenReturn(mockPage);

        // When
        Page<SchoolResponse> result = schoolService.listSchools(queryRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getSchoolName()).isEqualTo("北京第一中学");
        verify(schoolMapper).selectPageByQuery(any(Page.class), any(SchoolQueryRequest.class));
    }

    @Test
    @DisplayName("按名称搜索学校")
    void listSchools_SearchByName() {
        // Given
        SchoolQueryRequest queryRequest = new SchoolQueryRequest();
        queryRequest.setSchoolName("第一");
        queryRequest.setPageNum(1L);
        queryRequest.setPageSize(10L);

        Page<School> mockPage = new Page<>(1, 10);
        mockPage.setRecords(Arrays.asList(mockSchool));
        mockPage.setTotal(1);

        when(schoolMapper.selectPageByQuery(any(Page.class), any(SchoolQueryRequest.class)))
                .thenReturn(mockPage);

        // When
        Page<SchoolResponse> result = schoolService.listSchools(queryRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isEqualTo(1);
        verify(schoolMapper).selectPageByQuery(any(Page.class), eq(queryRequest));
    }

    @Test
    @DisplayName("按省市区筛选学校")
    void listSchools_FilterByLocation() {
        // Given
        SchoolQueryRequest queryRequest = new SchoolQueryRequest();
        queryRequest.setProvince("北京市");
        queryRequest.setCity("北京市");
        queryRequest.setDistrict("海淀区");
        queryRequest.setPageNum(1L);
        queryRequest.setPageSize(10L);

        Page<School> mockPage = new Page<>(1, 10);
        mockPage.setRecords(Arrays.asList(mockSchool));
        mockPage.setTotal(1);

        when(schoolMapper.selectPageByQuery(any(Page.class), any(SchoolQueryRequest.class)))
                .thenReturn(mockPage);

        // When
        Page<SchoolResponse> result = schoolService.listSchools(queryRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isEqualTo(1);
        verify(schoolMapper).selectPageByQuery(any(Page.class), eq(queryRequest));
    }

    @Test
    @DisplayName("查询空结果")
    void listSchools_EmptyResult() {
        // Given
        SchoolQueryRequest queryRequest = new SchoolQueryRequest();
        queryRequest.setPageNum(1L);
        queryRequest.setPageSize(10L);

        Page<School> mockPage = new Page<>(1, 10);
        mockPage.setRecords(Arrays.asList());
        mockPage.setTotal(0);

        when(schoolMapper.selectPageByQuery(any(Page.class), any(SchoolQueryRequest.class)))
                .thenReturn(mockPage);

        // When
        Page<SchoolResponse> result = schoolService.listSchools(queryRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isEqualTo(0);
        assertThat(result.getRecords()).isEmpty();
    }

    @Test
    @DisplayName("校管理员查询学校列表 - 收窄到本校")
    void listSchools_SchoolAdmin_NarrowedToOwnSchool() {
        // Given: 学校 1 的校管理员，带名称筛选与非默认分页
        TestAuth.asSchoolAdmin(1L);
        SchoolQueryRequest queryRequest = new SchoolQueryRequest();
        queryRequest.setSchoolName("第一");
        queryRequest.setPageNum(2L);
        queryRequest.setPageSize(5L);

        Page<School> mockPage = new Page<>(2, 5);
        mockPage.setRecords(Arrays.asList(mockSchool));
        mockPage.setTotal(1);
        when(schoolMapper.selectPageByQuery(any(Page.class), any(SchoolQueryRequest.class)))
                .thenReturn(mockPage);

        // When
        schoolService.listSchools(queryRequest);

        // Then: 请求被重建并注入本校 id，其余筛选与分页原样保留
        ArgumentCaptor<SchoolQueryRequest> captor = ArgumentCaptor.forClass(SchoolQueryRequest.class);
        verify(schoolMapper).selectPageByQuery(any(Page.class), captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L);
        assertThat(captor.getValue().getSchoolName()).isEqualTo("第一");
        assertThat(captor.getValue().getPageNum()).isEqualTo(2L);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5L);
    }

    @Test
    @DisplayName("校管理员查询别校详情 - 拒绝")
    void getSchoolById_SchoolAdmin_OtherSchool_Forbidden() {
        // Given
        TestAuth.asSchoolAdmin(1L);

        // When & Then: 不落库直接拒绝
        assertThatThrownBy(() -> schoolService.getSchoolById(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("其他学校");
        verify(schoolMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("校管理员查询本校详情 - 成功")
    void getSchoolById_SchoolAdmin_OwnSchool_Success() {
        // Given
        TestAuth.asSchoolAdmin(1L);
        when(schoolMapper.selectById(1L)).thenReturn(mockSchool);

        // When
        SchoolResponse response = schoolService.getSchoolById(1L);

        // Then
        assertThat(response.getId()).isEqualTo(1L);
        verify(schoolMapper).selectById(1L);
    }
}
