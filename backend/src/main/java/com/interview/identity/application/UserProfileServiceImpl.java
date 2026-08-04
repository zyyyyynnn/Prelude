package com.interview.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.shared.api.BusinessException;
import com.interview.shared.web.UserContext;
import com.interview.identity.api.UserProfileRequest;
import com.interview.identity.api.UserProfileResponse;
import com.interview.identity.domain.User;
import com.interview.identity.infrastructure.persistence.UserMapper;
import com.interview.identity.application.UserProfileService;
import com.interview.identity.application.port.AvatarContentProcessor;
import com.interview.identity.application.port.AvatarStoragePort;
import com.interview.identity.application.port.AvatarUpload;
import com.interview.identity.application.port.ProcessedAvatar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileServiceImpl implements UserProfileService {

    private static final Set<String> THEME_PREFERENCES = Set.of("light", "dark", "system");
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AvatarContentProcessor avatarContentProcessor;
    private final AvatarStoragePort avatarStoragePort;
    private final TransactionTemplate transactionTemplate;

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
    public UserProfileResponse updateAvatar(AvatarUpload upload) {
        User currentUser = requireCurrentUser();
        ProcessedAvatar processed = avatarContentProcessor.process(upload);
        String objectKey = AvatarObjectKeys.forUser(currentUser.getId(), processed.extension());
        AvatarStoragePort.StoredAvatar stored = avatarStoragePort.store(objectKey, processed);

        try {
            UserProfileResponse response = transactionTemplate.execute(status -> persistAvatar(
                currentUser.getId(),
                stored
            ));
            if (response == null) {
                throw new IllegalStateException("头像资料更新未返回结果");
            }
            return response;
        } catch (RuntimeException exception) {
            deleteQuietly(stored.objectKey(), "rollback");
            throw exception;
        }
    }

    private UserProfileResponse persistAvatar(
        Long userId,
        AvatarStoragePort.StoredAvatar stored
    ) {
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        String oldAvatarUrl = user.getAvatarUrl();
        user.setAvatarUrl(stored.publicUri());
        userMapper.updateById(user);

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("头像资料更新必须运行在事务中");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                AvatarObjectKeys.fromStoredUri(oldAvatarUrl)
                    .filter(oldObjectKey -> !oldObjectKey.equals(stored.objectKey()))
                    .ifPresent(oldObjectKey -> deleteQuietly(oldObjectKey, "afterCommit"));
            }

            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteQuietly(stored.objectKey(), "rollback");
                }
            }
        });
        return toResponse(user);
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

    private UserProfileResponse toResponse(User user) {
        return new UserProfileResponse(
            user.getUsername(),
            user.getEmail(),
            AvatarObjectKeys.toCanonicalUri(user.getAvatarUrl()),
            user.getThemePreference() == null ? "system" : user.getThemePreference()
        );
    }

    private void deleteQuietly(String objectKey, String phase) {
        try {
            avatarStoragePort.delete(objectKey);
        } catch (RuntimeException exception) {
            // Cleanup is compensating work. A committed profile must not be reported as failed.
            log.warn("avatar file cleanup failed phase={} objectKey={}", phase, objectKey, exception);
        }
    }
}
