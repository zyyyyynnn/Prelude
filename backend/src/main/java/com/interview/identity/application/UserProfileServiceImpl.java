package com.interview.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.identity.api.UserProfileRequest;
import com.interview.identity.api.UserProfileResponse;
import com.interview.identity.application.port.AvatarContentProcessor;
import com.interview.identity.application.port.AvatarStoragePort;
import com.interview.identity.application.port.AvatarUpload;
import com.interview.identity.application.port.LegacyAvatarSourcePort;
import com.interview.identity.application.port.ProcessedAvatar;
import com.interview.identity.domain.User;
import com.interview.identity.infrastructure.persistence.UserMapper;
import com.interview.shared.api.BusinessException;
import com.interview.shared.web.UserContext;
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
    private final LegacyAvatarSourcePort legacyAvatarSource;
    private final TransactionTemplate transactionTemplate;

    @Override
    public UserProfileResponse getCurrentUserProfile() {
        return toResponse(requireCurrentUser());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse updateCurrentUserProfile(UserProfileRequest request) {
        User currentUser = requireCurrentUser();
        Long userId = currentUser.getId();
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
        if ((oldPassword == null) != (newPassword == null)) {
            throw BusinessException.badRequest("请同时提供旧密码和新密码");
        }

        boolean changed = false;
        if (username != null && !username.equals(currentUser.getUsername())) {
            long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .ne(User::getId, userId));
            if (count > 0) {
                throw BusinessException.badRequest("用户名已存在");
            }
            userMapper.updateUsername(userId, username);
            changed = true;
        }
        if (email != null && !email.equals(currentUser.getEmail())) {
            userMapper.updateEmail(userId, email);
            changed = true;
        }
        if (themePreference != null && !themePreference.equals(currentUser.getThemePreference())) {
            userMapper.updateThemePreference(userId, themePreference);
            changed = true;
        }
        if (oldPassword != null) {
            if (!passwordEncoder.matches(oldPassword, currentUser.getPassword())) {
                throw BusinessException.badRequest("旧密码错误");
            }
            if (passwordEncoder.matches(newPassword, currentUser.getPassword())) {
                throw BusinessException.badRequest("新密码不能与旧密码相同");
            }
            userMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
            changed = true;
        }

        if (!changed) {
            throw BusinessException.badRequest("未检测到资料变更");
        }
        return toResponse(requireCurrentUser());
    }

    @Override
    public UserProfileResponse updateAvatar(AvatarUpload upload) {
        Long userId = requireCurrentUser().getId();
        AvatarRevisionClaim claim = claimAvatarRevision(userId);
        ProcessedAvatar processed = avatarContentProcessor.process(upload);
        String objectKey = AvatarObjectKeys.forUser(userId, processed.extension());
        AvatarStoragePort.StoredAvatar stored = avatarStoragePort.store(objectKey, processed);

        try {
            AvatarCommitResult commit = transactionTemplate.execute(status -> persistAvatar(
                userId,
                claim,
                stored
            ));
            if (commit == null) {
                throw new IllegalStateException("头像资料更新未返回结果");
            }
            if (commit.stale()) {
                throw BusinessException.conflict("头像上传已被更新的选择取代");
            }
            return commit.response();
        } catch (RuntimeException exception) {
            deleteQuietly(stored.objectKey(), "rollback");
            throw exception;
        }
    }

    private AvatarRevisionClaim claimAvatarRevision(Long userId) {
        AvatarRevisionClaim claim = transactionTemplate.execute(status -> {
            if (userMapper.claimAvatarRevision(userId) != 1) {
                throw BusinessException.unauthorized("请先登录");
            }
            User user = userMapper.selectById(userId);
            if (user == null || user.getAvatarRevision() == null) {
                throw BusinessException.unauthorized("请先登录");
            }
            return new AvatarRevisionClaim(user.getAvatarRevision());
        });
        if (claim == null) {
            throw new IllegalStateException("头像版本抢占未返回结果");
        }
        return claim;
    }

    private AvatarCommitResult persistAvatar(
        Long userId,
        AvatarRevisionClaim claim,
        AvatarStoragePort.StoredAvatar stored
    ) {
        String oldAvatarUrl = userMapper.selectAvatarUrlForUpdate(userId);
        int updated = userMapper.updateAvatarIfRevision(userId, stored.publicUri(), claim.revision());
        if (updated != 1) {
            return AvatarCommitResult.staleCommit();
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("头像资料更新必须运行在事务中");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                AvatarObjectKeys.fromStoredUri(oldAvatarUrl)
                    .filter(oldObjectKey -> !oldObjectKey.equals(stored.objectKey()))
                    .ifPresent(oldObjectKey -> deleteOldAvatarQuietly(
                        oldAvatarUrl,
                        oldObjectKey
                    ));
            }

            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteQuietly(stored.objectKey(), "rollback");
                }
            }
        });
        return AvatarCommitResult.successCommit(toResponse(user));
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
            user.getAvatarUrl(),
            user.getThemePreference() == null ? "system" : user.getThemePreference()
        );
    }

    private void deleteQuietly(String objectKey, String phase) {
        try {
            avatarStoragePort.delete(objectKey);
        } catch (RuntimeException exception) {
            log.warn("avatar file cleanup failed phase={} objectKey={}", phase, objectKey, exception);
        }
    }

    private void deleteOldAvatarQuietly(String avatarUrl, String objectKey) {
        try {
            if (avatarUrl.startsWith("/uploads/avatars/") || !AvatarObjectKeys.isCanonical(objectKey)) {
                legacyAvatarSource.delete(objectKey);
            } else {
                avatarStoragePort.delete(objectKey);
            }
        } catch (RuntimeException exception) {
            log.warn("avatar file cleanup failed objectKey={}", objectKey, exception);
        }
    }

    private record AvatarRevisionClaim(Long revision) {
    }

    private record AvatarCommitResult(boolean stale, UserProfileResponse response) {

        private static AvatarCommitResult staleCommit() {
            return new AvatarCommitResult(true, null);
        }

        private static AvatarCommitResult successCommit(UserProfileResponse response) {
            return new AvatarCommitResult(false, response);
        }
    }
}
