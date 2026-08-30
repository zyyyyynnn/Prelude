package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.interview.application.port.InterviewSessionRepository;
import com.prelude.interview.domain.InterviewSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewSessionAccessTest {

    @Test
    void rejectsAResourceOwnedByAnotherAccount() {
        InterviewSessionRepository repository = mock(InterviewSessionRepository.class);
        CurrentAccount currentAccount = mock(CurrentAccount.class);
        InterviewSession session = new InterviewSession();
        session.setId(51L);
        session.setAccountId(8L);
        when(repository.selectById(51L)).thenReturn(session);

        InterviewSessionAccess access = new InterviewSessionAccess(repository, currentAccount);

        assertThatThrownBy(() -> access.requireOwned(51L, 7L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("面试会话不存在或无权访问");
    }
}
