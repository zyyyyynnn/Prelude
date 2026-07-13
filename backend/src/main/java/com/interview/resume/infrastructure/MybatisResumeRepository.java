package com.interview.resume.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.shared.api.BusinessException;
import com.interview.resume.infrastructure.persistence.Resume;
import com.interview.resume.infrastructure.persistence.ResumeMapper;
import com.interview.resume.application.port.ResumeRepository;
import com.interview.resume.application.port.ResumeUsagePort;
import com.interview.resume.domain.ResumeDocument;
import com.interview.resume.domain.ResumeDocumentProjection;
import com.interview.resume.domain.ResumeDocumentProjector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Repository
public class MybatisResumeRepository implements ResumeRepository {

    private final ResumeMapper resumeMapper;
    private final ResumeUsagePort resumeUsagePort;
    private final ObjectMapper objectMapper;
    private final ResumeDocumentProjector projector = new ResumeDocumentProjector();

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
        Resume row = writeRow(draft.document(), 1, draft.sourceType(), draft.rawText());
        row.setUserId(draft.userId());
        row.setFileName(draft.fileName());
        resumeMapper.insert(row);
        return toStored(row);
    }

    @Override
    public Optional<StoredResume> findById(Long resumeId) {
        return Optional.ofNullable(resumeMapper.selectById(resumeId)).map(this::toStored);
    }

    @Override
    public List<ResumeListItem> listByOwner(Long userId) {
        List<Resume> resumes = resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
            .eq(Resume::getUserId, userId)
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
    public boolean updateDocument(
        Long resumeId,
        Long userId,
        int expectedVersion,
        ResumeDocument document,
        String sourceType
    ) {
        Resume row = writeRow(document, expectedVersion + 1, sourceType, null);
        row.setId(resumeId);
        row.setUserId(null);
        return resumeMapper.update(row, new LambdaUpdateWrapper<Resume>()
            .eq(Resume::getId, resumeId)
            .eq(Resume::getUserId, userId)
            .eq(Resume::getDocumentVersion, expectedVersion)) == 1;
    }

    @Override
    public void delete(Long resumeId) {
        resumeMapper.deleteById(resumeId);
    }

    @Override
    public long countWithoutDocument() {
        return resumeMapper.selectCount(missingDocumentQuery());
    }

    @Override
    public List<LegacyResume> findWithoutDocumentAfter(long afterId, int batchSize) {
        int limit = Math.max(1, Math.min(batchSize, 1_000));
        List<Resume> rows = resumeMapper.selectList(missingDocumentQuery()
            .gt(Resume::getId, afterId)
            .orderByAsc(Resume::getId)
            .last("LIMIT " + limit));
        if (rows == null) {
            return List.of();
        }
        return rows.stream().map(row -> new LegacyResume(
            row.getId(),
            row.getRawText(),
            readJson(row.getParsedSkills(), new TypeReference<List<String>>() {}, List.of()),
            readJson(
                row.getParsedProjects(),
                new TypeReference<List<ResumeRepository.LegacyProject>>() {},
                List.of()
            )
        )).toList();
    }

    @Override
    public boolean backfillDocument(Long resumeId, ResumeDocument document) {
        Resume row = writeRow(document, 1, "pdf_import", null);
        row.setRawText(null);
        row.setParsedSkills(null);
        row.setParsedProjects(null);
        return resumeMapper.update(row, new LambdaUpdateWrapper<Resume>()
            .eq(Resume::getId, resumeId)
            .and(wrapper -> wrapper.isNull(Resume::getDocumentJson).or().eq(Resume::getDocumentJson, ""))) == 1;
    }

    private Resume writeRow(ResumeDocument document, int version, String sourceType, String rawText) {
        ResumeDocumentProjection projection = projector.project(document);
        Resume row = new Resume();
        row.setDocumentJson(writeJson(document));
        row.setDocumentVersion(version);
        row.setSourceType(sourceType);
        row.setPlainTextProjection(projection.plainText());
        row.setRawText(rawText == null ? projection.plainText() : rawText);
        row.setParsedSkills(writeJson(projection.skills()));
        row.setParsedProjects(writeJson(document.projects().stream()
            .map(project -> new LegacyProjectRow(project.name(), legacyDescription(project)))
            .toList()));
        return row;
    }

    private StoredResume toStored(Resume row) {
        return new StoredResume(
            row.getId(),
            row.getUserId(),
            row.getFileName(),
            row.getRawText(),
            readDocument(row),
            row.getDocumentVersion() == null ? 0 : row.getDocumentVersion(),
            row.getSourceType(),
            row.getCreatedAt()
        );
    }

    private ResumeDocument readDocument(Resume row) {
        if (row.getDocumentJson() == null || row.getDocumentJson().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(row.getDocumentJson(), ResumeDocument.class);
        } catch (Exception exception) {
            log.warn("Resume {} contains invalid document JSON", row.getId());
            log.debug("Invalid resume document JSON", exception);
            return null;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw BusinessException.badRequest("简历文档序列化失败");
        }
    }

    private <T> T readJson(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            log.warn("Ignoring malformed legacy resume JSON during migration");
            log.debug("Malformed legacy resume JSON", exception);
            return fallback;
        }
    }

    private LambdaQueryWrapper<Resume> missingDocumentQuery() {
        return new LambdaQueryWrapper<Resume>()
            .and(wrapper -> wrapper.isNull(Resume::getDocumentJson).or().eq(Resume::getDocumentJson, ""));
    }

    private String legacyDescription(ResumeDocument.Project project) {
        List<String> values = new java.util.ArrayList<>(project.bullets());
        if (project.outcome() != null && !project.outcome().isBlank()) {
            values.add(project.outcome());
        }
        return String.join("\n", values);
    }

    private record LegacyProjectRow(String name, String description) {
    }
}
