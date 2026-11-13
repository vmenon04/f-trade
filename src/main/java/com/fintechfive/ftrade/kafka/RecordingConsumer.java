package com.fintechfive.ftrade.kafka;

import com.fintechfive.ftrade.dto.TradeRecordedDTO;
import com.fintechfive.ftrade.dto.TradeValidatedDTO;
import com.fintechfive.ftrade.service.TradeEventProducer;
import com.fintechfive.ftrade.service.TradeRecordingService;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes validated trades, records them as PENDING and publishes them to trade.recorded.
 * Runs unless trade.stage.recording.enabled=false, so a container can run only some of the stages.
 */
@ConditionalOnProperty(name = "trade.stage.recording.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class RecordingConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecordingConsumer.class);

    private final TradeRecordingService tradeRecordingService;
    private final TradeEventProducer tradeEventProducer;

    public RecordingConsumer(TradeRecordingService tradeRecordingService, TradeEventProducer tradeEventProducer) {
        this.tradeRecordingService = tradeRecordingService;
        this.tradeEventProducer = tradeEventProducer;
    }

    // record() has committed by the time it returns, so the trade is in the database before it's published.
    // If the publish fails the message is redelivered, and record() returns the same trade rather than a new one.
    @KafkaListener(topics = KafkaTopics.TRADE_VALIDATED, groupId = "recording-service")
    public void onTradeValidated(TradeValidatedDTO trade) throws InterruptedException, ExecutionException {
        LOGGER.info("Received validated trade event. Trade ID: {}", trade.externalTradeId());

        TradeRecordedDTO recorded = tradeRecordingService.recordTrade(trade);
        tradeEventProducer.publishTradeRecorded(recorded).get();
        LOGGER.info("Published trade recorded event to {}", KafkaTopics.TRADE_RECORDED);
    }
}
