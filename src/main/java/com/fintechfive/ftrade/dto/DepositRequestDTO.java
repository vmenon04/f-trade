package com.fintechfive.ftrade.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record DepositRequestDTO(
        @NotNull @Positive BigDecimal amount
) {}
