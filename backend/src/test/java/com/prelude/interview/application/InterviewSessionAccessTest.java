package com.prelude.interview.application;

import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.test.ExceptionFixtures;
import com.prelude.test.SessionFixtures;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewSessionAccessTest {

    @Test
    void rejectsAResourceOwnedByAnotherAccount() {
        InterviewSessionRepository repository = mock(InterviewSessionRepository.class);
        var session = SessionFixtures.create(51L, 8L, "ongoing");
        when(repository.selectById(51L)).thenReturn(session);

        InterviewSessionAccess access = new InterviewSessionAccess(repository, null);

        ExceptionFixtures.assertBusinessExceptionMessage(
            () -> access.requireOwned(51L, 7L), "面试会话不存在或无权访问");
    }
}
