package com.school.module.school.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.school.dto.SchoolCreateRequest;
import com.school.module.school.dto.SchoolQueryRequest;
import com.school.module.school.dto.SchoolResponse;
import com.school.module.school.dto.SchoolUpdateRequest;
import com.school.module.school.entity.School;
import com.school.module.school.mapper.SchoolMapper;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 学校服务实现类
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class SchoolServiceImpl implements SchoolService {

    private final SchoolMapper schoolMapper;

    private static final List<Integer> VALID_STATUS = Arrays.asList(0, 1);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SchoolResponse createSchool(SchoolCreateRequest request) {
        // 参数校验
        if (request == null) {
            throw new IllegalArgumentException("创建请求不能为空");
        }

        // 检查学校编码是否已存在
        School existingSchool = schoolMapper.selectBySchoolCode(request.getSchoolCode());
        if (existingSchool != null) {
            throw new BusinessException("学校编码已存在");
        }

        // 构建学校实体（不可变方式）
        School school = buildSchoolFromCreateRequest(request);
        school.setStatus(1); // 默认启用

        // 插入数据库
        schoolMapper.insert(school);

        // 返回响应
        return convertToResponse(school);
    }

    @Override
    public SchoolResponse getSchoolById(Long id) {
        // ponytail: G-2 — school 不受租户插件改写，非超管只能查本校那一所；未认证的内部调用沿用租户处理器的放行约定
        if (SecurityUtils.isAuthenticated() && !SecurityUtils.isSuperAdmin()
                && !SecurityUtils.currentSchoolId().equals(id)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不允许访问其他学校的数据");
        }
        School school = schoolMapper.selectById(id);
        if (school == null) {
            throw new BusinessException("学校不存在");
        }
        return convertToResponse(school);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SchoolResponse updateSchool(Long id, SchoolUpdateRequest request) {
        // 查询学校是否存在
        School existingSchool = schoolMapper.selectById(id);
        if (existingSchool == null) {
            throw new BusinessException("学校不存在");
        }

        // 构建更新后的学校对象（不可变方式）
        School updatedSchool = buildSchoolFromUpdateRequest(existingSchool, request);

        // 更新数据库
        schoolMapper.updateById(updatedSchool);

        // 返回更新后的数据
        return convertToResponse(updatedSchool);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSchoolStatus(Long id, Integer status) {
        // 校验状态值
        if (!VALID_STATUS.contains(status)) {
            throw new IllegalArgumentException("无效的状态值，只能是0或1");
        }

        // 查询学校是否存在
        School existingSchool = schoolMapper.selectById(id);
        if (existingSchool == null) {
            throw new BusinessException("学校不存在");
        }

        // 构建更新对象（不可变方式）
        School updatedSchool = new School();
        updatedSchool.setId(existingSchool.getId());
        updatedSchool.setSchoolName(existingSchool.getSchoolName());
        updatedSchool.setSchoolCode(existingSchool.getSchoolCode());
        updatedSchool.setSchoolType(existingSchool.getSchoolType());
        updatedSchool.setProvince(existingSchool.getProvince());
        updatedSchool.setCity(existingSchool.getCity());
        updatedSchool.setDistrict(existingSchool.getDistrict());
        updatedSchool.setAddress(existingSchool.getAddress());
        updatedSchool.setContactPerson(existingSchool.getContactPerson());
        updatedSchool.setContactPhone(existingSchool.getContactPhone());
        updatedSchool.setStatus(status);

        // 更新数据库
        schoolMapper.updateById(updatedSchool);
    }

    @Override
    public Page<SchoolResponse> listSchools(SchoolQueryRequest request) {
        // ponytail: G-2 — school 不在租户表白名单，非超管的列表必须收窄到本校（SchoolMapper.xml 按 query.id 过滤）
        SchoolQueryRequest effective = narrowToOwnSchool(request);

        // 构建分页对象
        Page<School> page = new Page<>(effective.getPageNum(), effective.getPageSize());

        // 分页查询
        Page<School> resultPage = schoolMapper.selectPageByQuery(page, effective);

        // 转换为响应DTO
        Page<SchoolResponse> responsePage = new Page<>(
                resultPage.getCurrent(),
                resultPage.getSize(),
                resultPage.getTotal()
        );

        List<SchoolResponse> responses = resultPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        responsePage.setRecords(responses);

        return responsePage;
    }

    /**
     * 非超管只能看到本校：重建查询请求并注入本校ID（不改入参）
     * <p>
     * 未认证的内部调用（如 AI 工具线程）沿用 SchoolTenantHandler 的放行约定，不在此拦截。
     */
    private SchoolQueryRequest narrowToOwnSchool(SchoolQueryRequest request) {
        if (!SecurityUtils.isAuthenticated() || SecurityUtils.isSuperAdmin()) {
            return request;
        }
        return SchoolQueryRequest.builder()
                .pageNum(request.getPageNum())
                .pageSize(request.getPageSize())
                .orderBy(request.getOrderBy())
                .isAsc(request.getIsAsc())
                .id(SecurityUtils.currentSchoolId())
                .schoolName(request.getSchoolName())
                .schoolCode(request.getSchoolCode())
                .schoolType(request.getSchoolType())
                .province(request.getProvince())
                .city(request.getCity())
                .district(request.getDistrict())
                .status(request.getStatus())
                .build();
    }

    /**
     * 从创建请求构建学校实体
     */
    private School buildSchoolFromCreateRequest(SchoolCreateRequest request) {
        School school = new School();
        school.setSchoolName(request.getSchoolName());
        school.setSchoolCode(request.getSchoolCode());
        school.setSchoolType(request.getSchoolType());
        school.setProvince(request.getProvince());
        school.setCity(request.getCity());
        school.setDistrict(request.getDistrict());
        school.setAddress(request.getAddress());
        school.setContactPerson(request.getContactPerson());
        school.setContactPhone(request.getContactPhone());
        return school;
    }

    /**
     * 从更新请求构建学校实体（不可变方式）
     */
    private School buildSchoolFromUpdateRequest(School existing, SchoolUpdateRequest request) {
        School school = new School();
        school.setId(existing.getId());
        school.setSchoolName(request.getSchoolName() != null ? request.getSchoolName() : existing.getSchoolName());
        school.setSchoolCode(existing.getSchoolCode()); // 学校编码不允许修改
        school.setSchoolType(request.getSchoolType() != null ? request.getSchoolType() : existing.getSchoolType());
        school.setProvince(request.getProvince() != null ? request.getProvince() : existing.getProvince());
        school.setCity(request.getCity() != null ? request.getCity() : existing.getCity());
        school.setDistrict(request.getDistrict() != null ? request.getDistrict() : existing.getDistrict());
        school.setAddress(request.getAddress() != null ? request.getAddress() : existing.getAddress());
        school.setContactPerson(request.getContactPerson() != null ? request.getContactPerson() : existing.getContactPerson());
        school.setContactPhone(request.getContactPhone() != null ? request.getContactPhone() : existing.getContactPhone());
        school.setStatus(existing.getStatus());
        return school;
    }

    /**
     * 转换为响应DTO
     */
    private SchoolResponse convertToResponse(School school) {
        return SchoolResponse.builder()
                .id(school.getId())
                .schoolName(school.getSchoolName())
                .schoolCode(school.getSchoolCode())
                .schoolType(school.getSchoolType())
                .province(school.getProvince())
                .city(school.getCity())
                .district(school.getDistrict())
                .address(school.getAddress())
                .contactPerson(school.getContactPerson())
                .contactPhone(school.getContactPhone())
                .status(school.getStatus())
                .createdAt(school.getCreatedAt())
                .updatedAt(school.getUpdatedAt())
                .build();
    }

}
