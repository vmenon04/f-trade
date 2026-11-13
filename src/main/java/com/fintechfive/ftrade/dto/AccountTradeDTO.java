package com.fintechfive.ftrade.dto;

import com.fintechfive.ftrade.enums.TradeSide;
import com.fintechfive.ftrade.enums.TradeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AccountTradeDTO(
        @Positive Long tradeId,
        @NotBlank String tradeTime,
        @Positive Long accountId,
        @Positive Long instrumentId,
        @NotNull TradeSide tradeSide,
        @Positive BigDecimal quantity,
        @Positive BigDecimal ppu,
        @Positive BigDecimal totalPrice,
        @NotNull TradeStatus status
) {}
