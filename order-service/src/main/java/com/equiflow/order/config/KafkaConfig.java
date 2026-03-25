package com.equiflow.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic orderPlacedTopic() {
        return TopicBuilder.name("equiflow.order.placed")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderFilledTopic() {
        return TopicBuilder.name("equiflow.order.filled")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderCancelledTopic() {
        return TopicBuilder.name("equiflow.order.cancelled")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic stopLossTriggeredTopic() {
        return TopicBuilder.name("equiflow.order.stop-loss.triggered")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
