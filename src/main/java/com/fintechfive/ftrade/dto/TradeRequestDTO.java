package com.fintechfive.ftrade.dto;

import com.fintechfive.ftrade.enums.TradeSide;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record TradeRequestDTO(
        @NotNull UUID accountId,
        @Positive @NotNull Long instrumentId,
        @NotNull TradeSide side,
        @NotNull @Positive BigDecimal quantity
) {}
