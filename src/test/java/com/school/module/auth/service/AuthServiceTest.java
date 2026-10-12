package com.school.module.auth.service;

import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.auth.dto.LoginResponse;
import com.school.module.auth.dto.PhoneLoginRequest;
import com.school.module.auth.dto.PublicSchoolResponse;
import com.school.module.auth.dto.RegisterRequest;
import com.school.module.config.service.ScoreRuleService;
import com.school.module.school.entity.School;
import com.school.module.school.mapper.SchoolMapper;
import com.school.module.sms.config.SmsProperties;
import com.school.module.sms.service.SmsCodeService;
import com.school.module.student.entity.Student;
import com.school.module.student.mapper.StudentMapper;
import com.school.module.user.entity.SysUser;
import com.school.module.user.enums.UserRole;
import com.school.module.user.mapper.SysUserMapper;
import com.school.security.model.LoginUser;
import com.school.security.util.JwtTokenUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证服务单测：手机验证码登录、自助注册、公开学校列表
 * <p>
 * 账号密码登录依赖 AuthenticationManager，已有 live-verify 覆盖，这里只测新增路径。
 *
 * @author School System
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("认证服务测试 - 手机号登录/注册")
class AuthServiceTest {

    private static final String PHONE = "13800138000";
    private static final String CODE = "123456";

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtTokenUtil jwtTokenUtil;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private RefreshTokenStore refreshTokenStore;
    @Mock
    private ScoreRuleService scoreRuleService;
    @Mock
    private SmsCodeService smsCodeService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SchoolMapper schoolMapper;
    @Mock
    private StudentMapper studentMapper;

    // ponytail: 配置对象用真实实例（@Spy 让 @InjectMocks 的构造注入也能找到它），测试里直接改开关
    @Spy
    private SmsProperties smsProperties = new SmsProperties();

    @InjectMocks
    private AuthService authService;

    private SysUser studentUser;
    private School activeSchool;

