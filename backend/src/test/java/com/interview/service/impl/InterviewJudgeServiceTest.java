package com.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.entity.InterviewMessage;
import com.interview.llm.LlmRouter;
import com.interview.mapper.InterviewMessageMapper;
import com.interview.service.DevFixtureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.service.impl.InterviewStageManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewJudgeServiceTest {

    @Mock private InterviewMessageMapper interviewMessageMapper;
    @Mock private LlmRouter llmRouter;
    @Mock private DevFixtureService devFixtureService;
    @Mock private InterviewStageManager interviewStageManager;
    @Mock private StringRedisTemplate stringRedisTemplate;

    private InterviewJudgeService service;

    @BeforeEach
    void setUp() {
        service = new InterviewJudgeService(
            interviewMessageMapper,
            llmRouter,
            devFixtureService,
            new ObjectMapper(),
            interviewStageManager,
            stringRedisTemplate
        );
    }

    @Test
    void nextSeqNumReturnsZeroWhenNoMessages() {
        when(interviewMessageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        int result = service.nextSeqNum(7L);

        assertThat(result).isZero();
    }

    @Test
    void nextSeqNumReturnsZeroWhenLatestSeqNumIsNull() {
        InterviewMessage latest = new InterviewMessage();
        latest.setSeqNum(null);
        when(interviewMessageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(latest);

        int result = service.nextSeqNum(7L);

        assertThat(result).isZero();
    }

    @Test
    void nextSeqNumReturnsLatestPlusOne() {
        InterviewMessage latest = new InterviewMessage();
        latest.setSeqNum(4);
        when(interviewMessageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(latest);

        int result = service.nextSeqNum(7L);

        assertThat(result).isEqualTo(5);
    }

    @Test
    void nextSeqNumHandlesSparseSequence() {
        InterviewMessage latest = new InterviewMessage();
        latest.setSeqNum(99);
        when(interviewMessageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(latest);

        int result = service.nextSeqNum(7L);

        assertThat(result).isEqualTo(100);
    }
}