package com.fintechfive.ftrade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ModelPortfolioDTO(
        @Positive Long modelPortfolioId,
        @NotBlank String name
) {}
