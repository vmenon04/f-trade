package com.fintechfive.ftrade.kafka;

import com.fintechfive.ftrade.dto.TradeSubmittedDTO;
import com.fintechfive.ftrade.dto.TradeValidatedDTO;
import com.fintechfive.ftrade.enums.TradeSide;
import com.fintechfive.ftrade.service.TradeEventProducer;
import com.fintechfive.ftrade.service.ValidationService;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidationConsumerTest {

    private static final UUID EXTERNAL_TRADE_ID = UUID.randomUUID();

    @Mock
    private ValidationService validationService;

    @Mock
    private TradeEventProducer tradeEventProducer;

    private ValidationConsumer validationConsumer;

    private final TradeSubmittedDTO trade = new TradeSubmittedDTO(
            10L, UUID.randomUUID(), TradeSide.BUY, new BigDecimal("5"), EXTERNAL_TRADE_ID);

    private final TradeValidatedDTO validated = new TradeValidatedDTO(
            10L, 1L, TradeSide.BUY, new BigDecimal("5"), new BigDecimal("99"), EXTERNAL_TRADE_ID);

    @BeforeEach
    void setUp() {
        validationConsumer = new ValidationConsumer(validationService, tradeEventProducer);
    }

    @Test
    void validatesTradeAndPublishesIt() throws Exception {
        when(validationService.validateSubmittedTrade(trade)).thenReturn(validated);
        when(tradeEventProducer.publishTradeValidated(validated)).thenReturn(CompletableFuture.completedFuture(null));

        validationConsumer.onTradeSubmitted(trade);

        verify(tradeEventProducer).publishTradeValidated(validated);
    }

    @Test
    void invalidTradeIsNotPublished() {
        when(validationService.validateSubmittedTrade(trade))
                .thenThrow(new IllegalStateException("Insufficient funds"));

        assertThrows(IllegalStateException.class, () -> validationConsumer.onTradeSubmitted(trade));
        verifyNoInteractions(tradeEventProducer);
    }

    @Test
    void failedPublishFailsTheListenerSoTheMessageIsRedelivered() {
        when(validationService.validateSubmittedTrade(trade)).thenReturn(validated);
        when(tradeEventProducer.publishTradeValidated(validated))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("Broker unavailable")));

        assertThrows(ExecutionException.class, () -> validationConsumer.onTradeSubmitted(trade));
    }
}
