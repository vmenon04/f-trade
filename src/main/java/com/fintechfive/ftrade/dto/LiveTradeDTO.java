package com.fintechfive.ftrade.dto;

import com.fintechfive.ftrade.enums.RejectionCode;
import com.fintechfive.ftrade.enums.TradeSide;
import com.fintechfive.ftrade.enums.TradeStage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A recent trade on the trade page, with each step it has reached so far.
 *
 * @param tradeId the trade's external identifier
 * @param ticker instrument ticker, or null if the instrument is gone
 * @param name instrument name, or null if the instrument is gone
 * @param stage the furthest step it has reached
 * @param finishedAt when it was fulfilled or rejected, or null while it is still going through
 * @param price unit price it was fulfilled at, or null
 * @param rejectionCode why it was rejected, or null
 */
public record LiveTradeDTO(
        UUID tradeId,
        String ticker,
        String name,
        TradeSide side,
        BigDecimal quantity,
        TradeStage stage,
        Instant submittedAt,
        Instant validatedAt,
        Instant recordedAt,
        Instant finishedAt,
        BigDecimal price,
        RejectionCode rejectionCode
) {
}
