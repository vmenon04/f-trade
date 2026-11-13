package com.fintechfive.ftrade.dto;

import com.fintechfive.ftrade.enums.ActivityStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AccountHoldingDTO(
        @Positive Long accountId,
        @Positive Long instrumentId,
        @NotBlank String asOfDate,
        @Positive BigDecimal quantity,
        @NotNull ActivityStatus status
) {}
