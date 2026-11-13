package com.fintechfive.ftrade.kafka;

import com.fintechfive.ftrade.dto.TradeAcceptedDTO;
import com.fintechfive.ftrade.dto.TradeFinishedDTO;
import com.fintechfive.ftrade.dto.TradeRecordedDTO;
import com.fintechfive.ftrade.enums.RejectionCode;
import com.fintechfive.ftrade.enums.TradeSide;
import com.fintechfive.ftrade.service.ExecutionService;
import com.fintechfive.ftrade.service.TradeEventProducer;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionConsumerTest {

    @Mock
    private ExecutionService executionService;

    @Mock
    private TradeEventProducer tradeEventProducer;

    private ExecutionConsumer executionConsumer;

    private final TradeRecordedDTO trade = new TradeRecordedDTO(
            10L, 1L, TradeSide.BUY, new BigDecimal("5"), new BigDecimal("99"), 42L,
            UUID.fromString("22222222-2222-2222-2222-222222222222"));

    private final TradeFinishedDTO outcome = new TradeFinishedDTO(
            42L, 1L, 10L, TradeSide.BUY, new BigDecimal("5"), new BigDecimal("100"), null,
            UUID.fromString("22222222-2222-2222-2222-222222222222"), null);

    @BeforeEach
    void setUp() {
        executionConsumer = new ExecutionConsumer(executionService, tradeEventProducer);
    }

    @Test
    void executesTradeAndPublishesOutcome() throws Exception {
        when(executionService.execute(trade)).thenReturn(outcome);
        when(tradeEventProducer.publishTradeFinished(outcome)).thenReturn(CompletableFuture.completedFuture(null));

        executionConsumer.onTradeRecorded(trade);

        verify(tradeEventProducer).publishTradeFinished(outcome);
    }

    @Test
    void fulfilledTradeIsPublishedAsAcceptedBeforeFinished() throws Exception {
        when(executionService.execute(trade)).thenReturn(outcome);
        when(tradeEventProducer.publishTradeAccepted(outcome.toTradeAcceptedDTO()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(tradeEventProducer.publishTradeFinished(outcome)).thenReturn(CompletableFuture.completedFuture(null));

        executionConsumer.onTradeRecorded(trade);

        InOrder order = inOrder(tradeEventProducer);
        order.verify(tradeEventProducer).publishTradeAccepted(new TradeAcceptedDTO(
                42L, 1L, 10L, TradeSide.BUY, new BigDecimal("5"), new BigDecimal("100")));
        order.verify(tradeEventProducer).publishTradeFinished(outcome);
    }

    // a rejected trade was never accepted, so only its outcome is published
    @Test
    void rejectedTradeIsNotPublishedAsAccepted() throws Exception {
        TradeFinishedDTO rejected = new TradeFinishedDTO(
                42L, 1L, 10L, TradeSide.BUY, new BigDecimal("5"), null, "Insufficient funds",
                UUID.fromString("22222222-2222-2222-2222-222222222222"), RejectionCode.INSUFFICIENT_FUNDS);
        when(executionService.execute(trade)).thenReturn(rejected);
        when(tradeEventProducer.publishTradeFinished(rejected)).thenReturn(CompletableFuture.completedFuture(null));

        executionConsumer.onTradeRecorded(trade);

        verify(tradeEventProducer, never()).publishTradeAccepted(any());
        verify(tradeEventProducer).publishTradeFinished(rejected);
    }

    // the exception stops the offset being committed, so Kafka redelivers the trade and both are sent again
    @Test
    void failedAcceptedPublishFailsTheListenerBeforeFinishedIsSent() {
        when(executionService.execute(trade)).thenReturn(outcome);
        when(tradeEventProducer.publishTradeAccepted(any()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("Broker unavailable")));

        assertThrows(ExecutionException.class, () -> executionConsumer.onTradeRecorded(trade));
        verify(tradeEventProducer, never()).publishTradeFinished(any());
    }

    // the exception stops the offset being committed, so Kafka redelivers the trade
    @Test
    void failedPublishFailsTheListenerSoTheMessageIsRedelivered() {
        when(executionService.execute(trade)).thenReturn(outcome);
        when(tradeEventProducer.publishTradeFinished(outcome))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("Broker unavailable")));

        assertThrows(ExecutionException.class, () -> executionConsumer.onTradeRecorded(trade));
    }

    @Test
    void failedExecutionPropagatesWithoutPublishing() {
        when(executionService.execute(trade)).thenThrow(new DataAccessResourceFailureException("Database down"));

        assertThrows(DataAccessResourceFailureException.class, () -> executionConsumer.onTradeRecorded(trade));
        verifyNoInteractions(tradeEventProducer);
    }
}
