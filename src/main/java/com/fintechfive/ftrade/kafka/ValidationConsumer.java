package com.fintechfive.ftrade.kafka;

import com.fintechfive.ftrade.dto.TradeSubmittedDTO;
import com.fintechfive.ftrade.dto.TradeValidatedDTO;
import com.fintechfive.ftrade.service.TradeEventProducer;
import com.fintechfive.ftrade.service.ValidationService;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes submitted trades, validates them and publishes the valid ones to trade.validated.
 * Runs unless trade.stage.validation.enabled=false, so a container can run only some of the stages.
 */
@ConditionalOnProperty(name = "trade.stage.validation.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class ValidationConsumer {

    public static final String GROUP_ID = "validation-service";

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidationConsumer.class);

    private final ValidationService validationService;
    private final TradeEventProducer tradeEventProducer;

    public ValidationConsumer(ValidationService validationService, TradeEventProducer tradeEventProducer) {
        this.validationService = validationService;
        this.tradeEventProducer = tradeEventProducer;
    }

    // waits for the send so a failed publish fails the listener and the message is redelivered
    @KafkaListener(topics = KafkaTopics.TRADE_SUBMITTED, groupId = GROUP_ID)
    public void onTradeSubmitted(TradeSubmittedDTO trade) throws InterruptedException, ExecutionException {
        LOGGER.info("Received submitted trade event. Trade ID: {}", trade.externalTradeId());

        TradeValidatedDTO validated = validationService.validateSubmittedTrade(trade);
        tradeEventProducer.publishTradeValidated(validated).get();
    }
}
