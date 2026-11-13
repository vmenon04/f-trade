package com.fintechfive.ftrade.kafka;

import com.fintechfive.ftrade.dto.TradeRejectedDTO;
import com.fintechfive.ftrade.dto.TradeSubmittedDTO;
import com.fintechfive.ftrade.enums.RejectionCode;
import com.fintechfive.ftrade.service.TradeEventProducer;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.support.KafkaUtils;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Gets each message the Kafka error handler gives up on (after its retries) and logs it. A trade that failed
 * validation never reaches the database, so it is also published to trade.rejected for the trade tracker. Only a
 * failure in validation's consumer group counts: the tracker reads trade.submitted too. Never throws: that would
 * have the error handler try the message again.
 */
@Component
public class TradeRejectionRecoverer implements ConsumerRecordRecoverer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TradeRejectionRecoverer.class);

    private final TradeEventProducer tradeEventProducer;
    private final JsonMapper json;

    public TradeRejectionRecoverer(TradeEventProducer tradeEventProducer, JsonMapper json) {
        this.tradeEventProducer = tradeEventProducer;
        this.json = json;
    }

    @Override
    public void accept(ConsumerRecord<?, ?> failed, Exception failure) {
        LOGGER.error("Skipping {}-{}@{} after it failed", failed.topic(), failed.partition(), failed.offset(), failure);
        if (!KafkaTopics.TRADE_SUBMITTED.equals(failed.topic())
                || !ValidationConsumer.GROUP_ID.equals(KafkaUtils.getConsumerGroupId())) {
            return;
        }
        UUID externalTradeId = externalTradeId(failed);
        if (externalTradeId == null) {
            LOGGER.warn("Can't tell which trade {}-{}@{} was, so it isn't published as rejected", failed.topic(),
                    failed.partition(), failed.offset());
            return;
        }
        TradeRejectedDTO rejected = new TradeRejectedDTO(externalTradeId, RejectionCode.of(failure));
        try {
            tradeEventProducer.publishTradeRejected(rejected).whenComplete((sent, error) -> {
                if (error != null) {
                    LOGGER.warn("Couldn't publish trade {} as rejected", externalTradeId, error);
                }
            });
        } catch (RuntimeException e) {
            LOGGER.warn("Couldn't publish trade {} as rejected", externalTradeId, e);
        }
    }

    // the consumers read each message as a JSON string
    private UUID externalTradeId(ConsumerRecord<?, ?> failed) {
        if (!(failed.value() instanceof String value)) {
            return null;
        }
        try {
            return json.readValue(value, TradeSubmittedDTO.class).externalTradeId();
        } catch (JacksonException e) {
            return null;
        }
    }
}
