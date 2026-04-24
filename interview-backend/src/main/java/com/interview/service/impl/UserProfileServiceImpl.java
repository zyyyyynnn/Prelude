package com.interview.service.impl;

import com.interview.common.BusinessException;
import com.interview.common.UserContext;
import com.interview.dto.UserProfileRequest;
import com.interview.dto.UserProfileResponse;
import com.interview.entity.User;
import com.interview.mapper.UserMapper;
import com.interview.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public UserProfileResponse getCurrentUserProfile() {
        User user = requireCurrentUser();
        return new UserProfileResponse(user.getUsername(), user.getEmail());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse updateCurrentUserProfile(UserProfileRequest request) {
        User user = requireCurrentUser();
        boolean changed = false;

        String email = normalizeNullable(request.getEmail());
        String oldPassword = normalizeNullable(request.getOldPassword());
        String newPassword = normalizeNullable(request.getNewPassword());

        if (request.getEmail() != null && email == null) {
            throw BusinessException.badRequest("邮箱不能为空");
        }

        if ((oldPassword == null) != (newPassword == null)) {
            throw BusinessException.badRequest("请同时提供旧密码和新密码");
        }

        if (email != null && !email.equals(user.getEmail())) {
            user.setEmail(email);
            changed = true;
        }

        if (oldPassword != null) {
            if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                throw BusinessException.badRequest("旧密码错误");
            }
            if (passwordEncoder.matches(newPassword, user.getPassword())) {
                throw BusinessException.badRequest("新密码不能与旧密码相同");
            }
            user.setPassword(passwordEncoder.encode(newPassword));
            changed = true;
        }

        if (!changed) {
            throw BusinessException.badRequest("未检测到资料变更");
        }

        userMapper.updateById(user);
        return getCurrentUserProfile();
    }

    private User requireCurrentUser() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return user;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
