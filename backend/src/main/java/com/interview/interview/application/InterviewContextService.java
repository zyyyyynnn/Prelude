package com.interview.interview.application;

import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.domain.InterviewSession;
import com.interview.interview.application.port.InterviewMessageRepository;
import com.interview.interview.application.port.InterviewSessionRepository;
import com.interview.platform.retrieval.RetrievalPort;
import com.interview.resume.api.port.ResumeContextPort;
import com.interview.resume.api.port.ResumeProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InterviewContextService {

    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";

    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewMessageRepository interviewMessageRepository;
    private final ResumeContextPort resumeContextPort;
    private final RetrievalPort retrievalPort;
    private final InterviewStageManager interviewStageManager;

    public List<Map<String, String>> buildContextMessages(Long sessionId) {
        InterviewSession session = interviewSessionRepository.selectById(sessionId);
        List<InterviewMessage> allMessages = listMessages(sessionId);
        List<InterviewMessage> systemMsgs = new ArrayList<>();
        List<InterviewMessage> dialogMsgs = new ArrayList<>();

        for (InterviewMessage message : allMessages) {
            if (ROLE_SYSTEM.equals(message.getRole())) {
                systemMsgs.add(message);
            } else {
                dialogMsgs.add(message);
            }
        }

        String latestUserMsg = latestUserMessage(allMessages);
        if (latestUserMsg.isEmpty() && session != null) {
            latestUserMsg = session.getTargetPosition();
        }

        String ragSystemPrompt = buildRagSystemPrompt(session, latestUserMsg);
        String summary = session != null ? session.getSummary() : null;
        if (summary != null && !summary.isBlank()) {
            return buildSummaryWindow(systemMsgs, dialogMsgs, ragSystemPrompt, summary);
        }
        return buildRecentWindow(systemMsgs, dialogMsgs, ragSystemPrompt);
    }

    public List<Map<String, String>> buildAutoStartMessages(InterviewSession session) {
        ResumeProjection resume = resumeContextPort.requireOwnedProjection(
            session.getUserId(),
            session.getResumeId()
        );
        List<Map<String, String>> messages = new ArrayList<>(buildContextMessages(session.getId()));
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("请作为模拟面试官主动发起第一问。")
            .append("目标岗位：").append(session.getTargetPosition()).append("。")
            .append("当前阶段：").append(interviewStageManager.currentStageName(session.getId())).append("。");
        if (resume != null) {
            userPrompt.append("候选人简历文件名：").append(resume.displayName()).append("。");
            if (resume.plainText() != null && !resume.plainText().isBlank()) {
                userPrompt.append("以下是候选人简历摘要，请基于它发问：\n")
                    .append(limitText(resume.plainText(), 1800));
            }
        }
        userPrompt.append("\n要求：只输出第一条面试问题，不要附加解释。");
        messages.add(Map.of("role", ROLE_USER, "content", userPrompt.toString()));
        return messages;
    }

    private List<Map<String, String>> buildSummaryWindow(
        List<InterviewMessage> systemMsgs,
        List<InterviewMessage> dialogMsgs,
        String ragSystemPrompt,
        String summary
    ) {
        List<Map<String, String>> messages = new ArrayList<>();
        for (InterviewMessage sysMsg : systemMsgs) {
            messages.add(Map.of("role", ROLE_SYSTEM, "content", sysMsg.getContent()));
        }
        if (!ragSystemPrompt.isEmpty()) {
            messages.add(Map.of("role", ROLE_SYSTEM, "content", ragSystemPrompt));
        }
        messages.add(Map.of("role", ROLE_SYSTEM, "content", "以下是此前面试对话的摘要总结（已对涉及手机号、邮箱、身份证等用户隐私数据做严格脱敏处理）：\n" + summary));
        int lastCount = Math.min(dialogMsgs.size(), 8);
        List<InterviewMessage> recentDialogs = dialogMsgs.subList(dialogMsgs.size() - lastCount, dialogMsgs.size());
        for (InterviewMessage message : recentDialogs) {
            messages.add(Map.of("role", message.getRole(), "content", message.getContent()));
        }
        return messages;
    }

    private List<Map<String, String>> buildRecentWindow(
        List<InterviewMessage> systemMsgs,
        List<InterviewMessage> dialogMsgs,
        String ragSystemPrompt
    ) {
        List<InterviewMessage> finalMessages = new ArrayList<>();
        if (!systemMsgs.isEmpty()) {
            finalMessages.add(systemMsgs.get(0));
        }
        if (!ragSystemPrompt.isEmpty()) {
            InterviewMessage ragMsg = new InterviewMessage();
            ragMsg.setRole(ROLE_SYSTEM);
            ragMsg.setContent(ragSystemPrompt);
            finalMessages.add(ragMsg);
        }
        for (int i = 1; i < systemMsgs.size(); i++) {
            finalMessages.add(systemMsgs.get(i));
        }

        int maxDialogs = 12;
        List<InterviewMessage> trimmedDialogs = dialogMsgs;
        if (dialogMsgs.size() > maxDialogs) {
            trimmedDialogs = new ArrayList<>(dialogMsgs.subList(dialogMsgs.size() - maxDialogs, dialogMsgs.size()));
        }
        finalMessages.addAll(trimmedDialogs);

        return finalMessages.stream()
            .map(message -> Map.of("role", message.getRole(), "content", message.getContent()))
            .toList();
    }

    private String latestUserMessage(List<InterviewMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            InterviewMessage message = messages.get(i);
            if (ROLE_USER.equals(message.getRole())) {
                return message.getContent();
            }
        }
        return "";
    }

    private String buildRagSystemPrompt(InterviewSession session, String latestUserMsg) {
        List<String> ragChunks = (session != null && !latestUserMsg.isEmpty())
            ? retrievalPort.search(RetrievalPort.SCOPE_SESSION, session.getId(), latestUserMsg, 5)
            : List.of();
        if (ragChunks.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("以下是与当前对话主题最相关的简历及岗位 JD 背景信息碎片，供提问和追问参考：\n");
        for (int i = 0; i < ragChunks.size(); i++) {
            builder.append("[").append(i + 1).append("] ").append(ragChunks.get(i)).append("\n");
        }
        return builder.toString();
    }

    private List<InterviewMessage> listMessages(Long sessionId) {
        return interviewMessageRepository.listBySession(sessionId);
    }

    private String limitText(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
