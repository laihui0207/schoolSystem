package com.school.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatRequest {

    @NotBlank(message = "消息不能为空")
    @Size(max = 2000, message = "消息长度不能超过2000字符")
    private String message;

    @NotBlank(message = "会话ID不能为空")
    private String sessionId;

}
