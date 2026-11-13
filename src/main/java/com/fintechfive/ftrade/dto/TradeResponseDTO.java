package com.fintechfive.ftrade.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TradeResponseDTO(
        Long instrumentId,
        String ticker,
        String name,
        UUID tradeId,
        OffsetDateTime tradeTime,
        String side,
        BigDecimal quantity,
        BigDecimal ppu,
        BigDecimal totalPrice,
        String status,
        String reason
) {}
