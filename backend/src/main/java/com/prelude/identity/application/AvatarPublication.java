package com.prelude.identity.application;

import com.prelude.BusinessException;
import com.prelude.identity.api.AvatarStoragePort;
import com.prelude.identity.application.port.AccountRepository;
import com.prelude.identity.domain.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AvatarPublication {

    private final AccountRepository accounts;
    private final AvatarStoragePort avatarStoragePort;

    @Transactional(rollbackFor = Exception.class)
    public void publish(String candidateUrl, String previousAvatarUrl, Account account) {
        int updated = accounts.replaceAvatar(candidateUrl, account, UUID.randomUUID().toString());
        if (updated != 1) {
            throw BusinessException.revisionConflict("资料已被其他操作更新，请刷新后重试");
        }
        avatarStoragePort.confirmReady(candidateUrl);
        avatarStoragePort.discard(account.getId(), previousAvatarUrl);
    }
}
