package com.prelude.resume.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prelude.BusinessException;
import com.prelude.resume.infrastructure.persistence.Resume;
import com.prelude.resume.infrastructure.persistence.ResumeMapper;
import com.prelude.resume.application.port.ResumeRepository;
import com.prelude.resume.application.port.ResumeUsagePort;
import com.prelude.resume.domain.ResumeDocument;
import com.prelude.resume.domain.ResumeDocumentProjection;
import com.prelude.resume.domain.ResumeDocumentProjector;
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
            .map(project -> new ProjectRow(project.name(), projectDescription(project)))
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
        } catch (JacksonException exception) {
            throw BusinessException.badRequest("简历文档序列化失败");
        }
    }

    private String projectDescription(ResumeDocument.Project project) {
        List<String> values = new java.util.ArrayList<>(project.bullets());
        if (project.outcome() != null && !project.outcome().isBlank()) {
            values.add(project.outcome());
        }
        return String.join("\n", values);
    }

    private record ProjectRow(String name, String description) {
    }
}
