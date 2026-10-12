package com.school.module.backup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 恢复确认请求
 * <p>
 * ponytail: 恢复会清空业务表，必须二次确认；用 Bean Validation 把「没填 RESTORE」变成 HTTP 400，
 * 与其他参数错误同一通道，前端拦截器会直接弹出 message。
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupRestoreRequest {

    public static final String CONFIRM_WORD = "RESTORE";

    @NotBlank(message = "必须填写 confirm=RESTORE 以确认恢复")
    @Pattern(regexp = "RESTORE", message = "confirm 必须为 RESTORE 才能执行恢复")
    private String confirm;

}
