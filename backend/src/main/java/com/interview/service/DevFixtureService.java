package com.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.BusinessException;
import com.interview.config.DevFixtureProperties;
import com.interview.dto.ResumeUploadResponse;
import com.interview.entity.InterviewMessage;
import com.interview.entity.InterviewSession;
import com.interview.entity.InterviewStage;
import com.interview.entity.PositionTemplate;
import com.interview.entity.Resume;
import com.interview.entity.ScoreHistory;
import com.interview.entity.User;
import com.interview.entity.UserWeakness;
import com.interview.mapper.InterviewMessageMapper;
import com.interview.mapper.InterviewSessionMapper;
import com.interview.mapper.InterviewStageMapper;
import com.interview.mapper.PositionTemplateMapper;
import com.interview.mapper.ResumeMapper;
import com.interview.mapper.ScoreHistoryMapper;
import com.interview.mapper.UserMapper;
import com.interview.mapper.UserWeaknessMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class DevFixtureService {

    private static final String DEV_TEST_USERNAME = "demo";
    private static final String DEV_TEST_EMAIL = "demo@example.com";
    private static final String DEV_TEST_PASSWORD_HASH = "$2a$10$cwL4a7RrPcB895DFoO2MyuhK6QGDWhU0fScSmKj/LuBDtIzmL2zL2";
    private static final String DEV_FIXTURE_API_KEY_PLACEHOLDER = "dev-fixture-key-placeholder";
    private static final String DEV_FIXTURE_JAVA_POSITION_NAME = "Java 后端工程师";
    private static final String DEV_FIXTURE_FRONTEND_POSITION_NAME = "前端工程师";
    private static final String DEV_FIXTURE_ALGORITHM_POSITION_NAME = "算法工程师";
    private static final String STATUS_ONGOING = "ongoing";
    private static final String STATUS_FINISHED = "finished";
    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    private static final String TECHNICAL_STAGE_PROMPT = "面试已进入技术问答阶段，请围绕岗位核心技术栈、项目实现细节和工程实践进行追问。";
    private static final String DEEP_DIVE_STAGE_PROMPT = "面试已进入深挖阶段，请针对候选人前面回答中的薄弱点和模糊点继续深挖。";
    private static final String CLOSING_STAGE_PROMPT = "面试已进入收尾阶段，请用 1 到 2 个总结性问题结束本场面试。";

    private final DevFixtureProperties devFixtureProperties;
    private final DevFixtureCatalog devFixtureCatalog;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;
    private final ResumeMapper resumeMapper;
    private final PositionTemplateMapper positionTemplateMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewMessageMapper interviewMessageMapper;
    private final InterviewStageMapper interviewStageMapper;
    private final ScoreHistoryMapper scoreHistoryMapper;
    private final UserWeaknessMapper userWeaknessMapper;

    public boolean isEnabled() {
        return devFixtureProperties.isEnabled();
    }

    public String resolveMockJudge(String stageName, int replyIndex) {
        assertEnabled();
        int score = 7 + (replyIndex % 3);
        String hint;
        if ("warmup".equals(stageName)) {
            hint = "开场表现自然，对过往项目描述清晰。";
        } else if ("technical".equals(stageName)) {
            hint = "技术概念阐述清晰，若能结合具体代码实现则更佳。";
        } else if ("deep_dive".equals(stageName)) {
            hint = "逻辑推导合理，但在底层机制的理解上还有提升空间。";
        } else {
            hint = "收尾陈述简明扼要，整体沟通顺畅。";
        }
        return String.format("{\"score\": %d, \"hint\": \"%s\"}", score, hint);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reset() {
        assertEnabled();

        User user = ensureDevTestUser();
        List<Long> sessionIds = interviewSessionMapper.selectList(new LambdaQueryWrapper<InterviewSession>()
                .eq(InterviewSession::getUserId, user.getId()))
            .stream()
            .map(InterviewSession::getId)
            .toList();

        if (!sessionIds.isEmpty()) {
            interviewMessageMapper.delete(new LambdaQueryWrapper<InterviewMessage>()
                .in(InterviewMessage::getSessionId, sessionIds));
            interviewStageMapper.delete(new LambdaQueryWrapper<InterviewStage>()
                .in(InterviewStage::getSessionId, sessionIds));
            scoreHistoryMapper.delete(new LambdaQueryWrapper<ScoreHistory>()
                .in(ScoreHistory::getSessionId, sessionIds));
            userWeaknessMapper.delete(new LambdaQueryWrapper<UserWeakness>()
                .in(UserWeakness::getSessionId, sessionIds));
            interviewSessionMapper.delete(new LambdaQueryWrapper<InterviewSession>()
                .in(InterviewSession::getId, sessionIds));
        }

        resumeMapper.delete(new LambdaQueryWrapper<Resume>()
            .eq(Resume::getUserId, user.getId()));

        DevFixtureCatalog.DevLlmConfigFixture fixture = devFixtureCatalog.llmConfig();
        user.setEmail(DEV_TEST_EMAIL);
        user.setLlmProvider(fixture.providerKey());
        user.setLlmModel(fixture.model());
        user.setLlmApiKeyEncrypted(DEV_FIXTURE_API_KEY_PLACEHOLDER);
        userMapper.updateById(user);

        seedStoryline(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public ResumeUploadResponse createDevFixtureResume(Long userId, String fileName) {
        assertEnabled();
        Resume resume = insertResume(userId, fileName, LocalDateTime.now());
        DevFixtureCatalog.DevResumeFixture fixture = devFixtureCatalog.resume(fileName);
        return new ResumeUploadResponse(resume.getId(), fixture.skills(), fixture.projects());
    }

    public String resolveScriptedReply(String stageName, int replyIndex) {
        assertEnabled();
        return devFixtureCatalog.scriptedReply(stageName, replyIndex);
    }

    public void streamReply(String reply, Consumer<String> consumer) {
        assertEnabled();
        if (reply == null || reply.isBlank()) {
            return;
        }
        int chunkSize = Math.max(1, devFixtureProperties.getChunkSize());
        int delayMs = Math.max(0, devFixtureProperties.getStreamDelayMs());
        for (int start = 0; start < reply.length(); start += chunkSize) {
            int end = Math.min(start + chunkSize, reply.length());
            consumer.accept(reply.substring(start, end));
            if (delayMs > 0 && end < reply.length()) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    public String resolveReport(String targetPosition) {
        assertEnabled();
        return devFixtureCatalog.report(targetPosition);
    }

    public List<UserWeakness> buildWeaknesses(Long userId, Long sessionId) {
        assertEnabled();
        List<DevFixtureCatalog.DevWeaknessFixture> items = devFixtureCatalog.weaknesses();
        return items.stream()
            .map(item -> buildWeakness(userId, sessionId, item.category(), item.description(), LocalDateTime.now()))
            .toList();
    }

    public String maskApiKey(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return null;
        }
        return "****dev";
    }

    public String nextStoredApiKey(String requestApiKey, String currentStoredApiKey) {
        if (requestApiKey == null) {
            return currentStoredApiKey;
        }
        return requestApiKey.isBlank() ? null : DEV_FIXTURE_API_KEY_PLACEHOLDER;
    }

    private void seedStoryline(User user) {
        PositionTemplate javaPosition = requireDevFixturePosition(DEV_FIXTURE_JAVA_POSITION_NAME);
        PositionTemplate frontendPosition = requireDevFixturePosition(DEV_FIXTURE_FRONTEND_POSITION_NAME);
        PositionTemplate algorithmPosition = requireDevFixturePosition(DEV_FIXTURE_ALGORITHM_POSITION_NAME);

        Resume javaResume = insertResume(user.getId(), "Java高级架构.pdf", LocalDateTime.of(2026, 4, 22, 16, 40));
        Resume frontendResume = insertResume(user.getId(), "大前端资深开发.pdf", LocalDateTime.of(2026, 4, 20, 16, 10));
        Resume algoResume = insertResume(user.getId(), "推荐算法工程师.pdf", LocalDateTime.of(2026, 4, 18, 15, 30));
        createOngoingSession(user.getId(), javaResume, javaPosition);
        createFinishedSession(
            user.getId(), javaResume, javaPosition, LocalDateTime.of(2026, 4, 22, 10, 0),
            devFixtureCatalog.javaScore(),
            devFixtureCatalog.javaStorylineWeaknesses(),
            devFixtureCatalog.javaScript()
        );
        createFinishedSession(
            user.getId(), frontendResume, frontendPosition, LocalDateTime.of(2026, 4, 20, 16, 10),
            devFixtureCatalog.frontendScore(),
            devFixtureCatalog.frontendStorylineWeaknesses(),
            devFixtureCatalog.frontendScript()
        );
        createFinishedSession(
            user.getId(), algoResume, algorithmPosition, LocalDateTime.of(2026, 4, 18, 15, 30),
            devFixtureCatalog.algorithmScore(),
            devFixtureCatalog.algorithmStorylineWeaknesses(),
            devFixtureCatalog.algorithmScript()
        );
    }

    private void createOngoingSession(Long userId, Resume resume, PositionTemplate position) {
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 23, 14, 0);
        InterviewSession session = buildSession(userId, resume, position, STATUS_ONGOING, createdAt, null);

        insertStage(session.getId(), "warmup", createdAt, createdAt.plusMinutes(12));
        insertStage(session.getId(), "technical", createdAt.plusMinutes(12), null);

        insertMessage(session.getId(), ROLE_SYSTEM, position.getSystemPrompt(), 0, createdAt);
        insertMessage(session.getId(), ROLE_ASSISTANT, resolveScriptedReply("warmup", 0), 1, createdAt.plusMinutes(1));
        insertMessage(
            session.getId(),
            ROLE_USER,
            devFixtureCatalog.javaOngoingUserAnswer(),
            2,
            createdAt.plusMinutes(4)
        );
        insertMessage(session.getId(), ROLE_SYSTEM, TECHNICAL_STAGE_PROMPT, 3, createdAt.plusMinutes(12));
        insertMessage(session.getId(), ROLE_ASSISTANT, resolveScriptedReply("technical", 0), 4, createdAt.plusMinutes(13));
    }

    private void createFinishedSession(
        Long userId, Resume resume, PositionTemplate position, LocalDateTime createdAt,
        DevFixtureCatalog.ScoreSeed score, List<DevFixtureCatalog.WeaknessSeed> weaknesses, List<DevFixtureCatalog.QnaPair> script
    ) {
        InterviewSession session = buildSession(userId, resume, position, STATUS_FINISHED, createdAt, resolveReport(position.getName()));

        insertStage(session.getId(), "warmup", createdAt, createdAt.plusMinutes(8));
        insertStage(session.getId(), "technical", createdAt.plusMinutes(8), createdAt.plusMinutes(18));
        insertStage(session.getId(), "deep_dive", createdAt.plusMinutes(18), createdAt.plusMinutes(28));
        insertStage(session.getId(), "closing", createdAt.plusMinutes(28), createdAt.plusMinutes(34));

        int seq = 0;
        insertMessage(session.getId(), ROLE_SYSTEM, position.getSystemPrompt(), seq++, createdAt);
        
        for (int i = 0; i < script.size(); i++) {
            DevFixtureCatalog.QnaPair pair = script.get(i);
            if (pair.systemPrompt() != null) {
                insertMessage(session.getId(), ROLE_SYSTEM, pair.systemPrompt(), seq++, createdAt.plusMinutes(1 + i * 2));
            }
            insertMessage(session.getId(), ROLE_ASSISTANT, pair.aiQuestion(), seq++, createdAt.plusMinutes(2 + i * 2));
            insertMessage(session.getId(), ROLE_USER, pair.userAnswer(), seq++, createdAt.plusMinutes(3 + i * 2));
        }

        ScoreHistory history = new ScoreHistory();
        history.setUserId(userId);
        history.setSessionId(session.getId());
        history.setTechnicalScore(score.technical());
        history.setExpressionScore(score.expression());
        history.setLogicScore(score.logic());
        history.setCreatedAt(createdAt.plusMinutes(35));
        scoreHistoryMapper.insert(history);

        for (int index = 0; index < weaknesses.size(); index++) {
            DevFixtureCatalog.WeaknessSeed weakness = weaknesses.get(index);
            userWeaknessMapper.insert(
                buildWeakness(userId, session.getId(), weakness.category(), weakness.description(), createdAt.plusMinutes(36 + index))
            );
        }
    }

    private InterviewSession buildSession(
        Long userId,
        Resume resume,
        PositionTemplate position,
        String status,
        LocalDateTime createdAt,
        String report
    ) {
        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setResumeId(resume.getId());
        session.setPositionId(position.getId());
        session.setTargetPosition(position.getName());
        session.setLlmProvider("deepseek");
        session.setLlmModel("deepseek-v4-pro");
        session.setStatus(status);
        session.setSummaryReport(report);
        session.setCreatedAt(createdAt);
        interviewSessionMapper.insert(session);
        return session;
    }

    private Resume insertResume(Long userId, String fileName, LocalDateTime createdAt) {
        DevFixtureCatalog.DevResumeFixture fixture = devFixtureCatalog.resume(fileName);

        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setFileName(fileName);
        resume.setRawText(fixture.rawText());
        resume.setParsedSkills(writeJson(fixture.skills()));
        resume.setParsedProjects(writeJson(fixture.projects()));
        resume.setCreatedAt(createdAt);
        resumeMapper.insert(resume);
        return resume;
    }

    private void insertStage(Long sessionId, String stageName, LocalDateTime startedAt, LocalDateTime endedAt) {
        InterviewStage stage = new InterviewStage();
        stage.setSessionId(sessionId);
        stage.setStageName(stageName);
        stage.setStartedAt(startedAt);
        stage.setEndedAt(endedAt);
        interviewStageMapper.insert(stage);
    }

    private void insertMessage(Long sessionId, String role, String content, int seqNum, LocalDateTime createdAt) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setSeqNum(seqNum);
        message.setCreatedAt(createdAt);
        interviewMessageMapper.insert(message);
    }

    private UserWeakness buildWeakness(Long userId, Long sessionId, String category, String description, LocalDateTime createdAt) {
        UserWeakness weakness = new UserWeakness();
        weakness.setUserId(userId);
        weakness.setSessionId(sessionId);
        weakness.setCategory(category);
        weakness.setDescription(description);
        weakness.setCreatedAt(createdAt);
        return weakness;
    }

    private PositionTemplate requireDevFixturePosition(String positionName) {
        PositionTemplate position = positionTemplateMapper.selectOne(new LambdaQueryWrapper<PositionTemplate>()
            .eq(PositionTemplate::getName, positionName)
            .last("LIMIT 1"));

        if (position == null) {
            throw BusinessException.badRequest("dev fixture 岗位模板不存在: " + positionName);
        }

        return position;
    }

    private User ensureDevTestUser() {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
            .eq(User::getUsername, DEV_TEST_USERNAME)
            .last("LIMIT 1"));
        if (user != null) {
            return user;
        }

        User devTestUser = new User();
        devTestUser.setUsername(DEV_TEST_USERNAME);
        devTestUser.setPassword(DEV_TEST_PASSWORD_HASH);
        devTestUser.setEmail(DEV_TEST_EMAIL);
        devTestUser.setCreatedAt(LocalDateTime.now());
        userMapper.insert(devTestUser);
        return devTestUser;
    }

    private void assertEnabled() {
        if (!isEnabled()) {
            throw BusinessException.badRequest("dev fixture 未启用");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException exception) {
            throw BusinessException.badRequest("dev fixture 夹具序列化失败");
        }
    }

}
