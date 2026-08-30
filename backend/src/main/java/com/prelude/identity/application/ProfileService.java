package com.prelude.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prelude.BusinessException;
import com.prelude.identity.Account;
import com.prelude.identity.AccountMapper;
import com.prelude.identity.AccountPrincipal;
import com.prelude.identity.api.AvatarStoragePort;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.identity.api.UserProfileRequest;
import com.prelude.identity.api.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final Set<String> THEME_PREFERENCES = Set.of("light", "dark", "system");
    private static final Set<String> AVATAR_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentAccount currentAccount;
    private final AvatarStoragePort avatarStoragePort;

    public UserProfileResponse getCurrentUserProfile() {
        return toResponse(requireAccount(currentAccount.requireId()));
    }

    public UserProfileResponse updateCurrentUserProfile(UserProfileRequest request) {
        long accountId = currentAccount.requireId();
        Account account = requireAccount(accountId);

        if (request.getOperationId().equals(account.getLastOperationId())) {
            return toResponse(account);
        }

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
        String newUsername = account.getUsername();
        String newEmail = account.getEmail();
        String newThemePreference = account.getThemePreference();
        String newPasswordHash = account.getPasswordHash();

        if (username != null && !username.equals(account.getUsername())) {
            long count = accountMapper.selectCount(new LambdaQueryWrapper<Account>()
                .eq(Account::getUsername, username)
                .ne(Account::getId, account.getId()));
            if (count > 0) {
                throw BusinessException.badRequest("用户名已存在");
            }
            newUsername = username;
            changed = true;
        }

        if (email != null && !email.equals(account.getEmail())) {
            newEmail = email;
            changed = true;
        }

        if (themePreference != null && !themePreference.equals(account.getThemePreference())) {
            newThemePreference = themePreference;
            changed = true;
        }

        if (oldPassword != null) {
            if (account.getPasswordHash() == null
                || !passwordEncoder.matches(oldPassword, account.getPasswordHash())) {
                throw BusinessException.badRequest("旧密码错误");
            }
            if (passwordEncoder.matches(newPassword, account.getPasswordHash())) {
                throw BusinessException.badRequest("新密码不能与旧密码相同");
            }
            newPasswordHash = passwordEncoder.encode(newPassword);
            changed = true;
        }

        if (!changed) {
            throw BusinessException.badRequest("未检测到资料变更");
        }

        int updated = accountMapper.updateProfileGuarded(
            accountId,
            newUsername,
            newEmail,
            newThemePreference,
            newPasswordHash,
            account.getAvatarUrl(),
            request.getExpectedRevision(),
            request.getOperationId()
        );
        if (updated != 1) {
            throw BusinessException.revisionConflict("资料已被其他操作更新，请刷新后重试");
        }
        return toResponse(accountMapper.selectById(accountId));
    }

    public UserProfileResponse updateAvatar(MultipartFile file) {
        long accountId = currentAccount.requireId();
        Account account = requireAccount(accountId);
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请选择头像文件");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!AVATAR_EXTENSIONS.contains(extension)) {
            throw BusinessException.badRequest("头像仅支持 JPG、PNG、WebP 或 GIF");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw BusinessException.badRequest("头像上传失败");
        }
        String mediaType = file.getContentType() == null || file.getContentType().isBlank()
            ? "application/octet-stream"
            : file.getContentType();

        String previousAvatarUrl = account.getAvatarUrl();
        String newAvatarUrl = avatarStoragePort.store(accountId, mediaType, bytes);
        int updated = accountMapper.updateProfileGuarded(
            accountId,
            account.getUsername(),
            account.getEmail(),
            account.getThemePreference(),
            account.getPasswordHash(),
            newAvatarUrl,
            account.getRevision(),
            UUID.randomUUID().toString()
        );
        if (updated != 1) {
            discardQuietly(accountId, newAvatarUrl);
            throw BusinessException.revisionConflict("资料已被其他操作更新，请刷新后重试");
        }
        // The committed reference is authoritative; obsolete-avatar cleanup is non-fatal.
        discardQuietly(accountId, previousAvatarUrl);
        return toResponse(accountMapper.selectById(accountId));
    }

    private void discardQuietly(long accountId, String avatarUrl) {
        try {
            avatarStoragePort.discard(accountId, avatarUrl);
        } catch (RuntimeException exception) {
            log.warn("Avatar cleanup failed for account {} at {}; the asset row remains as recovery anchor",
                accountId, avatarUrl, exception);
        }
    }

    private Account requireAccount(long accountId) {
        Account account = accountMapper.selectById(accountId);
        if (account == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return account;
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

    private UserProfileResponse toResponse(Account account) {
        return new UserProfileResponse(
            account.getId(),
            account.getUsername(),
            account.getEmail(),
            account.getAvatarUrl(),
            account.getThemePreference() == null ? "system" : account.getThemePreference(),
            account.getRevision() == null ? 0L : account.getRevision()
        );
    }
}
