package com.fintechfive.ftrade.config;

import com.fintechfive.ftrade.kafka.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {

    // Kafka gives each partition to one consumer in a group, so this is the most containers (or listener threads)
    // a stage can use at once. A topic that already exists with fewer partitions is increased at startup.
    static final int PARTITIONS = 8;

    @Bean
    public NewTopic tradeSubmittedTopic() {
        return new NewTopic(KafkaTopics.TRADE_SUBMITTED, PARTITIONS, (short) 1);
    }

    @Bean
    public NewTopic tradeValidatedTopic() {
        return new NewTopic(KafkaTopics.TRADE_VALIDATED, PARTITIONS, (short) 1);
    }

    @Bean
    public NewTopic tradeRecordedTopic() {
        return new NewTopic(KafkaTopics.TRADE_RECORDED, PARTITIONS, (short) 1);
    }

    @Bean
    public NewTopic tradeAcceptedTopic() {
        return new NewTopic(KafkaTopics.TRADE_ACCEPTED, PARTITIONS, (short) 1);
    }

    @Bean
    public NewTopic tradeFinishedTopic() {
        return new NewTopic(KafkaTopics.TRADE_FINISHED, PARTITIONS, (short) 1);
    }

    @Bean
    public NewTopic tradeRejectedTopic() {
        return new NewTopic(KafkaTopics.TRADE_REJECTED, PARTITIONS, (short) 1);
    }
}
