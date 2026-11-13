package com.fintechfive.ftrade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record OrderResponseDTO(
        @Positive Long tradeId,
        @NotBlank String status,
        @Positive BigDecimal executedPrice,
        @Positive BigDecimal executedQuantity,
        @NotBlank String reason
) {}
