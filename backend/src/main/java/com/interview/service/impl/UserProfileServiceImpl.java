package com.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private static final Set<String> THEME_PREFERENCES = Set.of("light", "dark", "system");
    private static final Set<String> AVATAR_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public UserProfileResponse getCurrentUserProfile() {
        User user = requireCurrentUser();
        return toResponse(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse updateCurrentUserProfile(UserProfileRequest request) {
        User user = requireCurrentUser();
        boolean changed = false;

        String username = normalizeNullable(request.getUsername());
        String email = normalizeNullable(request.getEmail());
        String themePreference = normalizeNullable(request.getThemePreference());
        String oldPassword = normalizeNullable(request.getOldPassword());
        String newPassword = normalizeNullable(request.getNewPassword());

        if (request.getUsername() != null && username == null) {
            throw BusinessException.badRequest("用户名不能为空");
        }
        if (request.getEmail() != null && email == null) {
            throw BusinessException.badRequest("邮箱不能为空");
        }
        if (themePreference != null && !THEME_PREFERENCES.contains(themePreference)) {
            throw BusinessException.badRequest("主题设置不正确");
        }

        if (username != null && !username.equals(user.getUsername())) {
            long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .ne(User::getId, user.getId()));
            if (count > 0) {
                throw BusinessException.badRequest("用户名已存在");
            }
            user.setUsername(username);
            changed = true;
        }

        if ((oldPassword == null) != (newPassword == null)) {
            throw BusinessException.badRequest("请同时提供旧密码和新密码");
        }

        if (email != null && !email.equals(user.getEmail())) {
            user.setEmail(email);
            changed = true;
        }

        if (themePreference != null && !themePreference.equals(user.getThemePreference())) {
            user.setThemePreference(themePreference);
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
        return toResponse(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse updateAvatar(MultipartFile file) {
        User user = requireCurrentUser();
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请选择头像文件");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!AVATAR_EXTENSIONS.contains(extension)) {
            throw BusinessException.badRequest("头像仅支持 JPG、PNG、WebP 或 GIF");
        }

        try {
            Path directory = Path.of("uploads", "avatars").toAbsolutePath().normalize();
            Files.createDirectories(directory);
            String fileName = user.getId() + "-" + UUID.randomUUID() + "." + extension;
            Path target = directory.resolve(fileName).normalize();
            if (!target.startsWith(directory)) {
                throw BusinessException.badRequest("头像文件名不正确");
            }
            file.transferTo(target);
            user.setAvatarUrl("/uploads/avatars/" + fileName);
            userMapper.updateById(user);
            return toResponse(user);
        } catch (IOException ex) {
            throw BusinessException.badRequest("头像上传失败");
        }
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

    private String extensionOf(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private UserProfileResponse toResponse(User user) {
        return new UserProfileResponse(
            user.getUsername(),
            user.getEmail(),
            user.getAvatarUrl(),
            user.getThemePreference() == null ? "system" : user.getThemePreference()
        );
    }
}
