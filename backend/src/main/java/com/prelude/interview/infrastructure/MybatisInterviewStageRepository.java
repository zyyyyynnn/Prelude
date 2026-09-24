package com.prelude.interview.infrastructure;

import com.prelude.interview.application.repository.InterviewStageRepository;
import com.prelude.interview.domain.InterviewStage;
import com.prelude.interview.infrastructure.persistence.InterviewStageEntity;
import com.prelude.interview.infrastructure.persistence.InterviewStageMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Adapts the stage port to the row table, so the port never carries the row type. */
@Repository
@RequiredArgsConstructor
public class MybatisInterviewStageRepository implements InterviewStageRepository {

    private final InterviewStageMapper stageMapper;

    @Override
    public int add(InterviewStage stage) {
        InterviewStageEntity entity = InterviewStageEntity.of(stage);
        int rows = stageMapper.insert(entity);
        stage.setId(entity.getId());
        return rows;
    }

    @Override
    public int update(InterviewStage stage) {
        return stageMapper.updateById(InterviewStageEntity.of(stage));
    }

    @Override
    public InterviewStage findCurrent(Long sessionId) {
        InterviewStageEntity entity = stageMapper.findCurrent(sessionId);
        return entity == null ? null : entity.toDomain();
    }

    @Override
    public InterviewStage findLatest(Long sessionId) {
        InterviewStageEntity entity = stageMapper.findLatest(sessionId);
        return entity == null ? null : entity.toDomain();
    }

    @Override
    public List<InterviewStage> listBySession(Long sessionId) {
        return stageMapper.listBySession(sessionId).stream()
            .map(InterviewStageEntity::toDomain)
            .toList();
    }
}
