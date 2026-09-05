package com.prelude.interview.application;

import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.application.port.InterviewMessageRepository;
import com.prelude.interview.application.port.InterviewSessionRepository;
import com.prelude.llm.api.LlmPort;
import com.prelude.llm.api.PromptIds;
import com.prelude.llm.api.PromptRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewSummaryService {

    private static final String ROLE_SYSTEM = "system";

    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewMessageRepository interviewMessageRepository;
    private final LlmPort llmPort;
    private final PromptRegistry promptRegistry;
    @Qualifier("sseTaskExecutor")
    private final Executor sseTaskExecutor;

    public void triggerAsyncSummarizeIfNeeded(InterviewSession session) {
        List<InterviewMessage> dialogMsgs = dialogMessages(session.getId());
        int rounds = dialogMsgs.size() / 2;
        if (rounds < 15 || (rounds - 10) % 5 != 0) {
            return;
        }
        int summaryRounds = rounds - 7;
        int msgEndIndex = summaryRounds * 2;
        List<InterviewMessage> messagesToSummarize = dialogMsgs.subList(0, msgEndIndex);

        sseTaskExecutor.execute(() -> {
            try {
                String newSummary = buildSummary(session, messagesToSummarize);
                session.setSummary(newSummary);
                interviewSessionRepository.update(session);
                log.info("Successfully updated sliding window memory summary for session {}", session.getId());
            } catch (Exception exception) {
                log.error("Failed to generate sliding window memory summary for session {}", session.getId(), exception);
            }
        });
    }

    private String buildSummary(InterviewSession session, List<InterviewMessage> messagesToSummarize) {
        StringBuilder builder = new StringBuilder();
        for (InterviewMessage message : messagesToSummarize) {
            builder.append(message.getRole()).append(": ").append(message.getContent()).append("\n");
        }
        String existingSummary = session.getSummary();
        String prompt = "请对以下模拟面试记录进行简明扼要的摘要总结。要求：保留候选人的核心技术栈、项目细节及表现评估，并进行严格的个人隐私数据脱敏（严禁包含手机号、邮箱、身份证等隐私信息）。以第三人称陈述，字数控制在 200 字以内。\n" +
            "已有摘要历史：" + (existingSummary != null ? existingSummary : "无") + "\n" +
            "新增面试记录：\n" + builder;

        LlmPort.CompletionResult completion = llmPort.complete(
            new LlmPort.ModelExecutionRequest(
                session.getModelExecutionSnapshotId(),
                "summary",
                PromptIds.SUMMARY,
                LlmPort.ResponseMode.PLAIN_TEXT,
                List.of(
                    new LlmPort.Message("system", promptRegistry.load(PromptIds.SUMMARY)),
                    new LlmPort.Message("user", prompt)
                ),
                List.of(),
                List.of()
            ));
        return completion.content();
    }

    private List<InterviewMessage> dialogMessages(Long sessionId) {
        List<InterviewMessage> allMessages = interviewMessageRepository.listBySession(sessionId);
        List<InterviewMessage> dialogMsgs = new ArrayList<>();
        for (InterviewMessage message : allMessages) {
            if (!ROLE_SYSTEM.equals(message.getRole())) {
                dialogMsgs.add(message);
            }
        }
        return dialogMsgs;
    }
}
