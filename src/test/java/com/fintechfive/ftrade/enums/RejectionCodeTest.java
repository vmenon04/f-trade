package com.fintechfive.ftrade.enums;

import com.fintechfive.ftrade.exception.InsufficientFundsException;
import com.fintechfive.ftrade.exception.InsufficientHoldingsException;
import com.fintechfive.ftrade.exception.PriceUnavailableException;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RejectionCodeTest {

    @Test
    void insufficientFunds() {
        assertEquals(RejectionCode.INSUFFICIENT_FUNDS, RejectionCode.of(new InsufficientFundsException("no cash")));
    }

    @Test
    void insufficientHoldings() {
        assertEquals(RejectionCode.INSUFFICIENT_SHARES,
                RejectionCode.of(new InsufficientHoldingsException("no shares")));
    }

    @Test
    void priceUnavailable() {
        assertEquals(RejectionCode.PRICE_UNAVAILABLE, RejectionCode.of(new PriceUnavailableException("no price")));
    }

    // the market data API couldn't be reached
    @Test
    void marketDataUnreachable_isPriceUnavailable() {
        assertEquals(RejectionCode.PRICE_UNAVAILABLE, RejectionCode.of(new ResourceAccessException("timed out")));
    }

    @Test
    void invalidOrder() {
        assertEquals(RejectionCode.INVALID_ORDER, RejectionCode.of(new IllegalArgumentException("Quantity must be positive")));
    }

    @Test
    void unknownAccountOrInstrument_isInvalidOrder() {
        assertEquals(RejectionCode.INVALID_ORDER, RejectionCode.of(new NoSuchElementException("No instrument with id 9")));
    }

    // the Kafka error handler gets the listener's exception wrapped
    @Test
    void looksThroughWrappers() {
        Exception wrapped = new ListenerExecutionFailedException("listener failed",
                new RuntimeException(new InsufficientFundsException("no cash")));

        assertEquals(RejectionCode.INSUFFICIENT_FUNDS, RejectionCode.of(wrapped));
    }

    // the message doesn't matter, only the type
    @Test
    void otherFailure_isUnknown() {
        assertEquals(RejectionCode.UNKNOWN, RejectionCode.of(new IllegalStateException("Insufficient funds")));
    }

    @Test
    void nothing_isUnknown() {
        assertEquals(RejectionCode.UNKNOWN, RejectionCode.of(null));
    }
}
