package com.school.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.school.common.constant.CommonConstants;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.auth.dto.CaptchaResponse;
import com.school.module.auth.dto.LoginRequest;
import com.school.module.auth.dto.LoginResponse;
import com.school.module.auth.dto.PhoneLoginRequest;
import com.school.module.auth.dto.PublicSchoolResponse;
import com.school.module.auth.dto.RegisterRequest;
import com.school.module.config.service.ScoreRuleService;
import com.school.module.school.entity.School;
import com.school.module.school.mapper.SchoolMapper;
import com.school.module.sms.config.SmsProperties;
import com.school.module.sms.enums.SmsScene;
import com.school.module.sms.service.SmsCodeService;
import com.school.module.student.entity.Student;
import com.school.module.student.mapper.StudentMapper;
import com.school.module.user.entity.SysUser;
import com.school.module.user.enums.UserRole;
import com.school.module.user.mapper.SysUserMapper;
import com.school.security.model.LoginUser;
import com.school.security.util.JwtTokenUtil;
import com.school.security.util.SecurityUtils;
import com.wf.captcha.SpecCaptcha;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现
 * <p>
 * 三条登录入口（账号密码 / 手机验证码 / 注册即登录）最终都走 {@link #issueTokens}，
 * 签发出的 LoginResponse 结构与 Redis 登记完全一致。
 *
 * @author School System
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final SysUserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserDetailsService userDetailsService;
    private final RefreshTokenStore refreshTokenStore;
    private final ScoreRuleService scoreRuleService;
    private final SmsCodeService smsCodeService;
    private final PasswordEncoder passwordEncoder;
    private final SchoolMapper schoolMapper;
    private final StudentMapper studentMapper;
    private final SmsProperties smsProperties;

    @Value("${app.captcha.enabled:true}")
    private Boolean captchaEnabled;

    @Value("${app.captcha.expire:300}")
    private Long captchaExpire;

    @Value("${app.jwt.expiration}")
    private Long jwtExpiration;

    /**
     * 用户登录
     */
    public LoginResponse login(LoginRequest request) {
        // 验证验证码
        if (captchaEnabled) {
            validateCaptcha(request.getCaptchaKey(), request.getCaptcha());
        }

        try {
            // 执行认证
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // 查询用户信息
            SysUser user = userMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getUsername, request.getUsername())
                            .eq(SysUser::getDeleted, CommonConstants.NOT_DELETED)
            );

            if (user == null) {
                throw new BusinessException(ResultCode.USER_NOT_FOUND);
            }

            // ponytail: 维护模式检查放在密码校验之后——"系统维护中"只回给凭证正确的用户，
            // 不会变成探测账号是否存在的信道；判断管理员要用 sys_user.user_type，也只有到这里才拿得到。
            // BusinessException 不是 AuthenticationException，不会被下面的 catch 吞成 LOGIN_FAIL。
            assertNotInMaintenance(user);

            // 构建用户信息：studentId / teacherId / headTeacher 只有认证主体 LoginUser 才有
            return issueTokens(user, asLoginUser(authentication.getPrincipal()));

        } catch (AuthenticationException e) {
            log.error("登录失败: username={}, error={}", request.getUsername(), e.getMessage());
            throw new BusinessException(ResultCode.LOGIN_FAIL);
        }
    }

    /**
     * 手机号 + 短信验证码登录
     * <p>
     * 不经过 AuthenticationManager（没有密码可比对），认证主体通过 UserDetailsService 重新装载，
     * 禁用账号会在那里被拒绝。
     */
    public LoginResponse loginByPhone(PhoneLoginRequest request) {
        String phone = smsCodeService.requireValidPhone(request.getPhone());
        // ponytail: 先验码再查用户——否则"该手机号未注册"就成了不必持有手机即可探测注册状态的信道；
        // 验证码一次性，验过即删，查不到用户时需要重新发送是可接受的代价
        smsCodeService.verify(phone, SmsScene.LOGIN.getCode(), request.getCode());

        SysUser user = findByPhone(phone);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "该手机号尚未注册");
        }
        assertNotInMaintenance(user);

        LoginUser principal = loadPrincipal(user);
        markPhoneVerified(user.getId());
        return issueTokens(user, principal);
    }

    /**
     * 学生 / 家长自助注册，成功后直接签发登录态
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(RegisterRequest request) {
        if (!smsProperties.isRegisterEnabled()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "自助注册已关闭，请联系学校管理员开户");
        }
        // ponytail: 注册出来的只会是学生/家长，维护模式下本就不能登录，提前拦住免得白白消耗验证码
        if (scoreRuleService.maintenanceMode()) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "系统维护中，暂不开放注册");
        }
        UserRole role = resolveSelfRegisterRole(request.getRole());
        String phone = smsCodeService.requireValidPhone(request.getPhone());
        smsCodeService.verify(phone, SmsScene.REGISTER.getCode(), request.getCode());

        School school = requireActiveSchool(request.getSchoolId());
        assertPhoneAvailable(phone);

        SysUser user = new SysUser();
        // ponytail: username 固定取手机号，这样账号密码 Tab 也能用手机号登录，不必再加一条"按 phone 认证"的分支
        user.setUsername(phone);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(phone);
        user.setRealName(request.getRealName().trim());
        user.setUserType(role.getUserType());
        user.setSchoolId(school.getId());
        user.setStatus(CommonConstants.STATUS_ENABLED);
        userMapper.insert(user);
        bindRole(user.getId(), role);

        if (role == UserRole.STUDENT) {
            bindStudentProfile(user, school.getId(), request.getStudentNo());
        }
        log.info("自助注册成功: userId={}, role={}, schoolId={}", user.getId(), role.getCode(), school.getId());

        LoginUser principal = loadPrincipal(user);
        markPhoneVerified(user.getId());
        return issueTokens(user, principal);
    }

    /**
     * 注册页学校下拉：仅启用中的学校，仅 id + 名称
     */
    public List<PublicSchoolResponse> listPublicSchools() {
        return schoolMapper.selectList(new LambdaQueryWrapper<School>()
                        .eq(School::getStatus, CommonConstants.STATUS_ENABLED)
                        .orderByAsc(School::getId))
                .stream()
                .map(school -> PublicSchoolResponse.builder()
                        .id(school.getId())
                        .schoolName(school.getSchoolName())
                        .build())
                .toList();
    }

    /**
     * 维护模式下非管理员一律拒绝
     */
    private void assertNotInMaintenance(SysUser user) {
        if (scoreRuleService.maintenanceMode() && !isAdminAccount(user)) {
            log.warn("维护模式拒绝登录: username={}, userType={}", user.getUsername(), user.getUserType());
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "系统维护中，请稍后再试");
        }
    }

    /**
     * 是否管理员账号（超级管理员 / 校管理员）：维护模式下仍允许登录
     */
    private boolean isAdminAccount(SysUser user) {
        Integer userType = user.getUserType();
        return UserRole.SUPER_ADMIN.getUserType().equals(userType)
                || UserRole.ADMIN.getUserType().equals(userType);
    }

    /**
     * 三条登录入口共用的签发逻辑：更新最后登录时间、生成并登记 access / refresh token、组装响应
     * <p>
     * ponytail: principal 由调用方传入而不是在这里重新 loadUserByUsername——密码登录已经通过
     * AuthenticationManager 拿到了主体，再查一遍角色/教师/学生表是纯浪费
     */
    private LoginResponse issueTokens(SysUser user, LoginUser principal) {
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        String accessToken = jwtTokenUtil.generateToken(user.getId(), user.getUsername());
        String refreshToken = jwtTokenUtil.generateRefreshToken(user.getId(), user.getUsername());

        storeToken(accessToken, user.getId());
        // ponytail: refresh token 同步登记（契约 T），否则登出后它仍能在已放行的 /auth/refresh 换出新会话
        refreshTokenStore.register(user.getId(), refreshToken);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration / 1000)
                .userInfo(buildUserInfo(user, principal))
                .build();
    }

    /**
     * 通过 UserDetailsService 装载认证主体：禁用账号在此抛 USER_DISABLED，同时拿到 studentId / teacherId / headTeacher
     */
    private LoginUser loadPrincipal(SysUser user) {
        UserDetails details = userDetailsService.loadUserByUsername(user.getUsername());
        return asLoginUser(details);
    }

    private SysUser findByPhone(String phone) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone)
                .eq(SysUser::getDeleted, CommonConstants.NOT_DELETED));
    }

    /**
     * 自助注册只开放学生与家长
     */
    private UserRole resolveSelfRegisterRole(String code) {
        UserRole role = UserRole.fromCode(code);
        if (role != UserRole.STUDENT && role != UserRole.PARENT) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅支持学生或家长自助注册");
        }
        return role;
    }

    private School requireActiveSchool(Long schoolId) {
        School school = schoolMapper.selectById(schoolId);
        if (school == null) {
            throw new BusinessException(ResultCode.SCHOOL_NOT_FOUND);
        }
        if (!CommonConstants.STATUS_ENABLED.equals(school.getStatus())) {
            throw new BusinessException(ResultCode.SCHOOL_DISABLED);
        }
        return school;
    }

    /**
     * 手机号既不能已被绑定，也不能与某个既有用户名撞车（注册出来的 username 就是手机号）
     */
    private void assertPhoneAvailable(String phone) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .and(w -> w.eq(SysUser::getPhone, phone).or().eq(SysUser::getUsername, phone)));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.PHONE_EXISTS);
        }
    }

    private void bindRole(Long userId, UserRole role) {
        if (userMapper.insertUserRole(userId, role.getRoleCode()) == 0) {
            throw new BusinessException("角色不存在：" + role.getRoleCode());
        }
    }

    /**
     * 学生注册时按学号自动绑定档案：本校存在该学号、尚未绑定账号、且姓名一致才绑
     * <p>
     * ponytail: 学号是半公开信息（名单、成绩单上都有），只凭学号就能把别人的档案绑到自己账号上进而看到其成绩；
     * 加一道姓名一致性校验。任何不满足都只跳过绑定、不阻断注册，管理员事后仍可手动关联。
     */
    private void bindStudentProfile(SysUser user, Long schoolId, String studentNo) {
        if (!StringUtils.hasText(studentNo)) {
            return;
        }
        Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getSchoolId, schoolId)
                .eq(Student::getStudentNo, studentNo.trim())
                .eq(Student::getDeleted, CommonConstants.NOT_DELETED));
        if (student == null) {
            log.info("注册未绑定学生档案：本校不存在该学号 userId={}, studentNo={}", user.getId(), studentNo);
            return;
        }
        if (student.getUserId() != null) {
            log.warn("注册未绑定学生档案：学号已绑定其他账号 userId={}, studentId={}", user.getId(), student.getId());
            return;
        }
        if (!Objects.equals(normalizeName(student.getName()), normalizeName(user.getRealName()))) {
            log.warn("注册未绑定学生档案：姓名与档案不一致 userId={}, studentId={}", user.getId(), student.getId());
            return;
        }
        // isNull 作为并发双重保险：两个请求同时通过上面的检查时只有一个能写入
        int updated = studentMapper.update(null, new LambdaUpdateWrapper<Student>()
                .eq(Student::getId, student.getId())
                .isNull(Student::getUserId)
                .set(Student::getUserId, user.getId()));
        if (updated == 0) {
            log.warn("注册未绑定学生档案：并发下已被抢先绑定 userId={}, studentId={}", user.getId(), student.getId());
        }
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "");
    }

    /**
     * 短信注册 / 登录成功后标记手机号已验证
     * <p>
     * ponytail: SysUser 实体归 user 模块，本组不往里加字段；phone_verified 是 V22 新列，这里用 setSql 直写
     */
    private void markPhoneVerified(Long userId) {
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .setSql("phone_verified = 1"));
    }

    /**
     * 用户登出：作废 access token 与该用户当前的 refresh token
     */
    public void logout(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        String tokenKey = CommonConstants.TOKEN_KEY + token;
        redisTemplate.delete(tokenKey);
        // ponytail: 只删 access token 时，泄露的 refresh token 仍可在 /auth/refresh 让会话复活；登出必须连带作废
        refreshTokenStore.revokeByUserId(resolveLogoutUserId(token));
    }

    /**
     * 登出时的用户ID：登出接口受保护，正常从 SecurityContext 取；拿不到时退回解析 token 自身
     */
    private Long resolveLogoutUserId(String token) {
        if (SecurityUtils.isAuthenticated()) {
            return SecurityUtils.currentUserId();
        }
        try {
            return jwtTokenUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            log.warn("登出时无法解析 token 对应用户，跳过 refresh token 作废: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 刷新Token
     */
    public LoginResponse refreshToken(String refreshToken) {
        if (!jwtTokenUtil.validateToken(refreshToken)) {
            throw new BusinessException(ResultCode.TOKEN_EXPIRED);
        }
        // ponytail: 必须校验 claims.type=refresh，否则任意未过期 access token 都能在此换出新 token 对
        if (!jwtTokenUtil.isRefreshToken(refreshToken)) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
        // ponytail: 必须仍在 Redis 登记中——登出/改密/禁用/轮换都会删掉登记，否则泄露的 refresh token 可无限重放
        if (!refreshTokenStore.isActive(refreshToken)) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }

        Long userId = jwtTokenUtil.getUserIdFromToken(refreshToken);
        String username = jwtTokenUtil.getUsernameFromToken(refreshToken);

        // 查询用户信息（已删除的账号不能续签）
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        // 重新加载认证主体：禁用账号会在此被拒绝，同时拿到 studentId / teacherId / headTeacher
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        // 生成新的Token
        String newAccessToken = jwtTokenUtil.generateToken(userId, username);
        String newRefreshToken = jwtTokenUtil.generateRefreshToken(userId, username);

        // 将新Token存入Redis
        storeToken(newAccessToken, userId);
        // ponytail: 轮换——旧 refresh token 用过即作废，避免同一个 refresh token 被反复兑换
        refreshTokenStore.rotate(refreshToken, userId, newRefreshToken);

        LoginResponse.UserInfo userInfo = buildUserInfo(user, asLoginUser(userDetails));

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration / 1000)
                .userInfo(userInfo)
                .build();
    }

    /**
     * 将访问令牌写入 Redis，TTL 与 JWT 过期时间同源
     */
    private void storeToken(String accessToken, Long userId) {
        // ponytail: TTL 取 app.jwt.expiration，不再写死 24h；JwtAuthenticationFilter 续期用同一个值
        String tokenKey = CommonConstants.TOKEN_KEY + accessToken;
        redisTemplate.opsForValue().set(tokenKey, userId, jwtTokenUtil.getExpirationMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * 认证主体若为 LoginUser 则返回，否则返回 null（容错，不影响登录）
     */
    private LoginUser asLoginUser(Object principal) {
        return principal instanceof LoginUser loginUser ? loginUser : null;
    }

    /**
     * 构建登录响应中的用户信息
     * <p>
     * 基础字段来自 sys_user；studentId / teacherId / headTeacher 来自 LoginUser，
     * 前端学生自查成绩一律用 studentId，而不是 sys_user.id。
     */
    private LoginResponse.UserInfo buildUserInfo(SysUser user, LoginUser loginUser) {
        UserRole role = UserRole.fromUserType(user.getUserType());
        return LoginResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .avatar(user.getAvatar())
                .userType(user.getUserType())
                .schoolId(user.getSchoolId())
                .studentId(loginUser != null ? loginUser.getStudentId() : null)
                .teacherId(loginUser != null ? loginUser.getTeacherId() : null)
                .headTeacher(loginUser != null && loginUser.isHeadTeacher())
                .role(role != null ? role.getCode() : null)
                // ponytail: 契约 L——前端家长端据此渲染孩子下拉的兜底；实时列表仍以 /parents/me/children 为准
                .childStudentIds(loginUser != null ? loginUser.getChildStudentIds() : List.of())
                .build();
    }

    /**
     * 生成验证码
     */
    public CaptchaResponse generateCaptcha() {
        // 生成验证码
        SpecCaptcha captcha = new SpecCaptcha(130, 48, 4);
        String code = captcha.text().toLowerCase();
        String image = captcha.toBase64();

        // 生成Key
        String key = UUID.randomUUID().toString();

        // 存入Redis
        String captchaKey = CommonConstants.CAPTCHA_CODE_KEY + key;
        redisTemplate.opsForValue().set(captchaKey, code, captchaExpire, TimeUnit.SECONDS);

        return new CaptchaResponse(key, image, captchaExpire);
    }

    /**
     * 验证验证码
     */
    private void validateCaptcha(String key, String code) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(code)) {
            throw new BusinessException(ResultCode.CAPTCHA_ERROR);
        }

        String captchaKey = CommonConstants.CAPTCHA_CODE_KEY + key;
        Object cachedCode = redisTemplate.opsForValue().get(captchaKey);

        if (cachedCode == null) {
            throw new BusinessException(ResultCode.CAPTCHA_EXPIRED);
        }

        if (!code.toLowerCase().equals(cachedCode.toString().toLowerCase())) {
            throw new BusinessException(ResultCode.CAPTCHA_ERROR);
        }

        // 验证成功后删除验证码
        redisTemplate.delete(captchaKey);
    }

}
