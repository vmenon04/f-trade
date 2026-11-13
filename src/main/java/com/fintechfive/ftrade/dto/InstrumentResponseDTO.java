package com.fintechfive.ftrade.dto;

import java.math.BigDecimal;

// price is null when no current price is available; assetClass is the InstrumentType name, e.g. STOCK
public record InstrumentResponseDTO(
        Long instrumentId,
        String ticker,
        String name,
        BigDecimal price,
        String assetClass
) {}
