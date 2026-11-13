package com.fintechfive.ftrade.dto;

import com.fintechfive.ftrade.enums.ActivityStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record ModelPortfolioHoldingDTO(
        @Positive Long modelPortfolioId,
        @Positive Long instrumentId,
        @NotBlank String effectiveDate,
        @PositiveOrZero double targetWeightPct,
        @NotNull ActivityStatus status
) {}
