package com.fintechfive.ftrade.dto;

import com.fintechfive.ftrade.enums.RejectionCode;
import java.util.UUID;

/**
 * Message published to trade.rejected for a trade that failed validation, so it never reached the database.
 *
 * @param externalTradeId the trade's public identifier
 * @param rejectionCode why it was rejected, safe to show the user
 */
public record TradeRejectedDTO(
        UUID externalTradeId,
        RejectionCode rejectionCode
) {
}
