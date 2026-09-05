package com.prelude.jobs.infrastructure;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * RabbitMQ topology for background job dispatch. The routing target matches
 * the @Externalized annotation on BackgroundJobRequested; Spring Modulith
 * externalizes the publication after the business transaction commits. The
 * DLQ receives poison/unprocessable broker messages only — ordinary business
 * failures stay in background_job.
 */
@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "prelude.job.exchange";
    public static final String QUEUE = "prelude.job.report.queue";
    public static final String ROUTING_KEY = "report.generate";
    public static final String DLQ = QUEUE + ".dlq";

    @Bean
    public MessageConverter rabbitMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    @Bean
    public DirectExchange jobExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue reportJobQueue() {
        return QueueBuilder.durable(QUEUE)
            .withArgument("x-dead-letter-exchange", EXCHANGE)
            .withArgument("x-dead-letter-routing-key", DLQ)
            .build();
    }

    @Bean
    public Queue reportJobDeadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding reportJobBinding(Queue reportJobQueue, DirectExchange jobExchange) {
        return BindingBuilder.bind(reportJobQueue)
            .to(jobExchange)
            .with(ROUTING_KEY);
    }

    @Bean
    public Binding reportJobDeadLetterBinding(Queue reportJobDeadLetterQueue, DirectExchange jobExchange) {
        return BindingBuilder.bind(reportJobDeadLetterQueue)
            .to(jobExchange)
            .with(DLQ);
    }
}
