package com.interview.interview.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.interview.domain.InterviewSession;
import com.interview.interview.infrastructure.persistence.InterviewSessionMapper;
import com.interview.resume.application.port.ResumeUsagePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MybatisResumeUsageAdapter implements ResumeUsagePort {

    private final InterviewSessionMapper interviewSessionMapper;

    @Override
    public Map<Long, Long> countSessions(List<Long> resumeIds) {
        if (resumeIds == null || resumeIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = interviewSessionMapper.selectMaps(
            new QueryWrapper<InterviewSession>()
                .select("resume_id AS resumeId", "COUNT(*) AS cnt")
                .in("resume_id", resumeIds)
                .groupBy("resume_id")
        );
        Map<Long, Long> counts = new HashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                if (row.get("resumeId") instanceof Number id && row.get("cnt") instanceof Number count) {
                    counts.put(id.longValue(), count.longValue());
                }
            }
        }
        return counts;
    }

    @Override
    public boolean isUsed(Long resumeId) {
        return interviewSessionMapper.selectCount(new LambdaQueryWrapper<InterviewSession>()
            .eq(InterviewSession::getResumeId, resumeId)) > 0;
    }
}
