package com.prelude.interview.application;

import com.prelude.assets.api.AttachmentContextPort;
import com.prelude.assets.api.AttachmentSnapshot;
import com.prelude.template.api.port.PositionCatalogPort;
import com.prelude.template.api.port.PositionCatalogPort.PositionSnapshot;
import com.prelude.BusinessException;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.llm.api.LlmPort;
import com.prelude.llm.api.ModelExecutionSnapshotRef;
import com.prelude.interview.application.port.InterviewSessionRepository;
import com.prelude.context.RetrievalPort;
import com.prelude.resume.api.port.ResumeContextPort;
import com.prelude.resume.api.port.ResumeProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
public class StartInterview {

    private static final String STATUS_ONGOING = "ongoing";
    private static final String ROLE_SYSTEM = "system";

    private final ResumeContextPort resumeContextPort;
    private final PositionCatalogPort positionCatalogPort;
    private final InterviewSessionRepository interviewSessionRepository;
    private final LlmPort llmPort;
    private final InterviewStageManager interviewStageManager;
    private final InterviewMessageService interviewMessageService;
    @Qualifier("sseTaskExecutor")
    private final Executor sseTaskExecutor;
    private final RetrievalPort retrievalPort;
    private final AttachmentContextPort attachmentContextPort;
    private final CurrentAccount currentAccount;

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
        ModelExecutionSnapshotRef snapshotRef = llmPort.freezeSnapshot(
            new LlmPort.FreezeSnapshotCommand(accountId, null, null, null, command.requestedModel()));
        session.setModelExecutionSnapshotId(snapshotRef.snapshotId());
        session.setStatus(STATUS_ONGOING);
        session.setJdText(command.jdText());
        interviewSessionRepository.add(session);
        attachmentContextPort.bind(accountId, command.attachmentIds(), "interview", session.getId());

        List<String> retrievalDocuments = new ArrayList<>();
        addIfPresent(retrievalDocuments, resume.plainText());
        addIfPresent(retrievalDocuments, command.jdText());
        attachments.stream()
            .filter(attachment -> !attachment.image())
            .map(AttachmentSnapshot::text)
            .forEach(text -> addIfPresent(retrievalDocuments, text));
        sseTaskExecutor.execute(() -> retrievalPort.index(
            RetrievalPort.SCOPE_SESSION,
            session.getId(),
            retrievalDocuments
        ));
        interviewMessageService.insertMessage(session.getId(), ROLE_SYSTEM, position.systemPrompt());
        interviewStageManager.ensureInitialStage(session);

        return new StartInterviewResult(session.getId(), position.name(), InterviewStageManager.STAGE_WARMUP);
    }

    private long currentAccountId() {
        return currentAccount.requireId();
    }

    private void addIfPresent(List<String> documents, String value) {
        if (value != null && !value.isBlank()) {
            documents.add(value);
        }
    }
}
