package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.assets.api.AssetRef;
import com.prelude.assets.api.AttachmentContextPort;
import com.prelude.assets.api.AttachmentSnapshot;
import com.prelude.context.RetrievalPort;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.llm.api.ModelExecutionPort;
import com.prelude.llm.api.ModelExecutionSnapshotRef;
import com.prelude.position.api.port.PositionCatalogPort;
import com.prelude.position.api.port.PositionCatalogPort.PositionSnapshot;
import com.prelude.resume.api.port.ResumeContextPort;
import com.prelude.resume.api.port.ResumeProjection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StartInterviewTest {

    private final ResumeContextPort resumeContextPort = mock(ResumeContextPort.class);
    private final PositionCatalogPort positionCatalogPort = mock(PositionCatalogPort.class);
    private final InterviewSessionRepository interviewSessionRepository = mock(InterviewSessionRepository.class);
    private final ModelExecutionPort llmPort = mock(ModelExecutionPort.class);
    private final AttachmentContextPort attachmentContextPort = mock(AttachmentContextPort.class);
    private final CurrentAccount currentAccount = mock(CurrentAccount.class);
    private final RetrievalPort retrievalPort = mock(RetrievalPort.class);
    private final Executor executor = Runnable::run;
    private final StartInterview service = serviceWith(executor);

    private StartInterview serviceWith(Executor taskExecutor) {
        return new StartInterview(
            resumeContextPort,
            positionCatalogPort,
            interviewSessionRepository,
            llmPort,
            mock(InterviewStageManager.class),
            mock(InterviewMessageService.class),
            retrievalPort,
            attachmentContextPort,
            currentAccount,
            taskExecutor
        );
    }
    private final StartInterviewCommand command = new StartInterviewCommand(
        3L, 5L, "jd text", "deepseek-v4-flash", "HIGH", List.of(8L, 9L));
    private final ResumeProjection resume = new ResumeProjection(
        3L, 7L, "resume.txt", "resume text", List.of(), List.of());
    private final List<AttachmentSnapshot> attachments = List.of(
        new AttachmentSnapshot(8L, "notes.txt", "text/plain", 5L, false, "attachment text", new AssetRef(100L)),
        new AttachmentSnapshot(9L, "diagram.png", "image/png", 5L, true, "image text", new AssetRef(101L))
    );

    private EmbeddedDatabase database;
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        transactionManager = new DataSourceTransactionManager(database);
        when(currentAccount.requireId()).thenReturn(7L);
        when(resumeContextPort.requireOwnedProjection(7L, 3L)).thenReturn(resume);
        when(attachmentContextPort.requireOwned(7L, List.of(8L, 9L))).thenReturn(attachments);
        when(positionCatalogPort.findAccessibleById(7L, 5L))
            .thenReturn(new PositionSnapshot(5L, "Engineer", "system prompt"));
        when(llmPort.freezeSnapshot(any(ModelExecutionPort.FreezeSnapshotCommand.class)))
            .thenReturn(new ModelExecutionSnapshotRef(99L));
        doAnswer(invocation -> {
            InterviewSession session = invocation.getArgument(0);
            session.setId(42L);
            return 1;
        }).when(interviewSessionRepository).add(any(InterviewSession.class));
    }

    @AfterEach
    void shutDownDatabase() {
        database.shutdown();
    }

    @Test
    void passesComposerModelAndReasoningToTheFrozenSnapshot() {
        var freezeCommand = new ModelExecutionPort.FreezeSnapshotCommand(
            7L, "HIGH", "deepseek-v4-flash");
        var failure = BusinessException.badRequest("所选模型不支持该思考深度");
        when(llmPort.freezeSnapshot(freezeCommand)).thenThrow(failure);

        assertThatThrownBy(() -> service.execute(command)).isSameAs(failure);

        verify(llmPort).freezeSnapshot(freezeCommand);
        verifyNoInteractions(interviewSessionRepository, retrievalPort);
    }

    @Test
    void indexesUnifiedDocumentsOnlyAfterCommit() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.execute(command);

            verifyNoInteractions(retrievalPort);
        });

        verify(retrievalPort).index(
            RetrievalPort.SCOPE_SESSION,
            42L,
            List.of("resume text", "jd text", "attachment text", "image text")
        );
    }

    @Test
    void rejectedRetrievalIndexingDoesNotFailACommittedInterview() {
        StartInterview rejecting = serviceWith(task -> {
            throw new RejectedExecutionException("retrieval executor unavailable");
        });

        assertThatCode(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            rejecting.execute(command))).doesNotThrowAnyException();

        verify(interviewSessionRepository).add(any(InterviewSession.class));
        verifyNoInteractions(retrievalPort);
    }

    @Test
    void rollbackDoesNotLeaveAnOrphanIndex() {
        BusinessException failure = BusinessException.badRequest("session write failed");
        doThrow(failure).when(interviewSessionRepository).add(any(InterviewSession.class));

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
            .executeWithoutResult(status -> service.execute(command)))
            .isSameAs(failure);

        verifyNoInteractions(retrievalPort);
    }

    @Test
    void nonTransactionalExecutionIndexesImmediately() {
        service.execute(command);

        verify(retrievalPort).index(
            RetrievalPort.SCOPE_SESSION,
            42L,
            List.of("resume text", "jd text", "attachment text", "image text")
        );
    }
}
