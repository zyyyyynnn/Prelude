package com.prelude.jobs;

import com.prelude.artifact.application.GenerateInterviewReport;
import com.prelude.artifact.application.port.InsightRepository;
import com.prelude.interview.api.port.InterviewReportPort;
import com.prelude.interview.domain.InterviewSession;
import com.prelude.jobs.infrastructure.RabbitMqConfig;
import com.prelude.jobs.integration.BackgroundJobOperations;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobRef;
import com.prelude.jobs.integration.BackgroundJobOperations.BackgroundJobRequest;
import com.prelude.jobs.integration.BackgroundJobSucceeded;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "PRELUDE_MYSQL_SMOKE", matches = "true")
@SpringBootTest(properties = {
    "prelude.jobs.report.consumer-enabled=true",
    "spring.rabbitmq.listener.simple.auto-startup=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BackgroundJobAmqpContractTest {

    @Autowired
    private BackgroundJobOperations jobs;

    @Autowired
    private com.prelude.identity.AccountMapper accountMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private JobCompletionProbe completionProbe;

    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private GenerateInterviewReport generateInterviewReport;

    @MockitoBean
    private InterviewReportPort interviewReportPort;

    @MockitoBean
    private InsightRepository insightRepository;

    @BeforeEach
    void prepare() {
        listenerRegistry.getListenerContainers().forEach(container -> container.stop());
        rabbitAdmin.purgeQueue(RabbitMqConfig.QUEUE, true);
        rabbitAdmin.purgeQueue(RabbitMqConfig.DLQ, true);
        when(generateInterviewReport.execute(anyLong(), anyLong()))
            .thenReturn(new GenerateInterviewReport.GenerationResult(
                GenerateInterviewReport.Outcome.GENERATED, "{}", null, List.of()));
        when(interviewReportPort.completeReport(anyLong(), anyString())).thenReturn(true);
        when(interviewReportPort.findSession(anyLong())).thenAnswer(invocation -> {
            InterviewSession session = new InterviewSession();
            session.setId(invocation.getArgument(0));
            session.setStatus("finished");
            session.setSummaryReport("{}");
            return session;
        });
        listenerRegistry.getListenerContainers().forEach(container -> container.start());
    }

    @AfterEach
    void stopListeners() {
        listenerRegistry.getListenerContainers().forEach(container -> container.stop());
    }

    @Test
    void externalizedRequestIsDecodedToTheAuthoritativeJobIdAndExecuted() throws Exception {
        long accountId = createAccount();
        BackgroundJobRef ref = jobs.request(new BackgroundJobRequest(
            "report.generate", accountId, 301L,
            "report.generate:amqp:" + System.nanoTime(), "{}"));

        BackgroundJobSucceeded succeeded = completionProbe.await(ref.jobId());
        assertThat(succeeded.jobId()).isEqualTo(ref.jobId());
        assertThat(succeeded.subjectId()).isEqualTo(301L);
        assertThat(jobs.view(ref.jobId(), accountId).status()).isEqualTo("SUCCEEDED");
        awaitCompletedPublication(ref.jobId());
    }

    @Test
    void malformedJsonIsRejectedToTheDeadLetterQueue() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbitTemplate.send("", RabbitMqConfig.QUEUE,
            new Message("{not-json".getBytes(StandardCharsets.UTF_8), properties));

        Message deadLetter = rabbitTemplate.receive(RabbitMqConfig.DLQ, 5_000);
        assertThat(deadLetter).isNotNull();
        assertThat(new String(deadLetter.getBody(), StandardCharsets.UTF_8)).isEqualTo("{not-json");
    }

    private long createAccount() {
        com.prelude.identity.Account account = new com.prelude.identity.Account();
        account.setUsername("jobs-amqp-" + System.nanoTime());
        account.setRevision(0L);
        accountMapper.insert(account);
        return account.getId();
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
        assertThat(completedPublicationRows(jobId)).isGreaterThanOrEqualTo(1);
        assertThat(incompletePublicationRows(jobId)).isZero();
    }

    private long incompletePublicationRows(String jobId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM EVENT_PUBLICATION "
                + "WHERE EVENT_TYPE = 'com.prelude.jobs.integration.BackgroundJobSucceeded' "
                + "AND LISTENER_ID LIKE 'com.prelude.artifact.application.ReportJobLifecycle.onSucceeded%' "
                + "AND SERIALIZED_EVENT LIKE ? AND COMPLETION_DATE IS NULL",
            Long.class,
            "%" + jobId + "%"
        );
        return count == null ? 0 : count;
    }

    private long completedPublicationRows(String jobId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM EVENT_PUBLICATION "
                + "WHERE EVENT_TYPE = 'com.prelude.jobs.integration.BackgroundJobSucceeded' "
                + "AND LISTENER_ID LIKE 'com.prelude.artifact.application.ReportJobLifecycle.onSucceeded%' "
                + "AND SERIALIZED_EVENT LIKE ? AND COMPLETION_DATE IS NOT NULL",
            Long.class,
            "%" + jobId + "%"
        );
        return count == null ? 0 : count;
    }

    @TestConfiguration
    static class ProbeConfiguration {

        @Bean
        JobCompletionProbe jobCompletionProbe() {
            return new JobCompletionProbe();
        }
    }

    static class JobCompletionProbe {
        private final ConcurrentHashMap<String, CompletableFuture<BackgroundJobSucceeded>> completions =
            new ConcurrentHashMap<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void onSucceeded(BackgroundJobSucceeded event) {
            completions.computeIfAbsent(event.jobId(), ignored -> new CompletableFuture<>()).complete(event);
        }

        BackgroundJobSucceeded await(String jobId) throws Exception {
            return completions.computeIfAbsent(jobId, ignored -> new CompletableFuture<>())
                .get(10, TimeUnit.SECONDS);
        }
    }
}
