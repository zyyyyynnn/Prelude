package com.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.entity.InterviewMessage;
import com.interview.entity.InterviewSession;
import com.interview.llm.LlmRouter;
import com.interview.mapper.InterviewMessageMapper;
import com.interview.mapper.InterviewSessionMapper;
import com.interview.service.DevFixtureService;
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

    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewMessageMapper interviewMessageMapper;
    private final LlmRouter llmRouter;
    private final DevFixtureService devFixtureService;
    @Qualifier("sseTaskExecutor")
    private final Executor sseTaskExecutor;

    public void triggerAsyncSummarizeIfNeeded(InterviewSession session, boolean voiceMode) {
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
                String newSummary = buildSummary(session, messagesToSummarize, voiceMode);
                session.setSummary(newSummary);
                interviewSessionMapper.updateById(session);
                log.info("Successfully updated sliding window memory summary for session {}", session.getId());
            } catch (Exception exception) {
                log.error("Failed to generate sliding window memory summary for session {}", session.getId(), exception);
            }
        });
    }

    private String buildSummary(InterviewSession session, List<InterviewMessage> messagesToSummarize, boolean voiceMode) {
        if (devFixtureService != null && devFixtureService.isEnabled()) {
            return voiceMode
                ? "dev fixture 下自动生成的模拟对话摘要。候选人对后端架构设计进行了基本的回答，表现稳定。"
                : "dev fixture 下自动生成的模拟对话摘要。候选人对后端架构设计、MyBatis-Plus 分页与自定义 SQL 执行进行了基本的回答，表现稳定。";
        }
        StringBuilder builder = new StringBuilder();
        for (InterviewMessage message : messagesToSummarize) {
            builder.append(message.getRole()).append(": ").append(message.getContent()).append("\n");
        }
        String existingSummary = session.getSummary();
        String prompt = "请对以下模拟面试记录进行简明扼要的摘要总结。要求：保留候选人的核心技术栈、项目细节及表现评估，并进行严格的个人隐私数据脱敏（严禁包含手机号、邮箱、身份证等隐私信息）。以第三人称陈述，字数控制在 200 字以内。\n" +
            "已有摘要历史：" + (existingSummary != null ? existingSummary : "无") + "\n" +
            "新增面试记录：\n" + builder;

        return llmRouter.chatWithSnapshot(
            session.getLlmProvider(),
            session.getLlmModel(),
            List.of(
                Map.of("role", "system", "content", "你是严谨的面试总结助手。请直接输出摘要，不要附带任何解释。"),
                Map.of("role", "user", "content", prompt)
            )
        );
    }

    private List<InterviewMessage> dialogMessages(Long sessionId) {
        List<InterviewMessage> allMessages = interviewMessageMapper.selectList(new LambdaQueryWrapper<InterviewMessage>()
            .eq(InterviewMessage::getSessionId, sessionId)
            .orderByAsc(InterviewMessage::getSeqNum));
        List<InterviewMessage> dialogMsgs = new ArrayList<>();
        for (InterviewMessage message : allMessages) {
            if (!ROLE_SYSTEM.equals(message.getRole())) {
                dialogMsgs.add(message);
            }
        }
        return dialogMsgs;
    }
}
