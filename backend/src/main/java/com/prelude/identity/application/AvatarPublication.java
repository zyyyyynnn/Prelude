package com.prelude.identity.application;

import com.prelude.BusinessException;
import com.prelude.identity.AccountMapper;
import com.prelude.identity.api.AvatarStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Transaction-boundary collaborator for avatar publication: the guarded
 * account reference commit and the staged asset's PENDING_UPLOAD → READY
 * transition commit together, after the remote object already exists. Any
 * failure rolls both back and leaves the asset PENDING for the reconciler.
 */
@Component
@RequiredArgsConstructor
public class AvatarPublication {

    private final AccountMapper accountMapper;
    private final AvatarStoragePort avatarStoragePort;

    @Transactional(rollbackFor = Exception.class)
    public void publish(
        String candidateUrl,
        long accountId,
        String username,
        String email,
        String themePreference,
        String passwordHash,
        long expectedRevision
    ) {
        int updated = accountMapper.updateProfileGuarded(
            accountId,
            username,
            email,
            themePreference,
            passwordHash,
            candidateUrl,
            expectedRevision,
            UUID.randomUUID().toString()
        );
        if (updated != 1) {
            throw BusinessException.revisionConflict("资料已被其他操作更新，请刷新后重试");
        }
        avatarStoragePort.confirmReady(candidateUrl);
    }
}
