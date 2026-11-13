package com.fintechfive.ftrade.dto;

import com.fintechfive.ftrade.enums.TradeSide;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record TradeValidatedDTO(

        @Positive Long instrumentId,
        @Positive Long accountId,
        @NotNull TradeSide side,
        @Positive BigDecimal quantity,
        @Positive BigDecimal quote,
        @NotNull UUID externalTradeId
){}
