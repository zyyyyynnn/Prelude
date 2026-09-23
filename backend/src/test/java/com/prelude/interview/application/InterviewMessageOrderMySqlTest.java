package com.prelude.interview.application;

import com.prelude.interview.domain.InterviewMessage;
import com.prelude.test.InterviewDataFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Message numbering against real MySQL.
 *
 * <p>Numbers used to be allocated under a lock that lived inside one JVM, which left the
 * database free to store the same number twice for one session. Both halves of the replacement
 * are asserted here: the unique key refuses the collision, and the service answers that refusal
 * by allocating again, so a set of concurrent appends ends up contiguous and unduplicated.
 */
@EnabledIfEnvironmentVariable(named = "PRELUDE_MYSQL_SMOKE", matches = "true")
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class InterviewMessageOrderMySqlTest {

    @Autowired
    private InterviewMessageService messages;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ExecutorService executor = Executors.newFixedThreadPool(6);

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void theSchemaRefusesASecondMessageWithTheSameNumberInOneSession() {
        long sessionId = InterviewDataFixtures.sessionInProgress(jdbcTemplate, "message-order-key");
        append(sessionId, 0, "first");

        assertThatThrownBy(() -> append(sessionId, 0, "collision"))
            .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void concurrentAppendsLeaveOneSessionWithNoGapAndNoDuplicate() throws Exception {
        long sessionId = InterviewDataFixtures.sessionInProgress(jdbcTemplate, "message-order-race");
        CyclicBarrier start = new CyclicBarrier(6);

        List<Future<Integer>> writes = new ArrayList<>();
        for (int writer = 0; writer < 6; writer++) {
            int index = writer;
            writes.add(executor.submit(() -> {
                start.await();
                return messages.insertMessage(sessionId, "user", "answer-" + index).getSeqNum();
            }));
        }

        List<Integer> allocated = new ArrayList<>();
        for (Future<Integer> write : writes) {
            allocated.add(write.get());
        }

        assertThat(allocated).containsExactlyInAnyOrder(0, 1, 2, 3, 4, 5);
        assertThat(jdbcTemplate.queryForList(
            "SELECT seq_num FROM interview_message WHERE session_id = ? ORDER BY seq_num",
            Integer.class, sessionId))
            .containsExactly(0, 1, 2, 3, 4, 5);
    }

    private void append(long sessionId, int seqNum, String content) {
        InterviewDataFixtures.insert(jdbcTemplate,
            "INSERT INTO interview_message (session_id, role, content, seq_num) VALUES (?, 'user', ?, ?)",
            sessionId, content, seqNum);
    }
}
