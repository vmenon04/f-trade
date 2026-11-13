package com.fintechfive.ftrade.kafka;

import com.fintechfive.ftrade.dto.TradeRejectedDTO;
import com.fintechfive.ftrade.enums.RejectionCode;
import com.fintechfive.ftrade.exception.InsufficientFundsException;
import com.fintechfive.ftrade.service.TradeEventProducer;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.KafkaUtils;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeRejectionRecovererTest {

    private static final UUID EXTERNAL_TRADE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String SUBMITTED = "{\"instrumentId\":10,"
            + "\"accountId\":\"11111111-1111-1111-1111-111111111111\","
            + "\"side\":\"BUY\",\"quantity\":5,\"externalTradeId\":\"" + EXTERNAL_TRADE_ID + "\"}";

    @Mock
    private TradeEventProducer tradeEventProducer;

    private TradeRejectionRecoverer recoverer;

    @BeforeEach
    void setUp() {
        recoverer = new TradeRejectionRecoverer(tradeEventProducer, JsonMapper.builder().build());
        // set by the listener container on its consumer thread
        KafkaUtils.setConsumerGroupId(ValidationConsumer.GROUP_ID);
    }

    @AfterEach
    void clearGroup() {
        KafkaUtils.clearConsumerGroupId();
    }

    private static ConsumerRecord<String, String> consumerRecord(String topic, String value) {
        return new ConsumerRecord<>(topic, 0, 7L, "key", value);
    }

    // the listener's exception reaches the recoverer wrapped, as it does from a real container
    private static Exception failure(Exception cause) {
        return new ListenerExecutionFailedException("listener failed", cause);
    }

    @Test
    void submittedTradeThatFailedValidation_isPublishedAsRejected() {
        TradeRejectedDTO rejected = new TradeRejectedDTO(EXTERNAL_TRADE_ID, RejectionCode.INSUFFICIENT_FUNDS);
        when(tradeEventProducer.publishTradeRejected(rejected)).thenReturn(CompletableFuture.completedFuture(null));

        recoverer.accept(consumerRecord(KafkaTopics.TRADE_SUBMITTED, SUBMITTED),
                failure(new InsufficientFundsException("Insufficient funds in account 1")));

        verify(tradeEventProducer).publishTradeRejected(rejected);
    }

    @Test
    void unexpectedFailure_isPublishedAsUnknown() {
        TradeRejectedDTO rejected = new TradeRejectedDTO(EXTERNAL_TRADE_ID, RejectionCode.UNKNOWN);
        when(tradeEventProducer.publishTradeRejected(rejected)).thenReturn(CompletableFuture.completedFuture(null));

        recoverer.accept(consumerRecord(KafkaTopics.TRADE_SUBMITTED, SUBMITTED),
                failure(new RuntimeException("Database down")));

        verify(tradeEventProducer).publishTradeRejected(rejected);
    }

    // a trade that failed recording or execution is already in the database, so it isn't trade.rejected's
    @Test
    void otherTopics_areOnlyLogged() {
        recoverer.accept(consumerRecord(KafkaTopics.TRADE_RECORDED, "{\"tradeId\":42}"),
                failure(new RuntimeException("Database down")));
        recoverer.accept(consumerRecord(KafkaTopics.TRADE_VALIDATED, SUBMITTED),
                failure(new RuntimeException("Database down")));

        verifyNoInteractions(tradeEventProducer);
    }

    // the trade tracker reads trade.submitted too; its failures aren't the trade's
    @Test
    void submittedTradeThatFailedInAnotherGroup_isOnlyLogged() {
        KafkaUtils.setConsumerGroupId(TradeTrackerConsumer.GROUP_ID);

        recoverer.accept(consumerRecord(KafkaTopics.TRADE_SUBMITTED, SUBMITTED),
                failure(new RuntimeException("Redis down")));

        verifyNoInteractions(tradeEventProducer);
    }

    @Test
    void unreadableMessage_isOnlyLogged() {
        assertDoesNotThrow(() -> recoverer.accept(consumerRecord(KafkaTopics.TRADE_SUBMITTED, "not json"),
                failure(new RuntimeException("can't convert"))));

        verifyNoInteractions(tradeEventProducer);
    }

    @Test
    void messageWithoutATradeId_isOnlyLogged() {
        assertDoesNotThrow(() -> recoverer.accept(consumerRecord(KafkaTopics.TRADE_SUBMITTED, "{\"instrumentId\":10}"),
                failure(new IllegalArgumentException("Quantity must be positive"))));

        verifyNoInteractions(tradeEventProducer);
    }

    // throwing here would have the error handler run validation again
    @Test
    void publishThatFails_doesNotThrow() {
        when(tradeEventProducer.publishTradeRejected(any())).thenThrow(new RuntimeException("Kafka down"));

        assertDoesNotThrow(() -> recoverer.accept(consumerRecord(KafkaTopics.TRADE_SUBMITTED, SUBMITTED),
                failure(new InsufficientFundsException("Insufficient funds in account 1"))));
    }

    @Test
    void publishThatFailsLater_doesNotThrow() {
        when(tradeEventProducer.publishTradeRejected(any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka down")));

        assertDoesNotThrow(() -> recoverer.accept(consumerRecord(KafkaTopics.TRADE_SUBMITTED, SUBMITTED),
                failure(new InsufficientFundsException("Insufficient funds in account 1"))));
    }
}
