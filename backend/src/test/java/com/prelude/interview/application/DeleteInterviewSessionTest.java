package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.assets.api.AttachmentContextPort;
import com.prelude.context.RetrievalPort;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeleteInterviewSessionTest {

    private final InterviewSessionAccess sessionAccess = mock(InterviewSessionAccess.class);
    private final InterviewSessionRepository repository = mock(InterviewSessionRepository.class);
    private final RetrievalPort retrievalPort = mock(RetrievalPort.class);
    private final AttachmentContextPort attachmentContextPort = mock(AttachmentContextPort.class);
    private final DeleteInterviewSession deleteInterviewSession = new DeleteInterviewSession(
        sessionAccess, repository, retrievalPort, attachmentContextPort);

    @BeforeEach
    void setUp() {
        when(sessionAccess.currentAccountId()).thenReturn(7L);
    }

    @Test
    void sessionDeletionPropagatesTheOwnedScopeInTheSameUseCase() {
        deleteInterviewSession.execute(42L);

        InOrder order = inOrder(sessionAccess, repository, retrievalPort, attachmentContextPort);
        order.verify(sessionAccess).currentAccountId();
        order.verify(sessionAccess).requireOwned(42L, 7L);
        order.verify(repository).deleteOwned(42L, 7L);
        order.verify(retrievalPort).invalidate(RetrievalPort.SCOPE_SESSION, 42L);
        order.verify(attachmentContextPort).deleteScope(7L, "interview", 42L);
    }

    @Test
    void ownershipFailurePreventsSessionAndAttachmentDeletion() {
        doThrow(BusinessException.notFound("会话不存在或不属于当前账号"))
            .when(sessionAccess).requireOwned(42L, 7L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> deleteInterviewSession.execute(42L))
            .isInstanceOf(BusinessException.class);

        verifyNoInteractions(repository, retrievalPort, attachmentContextPort);
    }
}
