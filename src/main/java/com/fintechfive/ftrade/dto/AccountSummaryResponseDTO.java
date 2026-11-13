package com.fintechfive.ftrade.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountSummaryResponseDTO(
        UUID accountId,
        BigDecimal investmentValue,
        BigDecimal portfolioValue,
        BigDecimal cashBalance,
        BigDecimal costBasis
) {}
