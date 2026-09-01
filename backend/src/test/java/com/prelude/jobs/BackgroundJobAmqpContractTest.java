package com.prelude.jobs;

import com.prelude.artifact.application.GenerateInterviewReport;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
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

    @MockitoBean
    private GenerateInterviewReport generateInterviewReport;

    @BeforeEach
    void prepare() {
        listenerRegistry.getListenerContainers().forEach(container -> container.stop());
        rabbitAdmin.purgeQueue(RabbitMqConfig.QUEUE, true);
        rabbitAdmin.purgeQueue(RabbitMqConfig.DLQ, true);
        when(generateInterviewReport.execute(anyLong(), anyLong()))
            .thenReturn(GenerateInterviewReport.Outcome.GENERATED);
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
