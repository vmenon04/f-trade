package com.fintechfive.ftrade.kafka;

import com.fintechfive.ftrade.dto.TradeFinishedDTO;
import com.fintechfive.ftrade.dto.TradeRecordedDTO;
import com.fintechfive.ftrade.dto.TradeRejectedDTO;
import com.fintechfive.ftrade.dto.TradeSubmittedDTO;
import com.fintechfive.ftrade.dto.TradeValidatedDTO;
import com.fintechfive.ftrade.enums.RejectionCode;
import com.fintechfive.ftrade.enums.TradeSide;
import com.fintechfive.ftrade.service.TradeTrackerService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TradeTrackerConsumerTest {

    private static final UUID TRADE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final BigDecimal QUANTITY = new BigDecimal("5");
    private static final BigDecimal PRICE = new BigDecimal("100");

    @Mock
    private TradeTrackerService tracker;

    private TradeTrackerConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TradeTrackerConsumer(tracker);
    }

    @Test
    void submitted() {
        TradeSubmittedDTO trade = new TradeSubmittedDTO(10L, UUID.randomUUID(), TradeSide.BUY, QUANTITY, TRADE);

        consumer.onTradeSubmitted(trade);

        verify(tracker).submitted(trade);
    }

    @Test
    void validated() {
        consumer.onTradeValidated(new TradeValidatedDTO(10L, 1L, TradeSide.BUY, QUANTITY, PRICE, TRADE));

        verify(tracker).validated(TRADE);
    }

    @Test
    void recorded() {
        consumer.onTradeRecorded(new TradeRecordedDTO(10L, 1L, TradeSide.BUY, QUANTITY, PRICE, 42L, TRADE));

        verify(tracker).recorded(TRADE);
    }

    @Test
    void finishedWithAPrice_isFulfilled() {
        consumer.onTradeFinished(new TradeFinishedDTO(42L, 1L, 10L, TradeSide.BUY, QUANTITY, PRICE, null, TRADE, null));

        verify(tracker).fulfilled(TRADE, PRICE);
    }

    @Test
    void finishedWithoutAPrice_isRejected() {
        consumer.onTradeFinished(new TradeFinishedDTO(42L, 1L, 10L, TradeSide.BUY, QUANTITY, null,
                "Insufficient funds in account 1", TRADE, RejectionCode.INSUFFICIENT_FUNDS));

        verify(tracker).rejected(TRADE, RejectionCode.INSUFFICIENT_FUNDS);
    }

    @Test
    void rejectedAtValidation() {
        consumer.onTradeRejected(new TradeRejectedDTO(TRADE, RejectionCode.PRICE_UNAVAILABLE));

        verify(tracker).rejected(TRADE, RejectionCode.PRICE_UNAVAILABLE);
    }

    // a failure here would go to the error handler: retried for nothing, then given to the recoverer
    @Test
    void trackerFailure_doesNotThrow() {
        doThrow(new IllegalStateException("bad data")).when(tracker).validated(TRADE);

        assertDoesNotThrow(() -> consumer.onTradeValidated(
                new TradeValidatedDTO(10L, 1L, TradeSide.BUY, QUANTITY, PRICE, TRADE)));
    }
}
