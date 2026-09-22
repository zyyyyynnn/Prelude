package com.prelude.test;

import com.prelude.activity.RealtimeConnection;
import com.prelude.activity.RealtimePort;
import com.prelude.identity.api.SessionValidity;
import com.prelude.interview.application.InterviewMessageService;
import com.prelude.interview.application.InterviewSessionAccess;
import com.prelude.interview.application.InterviewStageManager;
import com.prelude.interview.application.StreamChatTurn;
import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.application.repository.InterviewStageRepository;
import com.prelude.interview.application.port.InterviewTurnCommand;
import com.prelude.interview.application.port.InterviewTurnSessionSnapshot;
import com.prelude.interview.application.port.InterviewUserTurnSnapshot;
import com.prelude.interview.application.port.InterviewTurnPort;
import com.prelude.interview.application.port.InterviewTurnResult;
import com.prelude.interview.application.port.InterviewTurnSink;
import com.prelude.interview.application.port.JudgeResult;
import com.prelude.interview.domain.InterviewMessage;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.interview.domain.InterviewStage;
import com.prelude.interview.domain.InterviewStagePolicy;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class SessionFixtures {

    private SessionFixtures() {
    }

    /** A scheduler whose tasks never run: enough for SseSessionStream to register a heartbeat. */
    @SuppressWarnings("unchecked")
    private static ScheduledExecutorService inertHeartbeatExecutor() {
        ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);
        Mockito.when(executor.scheduleAtFixedRate(
                Mockito.any(Runnable.class),
                Mockito.anyLong(),
                Mockito.anyLong(),
                Mockito.any(TimeUnit.class)))
            .thenReturn(Mockito.mock(ScheduledFuture.class));
        return executor;
    }

    public static InterviewSession create(long id, long accountId, String status) {
        InterviewSession session = new InterviewSession();
        session.setId(id);
        session.setAccountId(accountId);
        session.setStatus(status);
        return session;
    }

    public static InterviewSession create(long id, long accountId, String status, String summaryReport) {
        InterviewSession session = new InterviewSession();
        session.setId(id);
        session.setAccountId(accountId);
        session.setStatus(status);
        session.setSummaryReport(summaryReport);
        return session;
    }

    public static InterviewSession create(long id, String status, String summaryReport) {
        InterviewSession session = new InterviewSession();
        session.setId(id);
        session.setStatus(status);
        session.setSummaryReport(summaryReport);
        return session;
    }

    public static InterviewSession create(long id) {
        InterviewSession session = new InterviewSession();
        session.setId(id);
        return session;
    }

    /** Adapts a mock servlet session to the port the use cases depend on. */
    public static com.prelude.identity.application.port.HttpSessionAccess sessionAccess(
        org.springframework.mock.web.MockHttpSession session) {
        return new com.prelude.identity.application.port.HttpSessionAccess() {
            @Override
            public String currentSessionId() {
                return session == null ? null : session.getId();
            }

            @Override
            public Object attribute(String name) {
                return session == null ? null : session.getAttribute(name);
            }

            @Override
            public void attribute(String name, Object value) {
                if (session != null) {
                    session.setAttribute(name, value);
                }
            }

            @Override
            public void removeAttribute(String name) {
                if (session != null) {
                    session.removeAttribute(name);
                }
            }
        };
    }

    /** Adapts a mock multipart upload to the avatar projection. */
    public static com.prelude.identity.application.port.AvatarUpload avatarUpload(
        org.springframework.mock.web.MockMultipartFile file) {
        try {
            return new com.prelude.identity.application.port.AvatarUpload(
                file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("mock upload should be readable", exception);
        }
    }

    public static com.prelude.interview.api.port.InterviewReportPort mockReportPort() {
        return Mockito.mock(com.prelude.interview.api.port.InterviewReportPort.class);
    }

    public static com.prelude.interview.api.port.InterviewSessionSnapshot reportSession(
        Long sessionId, Long accountId, String status, String summaryReport) {
        return new com.prelude.interview.api.port.InterviewSessionSnapshot(
            sessionId, accountId, "Java 后端", 3L, status, null, summaryReport);
    }

    public static com.prelude.interview.api.port.InterviewSessionSnapshot reportSession(
        Long sessionId, Long accountId, String status, String targetPosition, Long snapshotId) {
        return new com.prelude.interview.api.port.InterviewSessionSnapshot(
            sessionId, accountId, targetPosition, snapshotId, status, null, null);
    }

    public static InterviewTurnSessionSnapshot turnSession(Long sessionId) {
        return new InterviewTurnSessionSnapshot(sessionId, 7L, "Java 后端", 3L);
    }

    public static InterviewUserTurnSnapshot userTurn(Long messageId, Long sessionId, String content) {
        return new InterviewUserTurnSnapshot(messageId, sessionId, content, 2, java.time.LocalDateTime.now());
    }

    public static InterviewTurnPort mockTurnPort() {
        return Mockito.mock(InterviewTurnPort.class);
    }

    public static RealtimePort mockRealtimePort() {
        return Mockito.mock(RealtimePort.class);
    }

    public static RealtimeConnection mockRealtimeConnection() {
        return Mockito.mock(RealtimeConnection.class);
    }

    public static SessionValidity mockSessionValidity() {
        return Mockito.mock(SessionValidity.class);
    }

    public static StreamChatTurn createStreamChatTurn(
        InterviewSessionAccess sessionAccess,
        InterviewTurnPort turnPort,
        Object connection,
        boolean sessionActive,
        String authSessionId,
        long accountId
    ) {
        SessionValidity sessionValidity = mockSessionValidity();
        Mockito.when(sessionValidity.isActive(Mockito.eq(authSessionId), Mockito.eq(accountId))).thenReturn(sessionActive);
        Mockito.when(sessionAccess.currentAccountId()).thenReturn(accountId);
        return new StreamChatTurn(
            sessionAccess,
            turnPort,
            sessionValidity,
            Runnable::run
        );
    }

    /**
     * Opens a real stream over an inert heartbeat executor, so no task ever runs, and
     * returns the connection the hub registered — the one the assertions inspect.
     */
    public static com.prelude.activity.RealtimeConnection openStream(
        RealtimePort realtimePort, Long sessionId, com.prelude.activity.SseSessionStream[] opened) {
        com.prelude.activity.RealtimeConnection connection = mockRealtimeConnection();
        Mockito.when(realtimePort.register(Mockito.any(), Mockito.anyString(), Mockito.any()))
            .thenReturn(connection);
        opened[0] = com.prelude.activity.SseSessionStream.open(
            realtimePort,
            sessionId,
            // Turns run inline in tests, so the heartbeat has to stay inert rather than schedule.
            inertHeartbeatExecutor()
        );
        return connection;
    }

    public static InterviewStageRepository mockStageRepository() {
        return Mockito.mock(InterviewStageRepository.class);
    }

    public static InterviewMessageRepository mockMessageRepository() {
        return Mockito.mock(InterviewMessageRepository.class);
    }

    public static InterviewTurnSink noopSink() {
        return delta -> {};
    }

    public static InterviewTurnResult turnResult(
        InterviewTurnSessionSnapshot session,
        InterviewUserTurnSnapshot userTurn,
        String delta
    ) {
        return new InterviewTurnResult(session, userTurn, delta);
    }

    public static InterviewTurnCommand turnCommand(Long sessionId, Long accountId, String input, boolean stream, boolean isWarmup) {
        return new InterviewTurnCommand(sessionId, accountId, input, stream, isWarmup);
    }

    public static JudgeResult judgeResult(int score, String hint, String json) {
        return new JudgeResult(score, hint, json);
    }

    public static InterviewMessage message() {
        return new InterviewMessage();
    }

    public static InterviewStage stage(Long sessionId, String stageName) {
        InterviewStage stage = new InterviewStage();
        stage.setSessionId(sessionId);
        stage.setStageName(stageName);
        return stage;
    }

    public static InterviewStagePolicy stagePolicy() {
        return new InterviewStagePolicy();
    }

    public static void sendDelta(InvocationOnMock invocation, String delta) {
        InterviewTurnSink sink = invocation.getArgument(1);
        sink.assistantDelta(delta);
    }

    public static void verifyConnectionSend(Object connection, String event, String data) {
        Mockito.verify((RealtimeConnection) connection).send(event, data);
    }

    public static void verifyConnectionComplete(Object connection) {
        Mockito.verify((RealtimeConnection) connection).complete();
    }

    public static class StageManagerHarness {
        public final InterviewStageRepository stageRepository = Mockito.mock(InterviewStageRepository.class);
        public final InterviewMessageRepository messageRepository = Mockito.mock(InterviewMessageRepository.class);
        public final InterviewStagePolicy stagePolicy = new InterviewStagePolicy();
        public final List<InterviewStage> stages = new ArrayList<>();
        public final List<InterviewMessage> messages = new ArrayList<>();
        public final AtomicLong messageSeq = new AtomicLong();
        public final InterviewStageManager stageManager;

        public StageManagerHarness(InterviewMessageService messageService, Long sessionId) {
            this.stageManager = new InterviewStageManager(stageRepository, messageRepository, messageService, stagePolicy);
            Mockito.when(stageRepository.findCurrent(Mockito.eq(sessionId))).thenAnswer(invocation -> stages.stream()
                .filter(stage -> stage.getEndedAt() == null)
                .findFirst()
                .orElse(null));
            Mockito.when(stageRepository.findLatest(Mockito.eq(sessionId))).thenAnswer(invocation ->
                stages.isEmpty() ? null : stages.get(stages.size() - 1));
            Mockito.when(stageRepository.listBySession(Mockito.eq(sessionId))).thenAnswer(invocation -> List.copyOf(stages));
            Mockito.when(stageRepository.add(Mockito.any(InterviewStage.class))).thenAnswer(invocation -> {
                stages.add(invocation.getArgument(0));
                return 1;
            });
            Mockito.when(stageRepository.update(Mockito.any(InterviewStage.class))).thenReturn(1);
            Mockito.when(messageRepository.listBySession(Mockito.eq(sessionId))).thenAnswer(invocation -> List.copyOf(messages));
            Mockito.when(messageService.insertMessage(Mockito.eq(sessionId), Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(invocation -> {
                    InterviewMessage message = new InterviewMessage();
                    message.setSessionId(sessionId);
                    message.setRole(invocation.getArgument(1));
                    message.setContent(invocation.getArgument(2));
                    message.setSeqNum((int) messageSeq.incrementAndGet());
                    messages.add(message);
                    return message;
                });
        }

        public InterviewStage stage(Long sessionId, String name) {
            InterviewStage stage = new InterviewStage();
            stage.setSessionId(sessionId);
            stage.setStageName(name);
            stage.setStartedAt(java.time.LocalDateTime.now());
            return stage;
        }

        public InterviewMessage assistantMessage(Long sessionId, int seqNum) {
            return message(sessionId, "assistant", "回答内容 " + seqNum, seqNum);
        }

        public InterviewMessage userMessage(Long sessionId, int seqNum) {
            return message(sessionId, "user", "用户内容 " + seqNum, seqNum);
        }

        public InterviewMessage systemMessage(Long sessionId, int seqNum, String content) {
            return message(sessionId, "system", content, seqNum);
        }

        private InterviewMessage message(Long sessionId, String role, String content, int seqNum) {
            InterviewMessage message = new InterviewMessage();
            message.setSessionId(sessionId);
            message.setRole(role);
            message.setContent(content);
            message.setSeqNum(seqNum);
            return message;
        }
    }
}