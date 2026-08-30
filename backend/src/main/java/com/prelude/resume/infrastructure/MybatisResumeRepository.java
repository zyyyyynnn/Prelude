package com.prelude.resume.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.prelude.BusinessException;
import com.prelude.resume.infrastructure.persistence.Resume;
import com.prelude.resume.infrastructure.persistence.ResumeMapper;
import com.prelude.resume.application.port.ResumeRepository;
import com.prelude.resume.application.port.ResumeUsagePort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class MybatisResumeRepository implements ResumeRepository {

    private final ResumeMapper resumeMapper;
    private final ResumeUsagePort resumeUsagePort;
    private final ObjectMapper objectMapper;

    public MybatisResumeRepository(
        ResumeMapper resumeMapper,
        ResumeUsagePort resumeUsagePort,
        ObjectMapper objectMapper
    ) {
        this.resumeMapper = resumeMapper;
        this.resumeUsagePort = resumeUsagePort;
        this.objectMapper = objectMapper;
    }

    @Override
    public StoredResume create(NewResume draft) {
        Resume row = new Resume();
        row.setAccountId(draft.accountId());
        row.setFileName(draft.fileName());
        row.setRawText(draft.rawText());
        row.setParsedSkills(writeJson(draft.parsedSkills()));
        row.setParsedProjects(writeJson(draft.parsedProjects()));
        resumeMapper.insert(row);
        return toStored(row);
    }

    @Override
    public Optional<StoredResume> findById(Long resumeId) {
        return Optional.ofNullable(resumeMapper.selectById(resumeId)).map(this::toStored);
    }

    @Override
    public List<ResumeListItem> listByOwner(Long accountId) {
        List<Resume> resumes = resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
            .eq(Resume::getAccountId, accountId)
            .orderByDesc(Resume::getCreatedAt));
        if (resumes == null || resumes.isEmpty()) {
            return List.of();
        }
        List<Long> ids = resumes.stream().map(Resume::getId).toList();
        Map<Long, Long> countByResume = resumeUsagePort.countSessions(ids);
        return resumes.stream()
            .map(resume -> new ResumeListItem(
                resume.getId(),
                resume.getFileName(),
                resume.getCreatedAt(),
                countByResume.getOrDefault(resume.getId(), 0L)
            ))
            .toList();
    }

    @Override
    public boolean hasInterviewSessions(Long resumeId) {
        return resumeUsagePort.isUsed(resumeId);
    }

    @Override
    public void delete(Long resumeId) {
        resumeMapper.deleteById(resumeId);
    }

    private StoredResume toStored(Resume row) {
        return new StoredResume(
            row.getId(),
            row.getAccountId(),
            row.getFileName(),
            row.getRawText(),
            readJson(row.getParsedSkills(), new TypeReference<List<String>>() {}),
            readJson(row.getParsedProjects(), new TypeReference<List<ParsedProject>>() {}),
            row.getCreatedAt()
        );
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("简历解析数据缺失");
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception exception) {
            throw new IllegalStateException("简历解析数据读取失败", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw BusinessException.badRequest("简历解析数据序列化失败");
        }
    }
}
