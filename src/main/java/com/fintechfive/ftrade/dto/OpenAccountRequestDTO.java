package com.fintechfive.ftrade.dto;

import jakarta.validation.Valid;

// opens a trading account; clientDetails is only needed for the user's first one, which also creates their client
// (when given, every field is validated; when null, validation skips it)
public record OpenAccountRequestDTO(@Valid ClientDetailsDTO clientDetails) {}
