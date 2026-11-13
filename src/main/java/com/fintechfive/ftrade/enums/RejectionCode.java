package com.fintechfive.ftrade.enums;

import com.fintechfive.ftrade.exception.InsufficientFundsException;
import com.fintechfive.ftrade.exception.InsufficientHoldingsException;
import com.fintechfive.ftrade.exception.PriceUnavailableException;
import java.util.NoSuchElementException;
import org.springframework.web.client.RestClientException;

// Why a trade was rejected, safe to show the user (the exception's message isn't: it has internal ids and balances).
// It is decided by the exception's type, so messages can be reworded freely
public enum RejectionCode {
    INSUFFICIENT_FUNDS, INSUFFICIENT_SHARES, PRICE_UNAVAILABLE, INVALID_ORDER, UNKNOWN;

    // the failure or the first of its causes with a known type; the Kafka error handler gets the listener's
    // exception wrapped
    public static RejectionCode of(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            RejectionCode code = codeFor(cause);
            if (code != UNKNOWN) {
                return code;
            }
        }
        return UNKNOWN;
    }

    private static RejectionCode codeFor(Throwable failure) {
        if (failure instanceof InsufficientFundsException) {
            return INSUFFICIENT_FUNDS;
        }
        if (failure instanceof InsufficientHoldingsException) {
            return INSUFFICIENT_SHARES;
        }
        // RestClientException: the market data API couldn't be reached
        if (failure instanceof PriceUnavailableException || failure instanceof RestClientException) {
            return PRICE_UNAVAILABLE;
        }
        // an unknown account or instrument, or an invalid quantity or side
        if (failure instanceof IllegalArgumentException || failure instanceof NoSuchElementException) {
            return INVALID_ORDER;
        }
        return UNKNOWN;
    }
}
