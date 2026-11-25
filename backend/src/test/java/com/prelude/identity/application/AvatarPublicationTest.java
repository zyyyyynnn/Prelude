package com.prelude.identity.application;

import com.prelude.BusinessException;
import com.prelude.identity.api.AvatarStoragePort;
import com.prelude.identity.application.port.AccountRepository;
import com.prelude.identity.domain.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AvatarPublicationTest {

    private static final String CANDIDATE = "/api/assets/9/content";
    private static final String PREVIOUS = "/api/assets/5/content";

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AvatarStoragePort storage = mock(AvatarStoragePort.class);
    private final AvatarPublication publication = new AvatarPublication(accounts, storage);
    private final Account account = new Account();

    @BeforeEach
    void prepareAccount() {
        account.setId(7L);
        account.setAvatarUrl(PREVIOUS);
    }

    @Test
    void publishesTheReferenceAndDeletionIntentInOneOrderedTransaction() {
        when(accounts.replaceAvatar(eq(CANDIDATE), eq(account), any())).thenReturn(1);

        publication.publish(CANDIDATE, PREVIOUS, account);

        InOrder order = inOrder(accounts, storage);
        order.verify(accounts).replaceAvatar(eq(CANDIDATE), eq(account), any());
        order.verify(storage).confirmReady(CANDIDATE);
        order.verify(storage).discard(7L, PREVIOUS);
    }

    @Test
    void revisionConflictDoesNotConfirmOrDiscardAssets() {
        when(accounts.replaceAvatar(eq(CANDIDATE), eq(account), any())).thenReturn(0);

        assertThatThrownBy(() -> publication.publish(CANDIDATE, PREVIOUS, account))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getCode()).isEqualTo("revision_conflict"));

        verifyNoInteractions(storage);
    }

    @Test
    void previousAvatarDeletionFailurePropagatesToTheCaller() {
        when(accounts.replaceAvatar(eq(CANDIDATE), eq(account), any())).thenReturn(1);
        IllegalStateException failure = new IllegalStateException("registration unavailable");
        doThrow(failure).when(storage).discard(7L, PREVIOUS);

        assertThatThrownBy(() -> publication.publish(CANDIDATE, PREVIOUS, account)).isSameAs(failure);

        verify(storage).confirmReady(CANDIDATE);
        verify(storage, never()).discard(7L, CANDIDATE);
    }
}
