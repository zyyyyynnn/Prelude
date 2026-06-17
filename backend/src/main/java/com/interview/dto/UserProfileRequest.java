package com.interview.dto;

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
}
