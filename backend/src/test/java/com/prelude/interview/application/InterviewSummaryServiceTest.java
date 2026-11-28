package com.prelude.interview.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.llm.api.ModelExecutionPort;
import com.prelude.llm.api.PromptIds;
import com.prelude.llm.api.PromptRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InterviewSummaryServiceTest {

    @Test
    void summaryExcludesSupersededAnswersAndTheirAssistantReplies() {
        InterviewMessageRepository messages = mock(InterviewMessageRepository.class);
        InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
        ModelExecutionPort llm = mock(ModelExecutionPort.class);
        PromptRegistry prompts = mock(PromptRegistry.class);
        InterviewSession session = new InterviewSession();
        session.setId(51L);
        session.setModelExecutionSnapshotId(5L);
        List<InterviewMessage> dialog = new ArrayList<>();
        dialog.add(message(100L, "system", "system", null, null));
        for (int index = 1; index <= 16; index++) {
            long answerId = index * 2L - 1;
            long replyId = index * 2L;
            dialog.add(message(answerId, "user", index == 1 ? "old answer" : "answer " + index, null, null));
            dialog.add(message(replyId, "assistant", index == 1 ? "old follow-up" : "reply " + index, answerId, null));
        }
        dialog.get(1).setSupersededByMessageId(3L);
        when(messages.listBySession(51L)).thenReturn(dialog);
        when(prompts.load(PromptIds.SUMMARY)).thenReturn("summary prompt");
        when(llm.complete(any())).thenReturn(new ModelExecutionPort.CompletionResult("summary", null));
        InterviewSummaryService service = new InterviewSummaryService(
            sessions, messages, llm, prompts, Runnable::run);

        service.triggerAsyncSummarizeIfNeeded(session);

        ArgumentCaptor<ModelExecutionPort.ModelExecutionRequest> request =
            ArgumentCaptor.forClass(ModelExecutionPort.ModelExecutionRequest.class);
        verify(llm).complete(request.capture());
        String prompt = request.getValue().messages().get(1).content();
        assertThat(prompt).doesNotContain("old answer", "old follow-up").contains("answer 2", "reply 2");
        verify(sessions).updateSummary(51L, "summary");
    }

    private InterviewMessage message(Long id, String role, String content, Long replyToMessageId, Long supersededByMessageId) {
        InterviewMessage message = new InterviewMessage();
        message.setId(id);
        message.setSessionId(51L);
        message.setRole(role);
        message.setContent(content);
        message.setReplyToMessageId(replyToMessageId);
        message.setSupersededByMessageId(supersededByMessageId);
        return message;
    }
}
