package com.fintechfive.ftrade.dto;

import com.fintechfive.ftrade.enums.TradeSide;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record TradeRecordedDTO(
    @Positive Long instrumentId,
    @Positive Long accountId,
    @NotBlank TradeSide side,
    @NotNull @Positive BigDecimal quantity,
    @NotNull @Positive BigDecimal quote,
    @NotBlank Long tradeId,
    @NotNull UUID externalTradeId
){}
