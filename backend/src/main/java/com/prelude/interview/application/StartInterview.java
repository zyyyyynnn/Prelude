package com.prelude.interview.application;

import com.prelude.assets.api.AttachmentContextPort;
import com.prelude.assets.api.AttachmentSnapshot;
import com.prelude.position.api.port.PositionCatalogPort;
import com.prelude.position.api.port.PositionCatalogPort.PositionSnapshot;
import com.prelude.BusinessException;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.interview.integration.InterviewSessionStatus;
import com.prelude.interview.integration.InterviewStageCatalog;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.llm.api.ModelExecutionPort;
import com.prelude.llm.api.ModelExecutionSnapshotRef;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.context.RetrievalPort;
import com.prelude.context.RetrievalTransactions;
import com.prelude.resume.api.port.ResumeContextPort;
import com.prelude.resume.api.port.ResumeProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class StartInterview {

    private final ResumeContextPort resumeContextPort;
    private final PositionCatalogPort positionCatalogPort;
    private final InterviewSessionRepository interviewSessionRepository;
    private final ModelExecutionPort modelExecutionPort;
    private final InterviewStageManager interviewStageManager;
    private final InterviewMessageService interviewMessageService;
    private final RetrievalPort retrievalPort;
    private final AttachmentContextPort attachmentContextPort;
    private final CurrentAccount currentAccount;
    @Qualifier("sseTaskExecutor")
    private final Executor sseTaskExecutor;

    @Transactional(rollbackFor = Exception.class)
    public StartInterviewResult execute(StartInterviewCommand command) {
        long accountId = currentAccountId();
        ResumeProjection resume = resumeContextPort.requireOwnedProjection(accountId, command.resumeId());
        List<AttachmentSnapshot> attachments = attachmentContextPort.requireOwned(
            accountId, command.attachmentIds());

        PositionSnapshot position = positionCatalogPort.findAccessibleById(accountId, command.positionId());
        if (position == null) {
            throw BusinessException.badRequest("岗位模板不存在");
        }

        InterviewSession session = new InterviewSession();
        session.setAccountId(accountId);
        session.setResumeId(resume.resumeId());
        session.setPositionId(position.id());
        session.setTargetPosition(position.name());
        ModelExecutionSnapshotRef snapshotRef = modelExecutionPort.freezeSnapshot(
            new ModelExecutionPort.FreezeSnapshotCommand(accountId, command.reasoningLevel(), command.model()));
        session.setModelExecutionSnapshotId(snapshotRef.snapshotId());
        session.setStatus(InterviewSessionStatus.ONGOING);
        session.setJdText(command.jdText());
        interviewSessionRepository.add(session);
        attachmentContextPort.bind(accountId, command.attachmentIds(), "interview", session.getId());

        List<String> retrievalDocuments = InterviewRetrievalDocuments.from(
            resume, command.jdText(), attachments);
        Long sessionId = session.getId();
        RetrievalTransactions.afterCommit(() -> {
            try {
                sseTaskExecutor.execute(() -> retrievalPort.index(
                    RetrievalPort.SCOPE_SESSION,
                    sessionId,
                    retrievalDocuments
                ));
            } catch (RuntimeException exception) {
                log.warn("Retrieval indexing was rejected for committed session {}; source rebuild remains available",
                    sessionId, exception);
            }
        });
        interviewMessageService.insertSystemMessage(session.getId(), position.systemPrompt());
        interviewStageManager.ensureInitialStage(session);

        return new StartInterviewResult(
            session.getId(), position.name(), InterviewStageCatalog.STAGES.getFirst().wireName());
    }

    private long currentAccountId() {
        return currentAccount.requireId();
    }

}
