package com.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.BusinessException;
import com.interview.config.DemoProperties;
import com.interview.dto.ResumeProjectDto;
import com.interview.dto.ResumeUploadResponse;
import com.interview.entity.InterviewSession;
import com.interview.entity.Resume;
import com.interview.entity.ScoreHistory;
import com.interview.entity.User;
import com.interview.entity.UserWeakness;
import com.interview.mapper.InterviewMessageMapper;
import com.interview.mapper.InterviewSessionMapper;
import com.interview.mapper.InterviewStageMapper;
import com.interview.mapper.ResumeMapper;
import com.interview.mapper.ScoreHistoryMapper;
import com.interview.mapper.UserMapper;
import com.interview.mapper.UserWeaknessMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class DemoModeService {

    private static final String DEMO_USERNAME = "demo";
    private static final String DEMO_EMAIL = "demo@example.com";
    private static final String DEMO_PASSWORD_HASH = "$2a$10$cwL4a7RrPcB895DFoO2MyuhK6QGDWhU0fScSmKj/LuBDtIzmL2zL2";
    private static final String DEMO_API_KEY_PLACEHOLDER = "demo-key-placeholder";

    private final DemoProperties demoProperties;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;
    private final ResumeMapper resumeMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewMessageMapper interviewMessageMapper;
    private final InterviewStageMapper interviewStageMapper;
    private final ScoreHistoryMapper scoreHistoryMapper;
    private final UserWeaknessMapper userWeaknessMapper;

    public boolean isEnabled() {
        return demoProperties.isEnabled();
    }

    @Transactional(rollbackFor = Exception.class)
    public void reset() {
        assertEnabled();

        User user = ensureDemoUser();
        List<Long> sessionIds = interviewSessionMapper.selectList(new LambdaQueryWrapper<InterviewSession>()
                .eq(InterviewSession::getUserId, user.getId()))
            .stream()
            .map(InterviewSession::getId)
            .toList();

        if (!sessionIds.isEmpty()) {
            interviewMessageMapper.delete(new LambdaQueryWrapper<com.interview.entity.InterviewMessage>()
                .in(com.interview.entity.InterviewMessage::getSessionId, sessionIds));
            interviewStageMapper.delete(new LambdaQueryWrapper<com.interview.entity.InterviewStage>()
                .in(com.interview.entity.InterviewStage::getSessionId, sessionIds));
            scoreHistoryMapper.delete(new LambdaQueryWrapper<ScoreHistory>()
                .in(ScoreHistory::getSessionId, sessionIds));
            userWeaknessMapper.delete(new LambdaQueryWrapper<UserWeakness>()
                .in(UserWeakness::getSessionId, sessionIds));
            interviewSessionMapper.delete(new LambdaQueryWrapper<InterviewSession>()
                .in(InterviewSession::getId, sessionIds));
        }

        resumeMapper.delete(new LambdaQueryWrapper<Resume>()
            .eq(Resume::getUserId, user.getId()));

        DemoLlmConfigFixture fixture = readJson("demo/llm-config.json", new TypeReference<>() {
        });
        user.setEmail(DEMO_EMAIL);
        user.setLlmProvider(fixture.providerKey());
        user.setLlmModel(fixture.model());
        user.setLlmApiKeyEncrypted(DEMO_API_KEY_PLACEHOLDER);
        userMapper.updateById(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public ResumeUploadResponse createDemoResume(Long userId, String fileName) {
        assertEnabled();
        DemoResumeFixture fixture = readJson("demo/resume-template.json", new TypeReference<>() {
        });

        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setFileName(fileName);
        resume.setRawText(fixture.rawText());
        resume.setParsedSkills(writeJson(fixture.skills()));
        resume.setParsedProjects(writeJson(fixture.projects()));
        resumeMapper.insert(resume);

        return new ResumeUploadResponse(resume.getId(), fixture.skills(), fixture.projects());
    }

    public String resolveScriptedReply(String stageName, int replyIndex) {
        assertEnabled();
        DemoStageRepliesFixture fixture = readJson("demo/stage-replies.json", new TypeReference<>() {
        });
        List<String> replies = fixture.replies().get(stageName);
        if (replies == null || replies.isEmpty()) {
            throw BusinessException.badRequest("演示阶段回复未配置");
        }
        int index = Math.max(0, Math.min(replyIndex, replies.size() - 1));
        return replies.get(index);
    }

    public void streamReply(String reply, Consumer<String> consumer) {
        assertEnabled();
        if (reply == null || reply.isBlank()) {
            return;
        }
        int chunkSize = Math.max(1, demoProperties.getChunkSize());
        int delayMs = Math.max(0, demoProperties.getStreamDelayMs());
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
        String template = readText("demo/report-template.md");
        return template.replace("{{position}}", targetPosition == null ? "目标岗位" : targetPosition);
    }

    public List<UserWeakness> buildWeaknesses(Long userId, Long sessionId) {
        assertEnabled();
        List<DemoWeaknessFixture> items = readJson("demo/weaknesses.json", new TypeReference<>() {
        });
        return items.stream()
            .map(item -> {
                UserWeakness weakness = new UserWeakness();
                weakness.setUserId(userId);
                weakness.setSessionId(sessionId);
                weakness.setCategory(item.category());
                weakness.setDescription(item.description());
                return weakness;
            })
            .toList();
    }

    public String maskApiKey(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return null;
        }
        return "****demo";
    }

    public String nextStoredApiKey(String requestApiKey, String currentStoredApiKey) {
        if (requestApiKey == null) {
            return currentStoredApiKey;
        }
        return requestApiKey.isBlank() ? null : DEMO_API_KEY_PLACEHOLDER;
    }

    private User ensureDemoUser() {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
            .eq(User::getUsername, DEMO_USERNAME)
            .last("LIMIT 1"));
        if (user != null) {
            return user;
        }

        User demoUser = new User();
        demoUser.setUsername(DEMO_USERNAME);
        demoUser.setPassword(DEMO_PASSWORD_HASH);
        demoUser.setEmail(DEMO_EMAIL);
        demoUser.setCreatedAt(LocalDateTime.now());
        userMapper.insert(demoUser);
        return demoUser;
    }

    private void assertEnabled() {
        if (!isEnabled()) {
            throw BusinessException.badRequest("演示模式未启用");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException exception) {
            throw BusinessException.badRequest("演示夹具序列化失败");
        }
    }

    private String readText(String path) {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw BusinessException.badRequest("读取演示夹具失败: " + path);
        }
    }

    private <T> T readJson(String path, TypeReference<T> typeReference) {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(inputStream, typeReference);
        } catch (IOException exception) {
            throw BusinessException.badRequest("读取演示夹具失败: " + path);
        }
    }

    private record DemoLlmConfigFixture(String providerKey, String model, String apiKeyMasked) {
    }

    private record DemoResumeFixture(
        List<String> skills,
        List<ResumeProjectDto> projects,
        String rawText
    ) {
    }

    private record DemoStageRepliesFixture(Map<String, List<String>> replies) {
    }

    private record DemoWeaknessFixture(String category, String description) {
    }
}
