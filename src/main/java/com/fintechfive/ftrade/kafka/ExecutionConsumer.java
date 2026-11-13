package com.fintechfive.ftrade.kafka;

import com.fintechfive.ftrade.dto.TradeFinishedDTO;
import com.fintechfive.ftrade.dto.TradeRecordedDTO;
import com.fintechfive.ftrade.service.ExecutionService;
import com.fintechfive.ftrade.service.TradeEventProducer;
import java.util.concurrent.ExecutionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes recorded trades, executes them and publishes each outcome to trade.finished. A fulfilled trade is
 * published to trade.accepted first, so the reporting consumer sees its ACCEPTED step.
 * Runs unless trade.stage.execution.enabled=false, so a container can run only some of the stages.
 */
@ConditionalOnProperty(name = "trade.stage.execution.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class ExecutionConsumer {

    private final ExecutionService executionService;
    private final TradeEventProducer tradeEventProducer;

    public ExecutionConsumer(ExecutionService executionService, TradeEventProducer tradeEventProducer) {
        this.executionService = executionService;
        this.tradeEventProducer = tradeEventProducer;
    }
    
    @KafkaListener(topics = KafkaTopics.TRADE_RECORDED, groupId = "execution-service")
    public void onTradeRecorded(TradeRecordedDTO trade) throws InterruptedException, ExecutionException {
        TradeFinishedDTO outcome = executionService.execute(trade);
        if (outcome.price() != null) {
            tradeEventProducer.publishTradeAccepted(outcome.toTradeAcceptedDTO()).get();
        }
        tradeEventProducer.publishTradeFinished(outcome).get();
    }
}
