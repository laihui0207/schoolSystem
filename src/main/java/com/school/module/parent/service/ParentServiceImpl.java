package com.school.module.parent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.dict.entity.DictData;
import com.school.module.dict.service.DictService;
import com.school.module.parent.dto.ChildResponse;
import com.school.module.parent.entity.ParentStudent;
import com.school.module.parent.mapper.ParentStudentMapper;
import com.school.module.student.dto.StudentClassRow;
import com.school.module.student.entity.Student;
import com.school.module.student.mapper.ClassStudentMapper;
import com.school.module.student.mapper.StudentMapper;
import com.school.module.user.entity.SysUser;
import com.school.module.user.enums.UserRole;
import com.school.module.user.mapper.SysUserMapper;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 家长服务实现
 * <p>
 * 绑定校验三条：家长账号 user_type=5、学生与家长同校、同一对 (家长, 学生) 不重复。
 * 校管理员只能操作本校家长（sys_user 不在租户插件清单内，这里显式比对 school_id）。
 *
 * @author School System
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParentServiceImpl implements ParentService {

    private final ParentStudentMapper parentStudentMapper;
    private final SysUserMapper sysUserMapper;
    private final StudentMapper studentMapper;
    private final ClassStudentMapper classStudentMapper;
    private final DictService dictService;

    /** 关系字典类型（V17 种子：father/mother/other） */
    private static final String DICT_RELATIONSHIP = "relationship";

    private static final String DEFAULT_RELATIONSHIP = "other";

    /** 字典未配置时的兜底取值，与 V17 种子一致 */
    private static final List<String> FALLBACK_RELATIONSHIPS = List.of("father", "mother", DEFAULT_RELATIONSHIP);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChildResponse bindChild(Long parentUserId, Long studentId, String relationship) {
        SysUser parent = requireParentUser(parentUserId);
        if (studentId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "学生ID不能为空");
        }
        // ponytail: studentMapper 走租户插件，校管理员查不到他校学生 → 当作不存在，不泄露 id 是否存在
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "学生不存在");
        }
        // ponytail: 超管不受租户插件约束，必须显式比对同校；家长 school_id 已在 requireParentUser 保证非空
        if (!Objects.equals(student.getSchoolId(), parent.getSchoolId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "家长与学生不属于同一学校，无法绑定");
        }
        Map<String, String> relationshipLabels = relationshipLabels();
        String normalized = normalizeRelationship(relationship, relationshipLabels.keySet());

        Long duplicated = parentStudentMapper.selectCount(new LambdaQueryWrapper<ParentStudent>()
                .eq(ParentStudent::getParentUserId, parentUserId)
                .eq(ParentStudent::getStudentId, studentId));
        if (duplicated != null && duplicated > 0) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "该学生已绑定到此家长");
        }

        ParentStudent link = new ParentStudent();
        link.setParentUserId(parentUserId);
        link.setStudentId(studentId);
        link.setRelationship(normalized);
        parentStudentMapper.insert(link);
        log.info("家长 {} 绑定学生 {}（{}）", parentUserId, studentId, normalized);

        return toResponses(List.of(link), relationshipLabels).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindChild(Long parentUserId, Long studentId) {
        requireParentUser(parentUserId);
        // ponytail: @TableLogic 让 delete(wrapper) 变成 UPDATE deleted=1，V18 的部分唯一索引允许之后重新绑定
        int removed = parentStudentMapper.delete(new LambdaQueryWrapper<ParentStudent>()
                .eq(ParentStudent::getParentUserId, parentUserId)
                .eq(ParentStudent::getStudentId, studentId));
        if (removed == 0) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "未找到该绑定关系");
        }
        log.info("家长 {} 解绑学生 {}", parentUserId, studentId);
    }

    @Override
    public List<ChildResponse> listChildren(Long parentUserId) {
        requireParentUser(parentUserId);
        return toResponses(selectLinks(parentUserId), relationshipLabels());
    }

    @Override
    public List<ChildResponse> listMyChildren() {
        // ponytail: 接口层只要求登录（任何角色都能打 /parents/me/children），身份收口在这里
        if (!SecurityUtils.isParent()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅家长账号可查看我的孩子");
        }
        return toResponses(selectLinks(SecurityUtils.currentUserId()), relationshipLabels());
    }

    @Override
    public Map<Long, List<Long>> parentUserIdsByStudentIds(Collection<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        for (ParentStudent link : parentStudentMapper.selectByStudentIds(studentIds)) {
            if (link.getParentUserId() == null) {
                continue;
            }
            List<Long> parents = result.computeIfAbsent(link.getStudentId(), k -> new ArrayList<>());
            if (!parents.contains(link.getParentUserId())) {
                parents.add(link.getParentUserId());
            }
        }
        return result;
    }

    @Override
    public List<Long> parentUserIdsOf(Collection<Long> studentIds) {
        Set<Long> distinct = new LinkedHashSet<>();
        parentUserIdsByStudentIds(studentIds).values().forEach(distinct::addAll);
        return new ArrayList<>(distinct);
    }

    // ------------------------------------------------------------------
    // 校验
    // ------------------------------------------------------------------

    /**
     * 家长账号必须存在、user_type=5、有学校归属，且校管理员只能操作本校家长
     */
    private SysUser requireParentUser(Long parentUserId) {
        if (parentUserId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "家长用户ID不能为空");
        }
        SysUser user = sysUserMapper.selectById(parentUserId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        // ponytail: sys_user 不走租户插件，校管理员按 id 枚举能摸到他校账号；越界一律按不存在处理
        if (!SecurityUtils.isSuperAdmin() && !Objects.equals(user.getSchoolId(), SecurityUtils.currentSchoolId())) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!UserRole.PARENT.getUserType().equals(user.getUserType())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该用户不是家长账号");
        }
        if (user.getSchoolId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "家长账号未归属学校，无法绑定学生");
        }
        return user;
    }

    /**
     * 关系取值：空则 other；非空必须是字典 relationship 的 dict_value
     */
    private String normalizeRelationship(String relationship, Set<String> allowed) {
        String value = StringUtils.hasText(relationship) ? relationship.trim() : DEFAULT_RELATIONSHIP;
        if (!allowed.contains(value)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "关系取值无效，可选：" + String.join("/", allowed));
        }
        return value;
    }

    // ------------------------------------------------------------------
    // 查询与装配
    // ------------------------------------------------------------------

    private List<ParentStudent> selectLinks(Long parentUserId) {
        return parentStudentMapper.selectList(new LambdaQueryWrapper<ParentStudent>()
                .eq(ParentStudent::getParentUserId, parentUserId)
                .orderByAsc(ParentStudent::getId));
    }

    /**
     * 关系字典 value → label；字典为空时回落 V17 种子的三个取值
     */
    private Map<String, String> relationshipLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        List<DictData> rows = dictService.getDictDataByType(DICT_RELATIONSHIP);
        if (rows != null) {
            rows.stream()
                    .filter(row -> StringUtils.hasText(row.getDictValue()))
                    .forEach(row -> labels.putIfAbsent(row.getDictValue(), row.getDictLabel()));
        }
        if (labels.isEmpty()) {
            FALLBACK_RELATIONSHIPS.forEach(v -> labels.put(v, v));
        }
        return labels;
    }

    private List<ChildResponse> toResponses(List<ParentStudent> links, Map<String, String> relationshipLabels) {
        if (links.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> studentIds = links.stream()
                .map(ParentStudent::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        // ponytail: selectBatchIds 传空集合会生成 IN () 语法错误，先判空
        Map<Long, Student> students = studentIds.isEmpty() ? Collections.emptyMap()
                : studentMapper.selectBatchIds(studentIds).stream()
                        .collect(Collectors.toMap(Student::getId, Function.identity(), (a, b) -> a));
        Map<Long, StudentClassRow> classRows = studentIds.isEmpty() ? Collections.emptyMap()
                : classStudentMapper.selectClassRowsByStudentIds(studentIds).stream()
                        .collect(Collectors.toMap(StudentClassRow::getStudentId, Function.identity(), (a, b) -> a));

        return links.stream()
                .map(link -> {
                    Student student = students.get(link.getStudentId());
                    StudentClassRow classRow = classRows.get(link.getStudentId());
                    return ChildResponse.builder()
                            .id(link.getId())
                            .parentUserId(link.getParentUserId())
                            .studentId(link.getStudentId())
                            .studentNo(student == null ? null : student.getStudentNo())
                            .name(student == null ? null : student.getName())
                            .className(classRow == null ? null : classRow.getClassName())
                            .gradeName(classRow == null ? null : classRow.getGradeName())
                            .relationship(link.getRelationship())
                            .relationshipName(relationshipLabels.getOrDefault(link.getRelationship(), link.getRelationship()))
                            .build();
                })
                .collect(Collectors.toList());
    }

}