    // ponytail: bindStudentProfile 用 LambdaQueryWrapper/LambdaUpdateWrapper<Student>，MP 解析 Student::getXxx 需要 TableInfo；
    // 纯 Mockito 环境没有 MyBatis 容器，手工注册一次（与 ScoreWarningScanHandlerTest 对 Exam 的做法一致）
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Student.class);
    }

    @BeforeEach
    void setUp() {
        // @Value 字段不走构造器，手工补齐；captcha 关闭避免干扰
        ReflectionTestUtils.setField(authService, "captchaEnabled", false);
        ReflectionTestUtils.setField(authService, "captchaExpire", 300L);
        ReflectionTestUtils.setField(authService, "jwtExpiration", 86_400_000L);

        studentUser = new SysUser();
        studentUser.setId(42L);
        studentUser.setUsername(PHONE);
        studentUser.setPhone(PHONE);
        studentUser.setRealName("张三");
        studentUser.setUserType(UserRole.STUDENT.getUserType());
        studentUser.setSchoolId(1L);
        studentUser.setStatus(1);

        activeSchool = new School();
        activeSchool.setId(1L);
        activeSchool.setSchoolName("第一中学");
        activeSchool.setStatus(1);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(jwtTokenUtil.generateToken(anyLong(), anyString())).thenReturn("access-jwt");
        lenient().when(jwtTokenUtil.generateRefreshToken(anyLong(), anyString())).thenReturn("refresh-jwt");
        lenient().when(jwtTokenUtil.getExpirationMillis()).thenReturn(86_400_000L);
        lenient().when(smsCodeService.requireValidPhone(PHONE)).thenReturn(PHONE);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private LoginUser principalOf(SysUser user, Long studentId) {
        return LoginUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .schoolId(user.getSchoolId())
                .userType(user.getUserType())
                .studentId(studentId)
                .build();
    }

    // ---------------------------------------------------------------- loginByPhone

    @Test
    @DisplayName("手机登录 - 验证码正确且用户存在：签发 token、登记 refresh、返回 studentId")
    void shouldLoginByPhone_WhenCodeValidAndUserExists_ThenIssueTokens() {
        when(userMapper.selectOne(any())).thenReturn(studentUser);
        when(scoreRuleService.maintenanceMode()).thenReturn(false);
        when(userDetailsService.loadUserByUsername(PHONE)).thenReturn(principalOf(studentUser, 7L));

        LoginResponse response = authService.loginByPhone(new PhoneLoginRequest(PHONE, CODE));

        verify(smsCodeService).verify(PHONE, "login", CODE);
        assertThat(response.getAccessToken()).isEqualTo("access-jwt");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-jwt");
        assertThat(response.getExpiresIn()).isEqualTo(86_400L);
        assertThat(response.getUserInfo().getId()).isEqualTo(42L);
        assertThat(response.getUserInfo().getStudentId()).isEqualTo(7L);
        assertThat(response.getUserInfo().getRole()).isEqualTo("student");

        verify(valueOperations).set(eq("token:access-jwt"), eq(42L), anyLong(), any());
        verify(refreshTokenStore).register(42L, "refresh-jwt");
        // 最后登录时间 + phone_verified 标记
        verify(userMapper).updateById(studentUser);
        verify(userMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("手机登录 - 验证码错误：不查用户、不签发")
    void shouldRejectPhoneLogin_WhenCodeInvalid_ThenNoLookup() {
        doThrow(new BusinessException(ResultCode.CAPTCHA_ERROR))
                .when(smsCodeService).verify(PHONE, "login", CODE);

        assertThatThrownBy(() -> authService.loginByPhone(new PhoneLoginRequest(PHONE, CODE)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.CAPTCHA_ERROR.getCode());

        verify(userMapper, never()).selectOne(any());
        verify(refreshTokenStore, never()).register(anyLong(), anyString());
    }

    @Test
    @DisplayName("手机登录 - 手机号未注册：USER_NOT_FOUND，且验证码已先被校验（防枚举）")
    void shouldRejectPhoneLogin_WhenUserNotFound_ThenUserNotFound() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> authService.loginByPhone(new PhoneLoginRequest(PHONE, CODE)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.USER_NOT_FOUND.getCode());

        verify(smsCodeService).verify(PHONE, "login", CODE);
        verify(jwtTokenUtil, never()).generateToken(anyLong(), anyString());
    }

    @Test
    @DisplayName("手机登录 - 维护模式下学生被拒绝，管理员放行")
    void shouldRejectPhoneLogin_WhenMaintenanceAndNotAdmin_ThenServiceUnavailable() {
        when(userMapper.selectOne(any())).thenReturn(studentUser);
        when(scoreRuleService.maintenanceMode()).thenReturn(true);

        assertThatThrownBy(() -> authService.loginByPhone(new PhoneLoginRequest(PHONE, CODE)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.SERVICE_UNAVAILABLE.getCode());
        verify(jwtTokenUtil, never()).generateToken(anyLong(), anyString());

        // 管理员账号同样维护模式下可登录
        SysUser admin = new SysUser();
        admin.setId(1L);
        admin.setUsername(PHONE);
        admin.setUserType(UserRole.ADMIN.getUserType());
        admin.setSchoolId(1L);
        when(userMapper.selectOne(any())).thenReturn(admin);
        when(userDetailsService.loadUserByUsername(PHONE)).thenReturn(principalOf(admin, null));

        LoginResponse response = authService.loginByPhone(new PhoneLoginRequest(PHONE, CODE));
        assertThat(response.getUserInfo().getRole()).isEqualTo("admin");
    }

    @Test
    @DisplayName("手机登录 - 禁用账号在装载认证主体时被拒绝")
    void shouldRejectPhoneLogin_WhenUserDisabled_ThenUserDisabled() {
        when(userMapper.selectOne(any())).thenReturn(studentUser);
        when(scoreRuleService.maintenanceMode()).thenReturn(false);
        when(userDetailsService.loadUserByUsername(PHONE))
                .thenThrow(new BusinessException(ResultCode.USER_DISABLED));

        assertThatThrownBy(() -> authService.loginByPhone(new PhoneLoginRequest(PHONE, CODE)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.USER_DISABLED.getCode());
        verify(refreshTokenStore, never()).register(anyLong(), anyString());
    }

    // ---------------------------------------------------------------- register

    private RegisterRequest studentRegister(String studentNo) {
        return RegisterRequest.builder()
                .phone(PHONE)
                .code(CODE)
                .password("abc12345")
                .realName("张三")
                .role("student")
                .schoolId(1L)
                .studentNo(studentNo)
                .build();
    }

    @Test
    @DisplayName("注册 - 关闭开关时拒绝，不消耗验证码")
    void shouldRejectRegister_WhenDisabled_ThenForbidden() {
        smsProperties.setRegisterEnabled(false);

        assertThatThrownBy(() -> authService.register(studentRegister(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.FORBIDDEN.getCode());

        verify(smsCodeService, never()).verify(anyString(), anyString(), anyString());
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("注册 - 维护模式下拒绝")
    void shouldRejectRegister_WhenMaintenance_ThenServiceUnavailable() {
        when(scoreRuleService.maintenanceMode()).thenReturn(true);

        assertThatThrownBy(() -> authService.register(studentRegister(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.SERVICE_UNAVAILABLE.getCode());
        verify(smsCodeService, never()).verify(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("注册 - 非学生/家长角色拒绝")
    void shouldRejectRegister_WhenRoleNotAllowed_ThenParamError() {
        when(scoreRuleService.maintenanceMode()).thenReturn(false);
        RegisterRequest request = studentRegister(null);
        request.setRole("teacher");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.PARAM_ERROR.getCode());
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("注册 - 学校不存在 / 已停用")
    void shouldRejectRegister_WhenSchoolMissingOrDisabled() {
        when(scoreRuleService.maintenanceMode()).thenReturn(false);
        when(schoolMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> authService.register(studentRegister(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.SCHOOL_NOT_FOUND.getCode());

        activeSchool.setStatus(0);
        when(schoolMapper.selectById(1L)).thenReturn(activeSchool);
        assertThatThrownBy(() -> authService.register(studentRegister(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.SCHOOL_DISABLED.getCode());

        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("注册 - 手机号已被占用")
    void shouldRejectRegister_WhenPhoneTaken_ThenPhoneExists() {
        when(scoreRuleService.maintenanceMode()).thenReturn(false);
        when(schoolMapper.selectById(1L)).thenReturn(activeSchool);
        when(userMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> authService.register(studentRegister(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.PHONE_EXISTS.getCode());
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("注册 - 学生 + 学号匹配：建号、绑角色、绑定学生档案、直接登录")
    void shouldRegisterStudent_WhenStudentNoMatches_ThenBindProfileAndLogin() {
        when(scoreRuleService.maintenanceMode()).thenReturn(false);
        when(schoolMapper.selectById(1L)).thenReturn(activeSchool);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("abc12345")).thenReturn("$2a$10$encoded");
        when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, SysUser.class).setId(99L);
            return 1;
        });
        when(userMapper.insertUserRole(99L, "ROLE_STUDENT")).thenReturn(1);

        Student profile = new Student();
        profile.setId(7L);
        profile.setSchoolId(1L);
        profile.setStudentNo("S001");
        profile.setName("张 三");
        when(studentMapper.selectOne(any())).thenReturn(profile);
        when(studentMapper.update(isNull(), any())).thenReturn(1);
        when(userDetailsService.loadUserByUsername(PHONE)).thenAnswer(invocation -> {
            SysUser created = new SysUser();
            created.setId(99L);
            created.setUsername(PHONE);
            created.setUserType(UserRole.STUDENT.getUserType());
            created.setSchoolId(1L);
            return principalOf(created, 7L);
        });

        LoginResponse response = authService.register(studentRegister("S001"));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        SysUser saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo(PHONE);
        assertThat(saved.getPhone()).isEqualTo(PHONE);
        assertThat(saved.getPassword()).isEqualTo("$2a$10$encoded");
        assertThat(saved.getUserType()).isEqualTo(4);
        assertThat(saved.getSchoolId()).isEqualTo(1L);
        assertThat(saved.getStatus()).isEqualTo(1);

        verify(smsCodeService).verify(PHONE, "register", CODE);
        verify(studentMapper).update(isNull(), any());
        verify(refreshTokenStore).register(99L, "refresh-jwt");
        assertThat(response.getAccessToken()).isEqualTo("access-jwt");
        assertThat(response.getUserInfo().getStudentId()).isEqualTo(7L);
        assertThat(response.getUserInfo().getRole()).isEqualTo("student");
    }

    @Test
    @DisplayName("注册 - 学号已绑他人或姓名不符时跳过绑定但注册成功")
    void shouldRegisterStudent_WhenStudentNoBoundOrNameMismatch_ThenSkipBinding() {
        when(scoreRuleService.maintenanceMode()).thenReturn(false);
        when(schoolMapper.selectById(1L)).thenReturn(activeSchool);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encoded");
        when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, SysUser.class).setId(99L);
            return 1;
        });
        when(userMapper.insertUserRole(99L, "ROLE_STUDENT")).thenReturn(1);
        when(userDetailsService.loadUserByUsername(PHONE)).thenReturn(principalOf(studentUser, null));

        Student bound = new Student();
        bound.setId(7L);
        bound.setName("张三");
        bound.setUserId(5L);
        when(studentMapper.selectOne(any())).thenReturn(bound);
        authService.register(studentRegister("S001"));

        Student otherName = new Student();
        otherName.setId(8L);
        otherName.setName("李四");
        when(studentMapper.selectOne(any())).thenReturn(otherName);
        authService.register(studentRegister("S002"));

        verify(studentMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("注册 - 家长角色不查学生档案")
    void shouldRegisterParent_WhenRoleParent_ThenNoStudentLookup() {
        when(scoreRuleService.maintenanceMode()).thenReturn(false);
        when(schoolMapper.selectById(1L)).thenReturn(activeSchool);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encoded");
        when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, SysUser.class).setId(100L);
            return 1;
        });
        when(userMapper.insertUserRole(100L, "ROLE_PARENT")).thenReturn(1);
        SysUser parent = new SysUser();
        parent.setId(100L);
        parent.setUsername(PHONE);
        parent.setUserType(UserRole.PARENT.getUserType());
        parent.setSchoolId(1L);
        when(userDetailsService.loadUserByUsername(PHONE)).thenReturn(principalOf(parent, null));

        RegisterRequest request = studentRegister("S001");
        request.setRole("parent");
        LoginResponse response = authService.register(request);

        assertThat(response.getUserInfo().getRole()).isEqualTo("parent");
        verify(studentMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("注册 - 角色编码在 sys_role 中不存在时报错")
    void shouldFailRegister_WhenRoleRowMissing_ThenBusinessException() {
        when(scoreRuleService.maintenanceMode()).thenReturn(false);
        when(schoolMapper.selectById(1L)).thenReturn(activeSchool);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encoded");
        when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, SysUser.class).setId(99L);
            return 1;
        });
        when(userMapper.insertUserRole(99L, "ROLE_STUDENT")).thenReturn(0);

        assertThatThrownBy(() -> authService.register(studentRegister(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ROLE_STUDENT");
        verify(refreshTokenStore, never()).register(anyLong(), anyString());
    }

    // ---------------------------------------------------------------- listPublicSchools

    @Test
    @DisplayName("公开学校列表 - 只映射 id 与名称")
    void shouldListPublicSchools_ThenOnlyIdAndName() {
        School another = new School();
        another.setId(2L);
        another.setSchoolName("第二中学");
        another.setContactPhone("13900000000");
        another.setStatus(1);
        when(schoolMapper.selectList(any())).thenReturn(List.of(activeSchool, another));

        List<PublicSchoolResponse> schools = authService.listPublicSchools();

        assertThat(schools).extracting(PublicSchoolResponse::getId).containsExactly(1L, 2L);
        assertThat(schools).extracting(PublicSchoolResponse::getSchoolName).containsExactly("第一中学", "第二中学");
    }

}
