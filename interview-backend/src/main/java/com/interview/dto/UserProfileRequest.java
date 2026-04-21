package com.interview.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UserProfileRequest {

    @Pattern(regexp = "^$|^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "邮箱格式不正确")
    private String email;

    private String oldPassword;

    private String newPassword;
}
