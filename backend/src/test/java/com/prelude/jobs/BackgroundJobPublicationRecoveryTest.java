package com.prelude.jobs;

import com.prelude.jobs.integration.BackgroundJobOperations;
import com.prelude.jobs.integration.BackgroundJobRequested;
import com.prelude.test.AccountFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitMessageOperations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@EnabledIfEnvironmentVariable(named = "PRELUDE_MYSQL_SMOKE", matches = "true")
@SpringBootTest(properties = {
    "prelude.jobs.scheduling-enabled=false",
    "prelude.jobs.report.consumer-enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BackgroundJobPublicationRecoveryTest {

    private static final String JOB_TYPE = "test.publication";

    @Autowired
    private BackgroundJobOperations jobs;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IncompleteEventPublications incompleteEventPublications;

    @MockitoBean
    private RabbitMessageOperations rabbitMessageOperations;

    /**
     * Resubmission is driven through the predicate overload rather than the global sweep, and
     * no rows are deleted to make room: the shared test database holds other tests' incomplete
     * publications, and handing those to this test's mock would turn its call count into noise
     * while a blanket cleanup would destroy the evidence those tests assert on.
     */
    @Test
    void failedExternalizationRemainsDurableAndTheSamePublicationCanBeResubmitted() {
        doThrow(new AmqpException("broker unavailable"))
            .doNothing()
            .when(rabbitMessageOperations)
            .convertAndSend(anyString(), anyString(), any(), anyMap());

        long accountId = createAccount();
        var job = jobs.request(new BackgroundJobOperations.BackgroundJobRequest(
            JOB_TYPE,
            accountId,
            401L,
            JOB_TYPE + ":recovery:" + System.nanoTime(),
            "{}"
        ));

        assertThat(incompletePublicationRows(job.jobId())).isEqualTo(1);

        incompleteEventPublications.resubmitIncompletePublications(
            publication -> publication.getEvent() instanceof BackgroundJobRequested requested
                && requested.jobId().equals(job.jobId()));
        awaitCompletedPublication(job.jobId());

        verify(rabbitMessageOperations, times(2))
            .convertAndSend(anyString(), anyString(), any(), anyMap());
        assertThat(incompletePublicationRows(job.jobId())).isZero();
        assertThat(completedPublicationRows(job.jobId())).isEqualTo(1);
    }

    private void awaitCompletedPublication(String jobId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (completedPublicationRows(jobId) == 0 && System.nanoTime() < deadline) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for publication completion", exception);
            }
        }
        assertThat(completedPublicationRows(jobId)).isEqualTo(1);
    }

    private long createAccount() {
        return AccountFixtures.create(jdbcTemplate, "publication-recovery");
    }

    private long incompletePublicationRows(String jobId) {
        return publicationRows(jobId, "COMPLETION_DATE IS NULL");
    }

    private long completedPublicationRows(String jobId) {
        return publicationRows(jobId, "COMPLETION_DATE IS NOT NULL");
    }

    private long publicationRows(String jobId, String completionPredicate) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM EVENT_PUBLICATION WHERE SERIALIZED_EVENT LIKE ? AND " + completionPredicate,
            Long.class,
            "%" + jobId + "%"
        );
        return count == null ? 0 : count;
    }
}
