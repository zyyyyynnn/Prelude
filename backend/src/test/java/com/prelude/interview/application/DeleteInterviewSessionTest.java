package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.assets.api.AttachmentContextPort;
import com.prelude.context.RetrievalPort;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeleteInterviewSessionTest {

    private final InterviewSessionAccess sessionAccess = mock(InterviewSessionAccess.class);
    private final InterviewSessionRepository repository = mock(InterviewSessionRepository.class);
    private final RetrievalPort retrievalPort = mock(RetrievalPort.class);
    private final AttachmentContextPort attachmentContextPort = mock(AttachmentContextPort.class);
    private final DeleteInterviewSession deleteInterviewSession = new DeleteInterviewSession(
        sessionAccess, repository, retrievalPort, attachmentContextPort);

    private EmbeddedDatabase database;
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        transactionManager = new DataSourceTransactionManager(database);
        when(sessionAccess.currentAccountId()).thenReturn(7L);
    }

    @AfterEach
    void shutDownDatabase() {
        database.shutdown();
    }

    @Test
    void sessionDeletionPropagatesTheOwnedScopeInTheSameUseCase() {
        deleteInterviewSession.execute(42L);

        InOrder order = inOrder(sessionAccess, repository, attachmentContextPort, retrievalPort);
        order.verify(sessionAccess).currentAccountId();
        order.verify(sessionAccess).requireOwned(42L, 7L);
        order.verify(repository).deleteOwned(42L, 7L);
        order.verify(attachmentContextPort).deleteScope(7L, "interview", 42L);
        order.verify(retrievalPort).invalidate(RetrievalPort.SCOPE_SESSION, 42L);
    }

    @Test
    void ownershipFailurePreventsSessionAndAttachmentDeletion() {
        doThrow(BusinessException.notFound("会话不存在或不属于当前账号"))
            .when(sessionAccess).requireOwned(42L, 7L);

        assertThatThrownBy(() -> deleteInterviewSession.execute(42L))
            .isInstanceOf(BusinessException.class);

        verifyNoInteractions(repository, retrievalPort, attachmentContextPort);
    }

    @Test
    void invalidatesRetrievalOnlyAfterCommit() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            deleteInterviewSession.execute(42L);

            verify(repository).deleteOwned(42L, 7L);
            verify(attachmentContextPort).deleteScope(7L, "interview", 42L);
            verifyNoInteractions(retrievalPort);
        });

        verify(retrievalPort).invalidate(RetrievalPort.SCOPE_SESSION, 42L);
    }

    @Test
    void rollbackDoesNotInvalidateRetrieval() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            deleteInterviewSession.execute(42L);
            status.setRollbackOnly();
        });

        verifyNoInteractions(retrievalPort);
    }
}
