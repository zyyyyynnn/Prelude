package com.prelude.interview.application;

import com.prelude.interview.application.port.InterviewMessageRepository;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.llm.ChatPort;
import com.prelude.llm.PromptRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewJudgeServiceTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "not json",
        "{\"hint\":\"缺少评分\"}"
    })
    void invalidJudgeOutputDoesNotPersistInventedScore(String judgeOutput) {
        InterviewMessageRepository repository = mock(InterviewMessageRepository.class);
        ChatPort chatPort = mock(ChatPort.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        PromptRegistry promptRegistry = mock(PromptRegistry.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(promptRegistry.load(anyString())).thenReturn("judge prompt");
        when(chatPort.complete(any())).thenReturn(judgeOutput);

        InterviewSession session = new InterviewSession();
        session.setId(11L);
        session.setUserId(7L);
        session.setTargetPosition("Java 后端工程师");
        session.setLlmProvider("deepseek");
        session.setLlmModel("deepseek-v4-flash");

        InterviewMessage question = new InterviewMessage();
        question.setRole("assistant");
        question.setSeqNum(1);
        question.setContent("请说明事务隔离级别");
        InterviewMessage answer = new InterviewMessage();
        answer.setId(22L);
        answer.setSessionId(11L);
        answer.setRole("user");
        answer.setSeqNum(2);
        answer.setContent("候选人回答");
        when(repository.listBySession(11L)).thenReturn(List.of(question, answer));

        InterviewJudgeService service = new InterviewJudgeService(
            repository,
            chatPort,
            new ObjectMapper(),
            redisTemplate,
            promptRegistry
        );

        assertThat(service.judgeAndPersist(session, answer)).isEmpty();
        assertThat(answer.getScore()).isNull();
        assertThat(answer.getHint()).isNull();
        verify(repository, never()).update(any());
        verify(redisTemplate).delete("lock:judge:7:11");
    }
}
