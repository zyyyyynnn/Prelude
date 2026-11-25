package com.prelude.interview.application;

import com.prelude.BusinessException;
import com.prelude.assets.api.AttachmentContextPort;
import com.prelude.context.RetrievalPort;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.llm.api.LlmPort;
import com.prelude.position.api.port.PositionCatalogPort;
import com.prelude.position.api.port.PositionCatalogPort.PositionSnapshot;
import com.prelude.resume.api.port.ResumeContextPort;
import com.prelude.resume.api.port.ResumeProjection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StartInterviewTest {

    @Test
    void passesComposerModelAndReasoningToTheFrozenSnapshot() {
        var resumeContextPort = mock(ResumeContextPort.class);
        var positionCatalogPort = mock(PositionCatalogPort.class);
        var interviewSessionRepository = mock(InterviewSessionRepository.class);
        var llmPort = mock(LlmPort.class);
        var attachmentContextPort = mock(AttachmentContextPort.class);
        var currentAccount = mock(CurrentAccount.class);
        var service = new StartInterview(
            resumeContextPort,
            positionCatalogPort,
            interviewSessionRepository,
            llmPort,
            mock(InterviewStageManager.class),
            mock(InterviewMessageService.class),
            mock(Executor.class),
            mock(RetrievalPort.class),
            attachmentContextPort,
            currentAccount
        );
        var resume = mock(ResumeProjection.class);
        var position = mock(PositionSnapshot.class);
        when(currentAccount.requireId()).thenReturn(7L);
        when(resumeContextPort.requireOwnedProjection(7L, 3L)).thenReturn(resume);
        when(resume.resumeId()).thenReturn(3L);
        when(attachmentContextPort.requireOwned(7L, List.of())).thenReturn(List.of());
        when(positionCatalogPort.findAccessibleById(7L, 5L)).thenReturn(position);
        when(position.id()).thenReturn(5L);
        var failure = BusinessException.badRequest("所选模型不支持该思考深度");
        var freezeCommand = new LlmPort.FreezeSnapshotCommand(
            7L, "HIGH", "deepseek-v4-flash");
        when(llmPort.freezeSnapshot(freezeCommand)).thenThrow(failure);

        assertThatThrownBy(() -> service.execute(new StartInterviewCommand(
            3L, 5L, "jd", "deepseek-v4-flash", "HIGH", List.of())))
            .isSameAs(failure);

        verify(llmPort).freezeSnapshot(freezeCommand);
        verifyNoInteractions(interviewSessionRepository);
    }
}
