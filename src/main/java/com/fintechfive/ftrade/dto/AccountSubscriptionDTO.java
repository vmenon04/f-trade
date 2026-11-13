package com.fintechfive.ftrade.dto;

import com.fintechfive.ftrade.enums.ActivityStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record AccountSubscriptionDTO(
        @Positive Long accountId,
        @Positive Long modelPortfolioId,
        @NotBlank String subscriptionDate,
        @NotNull ActivityStatus status
) {}
