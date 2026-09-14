package com.school.common.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 统一响应结果测试
 *
 * @author School System
 */
@DisplayName("统一响应结果测试")
class ResultTest {

    @Test
    @DisplayName("应该创建成功响应")
    void shouldCreateSuccessResult() {
        // When
        Result<Void> result = Result.success();

        // Then
        assertNotNull(result);
        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode());
        assertEquals(ResultCode.SUCCESS.getMessage(), result.getMessage());
        assertNull(result.getData());
        assertNotNull(result.getTimestamp());
    }

    @Test
    @DisplayName("应该创建带数据的成功响应")
    void shouldCreateSuccessResultWithData() {
        // Given
        String data = "test data";

        // When
        Result<String> result = Result.success(data);

        // Then
        assertNotNull(result);
        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode());
        assertEquals(data, result.getData());
    }

    @Test
    @DisplayName("应该创建失败响应")
    void shouldCreateFailResult() {
        // When
        Result<Void> result = Result.fail();

        // Then
        assertNotNull(result);
        assertEquals(ResultCode.FAIL.getCode(), result.getCode());
        assertEquals(ResultCode.FAIL.getMessage(), result.getMessage());
    }

    @Test
    @DisplayName("应该创建自定义消息的失败响应")
    void shouldCreateFailResultWithCustomMessage() {
        // Given
        String customMessage = "Custom error message";

        // When
        Result<Void> result = Result.fail(customMessage);

        // Then
        assertNotNull(result);
        assertEquals(ResultCode.FAIL.getCode(), result.getCode());
        assertEquals(customMessage, result.getMessage());
    }

    @Test
    @DisplayName("应该根据布尔值返回正确的结果")
    void shouldReturnCorrectResultBasedOnBoolean() {
        // When
        Result<Void> successResult = Result.result(true);
        Result<Void> failResult = Result.result(false);

        // Then
        assertEquals(ResultCode.SUCCESS.getCode(), successResult.getCode());
        assertEquals(ResultCode.FAIL.getCode(), failResult.getCode());
    }

}
