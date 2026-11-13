package com.fintechfive.ftrade.dto;

import com.fintechfive.ftrade.enums.TradeSide;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record TradeSubmittedDTO(
        @NotNull @Positive Long instrumentId,
        @NotNull UUID accountId,
        @NotNull TradeSide side,
        @NotNull @Positive BigDecimal quantity,
        @NotNull UUID externalTradeId
) {}
