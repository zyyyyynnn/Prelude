package com.interview.interview.application;

import com.interview.interview.application.InterviewResponseAssembler;
import com.interview.interview.application.InterviewStageManager;

import com.interview.interview.api.InterviewMessageItemResponse;
import com.interview.interview.api.InterviewMessagesResponse;
import com.interview.interview.api.InterviewSessionItemResponse;
import com.interview.interview.api.InterviewStageItemResponse;
import com.interview.interview.domain.InterviewMessage;
import com.interview.interview.domain.InterviewSession;
import com.interview.interview.domain.InterviewStage;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewResponseAssemblerTest {

    private final InterviewResponseAssembler assembler = new InterviewResponseAssembler();

    @Test
    void toSessionItemMapsAllFields() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 19, 12, 0);
        InterviewSession session = new InterviewSession();
        session.setId(7L);
        session.setTargetPosition("Backend Engineer");
        session.setStatus("ongoing");
        session.setCreatedAt(createdAt);
        session.setLlmProvider("deepseek");
        session.setLlmModel("deepseek-chat");
        session.setSummaryReport(null);

        InterviewSessionItemResponse response = assembler.toSessionItem(session, "technical");

        assertThat(response.sessionId()).isEqualTo(7L);
        assertThat(response.targetPosition()).isEqualTo("Backend Engineer");
        assertThat(response.status()).isEqualTo("ongoing");
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.currentStage()).isEqualTo("technical");
        assertThat(response.llmProvider()).isEqualTo("deepseek");
        assertThat(response.llmModel()).isEqualTo("deepseek-chat");
        assertThat(response.summaryReport()).isNull();
    }

    @Test
    void toSessionItemCarriesSummaryReportWhenSet() {
        InterviewSession session = new InterviewSession();
        session.setId(8L);
        session.setStatus("finished");
        session.setSummaryReport("# Report");

        InterviewSessionItemResponse response = assembler.toSessionItem(session, "wrap_up");

        assertThat(response.summaryReport()).isEqualTo("# Report");
        assertThat(response.currentStage()).isEqualTo("wrap_up");
    }

    @Test
    void toMessagesResponseFallsBackToWarmupWhenStagesEmpty() {
        InterviewSession session = sessionBuilder(7L);

        InterviewMessagesResponse response = assembler.toMessagesResponse(
            session,
            Collections.emptyList(),
            Collections.emptyList()
        );

        assertThat(response.sessionId()).isEqualTo(7L);
        assertThat(response.targetPosition()).isEqualTo("Backend Engineer");
        assertThat(response.status()).isEqualTo("ongoing");
        assertThat(response.currentStage()).isEqualTo(InterviewStageManager.STAGE_WARMUP);
        assertThat(response.stages()).isEmpty();
        assertThat(response.messages()).isEmpty();
    }

    @Test
    void toMessagesResponseMapsStagesAndMessagesInOrder() {
        InterviewSession session = sessionBuilder(7L);
        LocalDateTime stageStart = LocalDateTime.of(2026, 6, 19, 10, 0);
        LocalDateTime stageEnd = LocalDateTime.of(2026, 6, 19, 10, 30);

        InterviewStage warmup = stageEntity("warmup", stageStart, stageEnd);
        InterviewStage technical = stageEntity("technical", stageEnd, null);

        InterviewMessage user = messageEntity(1L, "user", "hello", 0, null);
        InterviewMessage assistant = messageEntity(2L, "assistant", "hi there", 1, 8);
        InterviewMessage system = messageEntity(3L, "system", "sys prompt", 2, null);

        List<InterviewStage> stages = new ArrayList<>();
        stages.add(warmup);
        stages.add(technical);

        List<InterviewMessage> messages = new ArrayList<>();
        messages.add(user);
        messages.add(assistant);
        messages.add(system);

        InterviewMessagesResponse response = assembler.toMessagesResponse(session, stages, messages);

        assertThat(response.currentStage()).isEqualTo("technical");
        assertThat(response.stages()).containsExactly(
            new InterviewStageItemResponse("warmup", stageStart, stageEnd),
            new InterviewStageItemResponse("technical", stageEnd, null)
        );
        assertThat(response.messages()).hasSize(3);
        assertThat(response.messages().get(0).role()).isEqualTo("user");
        assertThat(response.messages().get(1).role()).isEqualTo("assistant");
        assertThat(response.messages().get(2).role()).isEqualTo("system");
        assertThat(response.messages().get(1).score()).isEqualTo(8);
        assertThat(response.messages().get(0).score()).isNull();
    }

    @Test
    void toMessagesResponseCarriesHintAndScoreFields() {
        InterviewSession session = sessionBuilder(7L);
        InterviewStage technical = stageEntity("technical", LocalDateTime.now(), null);
        InterviewMessage scored = messageEntity(11L, "assistant", "answer", 0, 9);
        scored.setHint("可以补充指标口径");

        InterviewMessagesResponse response = assembler.toMessagesResponse(
            session,
            List.of(technical),
            List.of(scored)
        );

        InterviewMessageItemResponse item = response.messages().get(0);
        assertThat(item.id()).isEqualTo(11L);
        assertThat(item.role()).isEqualTo("assistant");
        assertThat(item.content()).isEqualTo("answer");
        assertThat(item.seqNum()).isEqualTo(0);
        assertThat(item.score()).isEqualTo(9);
        assertThat(item.hint()).isEqualTo("可以补充指标口径");
    }

    @Test
    void toMessagesResponseIncludesSessionMetadata() {
        InterviewSession session = sessionBuilder(7L);
        session.setResumeId(101L);
        session.setPositionId(202L);
        session.setJdText("岗位描述");

        InterviewMessagesResponse response = assembler.toMessagesResponse(
            session,
            Collections.emptyList(),
            Collections.emptyList()
        );

        assertThat(response.resumeId()).isEqualTo(101L);
        assertThat(response.positionId()).isEqualTo(202L);
        assertThat(response.jdText()).isEqualTo("岗位描述");
        assertThat(response.summaryReport()).isNull();
    }

    private InterviewSession sessionBuilder(long id) {
        InterviewSession session = new InterviewSession();
        session.setId(id);
        session.setUserId(42L);
        session.setTargetPosition("Backend Engineer");
        session.setStatus("ongoing");
        session.setCreatedAt(LocalDateTime.of(2026, 6, 19, 12, 0));
        return session;
    }

    private InterviewStage stageEntity(String name, LocalDateTime startedAt, LocalDateTime endedAt) {
        InterviewStage stage = new InterviewStage();
        stage.setId(1L);
        stage.setSessionId(7L);
        stage.setStageName(name);
        stage.setStartedAt(startedAt);
        stage.setEndedAt(endedAt);
        return stage;
    }

    private InterviewMessage messageEntity(Long id, String role, String content, int seq, Integer score) {
        InterviewMessage message = new InterviewMessage();
        message.setId(id);
        message.setSessionId(7L);
        message.setRole(role);
        message.setContent(content);
        message.setSeqNum(seq);
        message.setScore(score);
        message.setCreatedAt(LocalDateTime.of(2026, 6, 19, 12, 0));
        return message;
    }
}
