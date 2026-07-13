package com.interview.interview.application;

import com.interview.catalog.api.port.PositionCatalogPort;
import com.interview.catalog.api.port.PositionCatalogPort.PositionSnapshot;
import com.interview.shared.api.BusinessException;
import com.interview.shared.web.UserContext;
import com.interview.interview.api.InterviewStartRequest;
import com.interview.interview.api.InterviewStartResponse;
import com.interview.interview.domain.InterviewSession;
import com.interview.platform.llm.LlmSelection;
import com.interview.interview.application.port.InterviewSessionRepository;
import com.interview.platform.retrieval.RetrievalPort;
import com.interview.platform.llm.LlmConfigPort;
import com.interview.platform.llm.PromptVersions;
import com.interview.resume.api.port.ResumeContextPort;
import com.interview.resume.api.port.ResumeProjection;
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
    private final LlmConfigPort llmConfigPort;
    private final InterviewStageManager interviewStageManager;
    private final InterviewMessageService interviewMessageService;
    @Qualifier("sseTaskExecutor")
    private final Executor sseTaskExecutor;
    private final RetrievalPort retrievalPort;

    @Transactional(rollbackFor = Exception.class)
    public InterviewStartResponse execute(InterviewStartRequest request) {
        Long userId = currentUserId();
        ResumeProjection resume = resumeContextPort.requireOwnedProjection(userId, request.getResumeId());

        PositionSnapshot position = positionCatalogPort.findById(request.getPositionId());
        if (position == null) {
            throw BusinessException.badRequest("岗位模板不存在");
        }

        InterviewSession existingSession = interviewSessionRepository.findOngoing(
            userId, resume.resumeId(), position.id()
        );
        if (existingSession != null) {
            interviewStageManager.ensureInitialStage(existingSession);
            String currentStage = interviewStageManager.currentStageName(existingSession.getId());
            return new InterviewStartResponse(
                existingSession.getId(),
                existingSession.getTargetPosition(),
                currentStage == null ? InterviewStageManager.STAGE_WARMUP : currentStage
            );
        }

        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setResumeId(resume.resumeId());
        session.setPositionId(position.id());
        session.setTargetPosition(position.name());
        LlmSelection selection = llmConfigPort.resolveSelection(userId, request.getLlmModel());
        session.setLlmProvider(selection.providerKey());
        session.setLlmModel(selection.model());
        session.setPromptVersionsJson(PromptVersions.DEFAULT_SNAPSHOT_JSON);
        session.setStatus(STATUS_ONGOING);
        session.setJdText(request.getJdText());
        interviewSessionRepository.add(session);

        List<String> retrievalDocuments = new ArrayList<>();
        addIfPresent(retrievalDocuments, resume.plainText());
        addIfPresent(retrievalDocuments, request.getJdText());
        sseTaskExecutor.execute(() -> retrievalPort.index(
            RetrievalPort.SCOPE_SESSION,
            session.getId(),
            retrievalDocuments
        ));
        interviewMessageService.insertMessage(session.getId(), ROLE_SYSTEM, position.systemPrompt());
        interviewStageManager.ensureInitialStage(session);

        return new InterviewStartResponse(session.getId(), position.name(), InterviewStageManager.STAGE_WARMUP);
    }

    private Long currentUserId() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return userId;
    }

    private void addIfPresent(List<String> documents, String value) {
        if (value != null && !value.isBlank()) {
            documents.add(value);
        }
    }
}
