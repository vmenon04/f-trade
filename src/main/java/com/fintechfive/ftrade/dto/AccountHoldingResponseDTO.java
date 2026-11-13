package com.fintechfive.ftrade.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountHoldingResponseDTO(
        UUID accountId,
        String name,
        String ticker,
        BigDecimal quantity,
        BigDecimal price,
        // the id to sell this holding with (POST /v1/accounts/{accountId}/trades)
        Long instrumentId,
        // the InstrumentType name, e.g. STOCK
        String assetClass
) {}
