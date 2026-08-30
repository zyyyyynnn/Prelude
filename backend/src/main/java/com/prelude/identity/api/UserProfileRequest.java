package com.prelude.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserProfileRequest {

    @Size(max = 64, message = "用户名长度不能超过64个字符")
    private String username;

    @Pattern(regexp = "^$|^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "邮箱格式不正确")
    private String email;

    @Pattern(regexp = "^$|light|dark|system", message = "主题设置不正确")
    private String themePreference;

    private String oldPassword;

    private String newPassword;

    @NotNull(message = "缺少资料版本号")
    private Long expectedRevision;

    @NotBlank(message = "缺少操作标识")
    private String operationId;
}
