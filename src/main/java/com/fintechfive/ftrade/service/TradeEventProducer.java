package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.dto.TradeAcceptedDTO;
import com.fintechfive.ftrade.dto.TradeFinishedDTO;
import com.fintechfive.ftrade.dto.TradeRecordedDTO;
import com.fintechfive.ftrade.dto.TradeRejectedDTO;
import com.fintechfive.ftrade.dto.TradeSubmittedDTO;
import com.fintechfive.ftrade.dto.TradeValidatedDTO;
import com.fintechfive.ftrade.kafka.KafkaTopics;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

@Service
public class TradeEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TradeEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, Object>> publishTradeSubmitted(TradeSubmittedDTO event) {
        return kafkaTemplate.send(KafkaTopics.TRADE_SUBMITTED, event.accountId().toString(), event);
    }

    public CompletableFuture<SendResult<String, Object>> publishTradeValidated(TradeValidatedDTO event) {
        return kafkaTemplate.send(KafkaTopics.TRADE_VALIDATED, event.accountId().toString(), event);
    }

    public CompletableFuture<SendResult<String, Object>> publishTradeRecorded(TradeRecordedDTO event) {
        return kafkaTemplate.send(KafkaTopics.TRADE_RECORDED, event.accountId().toString(), event);
    }

    public CompletableFuture<SendResult<String, Object>> publishTradeAccepted(TradeAcceptedDTO event) {
        return kafkaTemplate.send(KafkaTopics.TRADE_ACCEPTED, event.tradeId().toString(), event);
    }

    public CompletableFuture<SendResult<String, Object>> publishTradeFinished(TradeFinishedDTO event) {
        return kafkaTemplate.send(KafkaTopics.TRADE_FINISHED, event.tradeId().toString(), event);
    }

    public CompletableFuture<SendResult<String, Object>> publishTradeRejected(TradeRejectedDTO event) {
        return kafkaTemplate.send(KafkaTopics.TRADE_REJECTED, event.externalTradeId().toString(), event);
    }
}