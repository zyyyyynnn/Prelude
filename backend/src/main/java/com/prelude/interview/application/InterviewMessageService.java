package com.prelude.interview.application;

import com.prelude.interview.application.repository.InterviewMessageRepository;
import com.prelude.interview.application.repository.InterviewSessionRepository;
import com.prelude.interview.domain.InterviewMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Appends messages to a session, numbering them from 0 within that session. */
@Service
@RequiredArgsConstructor
public class InterviewMessageService {

    private final InterviewMessageRepository interviewMessageRepository;
    private final InterviewSessionRepository interviewSessionRepository;

    /**
     * The number is allocated under the session row's write lock, so whoever appends next reads
     * what the previous append committed rather than racing it, and the unique key on
     * {@code (session_id, seq_num)} refuses the alternative outloud. A monitor held in one JVM
     * could promise neither: another process walks past it, and a bounded in-memory cache of
     * monitors can hand two writers different objects for the same session key.
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewMessage insertMessage(Long sessionId, String role, String content) {
        interviewSessionRepository.lockAppendOrder(sessionId);

        InterviewMessage message = new InterviewMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setSeqNum(nextSeqNum(sessionId));
        interviewMessageRepository.add(message);
        return message;
    }

    private int nextSeqNum(Long sessionId) {
        InterviewMessage latest = interviewMessageRepository.findLatest(sessionId);
        Integer seqNum = latest == null ? null : latest.getSeqNum();
        return seqNum == null ? 0 : seqNum + 1;
    }
}
