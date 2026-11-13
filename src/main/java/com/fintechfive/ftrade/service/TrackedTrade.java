package com.fintechfive.ftrade.service;

import com.fintechfive.ftrade.enums.RejectionCode;
import com.fintechfive.ftrade.enums.TradeSide;
import com.fintechfive.ftrade.enums.TradeStage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A trade as the trade tracker has seen it go through the pipeline.
 *
 * @param finishedAt when it was fulfilled or rejected, or null while it is still going through
 * @param price unit price it was fulfilled at, or null
 * @param rejectionCode why it was rejected, or null
 */
public record TrackedTrade(
        UUID externalTradeId,
        Long instrumentId,
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
