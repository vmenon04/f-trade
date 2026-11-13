package com.fintechfive.ftrade.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record AccountDTO(
        @NotNull @Positive UUID accountId
) {}
