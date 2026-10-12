package com.school.module.log.aspect;

import com.school.common.exception.BusinessException;
import com.school.module.auth.controller.AuthController;
import com.school.module.auth.dto.LoginRequest;
import com.school.module.log.entity.OperationLog;
import com.school.module.log.service.OperationLogService;
import com.school.module.score.controller.ScoreController;
import com.school.module.score.dto.ScoreReviewRequest;
import com.school.support.TestAuth;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 操作日志切面单元测试：不起容器，手工构造 ProceedingJoinPoint
 *
 * @author School System
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("操作日志切面测试")
class OperationLogAspectTest {

    @Mock
    private OperationLogService operationLogService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    private OperationLogAspect aspect;

    @BeforeEach
    void setUp() throws Exception {
        aspect = new OperationLogAspect(operationLogService);
        TestAuth.asSchoolAdmin(1L);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/scores/review");
        request.addHeader("X-Forwarded-For", "10.0.0.8, 192.168.1.1");
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Method reviewMethod = ScoreController.class.getMethod("reviewScores", ScoreReviewRequest.class);
        ScoreReviewRequest reviewRequest = new ScoreReviewRequest();
        reviewRequest.setScoreIds(List.of(1L));
        reviewRequest.setResult(2);
        reviewRequest.setRejectReason("分数异常");

        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        lenient().when(signature.getMethod()).thenReturn(reviewMethod);
        lenient().when(signature.getParameterNames()).thenReturn(new String[]{"request"});
        lenient().when(joinPoint.getArgs()).thenReturn(new Object[]{reviewRequest});
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    private OperationLog recorded() {
        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).record(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("应该记录成功日志_当目标方法正常返回")
    void shouldRecordSuccess_WhenProceedReturns_ThenStatusOne() throws Throwable {
        // Given
        when(joinPoint.proceed()).thenReturn("ok");

        // When
        Object result = aspect.around(joinPoint);

        // Then
        assertThat(result).isEqualTo("ok");
        OperationLog log = recorded();
        assertThat(log.getStatus()).isEqualTo(OperationLog.STATUS_SUCCESS);
        assertThat(log.getErrorMsg()).isNull();
        assertThat(log.getUserId()).isEqualTo(100L);
        assertThat(log.getUsername()).isEqualTo("admin_school1");
        assertThat(log.getOperation()).isEqualTo("[score] 审核成绩");
        assertThat(log.getMethod()).isEqualTo("POST /api/v1/scores/review");
        assertThat(log.getIp()).isEqualTo("10.0.0.8");
        assertThat(log.getParams()).contains("\"request\"").contains("分数异常").contains("\"scoreIds\":[1]");
        assertThat(log.getExecuteTime()).isNotNull().isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("应该记录失败日志并原样抛出_当目标方法抛业务异常")
    void shouldRecordFailure_WhenProceedThrows_ThenStatusZeroAndRethrow() throws Throwable {
        // Given
        when(joinPoint.proceed()).thenThrow(new BusinessException("驳回时必须填写原因"));

        // When & Then
        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isInstanceOf(BusinessException.class)
                .hasMessage("驳回时必须填写原因");

        OperationLog log = recorded();
        assertThat(log.getStatus()).isEqualTo(OperationLog.STATUS_FAIL);
        assertThat(log.getErrorMsg()).isEqualTo("驳回时必须填写原因");
        assertThat(log.getUserId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("应该记录越权尝试_当PreAuthorize拒绝")
    void shouldRecordDenied_WhenAccessDenied_ThenChineseMessage() throws Throwable {
        // Given
        when(joinPoint.proceed()).thenThrow(new AccessDeniedException("Access Denied"));

        // When & Then
        assertThatThrownBy(() -> aspect.around(joinPoint)).isInstanceOf(AccessDeniedException.class);

        OperationLog log = recorded();
        assertThat(log.getStatus()).isEqualTo(OperationLog.STATUS_FAIL);
        assertThat(log.getErrorMsg()).startsWith("无权限访问");
    }

    @Test
    @DisplayName("应该打码密码并从参数取用户名_当未登录调用登录接口")
    void shouldMaskPasswordAndCaptureUsername_WhenAnonymousLogin() throws Throwable {
        // Given
        TestAuth.clear();
        Method loginMethod = AuthController.class.getMethod("login", LoginRequest.class);
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("admin");
        loginRequest.setPassword("admin123");
        when(signature.getMethod()).thenReturn(loginMethod);
        when(joinPoint.getArgs()).thenReturn(new Object[]{loginRequest});
        when(joinPoint.proceed()).thenReturn("ok");

        // When
        aspect.around(joinPoint);

        // Then
        OperationLog log = recorded();
        assertThat(log.getUserId()).isNull();
        assertThat(log.getUsername()).isEqualTo("admin");
        assertThat(log.getOperation()).isEqualTo("AuthController#login");
        assertThat(log.getParams()).contains("\"password\":\"***\"").doesNotContain("admin123");
    }

    @Test
    @DisplayName("应该跳过Servlet参数并截断超长参数_当参数含请求对象与长文本")
    void shouldSkipServletArgsAndTruncate_WhenArgsContainRequestAndLongText() throws Throwable {
        // Given
        String longText = "x".repeat(OperationLogAspect.PARAMS_MAX_LENGTH + 500);
        when(signature.getParameterNames()).thenReturn(new String[]{"request", "httpRequest", "token"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{longText, new MockHttpServletRequest(), "jwt-secret"});
        when(joinPoint.proceed()).thenReturn("ok");

        // When
        aspect.around(joinPoint);

        // Then
        OperationLog log = recorded();
        assertThat(log.getParams()).hasSize(OperationLogAspect.PARAMS_MAX_LENGTH);
        assertThat(log.getParams()).doesNotContain("httpRequest").doesNotContain("MockHttpServletRequest");
    }

    @Test
    @DisplayName("应该打码token字段_当参数名含token")
    void shouldMaskToken_WhenParameterNameContainsToken() throws Throwable {
        // Given
        when(signature.getParameterNames()).thenReturn(new String[]{"refreshToken"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"eyJhbGciOi"});
        when(joinPoint.proceed()).thenReturn("ok");

        // When
        aspect.around(joinPoint);

        // Then
        assertThat(recorded().getParams()).isEqualTo("{\"refreshToken\":\"***\"}");
    }

    @Test
    @DisplayName("应该不影响业务返回_当日志写入失败")
    void shouldNotBreakBusiness_WhenRecordThrows_ThenStillReturn() throws Throwable {
        // Given
        doThrow(new RuntimeException("db down")).when(operationLogService).record(any(OperationLog.class));
        when(joinPoint.proceed()).thenReturn("ok");

        // When
        Object result = aspect.around(joinPoint);

        // Then
        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("应该退回Controller#method_当没有HTTP请求上下文")
    void shouldFallbackMethodName_WhenNoRequestContext() throws Throwable {
        // Given
        RequestContextHolder.resetRequestAttributes();
        when(joinPoint.proceed()).thenReturn("ok");

        // When
        aspect.around(joinPoint);

        // Then
        OperationLog log = recorded();
        assertThat(log.getMethod()).isEqualTo("ScoreController#reviewScores");
        assertThat(log.getIp()).isNull();
    }

}
