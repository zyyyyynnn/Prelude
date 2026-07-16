package com.interview.platform.job.infrastructure;

import com.interview.platform.job.ReportJobChannel;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    public DirectExchange reportExchange() {
        return new DirectExchange(ReportJobChannel.EXCHANGE, true, false);
    }

    @Bean
    public Queue reportQueue() {
        return new Queue(ReportJobChannel.QUEUE, true);
    }

    @Bean
    public Binding reportBinding(Queue reportQueue, DirectExchange reportExchange) {
        return BindingBuilder.bind(reportQueue)
            .to(reportExchange)
            .with(ReportJobChannel.ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
        ConnectionFactory connectionFactory,
        Jackson2JsonMessageConverter messageConverter
    ) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
