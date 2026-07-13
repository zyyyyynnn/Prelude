package com.interview.interview.application;

import com.interview.shared.api.BusinessException;
import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.domain.InterviewSession;
import com.interview.platform.llm.LlmSelection;
import com.interview.interview.application.port.InterviewMessageRepository;
import com.interview.platform.llm.ChatPort;
import com.interview.platform.llm.ChatRequest;
import com.interview.platform.llm.LlmPurpose;
import com.interview.platform.llm.PromptVersions;
import com.interview.interview.application.port.InterviewFixturePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RunInterviewTurn {

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String STAGE_COMPLETE_TAG = "[STAGE_COMPLETE]";

    private final InterviewSessionAccess sessionAccess;
    private final InterviewMessageRepository interviewMessageRepository;
    private final ChatPort chatPort;
    private final InterviewFixturePort devFixtureService;
    private final InterviewStageManager interviewStageManager;
    private final InterviewContextService interviewContextService;
    private final InterviewMessageService interviewMessageService;

    public InterviewTurnResult execute(InterviewTurnCommand command, InterviewTurnSink sink) {
        InterviewMessage insertedUserMessage = null;
        boolean assistantPersisted = false;
        try {
            InterviewSession session = sessionAccess.requireOngoing(command.sessionId(), command.userId());
            String content = normalizeContent(command.content());
            boolean firstRound = !hasConversationRound(command.sessionId());
            List<Map<String, String>> messages;

            if (command.autoStart() && firstRound && content.isEmpty()) {
                messages = interviewContextService.buildAutoStartMessages(session);
            } else {
                if (content.isEmpty()) {
                    throw BusinessException.badRequest("回答内容不能为空");
                }
                insertedUserMessage = interviewMessageService.insertMessage(command.sessionId(), ROLE_USER, content);
                sink.userAccepted(insertedUserMessage);
                messages = interviewContextService.buildContextMessages(command.sessionId());
            }

            StringBuilder assistantReply = new StringBuilder();
            streamAssistantReply(session, messages, assistantReply, sink);
            boolean shouldAdvance = assistantReply.indexOf(STAGE_COMPLETE_TAG) >= 0;
            String finalReply = assistantReply.toString().replace(STAGE_COMPLETE_TAG, "").trim();
            if (!finalReply.isEmpty()) {
                interviewMessageService.insertMessage(command.sessionId(), ROLE_ASSISTANT, finalReply);
            }
            assistantPersisted = true;

            if (shouldAdvance) {
                interviewStageManager.advanceStage(command.sessionId(), command.completionPrompt());
            }
            return new InterviewTurnResult(session, insertedUserMessage, finalReply);
        } catch (RuntimeException error) {
            if (insertedUserMessage != null && insertedUserMessage.getId() != null && !assistantPersisted) {
                interviewMessageRepository.delete(insertedUserMessage.getId());
            }
            throw error;
        }
    }

    private boolean hasConversationRound(Long sessionId) {
        return interviewMessageRepository.countConversationMessages(sessionId) > 0;
    }

    private void streamAssistantReply(
        InterviewSession session,
        List<Map<String, String>> messages,
        StringBuilder assistantReply,
        InterviewTurnSink sink
    ) {
        if (devFixtureService != null && devFixtureService.isEnabled()) {
            String currentStage = interviewStageManager.currentStageName(session.getId());
            int replyIndex = interviewStageManager.assistantRepliesInCurrentStage(session.getId());
            String reply = devFixtureService.resolveScriptedReply(currentStage, replyIndex);
            devFixtureService.streamReply(reply, delta -> appendAndSend(assistantReply, sink, delta));
            return;
        }
        chatPort.stream(
            ChatRequest.snapshot(
                session.getUserId(),
                LlmPurpose.CHAT,
                PromptVersions.CHAT,
                PromptVersions.V1,
                messages,
                new LlmSelection(session.getLlmProvider(), session.getLlmModel()),
                null
            ),
            delta -> appendAndSend(assistantReply, sink, delta)
        );
    }

    private void appendAndSend(StringBuilder reply, InterviewTurnSink sink, String delta) {
        reply.append(delta);
        sink.assistantDelta(delta);
    }

    private String normalizeContent(String content) {
        return content == null ? "" : content.trim();
    }
}
