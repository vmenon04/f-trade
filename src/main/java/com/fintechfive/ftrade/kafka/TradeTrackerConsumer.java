package com.fintechfive.ftrade.kafka;

import com.fintechfive.ftrade.dto.TradeFinishedDTO;
import com.fintechfive.ftrade.dto.TradeRecordedDTO;
import com.fintechfive.ftrade.dto.TradeRejectedDTO;
import com.fintechfive.ftrade.dto.TradeSubmittedDTO;
import com.fintechfive.ftrade.dto.TradeValidatedDTO;
import com.fintechfive.ftrade.service.TradeTrackerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Follows each trade through the pipeline for the trade page, in its own consumer group, so the stages themselves
 * don't change. Each topic only gets a trade once the stage before it has finished. Runs with the api stage, which
 * serves GET /v1/accounts/{accountId}/trades/live.
 *
 * Tracking is only for showing, so a failure is logged and not thrown: the error handler would only retry it.
 */
@ConditionalOnProperty(name = "trade.stage.api.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class TradeTrackerConsumer {

    public static final String GROUP_ID = "trade-tracker";

    private static final Logger LOGGER = LoggerFactory.getLogger(TradeTrackerConsumer.class);

    private final TradeTrackerService tracker;

    public TradeTrackerConsumer(TradeTrackerService tracker) {
        this.tracker = tracker;
    }

    @KafkaListener(topics = KafkaTopics.TRADE_SUBMITTED, groupId = GROUP_ID)
    public void onTradeSubmitted(TradeSubmittedDTO trade) {
        track(() -> tracker.submitted(trade));
    }

    @KafkaListener(topics = KafkaTopics.TRADE_VALIDATED, groupId = GROUP_ID)
    public void onTradeValidated(TradeValidatedDTO trade) {
        track(() -> tracker.validated(trade.externalTradeId()));
    }

    @KafkaListener(topics = KafkaTopics.TRADE_RECORDED, groupId = GROUP_ID)
    public void onTradeRecorded(TradeRecordedDTO trade) {
        track(() -> tracker.recorded(trade.externalTradeId()));
    }

    // a null price means the trade was rejected
    @KafkaListener(topics = KafkaTopics.TRADE_FINISHED, groupId = GROUP_ID)
    public void onTradeFinished(TradeFinishedDTO trade) {
        if (trade.price() != null) {
            track(() -> tracker.fulfilled(trade.externalTradeId(), trade.price()));
        } else {
            track(() -> tracker.rejected(trade.externalTradeId(), trade.rejectionCode()));
        }
    }

    @KafkaListener(topics = KafkaTopics.TRADE_REJECTED, groupId = GROUP_ID)
    public void onTradeRejected(TradeRejectedDTO trade) {
        track(() -> tracker.rejected(trade.externalTradeId(), trade.rejectionCode()));
    }

    private static void track(Runnable step) {
        try {
            step.run();
        } catch (RuntimeException e) {
            LOGGER.warn("Couldn't track a trade step", e);
        }
    }
}
